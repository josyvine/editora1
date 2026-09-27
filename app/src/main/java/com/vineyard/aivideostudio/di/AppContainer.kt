package com.vineyard.aivideostudio.di

import android.content.Context
import com.vineyard.aivideostudio.ai.gemini.GeminiClient
import com.vineyard.aivideostudio.data.local.database.AppDatabase
import com.vineyard.aivideostudio.data.preferences.AppPreferences
import com.vineyard.aivideostudio.data.preferences.GeminiPreferences
import com.vineyard.aivideostudio.data.preferences.ProcessingPreferences
import com.vineyard.aivideostudio.data.remote.network.NetworkModule
import com.vineyard.aivideostudio.data.repository.ModelRepositoryImpl
import com.vineyard.aivideostudio.data.repository.ProjectRepositoryImpl
import com.vineyard.aivideostudio.data.repository.VoiceRepositoryImpl
import com.vineyard.aivideostudio.data.storage.ProjectStorageManager
import com.vineyard.aivideostudio.data.storage.StorageManager
import com.vineyard.aivideostudio.domain.pipeline.VideoProcessingPipeline
import com.vineyard.aivideostudio.media.audio.AudioExtractor
import com.vineyard.aivideostudio.media.transformer.Media3TransformerEngine
import com.vineyard.aivideostudio.media.video.VideoMetadataReader
import com.vineyard.aivideostudio.processing.controller.ProcessingController
import com.vineyard.aivideostudio.processing.logger.ProcessingLogger
import com.vineyard.aivideostudio.project.repository.ProjectRepository
import com.vineyard.aivideostudio.voice.tts.GeminiTtsEngine

class AppContainer(private val context: Context) {

    val geminiPreferences = GeminiPreferences(context)
    val processingPreferences = ProcessingPreferences(context)
    val appPreferences = AppPreferences(geminiPreferences, processingPreferences)

    val database = AppDatabase.getInstance(context)

    val storageManager = StorageManager(context)
    val projectStorageManager = ProjectStorageManager(storageManager)

    val logger = ProcessingLogger(database.persistentLogDao())

    val networkLogger = com.vineyard.aivideostudio.data.remote.network.NetworkLoggingInterceptor { message, isError, details ->
        logger.log(
            projectId = "NETWORK",
            stage = com.vineyard.aivideostudio.core.model.PipelineStatus.IDLE,
            message = message,
            severity = if (isError) com.vineyard.aivideostudio.processing.logger.LogSeverity.ERROR else com.vineyard.aivideostudio.processing.logger.LogSeverity.INFO,
            details = details
        )
    }

    val okHttpClient = NetworkModule.createOkHttpClient(
        apiKeyProvider = { geminiPreferences.getApiKey() },
        networkLogger = networkLogger
    )
    val geminiApiService = NetworkModule.createGeminiApiService(okHttpClient)

    val projectRepository: ProjectRepository = ProjectRepositoryImpl(database)
    val modelRepository = ModelRepositoryImpl(geminiApiService, geminiPreferences, database.modelConfigurationDao())
    val voiceRepository = VoiceRepositoryImpl(database.voiceDao())

    val aiRequestDao = database.aiRequestDao()
    val geminiClient = GeminiClient(geminiApiService, geminiPreferences, aiRequestDao)

    val videoMetadataReader = VideoMetadataReader(context)
    val transformerEngine = Media3TransformerEngine(context)
    val audioExtractor = AudioExtractor(context)
    val ttsEngine = GeminiTtsEngine(context, geminiApiService, geminiPreferences, modelRepository)

    val pipeline = VideoProcessingPipeline(
        context = context,
        projectRepository = projectRepository,
        modelRepository = modelRepository,
        geminiClient = geminiClient,
        transformerEngine = transformerEngine,
        audioExtractor = audioExtractor,
        ttsEngine = ttsEngine,
        storageManager = projectStorageManager,
        preferences = processingPreferences,
        logger = logger
    )

    val processingController = ProcessingController(
        context = context,
        pipeline = pipeline,
        projectRepository = projectRepository,
        logger = logger
    )
}
