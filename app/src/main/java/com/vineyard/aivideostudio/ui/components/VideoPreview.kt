package com.vineyard.aivideostudio.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.vineyard.aivideostudio.core.model.Caption
import com.vineyard.aivideostudio.media.preview.PreviewPlayer
import com.vineyard.aivideostudio.ui.theme.EmeraldSuccess
import com.vineyard.aivideostudio.ui.theme.RoseError
import com.vineyard.aivideostudio.ui.theme.StudioDarkBg
import com.vineyard.aivideostudio.ui.theme.TextSecondary
import com.vineyard.aivideostudio.ui.theme.VioletPrimary

@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewPlayer(
    previewPlayer: PreviewPlayer,
    modifier: Modifier = Modifier,
    captions: List<Caption> = emptyList()
) {
    val currentPositionMs by previewPlayer.currentPosition.collectAsState()
    val isOriginalAudioMuted by previewPlayer.isOriginalAudioMuted.collectAsState()
    val hasCommentaryTrack by previewPlayer.hasCommentaryTrack.collectAsState()

    // Match playback timestamp (in seconds) to caption start/end (Double)
    val currentPositionSec = currentPositionMs / 1000.0
    val activeCaption = remember(currentPositionMs, captions) {
        captions.firstOrNull { currentPositionSec >= it.start && currentPositionSec <= it.end }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(StudioDarkBg)
    ) {
        // 1. Android Media3 Video Player
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = previewPlayer.getPlayer()
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Audio Copyright & Live Commentary Status Badge
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xCC121620))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isOriginalAudioMuted) EmeraldSuccess else RoseError)
            )
            Text(
                text = if (isOriginalAudioMuted) "SOURCE AUDIO: MUTED" else "SOURCE AUDIO: ACTIVE",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp)
            )

            if (hasCommentaryTrack) {
                Text(
                    text = "• LIVE COMMENTARY",
                    color = VioletPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }

        // 3. Real-Time Subtitle Overlay Layer
        if (activeCaption != null && activeCaption.text.isNotBlank()) {
            val posX = if (activeCaption.x > 1.0f) activeCaption.x / 100f else activeCaption.x
            val posY = if (activeCaption.y > 1.0f) activeCaption.y / 100f else activeCaption.y

            // Convert [0, 1] normalized space to Compose BiasAlignment [-1, 1]
            val biasX = ((posX.coerceIn(0.1f, 0.9f) * 2f) - 1f)
            val biasY = ((posY.coerceIn(0.1f, 0.95f) * 2f) - 1f)

            val parsedTextColor = remember(activeCaption.fontColorHex) {
                try {
                    Color(android.graphics.Color.parseColor(activeCaption.fontColorHex))
                } catch (_: Exception) {
                    Color.White
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(BiasAlignment(biasX, biasY))
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = BiasAlignment(biasX, biasY)
            ) {
                Text(
                    text = activeCaption.text.uppercase(),
                    color = parsedTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC000000))
                        .border(1.5.dp, Color(0x66FFFFFF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Standalone overload that instantiates a managed PreviewPlayer for simple screen integration.
 */
@Composable
fun VideoPreviewPlayer(
    videoUriString: String?,
    modifier: Modifier = Modifier,
    captions: List<Caption> = emptyList(),
    commentaryUriString: String? = null,
    muteOriginalAudio: Boolean = true,
    autoPlay: Boolean = false
) {
    val context = LocalContext.current

    if (videoUriString.isNullOrBlank()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(StudioDarkBg),
            contentAlignment = Alignment.Center
        ) {
            Text("No video source selected", color = TextSecondary)
        }
        return
    }

    val previewPlayer = remember(videoUriString, commentaryUriString, muteOriginalAudio) {
        PreviewPlayer(context).apply {
            setMedia(
                videoUri = Uri.parse(videoUriString),
                commentaryAudioUri = commentaryUriString?.let { Uri.parse(it) },
                muteOriginalAudio = muteOriginalAudio,
                playWhenReady = autoPlay
            )
        }
    }

    DisposableEffect(previewPlayer) {
        onDispose {
            previewPlayer.release()
        }
    }

    VideoPreviewPlayer(
        previewPlayer = previewPlayer,
        modifier = modifier,
        captions = captions
    )
}