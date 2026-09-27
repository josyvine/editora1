package com.vineyard.aivideostudio.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vineyard.aivideostudio.core.util.TimeUtils
import com.vineyard.aivideostudio.ui.components.AppTopBar
import com.vineyard.aivideostudio.ui.components.VideoPreviewPlayer
import com.vineyard.aivideostudio.ui.theme.AmberAccent
import com.vineyard.aivideostudio.ui.theme.BorderSubtle
import com.vineyard.aivideostudio.ui.theme.EmeraldSuccess
import com.vineyard.aivideostudio.ui.theme.RoseError
import com.vineyard.aivideostudio.ui.theme.StudioDarkBg
import com.vineyard.aivideostudio.ui.theme.StudioSurface
import com.vineyard.aivideostudio.ui.theme.StudioSurfaceElevated
import com.vineyard.aivideostudio.ui.theme.TextPrimary
import com.vineyard.aivideostudio.ui.theme.TextSecondary
import com.vineyard.aivideostudio.ui.theme.TextTertiary
import com.vineyard.aivideostudio.ui.theme.VioletAccent
import com.vineyard.aivideostudio.ui.theme.VioletPrimary

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val project = state.project
    var selectedTab by remember { mutableIntStateOf(0) }

    // Locate active commentary soundtrack if available
    val activeCommentaryUri = state.commentary.firstOrNull { !it.audioArtifactUri.isNullOrBlank() }?.audioArtifactUri

    Scaffold(
        topBar = {
            AppTopBar(
                title = project?.name ?: "Studio Inspector",
                subtitle = "Timeline & Asset Inspection",
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = StudioDarkBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Synchronized Video Preview (Subtitles rendered in real time, Original Audio Muted)
            item {
                Spacer(modifier = Modifier.height(4.dp))
                VideoPreviewPlayer(
                    videoUriString = project?.currentVideoUri,
                    captions = state.captions,
                    commentaryUriString = activeCommentaryUri,
                    muteOriginalAudio = true, // Enforce copyright protection during preview
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }

            // 2. Copyright & Audio Replacement Security Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Copyright Safe",
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Copyright Transform Active",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Source audio purged • Replaced with original AI voice",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activeCommentaryUri != null) VioletPrimary.copy(alpha = 0.2f) else EmeraldSuccess.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (activeCommentaryUri != null) "LIVE AUDIO ACTIVE" else "AUDIO MUTED",
                                color = if (activeCommentaryUri != null) VioletAccent else EmeraldSuccess,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 3. Navigation Tabs: Timeline, Captions, Commentary, QA History
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = StudioSurfaceElevated,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = VioletPrimary
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Timeline", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Captions", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Commentary", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("QA Log", fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            // 4. Tab Content Panels
            when (selectedTab) {
                0 -> {
                    // Timeline Segments Panel
                    item {
                        Text(
                            text = "ACTIVE TIMELINE SEGMENTS (${state.timelineSegments.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (state.timelineSegments.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Base timeline active. Pacing cuts applied in final render.",
                                    color = TextSecondary,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(state.timelineSegments.size) { index ->
                            val seg = state.timelineSegments[index]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Segment #${index + 1} (${seg.stageApplied.name})",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Original: ${TimeUtils.formatDuration(seg.originalStart)} → ${TimeUtils.formatDuration(seg.originalEnd)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(VioletPrimary.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${TimeUtils.formatDuration(seg.currentStart)} - ${TimeUtils.formatDuration(seg.currentEnd)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VioletAccent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Captions & Subtitles Panel
                    item {
                        Text(
                            text = "BURNED-IN SUBTITLES (${state.captions.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (state.captions.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "No captions configured for this project.",
                                    color = TextSecondary,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(state.captions.size) { idx ->
                            val cap = state.captions[idx]

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${TimeUtils.formatDuration(cap.start)} → ${TimeUtils.formatDuration(cap.end)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AmberAccent,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Pos (${(cap.x * 100).toInt()}%, ${(cap.y * 100).toInt()}%)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextTertiary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = cap.text,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Voiceover Commentary Panel
                    item {
                        Text(
                            text = "LIVE VOICEOVER COMMENTARY (${state.commentary.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (state.commentary.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "No commentary track synthesized yet.",
                                    color = TextSecondary,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(state.commentary.size) { idx ->
                            val comm = state.commentary[idx]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${TimeUtils.formatDuration(comm.start)} → ${TimeUtils.formatDuration(comm.end)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VioletAccent,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (!comm.audioArtifactUri.isNullOrBlank()) {
                                            Text(
                                                text = "AUDIO SYNCED",
                                                color = EmeraldSuccess,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = comm.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // QA Log Inspection Panel
                    item {
                        Text(
                            text = "INSPECTION & QA SIGN-OFFS (${state.qaResults.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (state.qaResults.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "No QA sign-off records generated.",
                                    color = TextSecondary,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(state.qaResults.size) { idx ->
                            val qa = state.qaResults[idx]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = qa.stage.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = qa.verdict.name,
                                            color = if (qa.verdict.name == "PASS") EmeraldSuccess else RoseError,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = qa.feedback,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}