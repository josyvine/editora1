package com.vineyard.aivideostudio.media.transformer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.vineyard.aivideostudio.core.model.Caption
import com.vineyard.aivideostudio.core.result.AppError
import com.vineyard.aivideostudio.core.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class Media3TransformerEngine(private val context: Context) {

    /**
     * Clips video boundaries (dead-air removal / pacing cuts).
     */
    suspend fun trimVideo(
        inputUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long,
        stripAudio: Boolean = false
    ): AppResult<File> = withContext(Dispatchers.Main) {
        outputFile.parentFile?.mkdirs()

        val mediaItem = MediaItem.Builder()
            .setUri(inputUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(stripAudio)
            .build()

        runTransformer(editedMediaItem, outputFile)
    }

    /**
     * Crops and reframes video using normalized coordinates.
     */
    suspend fun cropVideo(
        inputUri: Uri,
        outputFile: File,
        normalizedLeft: Float,
        normalizedRight: Float,
        normalizedBottom: Float,
        normalizedTop: Float,
        stripAudio: Boolean = false
    ): AppResult<File> = withContext(Dispatchers.Main) {
        outputFile.parentFile?.mkdirs()

        // Standard normalized [0, 1] mapped to Media3 Crop [-1, 1]
        val left = (normalizedLeft * 2f) - 1f
        val right = (normalizedRight * 2f) - 1f
        val bottom = (normalizedBottom * 2f) - 1f
        val top = (normalizedTop * 2f) - 1f

        val cropEffect = Crop(left, right, bottom, top)
        val effects = Effects(emptyList(), listOf(cropEffect))

        val mediaItem = MediaItem.fromUri(inputUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(stripAudio)
            .setEffects(effects)
            .build()

        runTransformer(editedMediaItem, outputFile)
    }

    /**
     * Applies punch-in zoom scaling to the video frames.
     */
    suspend fun zoomVideo(
        inputUri: Uri,
        outputFile: File,
        scale: Float,
        stripAudio: Boolean = false
    ): AppResult<File> = withContext(Dispatchers.Main) {
        outputFile.parentFile?.mkdirs()

        val scaleEffect = ScaleAndRotateTransformation.Builder()
            .setScale(scale, scale)
            .build()
        val effects = Effects(emptyList(), listOf(scaleEffect))

        val mediaItem = MediaItem.fromUri(inputUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(stripAudio)
            .setEffects(effects)
            .build()

        runTransformer(editedMediaItem, outputFile)
    }

    /**
     * Comprehensive final export pass:
     * 1. Strips copyrighted source audio track.
     * 2. Injects the replacement commentary audio file (M4A/AAC) as primary soundtrack.
     * 3. Burns subtitle captions directly into the video frames.
     * 4. Applies target aspect ratio reframing and optional zoom punch-in.
     */
    suspend fun exportVideo(
        inputUri: Uri,
        outputFile: File,
        commentaryAudioUri: Uri? = null,
        stripOriginalAudio: Boolean = true,
        captions: List<Caption> = emptyList(),
        targetAspectRatio: String = "ORIGINAL",
        zoomScale: Float = 1.0f
    ): AppResult<File> = withContext(Dispatchers.Main) {
        outputFile.parentFile?.mkdirs()

        val videoEffects = mutableListOf<Effect>()

        // 1. Aspect Ratio Presentation Effect
        when (targetAspectRatio) {
            "9:16" -> videoEffects.add(Presentation.createForAspectRatio(9f / 16f, Presentation.LAYOUT_SCALE_TO_FIT))
            "16:9" -> videoEffects.add(Presentation.createForAspectRatio(16f / 9f, Presentation.LAYOUT_SCALE_TO_FIT))
            "1:1" -> videoEffects.add(Presentation.createForAspectRatio(1f, Presentation.LAYOUT_SCALE_TO_FIT))
            else -> {} // Keep original aspect ratio
        }

        // 2. Zoom Punch-In Effect (if scale factor is specified)
        if (zoomScale > 1.0f) {
            videoEffects.add(
                ScaleAndRotateTransformation.Builder()
                    .setScale(zoomScale, zoomScale)
                    .build()
            )
        }

        // 3. Caption Burn-In Overlay Effect
        if (captions.isNotEmpty()) {
            val captionOverlay = SubtitleBitmapOverlay(captions)
            videoEffects.add(OverlayEffect(ImmutableList.of(captionOverlay)))
        }

        // 4. Construct Video Sequence with Audio Removal
        val videoMediaItem = MediaItem.fromUri(inputUri)
        val editedVideoItem = EditedMediaItem.Builder(videoMediaItem)
            .setRemoveAudio(stripOriginalAudio)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()
        val videoSequence = EditedMediaItemSequence(editedVideoItem)

        // 5. Construct Multi-Track Composition (Inject Replacement Commentary Track)
        val composition = if (commentaryAudioUri != null) {
            val audioMediaItem = MediaItem.fromUri(commentaryAudioUri)
            val editedAudioItem = EditedMediaItem.Builder(audioMediaItem)
                .setRemoveVideo(true)
                .build()
            val audioSequence = EditedMediaItemSequence(editedAudioItem)

            Composition.Builder(listOf(videoSequence, audioSequence)).build()
        } else {
            Composition.Builder(listOf(videoSequence)).build()
        }

        runTransformer(composition, outputFile)
    }

    private suspend fun runTransformer(
        editedMediaItem: EditedMediaItem,
        outputFile: File
    ): AppResult<File> {
        val sequence = EditedMediaItemSequence(editedMediaItem)
        val composition = Composition.Builder(listOf(sequence)).build()
        return runTransformer(composition, outputFile)
    }

    private suspend fun runTransformer(
        composition: Composition,
        outputFile: File
    ): AppResult<File> = suspendCancellableCoroutine { continuation ->
        var activeTransformer: Transformer? = null

        val listener = object : Transformer.Listener {
            override fun onCompleted(comp: Composition, exportResult: ExportResult) {
                if (continuation.isActive) {
                    continuation.resume(AppResult.Success(outputFile))
                }
            }

            override fun onError(
                comp: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                if (continuation.isActive) {
                    continuation.resume(
                        AppResult.Error(
                            AppError.MediaProcessingError(
                                "Media3 transformation failed: ${exportException.message}",
                                exportException
                            )
                        )
                    )
                }
            }
        }

        try {
            activeTransformer = Transformer.Builder(context)
                .addListener(listener)
                .build()

            activeTransformer.start(composition, outputFile.absolutePath)
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume(
                    AppResult.Error(
                        AppError.MediaProcessingError("Failed starting Media3 Transformer: ${e.message}", e)
                    )
                )
            }
        }

        continuation.invokeOnCancellation {
            try {
                activeTransformer?.cancel()
            } catch (_: Exception) {}
        }
    }

    /**
     * Dynamic BitmapOverlay that renders synchronized subtitles directly onto video frames.
     */
    private class SubtitleBitmapOverlay(
        private val captions: List<Caption>,
        private val targetWidth: Int = 1080,
        private val targetHeight: Int = 1920
    ) : BitmapOverlay() {

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            strokeWidth = 10f
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        private val backgroundPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val frameBitmap: Bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        private val canvas: Canvas = Canvas(frameBitmap)

        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val currentMs = presentationTimeUs / 1000L
            frameBitmap.eraseColor(Color.TRANSPARENT)

            val activeCaption = captions.firstOrNull { currentMs in it.startMs..it.endMs }
            if (activeCaption != null && activeCaption.text.isNotBlank()) {
                val posX = if (activeCaption.positionX > 1.0f) activeCaption.positionX / 100f else activeCaption.positionX
                val posY = if (activeCaption.positionY > 1.0f) activeCaption.positionY / 100f else activeCaption.positionY

                val x = targetWidth * posX.coerceIn(0.05f, 0.95f)
                val y = targetHeight * posY.coerceIn(0.1f, 0.95f)

                val scaledFontSize = if (activeCaption.fontSize > 0f) {
                    activeCaption.fontSize * (targetWidth / 480f)
                } else {
                    56f
                }

                textPaint.textSize = scaledFontSize
                strokePaint.textSize = scaledFontSize

                try {
                    textPaint.color = Color.parseColor(activeCaption.fontColor)
                } catch (_: Exception) {
                    textPaint.color = Color.WHITE
                }

                // Render background highlight box if configured
                if (activeCaption.backgroundColor.isNotBlank() && activeCaption.backgroundColor != "#00000000") {
                    try {
                        backgroundPillPaint.color = Color.parseColor(activeCaption.backgroundColor)
                        val textWidth = textPaint.measureText(activeCaption.text)
                        val padH = 28f
                        val padV = 16f
                        val pillRect = RectF(
                            x - (textWidth / 2f) - padH,
                            y - scaledFontSize - padV,
                            x + (textWidth / 2f) + padH,
                            y + padV
                        )
                        canvas.drawRoundRect(pillRect, 20f, 20f, backgroundPillPaint)
                    } catch (_: Exception) {}
                }

                // Draw solid black stroke for high-contrast visibility on any video background
                canvas.drawText(activeCaption.text, x, y, strokePaint)
                // Draw main text
                canvas.drawText(activeCaption.text, x, y, textPaint)
            }

            return frameBitmap
        }
    }
}