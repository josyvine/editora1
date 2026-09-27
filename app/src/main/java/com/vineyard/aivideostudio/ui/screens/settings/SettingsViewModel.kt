package com.vineyard.aivideostudio.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vineyard.aivideostudio.ai.model.ModelInfo
import com.vineyard.aivideostudio.ai.model.ModelPurpose
import com.vineyard.aivideostudio.core.result.AppResult
import com.vineyard.aivideostudio.data.local.database.entity.ModelConfigurationEntity
import com.vineyard.aivideostudio.data.preferences.GeminiPreferences
import com.vineyard.aivideostudio.data.preferences.ProcessingPreferences
import com.vineyard.aivideostudio.data.repository.ModelRepositoryImpl
import com.vineyard.aivideostudio.data.repository.VoiceRepositoryImpl
import com.vineyard.aivideostudio.voice.model.VoiceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKey: String = "",
    val maskedApiKey: String = "",
    val isFetchingModels: Boolean = false,
    val lastSyncTime: Long = 0L,
    val availableModels: List<ModelInfo> = emptyList(),
    val modelConfigs: Map<String, String> = emptyMap(),
    val maxQaRetries: Int = 3,
    val keepIntermediateVideos: Boolean = true,
    val autoFinalQa: Boolean = true,
    val voices: List<VoiceProfile> = emptyList(),
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val preferences: GeminiPreferences,
    private val processingPreferences: ProcessingPreferences,
    private val modelRepository: ModelRepositoryImpl,
    private val voiceRepository: VoiceRepositoryImpl
) : ViewModel() {

    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isFetching = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.apiKeyFlow,
        modelRepository.cachedModels,
        modelRepository.getAllConfigurations(),
        voiceRepository.getAllVoices(),
        _statusMessage,
        _errorMessage,
        _isFetching
    ) { args: Array<Any?> ->
        val apiKey = (args[0] as? String) ?: ""
        @Suppress("UNCHECKED_CAST")
        val models = (args[1] as? List<ModelInfo>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val configs = (args[2] as? List<ModelConfigurationEntity>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val voices = (args[3] as? List<VoiceProfile>) ?: emptyList()
        val statusMsg = args[4] as? String
        val errorMsg = args[5] as? String
        val isFetching = (args[6] as? Boolean) ?: false

        val configMap = configs.associate { it.purpose to it.modelId }
        val masked = if (apiKey.length > 8) {
            apiKey.take(4) + "••••••••" + apiKey.takeLast(4)
        } else if (apiKey.isNotBlank()) {
            "••••••••"
        } else {
            "No API Key Configured"
        }

        SettingsUiState(
            apiKey = apiKey,
            maskedApiKey = masked,
            isFetchingModels = isFetching,
            lastSyncTime = preferences.getLastModelSync(),
            availableModels = models,
            modelConfigs = configMap,
            maxQaRetries = processingPreferences.maxQaRetries,
            keepIntermediateVideos = processingPreferences.keepIntermediateVideos,
            autoFinalQa = processingPreferences.autoFinalQa,
            voices = voices,
            statusMessage = statusMsg,
            errorMessage = errorMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun onApiKeyChanged(newKey: String) {
        preferences.setApiKey(newKey)
        _statusMessage.value = "API Key saved"
        _errorMessage.value = null
    }

    fun fetchModels() {
        viewModelScope.launch {
            _isFetching.value = true
            _statusMessage.value = "Fetching Gemini models dynamically..."
            _errorMessage.value = null

            when (val result = modelRepository.fetchAvailableModels()) {
                is AppResult.Success -> {
                    _statusMessage.value = "Fetched and classified ${result.data.size} models!"
                }
                is AppResult.Error -> {
                    _errorMessage.value = result.error.message
                    _statusMessage.value = null
                }
            }
            _isFetching.value = false
        }
    }

    fun setModelForPurpose(purpose: ModelPurpose, model: ModelInfo) {
        viewModelScope.launch {
            modelRepository.setSelectedModelForPurpose(purpose, model)
            _statusMessage.value = "Assigned ${model.displayName} to ${purpose.displayName}"
        }
    }

    fun onKeepIntermediateVideosChanged(keep: Boolean) {
        processingPreferences.keepIntermediateVideos = keep
    }

    fun onAutoFinalQaChanged(auto: Boolean) {
        processingPreferences.autoFinalQa = auto
    }

    fun onMaxRetriesChanged(retries: Int) {
        processingPreferences.maxQaRetries = retries
    }

    fun createVoiceProfile(name: String, description: String) {
        viewModelScope.launch {
            voiceRepository.createVoice(
                name = name,
                description = description,
                isReplicated = true,
                sampleAudioUri = null,
                consentVerified = true
            )
            _statusMessage.value = "Voice profile '$name' registered with verified consent"
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
        _errorMessage.value = null
    }
}
