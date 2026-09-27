package com.vineyard.aivideostudio.ai.classifier

import com.vineyard.aivideostudio.ai.model.ModelCapabilities
import com.vineyard.aivideostudio.ai.model.ModelInfo
import com.vineyard.aivideostudio.ai.model.ModelPurpose
import com.vineyard.aivideostudio.data.remote.gemini.ModelDto

object ModelCapabilityClassifier {

    fun classify(dto: ModelDto): ModelInfo {
        val rawId = dto.name
        val cleanId = rawId.removePrefix("models/")
        val lowerId = cleanId.lowercase()
        val lowerDesc = (dto.description ?: "").lowercase()
        val lowerMethods = dto.supportedGenerationMethods.map { it.lowercase() }

        // Determine capabilities from supportedGenerationMethods, descriptions, and naming metadata
        val hasGenerateContent = lowerMethods.contains("generatecontent") || lowerMethods.contains("streamgeneratecontent")
        val isTts = lowerId.contains("tts") || lowerDesc.contains("text-to-speech") || lowerDesc.contains("speech")
        val isLive = lowerId.contains("native-audio") || lowerId.contains("live") || lowerDesc.contains("realtime") || lowerDesc.contains("bidirectional")
        
        // Gemini flash and pro models natively support multimodal video, audio, text input
        val isFlashOrPro = lowerId.contains("flash") || lowerId.contains("pro") || lowerId.contains("gemini")
        val supportsVideo = (isFlashOrPro && !isTts) || lowerDesc.contains("video") || lowerDesc.contains("multimodal")
        val supportsAudio = isFlashOrPro || isTts || isLive || lowerDesc.contains("audio")
        val supportsText = hasGenerateContent || isFlashOrPro
        val supportsStructuredJson = hasGenerateContent && !isLive

        val capabilities = ModelCapabilities(
            supportsVideo = supportsVideo,
            supportsAudio = supportsAudio,
            supportsText = supportsText,
            supportsTts = isTts,
            supportsLive = isLive,
            supportsStructuredJson = supportsStructuredJson
        )

        // Classify which purposes this model qualifies for
        val purposes = mutableListOf<ModelPurpose>()
        if (capabilities.supportsVideo) {
            purposes.add(ModelPurpose.VIDEO_ANALYSIS)
            purposes.add(ModelPurpose.EDITING_DIRECTOR)
        }
        if (capabilities.supportsAudio || capabilities.supportsText) {
            purposes.add(ModelPurpose.AUDIO_TRANSCRIPTION)
        }
        if (capabilities.supportsText) {
            purposes.add(ModelPurpose.COMMENTARY)
            if (!purposes.contains(ModelPurpose.EDITING_DIRECTOR)) {
                purposes.add(ModelPurpose.EDITING_DIRECTOR)
            }
        }
        if (capabilities.supportsTts) {
            purposes.add(ModelPurpose.TEXT_TO_SPEECH)
        }
        if (capabilities.supportsLive) {
            purposes.add(ModelPurpose.LIVE_VOICE)
        }

        return ModelInfo(
            id = rawId,
            baseId = cleanId,
            displayName = dto.displayName ?: cleanId,
            description = dto.description ?: "Gemini generative model",
            version = dto.version ?: "1.0",
            inputTokenLimit = dto.inputTokenLimit ?: 32768,
            outputTokenLimit = dto.outputTokenLimit ?: 8192,
            capabilities = capabilities,
            supportedPurposes = purposes
        )
    }
}
