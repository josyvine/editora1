package com.vineyard.aivideostudio.media.transformer

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.vineyard.aivideostudio.core.result.AppError
import com.vineyard.aivideostudio.core.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class Media3TransformerEngine(private val context: Context) {

    suspend fun trimVideo(
        inputUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long
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

        val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()
        runTransformer(editedMediaItem, outputFile)
    }

    suspend fun cropVideo(
        inputUri: Uri,
        outputFile: File,
        normalizedLeft: Float,
        normalizedRight: Float,
        normalizedBottom: Float,
        normalizedTop: Float
    ): AppResult<File> = withContext(Dispatchers.Main) {
        outputFile.parentFile?.mkdirs()

        // Media3 Crop takes coordinates in range [-1, 1] or normalized boundaries
        // Standard normalized (0 to 1) mapped to Crop (-1 to 1)
        val left = (normalizedLeft * 2f) - 1f
        val right = (normalizedRight * 2f) - 1f
        val bottom = (normalizedBottom * 2f) - 1f
        val top = (normalizedTop * 2f) - 1f

        val cropEffect = Crop(left, right, bottom, top)
        val effects = Effects(emptyList(), listOf(cropEffect))

        val mediaItem = MediaItem.fromUri(inputUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .build()

        runTransformer(editedMediaItem, outputFile)
    }

    suspend fun zoomVideo(
        inputUri: Uri,
        outputFile: File,
        scale: Float
    ): AppResult<File> = withContext(Dispatchers.Main) {
        outputFile.parentFile?.mkdirs()

        val scaleEffect = ScaleAndRotateTransformation.Builder()
            .setScale(scale, scale)
            .build()
        val effects = Effects(emptyList(), listOf(scaleEffect))

        val mediaItem = MediaItem.fromUri(inputUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .build()

        runTransformer(editedMediaItem, outputFile)
    }

    suspend fun exportVideo(
        inputUri: Uri,
        outputFile: File,
        targetAspectRatio: String = "ORIGINAL"
    ): AppResult<File> = withContext(Dispatchers.Main) {
        outputFile.parentFile?.mkdirs()

        val videoEffects = mutableListOf<Effect>()
        when (targetAspectRatio) {
            "9:16" -> videoEffects.add(Presentation.createForAspectRatio(9f / 16f, Presentation.LAYOUT_SCALE_TO_FIT))
            "16:9" -> videoEffects.add(Presentation.createForAspectRatio(16f / 9f, Presentation.LAYOUT_SCALE_TO_FIT))
            "1:1" -> videoEffects.add(Presentation.createForAspectRatio(1f, Presentation.LAYOUT_SCALE_TO_FIT))
            else -> {} // Keep original
        }

        val mediaItem = MediaItem.fromUri(inputUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        runTransformer(editedMediaItem, outputFile)
    }

    private suspend fun runTransformer(
        editedMediaItem: EditedMediaItem,
        outputFile: File
    ): AppResult<File> = suspendCancellableCoroutine { continuation ->
        var activeTransformer: Transformer? = null

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                if (continuation.isActive) {
                    continuation.resume(AppResult.Success(outputFile))
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                if (continuation.isActive) {
                    continuation.resume(
                        AppResult.Error(
                            AppError.MediaProcessingError(
                                "Media export failed: ${exportException.message}",
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

            activeTransformer.start(editedMediaItem, outputFile.absolutePath)
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume(
                    AppResult.Error(
                        AppError.MediaProcessingError("Failed to start Transformer: ${e.message}", e)
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
}
