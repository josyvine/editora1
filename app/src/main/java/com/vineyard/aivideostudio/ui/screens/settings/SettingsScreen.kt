package com.vineyard.aivideostudio.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vineyard.aivideostudio.ai.model.ModelPurpose
import com.vineyard.aivideostudio.core.util.TimeUtils
import com.vineyard.aivideostudio.ui.components.AppTopBar
import com.vineyard.aivideostudio.ui.components.ModelDropdown
import com.vineyard.aivideostudio.ui.theme.AmberAccent
import com.vineyard.aivideostudio.ui.theme.BorderSubtle
import com.vineyard.aivideostudio.ui.theme.CyanInfo
import com.vineyard.aivideostudio.ui.theme.EmeraldSuccess
import com.vineyard.aivideostudio.ui.theme.RoseError
import com.vineyard.aivideostudio.ui.theme.StudioCardBg
import com.vineyard.aivideostudio.ui.theme.StudioDarkBg
import com.vineyard.aivideostudio.ui.theme.StudioSurface
import com.vineyard.aivideostudio.ui.theme.StudioSurfaceElevated
import com.vineyard.aivideostudio.ui.theme.TextPrimary
import com.vineyard.aivideostudio.ui.theme.TextSecondary
import com.vineyard.aivideostudio.ui.theme.TextTertiary
import com.vineyard.aivideostudio.ui.theme.VioletAccent
import com.vineyard.aivideostudio.ui.theme.VioletPrimary

enum class SettingsTab(
    val title: String,
    val icon: ImageVector,
    val testTag: String
) {
    KEY("API Key", Icons.Filled.Key, "settings_tab_api_key"),
    MODELS("Models", Icons.Filled.AutoAwesome, "settings_tab_models"),
    PIPELINE("Pipeline", Icons.Filled.Tune, "settings_tab_pipeline"),
    VOICES("Voices", Icons.Filled.RecordVoiceOver, "settings_tab_voices"),
    ABOUT("About", Icons.Filled.Info, "settings_tab_about")
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(SettingsTab.KEY) }
    var showVoiceDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Settings",
                subtitle = "Studio Preferences & Configuration"
            )
        },
        containerColor = StudioDarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header Tab Navigation Row with Icons & Labels
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = StudioDarkBg,
                contentColor = TextPrimary,
                indicator = { tabPositions ->
                    if (selectedTab.ordinal < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                            color = VioletAccent,
                            height = 3.dp
                        )
                    }
                },
                divider = {
                    HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
                }
            ) {
                SettingsTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        modifier = Modifier
                            .testTag(tab.testTag)
                            .padding(vertical = 4.dp),
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(20.dp),
                                tint = if (isSelected) VioletAccent else TextSecondary
                            )
                        },
                        text = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) VioletAccent else TextSecondary,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            // Tab Content with LazyColumn
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Spacer(modifier = Modifier.height(6.dp)) }

                // Status & Error Banners
                if (state.statusMessage != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(EmeraldSuccess.copy(alpha = 0.15f))
                                .border(1.dp, EmeraldSuccess.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    state.statusMessage!!,
                                    color = EmeraldSuccess,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                if (state.errorMessage != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(RoseError.copy(alpha = 0.15f))
                                .border(1.dp, RoseError.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                state.errorMessage!!,
                                color = RoseError,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                when (selectedTab) {
                    SettingsTab.KEY -> {
                        item {
                            ApiKeyTabContent(
                                state = state,
                                onSaveKey = { viewModel.onApiKeyChanged(it) },
                                onFetchModels = { viewModel.fetchModels() }
                            )
                        }
                    }

                    SettingsTab.MODELS -> {
                        item {
                            ModelsTabContent(
                                state = state,
                                onModelSelected = { purpose, model ->
                                    viewModel.setModelForPurpose(purpose, model)
                                }
                            )
                        }
                    }

                    SettingsTab.PIPELINE -> {
                        item {
                            PipelineTabContent(
                                state = state,
                                onKeepIntermediateVideosChanged = { viewModel.onKeepIntermediateVideosChanged(it) },
                                onAutoFinalQaChanged = { viewModel.onAutoFinalQaChanged(it) },
                                onMaxRetriesChanged = { viewModel.onMaxRetriesChanged(it) }
                            )
                        }
                    }

                    SettingsTab.VOICES -> {
                        item {
                            VoicesTabContent(
                                state = state,
                                onOpenRegisterDialog = { showVoiceDialog = true }
                            )
                        }
                    }

                    SettingsTab.ABOUT -> {
                        item {
                            AboutTabContent()
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    // Register Voice Profile Dialog
    if (showVoiceDialog) {
        RegisterVoiceDialog(
            onDismiss = { showVoiceDialog = false },
            onConfirm = { name, description ->
                viewModel.createVoiceProfile(name, description)
                showVoiceDialog = false
            }
        )
    }
}

// -------------------------------------------------------------------------
// TAB 1: GEMINI API KEY CONFIGURATION
// -------------------------------------------------------------------------
@Composable
private fun ApiKeyTabContent(
    state: SettingsUiState,
    onSaveKey: (String) -> Unit,
    onFetchModels: () -> Unit
) {
    var inputKey by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(AmberAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Key,
                        contentDescription = null,
                        tint = AmberAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "Gemini API Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "Direct REST access to Google Generative AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Stored securely in encrypted preferences and injected via BuildConfig. Enter or update your API key below:",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = inputKey,
                onValueChange = { inputKey = it },
                placeholder = { Text("Paste API Key: AIzaSy...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_api_key_input"),
                visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                        Icon(
                            imageVector = if (isKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (isKeyVisible) "Hide key" else "Show key",
                            tint = TextSecondary
                        )
                    }
                },
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VioletPrimary,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onSaveKey(inputKey) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_save_api_key_button"),
                colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceElevated),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save Key", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Fetch Models Button
            Button(
                onClick = onFetchModels,
                enabled = !state.isFetchingModels,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("settings_fetch_models_button"),
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (state.isFetchingModels) {
                    CircularProgressIndicator(color = TextPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Discovering Models...", color = TextPrimary)
                } else {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = TextPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fetch Available Models", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }

            if (state.lastSyncTime > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Last model sync: ${TimeUtils.formatTimestamp(state.lastSyncTime)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Connectivity status card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("REST API STATUS", style = MaterialTheme.typography.labelSmall, color = VioletAccent, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (state.apiKey.isNotBlank()) Icons.Filled.CheckCircle else Icons.Filled.Shield,
                    contentDescription = null,
                    tint = if (state.apiKey.isNotBlank()) EmeraldSuccess else AmberAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.apiKey.isNotBlank()) "Direct Gemini REST API Connected" else "API Key Pending Configuration",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Endpoints: generativelanguage.googleapis.com/v1beta • SSE Streaming Enabled",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
    }
}

// -------------------------------------------------------------------------
// TAB 2: AI MODEL ASSIGNMENTS PER PURPOSE
// -------------------------------------------------------------------------
@Composable
private fun ModelsTabContent(
    state: SettingsUiState,
    onModelSelected: (ModelPurpose, com.vineyard.aivideostudio.ai.model.ModelInfo) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(VioletPrimary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = VioletAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "AI Model Assignments",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "Per-purpose model routing",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(StudioCardBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${state.availableModels.size} Models",
                        color = VioletAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Assign autonomous models for each specific stage in the editing pipeline:",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ModelPurpose.entries.forEach { purpose ->
                    val selectedId = state.modelConfigs[purpose.name] ?: when (purpose) {
                        ModelPurpose.VIDEO_ANALYSIS -> "gemini-3.5-flash"
                        ModelPurpose.AUDIO_TRANSCRIPTION -> "gemini-3.5-flash"
                        ModelPurpose.EDITING_DIRECTOR -> "gemini-3.5-flash"
                        ModelPurpose.COMMENTARY -> "gemini-3.5-flash"
                        ModelPurpose.TEXT_TO_SPEECH -> "gemini-2.5-flash-preview-tts"
                        ModelPurpose.LIVE_VOICE -> "gemini-2.5-flash-native-audio-preview-12-2025"
                    }

                    val eligibleModels = state.availableModels.filter {
                        it.supportedPurposes.contains(purpose) || it.capabilities.supportsText
                    }

                    ModelDropdown(
                        label = purpose.displayName,
                        selectedModelId = selectedId,
                        availableModels = eligibleModels,
                        onModelSelected = { model ->
                            onModelSelected(purpose, model)
                        }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// TAB 3: PRODUCTION PIPELINE PREFERENCES
// -------------------------------------------------------------------------
@Composable
private fun PipelineTabContent(
    state: SettingsUiState,
    onKeepIntermediateVideosChanged: (Boolean) -> Unit,
    onAutoFinalQaChanged: (Boolean) -> Unit,
    onMaxRetriesChanged: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(CyanInfo.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = null,
                        tint = CyanInfo,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "Pipeline Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "Deterministic Media3 execution & verification",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Keep intermediate videos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Keep Intermediate Videos", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text("Preserve output from trim, crop, zoom stages for debugging", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Switch(
                    checked = state.keepIntermediateVideos,
                    onCheckedChange = onKeepIntermediateVideosChanged,
                    colors = SwitchDefaults.colors(checkedThumbColor = TextPrimary, checkedTrackColor = VioletPrimary)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Auto final QA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Automatic Final QA", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text("Verify source retention & media coherence before export", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Switch(
                    checked = state.autoFinalQa,
                    onCheckedChange = onAutoFinalQaChanged,
                    colors = SwitchDefaults.colors(checkedThumbColor = TextPrimary, checkedTrackColor = VioletPrimary)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Max Retries
            Text(
                text = "Maximum QA Retries: ${state.maxQaRetries}",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text = "Autonomous correction attempts when QA detects discrepancies",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Slider(
                value = state.maxQaRetries.toFloat(),
                onValueChange = { onMaxRetriesChanged(it.toInt()) },
                valueRange = 1f..5f,
                steps = 3,
                colors = SliderDefaults.colors(
                    thumbColor = VioletAccent,
                    activeTrackColor = VioletPrimary,
                    inactiveTrackColor = BorderSubtle
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Engine Specs card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("MEDIA3 TRANSFORMATION SPECIFICATION", style = MaterialTheme.typography.labelSmall, color = CyanInfo, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("• Target Export: 1080p (1920x1080) @ 60 FPS", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            Text("• Codec Profile: H.264 High Profile / AAC-LC 192kbps Stereo", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            Text("• Color Matrix: Rec. 709 Standard Range", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            Text("• Trimming Accuracy: Sample-Accurate Media3 Transformer", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
        }
    }
}

// -------------------------------------------------------------------------
// TAB 4: VOICE MANAGEMENT & REPLICATION
// -------------------------------------------------------------------------
@Composable
private fun VoicesTabContent(
    state: SettingsUiState,
    onOpenRegisterDialog: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(EmeraldSuccess.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.RecordVoiceOver,
                        contentDescription = null,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "Voice Management & Replication",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "Consent-verified voice replication profiles",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Each replicated voice profile requires consent verification to ensure policy adherence and prevent unauthorized cloning.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            state.voices.forEach { voice ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioCardBg)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(voice.name, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text(
                                if (voice.consentVerified) "Consent Verified" else "Pending Consent",
                                color = if (voice.consentVerified) EmeraldSuccess else AmberAccent,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onOpenRegisterDialog,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("settings_register_voice_button"),
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = TextPrimary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Register Authorized Voice Profile", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------------------
// TAB 5: ABOUT
// -------------------------------------------------------------------------
@Composable
private fun AboutTabContent() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(VioletPrimary.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = VioletAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "About Editora AI Video Studio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "Version 1.0.0 (Build 100)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Built with Kotlin, Coroutines, Jetpack Compose & Android Media3.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Autonomous AI-directed video editing state machine with deterministic sample-accurate Media3 execution, multi-stage QA feedback loops, and floating persistent logging.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Tech specs breakdown
            Text("SYSTEM & ENGINE ARCHITECTURE", style = MaterialTheme.typography.labelSmall, color = VioletAccent, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Runtime Engine", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                Text("Android Media3 1.5.1", color = TextPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Local Persistence", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                Text("SQLite Room 2.7.0", color = TextPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("AI Connectivity", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                Text("Gemini 3.5 & 2.5 REST", color = TextPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("UI Design System", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                Text("Material 3 (M3)", color = TextPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// -------------------------------------------------------------------------
// REGISTER VOICE DIALOG
// -------------------------------------------------------------------------
@Composable
private fun RegisterVoiceDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit
) {
    var voiceName by remember { mutableStateOf("") }
    var voiceDescription by remember { mutableStateOf("") }
    var consentAgreed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Register Authorized Voice Profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Provide a profile title and description for narrator voice replication:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = voiceName,
                    onValueChange = { voiceName = it },
                    label = { Text("Voice Profile Name") },
                    placeholder = { Text("e.g. Director Voice") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VioletPrimary,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                OutlinedTextField(
                    value = voiceDescription,
                    onValueChange = { voiceDescription = it },
                    label = { Text("Description / Character") },
                    placeholder = { Text("e.g. Professional documentary narrator") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VioletPrimary,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Checkbox(
                        checked = consentAgreed,
                        onCheckedChange = { consentAgreed = it },
                        colors = CheckboxDefaults.colors(checkedColor = VioletPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "I verify consent authorization to replicate this voice profile for studio productions.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(voiceName, voiceDescription) },
                enabled = voiceName.isNotBlank() && consentAgreed,
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
            ) {
                Text("Register Profile", color = TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = StudioSurface,
        shape = RoundedCornerShape(12.dp)
    )
}
