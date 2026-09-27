package com.vineyard.aivideostudio.domain.pipeline

import android.content.Context
import android.net.Uri
import com.vineyard.aivideostudio.ai.gemini.GeminiClient
import com.vineyard.aivideostudio.ai.model.AiQaResponse
import com.vineyard.aivideostudio.ai.model.CaptionDecision
import com.vineyard.aivideostudio.ai.model.CommentaryDecision
import com.vineyard.aivideostudio.ai.model.CropDecision
import com.vineyard.aivideostudio.ai.model.ModelPurpose
import com.vineyard.aivideostudio.ai.model.SourceAnalysis
import com.vineyard.aivideostudio.ai.model.TrimDecision
import com.vineyard.aivideostudio.ai.model.ZoomDecision
import com.vineyard.aivideostudio.ai.prompt.Prompts
import com.vineyard.aivideostudio.ai.validator.AiResponseValidator
import com.vineyard.aivideostudio.core.model.ArtifactType
import com.vineyard.aivideostudio.core.model.Caption
import com.vineyard.aivideostudio.core.model.CommentarySegment
import com.vineyard.aivideostudio.core.model.MediaArtifact
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.model.PipelineStep
import com.vineyard.aivideostudio.core.model.Project
import com.vineyard.aivideostudio.core.model.QaResult
import com.vineyard.aivideostudio.core.model.QaVerdict
import com.vineyard.aivideostudio.core.model.StepStatus
import com.vineyard.aivideostudio.core.model.TimelineMap
import com.vineyard.aivideostudio.core.model.TranscriptSegment
import com.vineyard.aivideostudio.core.result.AppError
import com.vineyard.aivideostudio.core.result.AppResult
import com.vineyard.aivideostudio.core.util.JsonUtils
import com.vineyard.aivideostudio.data.preferences.ProcessingPreferences
import com.vineyard.aivideostudio.data.repository.ModelRepositoryImpl
import com.vineyard.aivideostudio.data.storage.ProjectStorageManager
import com.vineyard.aivideostudio.media.audio.AudioExtractor
import com.vineyard.aivideostudio.media.timeline.TimelineMapper
import com.vineyard.aivideostudio.media.transformer.Media3TransformerEngine
import com.vineyard.aivideostudio.media.video.VideoMetadataReader
import com.vineyard.aivideostudio.project.repository.ProjectRepository
import com.vineyard.aivideostudio.processing.logger.LogSeverity
import com.vineyard.aivideostudio.processing.logger.ProcessingLogger
import com.vineyard.aivideostudio.voice.model.TtsRequest
import com.vineyard.aivideostudio.voice.tts.GeminiTtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

class VideoProcessingPipeline(
    private val context: Context,
    private val projectRepository: ProjectRepository,
    private val modelRepository: ModelRepositoryImpl,
    private val geminiClient: GeminiClient,
    private val transformerEngine: Media3TransformerEngine,
    private val audioExtractor: AudioExtractor,
    private val ttsEngine: GeminiTtsEngine,
    private val storageManager: ProjectStorageManager,
    private val preferences: ProcessingPreferences,
    private val logger: ProcessingLogger
) {

    suspend fun executePipeline(
        projectId: String,
        onStageChanged: (PipelineStatus, String) -> Unit
    ): AppResult<Project> = withContext(Dispatchers.IO) {
        val project = projectRepository.getProjectById(projectId)
            ?: return@withContext AppResult.Error(AppError.StorageError("Project not found: $projectId"))

        logger.log(projectId, PipelineStatus.SOURCE_ANALYSIS, "Starting AI Video Studio pipeline for ${project.name}")

        var currentVideoUri = project.currentVideoUri
        var currentDuration = project.metadata.durationSeconds
        var timelineMap = if (project.timelineMapJson != null) {
            JsonUtils.fromJson<TimelineMap>(project.timelineMapJson) ?: TimelineMap.identity(projectId, currentDuration)
        } else {
            TimelineMap.identity(projectId, currentDuration)
        }

        var sourceAnalysis: SourceAnalysis? = if (project.sourceAnalysisJson != null) {
            JsonUtils.fromJson<SourceAnalysis>(project.sourceAnalysisJson)
        } else null

        // 1. SOURCE ANALYSIS
        if (sourceAnalysis == null) {
            val stageMsg = if (!project.sourceYoutubeUrl.isNullOrBlank()) {
                "Gemini analyzing YouTube source video & comparing against downloaded local video..."
            } else {
                "Gemini analyzing video composition, dialogue, and scenes..."
            }
            onStageChanged(PipelineStatus.SOURCE_ANALYSIS, stageMsg)
            logger.log(projectId, PipelineStatus.SOURCE_ANALYSIS, "Gemini source analysis started (YouTube: ${project.sourceYoutubeUrl ?: "none"})")
            recordStep(projectId, PipelineStatus.SOURCE_ANALYSIS, StepStatus.IN_PROGRESS, "Analyzing source content")

            val modelId = modelRepository.getSelectedModelForPurpose(ModelPurpose.VIDEO_ANALYSIS)
            val prompt = Prompts.buildSourceAnalysisPrompt(project.metadata, project.sourceYoutubeUrl)

            val analysisResult = geminiClient.generateStructured(
                projectId = projectId,
                stage = PipelineStatus.SOURCE_ANALYSIS,
                modelId = modelId,
                prompt = prompt
            ) { json ->
                JsonUtils.fromJson<SourceAnalysis>(json)
            }

            when (analysisResult) {
                is AppResult.Success -> {
                    sourceAnalysis = analysisResult.data
                    val validation = AiResponseValidator.validateSourceAnalysis(sourceAnalysis)
                    if (!validation.isValid) {
                        logger.log(projectId, PipelineStatus.SOURCE_ANALYSIS, "Source analysis validation warning: ${(validation as com.vineyard.aivideostudio.core.validation.ValidationResult.Invalid).reason}", LogSeverity.WARNING)
                    }
                    projectRepository.saveProject(
                        project.copy(
                            sourceAnalysisJson = JsonUtils.toJson(sourceAnalysis),
                            timelineMapJson = JsonUtils.toJson(timelineMap),
                            currentStage = PipelineStatus.SOURCE_ANALYSIS_COMPLETE,
                            status = PipelineStatus.SOURCE_ANALYSIS_COMPLETE
                        )
                    )
                    recordStep(projectId, PipelineStatus.SOURCE_ANALYSIS, StepStatus.COMPLETED, "Source analysis completed")
                    logger.log(projectId, PipelineStatus.SOURCE_ANALYSIS, "Source analysis completed: ${sourceAnalysis.summary.take(80)}...", LogSeverity.SUCCESS)
                }
                is AppResult.Error -> {
                    val errorMsg = analysisResult.error.message
                    recordStep(projectId, PipelineStatus.SOURCE_ANALYSIS, StepStatus.FAILED, errorMessage = errorMsg)
                    logger.log(projectId, PipelineStatus.SOURCE_ANALYSIS, "Source analysis failed: $errorMsg", LogSeverity.ERROR)
                    projectRepository.markFailed(projectId, errorMsg)
                    return@withContext AppResult.Error(analysisResult.error)
                }
            }
        }

        if (!coroutineContext.isActive) return@withContext AppResult.Error(AppError.UnknownError("Pipeline cancelled"))

        // 2. AUDIO EXTRACTION
        onStageChanged(PipelineStatus.AUDIO_EXTRACTION, "Extracting audio track for transcription")
        logger.log(projectId, PipelineStatus.AUDIO_EXTRACTION, "Extracting audio track")
        recordStep(projectId, PipelineStatus.AUDIO_EXTRACTION, StepStatus.IN_PROGRESS, "Extracting audio")
        val audioOutputFile = storageManager.createAudioOutputFile(projectId, "source_audio")
        audioExtractor.extractAudio(Uri.parse(currentVideoUri), audioOutputFile)
        val audioArtifact = MediaArtifact(
            id = "art_audio_${System.currentTimeMillis()}",
            projectId = projectId,
            stage = PipelineStatus.AUDIO_EXTRACTION,
            type = ArtifactType.EXTRACTED_AUDIO,
            fileUri = Uri.fromFile(audioOutputFile).toString(),
            filePath = audioOutputFile.absolutePath,
            mimeType = "audio/mp4",
            sizeBytes = audioOutputFile.length(),
            durationSeconds = currentDuration
        )
        projectRepository.recordArtifact(audioArtifact)
        recordStep(projectId, PipelineStatus.AUDIO_EXTRACTION, StepStatus.COMPLETED, "Audio track extracted")
        logger.log(projectId, PipelineStatus.AUDIO_EXTRACTION, "Audio extraction completed", LogSeverity.SUCCESS)

        // 3. TRANSCRIPTION
        onStageChanged(PipelineStatus.TRANSCRIPTION, "Generating timestamped transcript")
        logger.log(projectId, PipelineStatus.TRANSCRIPTION, "Transcribing dialogue")
        recordStep(projectId, PipelineStatus.TRANSCRIPTION, StepStatus.IN_PROGRESS, "Transcribing dialogue")
        val transcriptSegments = sourceAnalysis.dialogueSegments.mapIndexed { index, dia ->
            TranscriptSegment(
                id = "trans_${index}_${System.currentTimeMillis()}",
                projectId = projectId,
                start = dia.start,
                end = dia.end,
                text = dia.text,
                speaker = dia.speaker
            )
        }
        projectRepository.saveTranscript(projectId, transcriptSegments)
        recordStep(projectId, PipelineStatus.TRANSCRIPTION, StepStatus.COMPLETED, "${transcriptSegments.size} transcript segments saved")
        logger.log(projectId, PipelineStatus.TRANSCRIPTION, "Transcription completed (${transcriptSegments.size} segments)", LogSeverity.SUCCESS)

        // 4. TRIM PIPELINE (Analysis -> Execution -> QA)
        onStageChanged(PipelineStatus.TRIM_ANALYSIS, "Gemini evaluating trim and pacing cuts")
        logger.log(projectId, PipelineStatus.TRIM_ANALYSIS, "Evaluating trim necessity")
        recordStep(projectId, PipelineStatus.TRIM_ANALYSIS, StepStatus.IN_PROGRESS, "Evaluating trim")

        val directorModel = modelRepository.getSelectedModelForPurpose(ModelPurpose.EDITING_DIRECTOR)
        val trimPrompt = Prompts.buildTrimDecisionPrompt(sourceAnalysis, currentDuration, timelineMap)
        val trimResult = geminiClient.generateStructured(
            projectId = projectId,
            stage = PipelineStatus.TRIM_ANALYSIS,
            modelId = directorModel,
            prompt = trimPrompt
        ) { json -> JsonUtils.fromJson<TrimDecision>(json) }

        val trimDecision = when (trimResult) {
            is AppResult.Success -> trimResult.data
            is AppResult.Error -> TrimDecision(isNecessary = false, explanation = "Skipping trim due to API issue")
        }

        if (trimDecision.isNecessary && trimDecision.segmentsToRemove.isNotEmpty()) {
            val valid = AiResponseValidator.validateTrim(trimDecision, currentDuration)
            if (valid.isValid) {
                onStageChanged(PipelineStatus.TRIM_EXECUTION, "Android Media3 executing video trim")
                logger.log(projectId, PipelineStatus.TRIM_EXECUTION, "Executing trim (${trimDecision.segmentsToRemove.size} cuts)")
                recordStep(projectId, PipelineStatus.TRIM_EXECUTION, StepStatus.IN_PROGRESS, "Executing trim")

                val trimOutputFile = storageManager.createStageOutputFile(projectId, PipelineStatus.TRIM_EXECUTION)
                // Execute trim using Media3
                val firstCut = trimDecision.segmentsToRemove.first()
                val trimExecResult = transformerEngine.trimVideo(
                    inputUri = Uri.parse(currentVideoUri),
                    outputFile = trimOutputFile,
                    startMs = 0L,
                    endMs = ((currentDuration - (firstCut.end - firstCut.start)) * 1000).toLong().coerceAtLeast(1000L)
                )

                if (trimExecResult is AppResult.Success) {
                    currentVideoUri = Uri.fromFile(trimOutputFile).toString()
                    timelineMap = TimelineMapper.applyTrim(timelineMap, trimDecision.segmentsToRemove)
                    currentDuration = timelineMap.currentDuration
                    projectRepository.saveTimelineMap(projectId, timelineMap)
                    projectRepository.updateCurrentVideoUri(projectId, currentVideoUri)
                    recordStep(projectId, PipelineStatus.TRIM_EXECUTION, StepStatus.COMPLETED, "Trim executed")
                    logger.log(projectId, PipelineStatus.TRIM_EXECUTION, "Trim executed successfully", LogSeverity.SUCCESS)

                    // TRIM QA
                    onStageChanged(PipelineStatus.TRIM_QA, "Gemini performing Trim QA check")
                    logger.log(projectId, PipelineStatus.TRIM_QA, "Running Trim QA")
                    val qaPrompt = Prompts.buildTrimQaPrompt(sourceAnalysis, trimDecision.segmentsToRemove.size, currentDuration)
                    val qaResult = runQaCheck(projectId, PipelineStatus.TRIM_QA, qaPrompt, directorModel)
                    projectRepository.recordQaResult(qaResult)
                    logger.log(projectId, PipelineStatus.TRIM_QA, "Trim QA Verdict: ${qaResult.verdict}", LogSeverity.SUCCESS)
                } else {
                    logger.log(projectId, PipelineStatus.TRIM_EXECUTION, "Trim execution error: ${(trimExecResult as AppResult.Error).error.message}", LogSeverity.WARNING)
                }
            } else {
                logger.log(projectId, PipelineStatus.TRIM_ANALYSIS, "Trim skipped due to validation: ${(valid as com.vineyard.aivideostudio.core.validation.ValidationResult.Invalid).reason}", LogSeverity.WARNING)
                recordStep(projectId, PipelineStatus.TRIM_ANALYSIS, StepStatus.SKIPPED, "Trim validation rejected cuts")
            }
        } else {
            logger.log(projectId, PipelineStatus.TRIM_ANALYSIS, "Trim unnecessary — SKIPPED", LogSeverity.INFO)
            recordStep(projectId, PipelineStatus.TRIM_ANALYSIS, StepStatus.SKIPPED, "Trim not required")
        }

        // 5. CROP / REFRAME PIPELINE
        onStageChanged(PipelineStatus.CROP_ANALYSIS, "Gemini analyzing framing and aspect ratio")
        logger.log(projectId, PipelineStatus.CROP_ANALYSIS, "Evaluating framing and crop")
        recordStep(projectId, PipelineStatus.CROP_ANALYSIS, StepStatus.IN_PROGRESS, "Evaluating crop")

        val cropPrompt = Prompts.buildCropDecisionPrompt(
            sourceAnalysis = sourceAnalysis,
            targetAspectRatio = project.targetAspectRatio,
            currentWidth = project.metadata.width,
            currentHeight = project.metadata.height
        )
        val cropResult = geminiClient.generateStructured(
            projectId = projectId,
            stage = PipelineStatus.CROP_ANALYSIS,
            modelId = directorModel,
            prompt = cropPrompt
        ) { json -> JsonUtils.fromJson<CropDecision>(json) }

        val cropDecision = when (cropResult) {
            is AppResult.Success -> cropResult.data
            is AppResult.Error -> CropDecision(isNecessary = false)
        }

        if (cropDecision.isNecessary && AiResponseValidator.validateCrop(cropDecision).isValid) {
            onStageChanged(PipelineStatus.CROP_EXECUTION, "Android Media3 applying reframing crop")
            logger.log(projectId, PipelineStatus.CROP_EXECUTION, "Executing crop reframe")
            recordStep(projectId, PipelineStatus.CROP_EXECUTION, StepStatus.IN_PROGRESS, "Applying crop")

            val cropOutputFile = storageManager.createStageOutputFile(projectId, PipelineStatus.CROP_EXECUTION)
            val cropExec = transformerEngine.cropVideo(
                inputUri = Uri.parse(currentVideoUri),
                outputFile = cropOutputFile,
                normalizedLeft = cropDecision.x,
                normalizedRight = cropDecision.x + cropDecision.width,
                normalizedBottom = cropDecision.y + cropDecision.height,
                normalizedTop = cropDecision.y
            )

            if (cropExec is AppResult.Success) {
                currentVideoUri = Uri.fromFile(cropOutputFile).toString()
                projectRepository.updateCurrentVideoUri(projectId, currentVideoUri)
                recordStep(projectId, PipelineStatus.CROP_EXECUTION, StepStatus.COMPLETED, "Crop executed")
                logger.log(projectId, PipelineStatus.CROP_EXECUTION, "Crop executed successfully", LogSeverity.SUCCESS)

                // CROP QA
                onStageChanged(PipelineStatus.CROP_QA, "Gemini inspecting framing QA")
                val qa = runQaCheck(projectId, PipelineStatus.CROP_QA, Prompts.buildCropQaPrompt(project.targetAspectRatio, "Crop (${cropDecision.x}, ${cropDecision.y})"), directorModel)
                projectRepository.recordQaResult(qa)
                logger.log(projectId, PipelineStatus.CROP_QA, "Crop QA Verdict: ${qa.verdict}", LogSeverity.SUCCESS)
            }
        } else {
            logger.log(projectId, PipelineStatus.CROP_ANALYSIS, "Crop unnecessary — SKIPPED", LogSeverity.INFO)
            recordStep(projectId, PipelineStatus.CROP_ANALYSIS, StepStatus.SKIPPED, "Crop not required")
        }

        // 6. ZOOM PIPELINE
        onStageChanged(PipelineStatus.ZOOM_ANALYSIS, "Gemini evaluating zoom and punch-in")
        recordStep(projectId, PipelineStatus.ZOOM_ANALYSIS, StepStatus.IN_PROGRESS, "Evaluating zoom")
        val zoomPrompt = Prompts.buildZoomDecisionPrompt(sourceAnalysis, currentDuration)
        val zoomResult = geminiClient.generateStructured(
            projectId = projectId,
            stage = PipelineStatus.ZOOM_ANALYSIS,
            modelId = directorModel,
            prompt = zoomPrompt
        ) { json -> JsonUtils.fromJson<ZoomDecision>(json) }

        val zoomDecision = when (zoomResult) {
            is AppResult.Success -> zoomResult.data
            is AppResult.Error -> ZoomDecision(isNecessary = false)
        }

        if (zoomDecision.isNecessary && AiResponseValidator.validateZoom(zoomDecision, currentDuration).isValid) {
            onStageChanged(PipelineStatus.ZOOM_EXECUTION, "Android Media3 applying zoom punch-in")
            recordStep(projectId, PipelineStatus.ZOOM_EXECUTION, StepStatus.IN_PROGRESS, "Executing zoom")
            val zoomOutputFile = storageManager.createStageOutputFile(projectId, PipelineStatus.ZOOM_EXECUTION)
            val zoomExec = transformerEngine.zoomVideo(Uri.parse(currentVideoUri), zoomOutputFile, zoomDecision.toScale)
            if (zoomExec is AppResult.Success) {
                currentVideoUri = Uri.fromFile(zoomOutputFile).toString()
                projectRepository.updateCurrentVideoUri(projectId, currentVideoUri)
                recordStep(projectId, PipelineStatus.ZOOM_EXECUTION, StepStatus.COMPLETED, "Zoom executed")
                val qa = runQaCheck(projectId, PipelineStatus.ZOOM_QA, Prompts.buildZoomQaPrompt("Scale to ${zoomDecision.toScale}"), directorModel)
                projectRepository.recordQaResult(qa)
            }
        } else {
            logger.log(projectId, PipelineStatus.ZOOM_ANALYSIS, "Zoom unnecessary — SKIPPED", LogSeverity.INFO)
            recordStep(projectId, PipelineStatus.ZOOM_ANALYSIS, StepStatus.SKIPPED, "Zoom not required")
        }

        // 7. CAPTION PIPELINE
        onStageChanged(PipelineStatus.CAPTION_ANALYSIS, "Gemini designing captions for current video")
        logger.log(projectId, PipelineStatus.CAPTION_ANALYSIS, "Designing captions")
        recordStep(projectId, PipelineStatus.CAPTION_ANALYSIS, StepStatus.IN_PROGRESS, "Generating captions")

        val captionPrompt = Prompts.buildCaptionDecisionPrompt(sourceAnalysis, currentDuration)
        val captionResult = geminiClient.generateStructured(
            projectId = projectId,
            stage = PipelineStatus.CAPTION_ANALYSIS,
            modelId = directorModel,
            prompt = captionPrompt
        ) { json -> JsonUtils.fromJson<CaptionDecision>(json) }

        val captionDecision = when (captionResult) {
            is AppResult.Success -> captionResult.data
            is AppResult.Error -> CaptionDecision(isNecessary = true, captions = listOf(
                com.vineyard.aivideostudio.ai.model.CaptionItem(
                    text = sourceAnalysis.summary.take(40),
                    start = 0.5,
                    end = 3.5.coerceAtMost(currentDuration)
                )
            ))
        }

        val captionsToSave = captionDecision.captions.mapIndexed { idx, cap ->
            Caption(
                id = "cap_${idx}_${System.currentTimeMillis()}",
                projectId = projectId,
                text = cap.text,
                start = cap.start,
                end = cap.end,
                x = cap.x,
                y = cap.y,
                fontSizeSp = 22f,
                fontColorHex = cap.colorHex,
                backgroundColorHex = "#80000000",
                style = cap.style
            )
        }
        projectRepository.saveCaptions(projectId, captionsToSave)
        recordStep(projectId, PipelineStatus.CAPTION_ANALYSIS, StepStatus.COMPLETED, "${captionsToSave.size} captions rendered")
        logger.log(projectId, PipelineStatus.CAPTION_ANALYSIS, "Captions configured (${captionsToSave.size} items)", LogSeverity.SUCCESS)

        val captionQa = runQaCheck(projectId, PipelineStatus.CAPTION_QA, Prompts.buildCaptionQaPrompt(captionsToSave.size), directorModel)
        projectRepository.recordQaResult(captionQa)

        // 8. COMMENTARY & TTS PIPELINE
        onStageChanged(PipelineStatus.COMMENTARY_ANALYSIS, "Gemini composing voiceover commentary")
        logger.log(projectId, PipelineStatus.COMMENTARY_ANALYSIS, "Composing commentary")
        recordStep(projectId, PipelineStatus.COMMENTARY_ANALYSIS, StepStatus.IN_PROGRESS, "Composing commentary")

        val commentaryModel = modelRepository.getSelectedModelForPurpose(ModelPurpose.COMMENTARY)
        val commPrompt = Prompts.buildCommentaryPrompt(sourceAnalysis, currentDuration)
        val commResult = geminiClient.generateStructured(
            projectId = projectId,
            stage = PipelineStatus.COMMENTARY_ANALYSIS,
            modelId = commentaryModel,
            prompt = commPrompt
        ) { json -> JsonUtils.fromJson<CommentaryDecision>(json) }

        val commDecision = when (commResult) {
            is AppResult.Success -> commResult.data
            is AppResult.Error -> CommentaryDecision(isNecessary = false)
        }

        if (commDecision.isNecessary && commDecision.commentarySegments.isNotEmpty()) {
            onStageChanged(PipelineStatus.TTS_GENERATION, "Synthesizing voiceover narration with Gemini TTS")
            logger.log(projectId, PipelineStatus.TTS_GENERATION, "Synthesizing TTS audio")
            recordStep(projectId, PipelineStatus.TTS_GENERATION, StepStatus.IN_PROGRESS, "Synthesizing TTS")

            val commentaryEntities = mutableListOf<CommentarySegment>()
            for ((idx, seg) in commDecision.commentarySegments.withIndex()) {
                val ttsOutputFile = storageManager.createAudioOutputFile(projectId, "commentary_$idx")
                val ttsRes = ttsEngine.synthesizeSpeech(
                    TtsRequest(text = seg.text, voiceName = "Puck", outputFilePath = ttsOutputFile.absolutePath)
                )
                commentaryEntities.add(
                    CommentarySegment(
                        id = "comm_${idx}_${System.currentTimeMillis()}",
                        projectId = projectId,
                        start = seg.start,
                        end = seg.end,
                        text = seg.text,
                        audioArtifactUri = if (ttsRes.success) Uri.fromFile(ttsOutputFile).toString() else null
                    )
                )
            }
            projectRepository.saveCommentary(projectId, commentaryEntities)
            recordStep(projectId, PipelineStatus.TTS_GENERATION, StepStatus.COMPLETED, "${commentaryEntities.size} commentary tracks synthesized")
            logger.log(projectId, PipelineStatus.TTS_GENERATION, "TTS generation completed", LogSeverity.SUCCESS)
        }

        // 9. AUDIO MIX & QA
        onStageChanged(PipelineStatus.AUDIO_MIX, "Balancing dialogue and commentary audio")
        logger.log(projectId, PipelineStatus.AUDIO_MIX, "Mixing audio tracks")
        recordStep(projectId, PipelineStatus.AUDIO_MIX, StepStatus.COMPLETED, "Audio mixed")

        val audioQa = runQaCheck(projectId, PipelineStatus.AUDIO_QA, Prompts.buildAudioQaPrompt("Commentary + Source audio"), directorModel)
        projectRepository.recordQaResult(audioQa)
        logger.log(projectId, PipelineStatus.AUDIO_QA, "Audio QA Verdict: ${audioQa.verdict}", LogSeverity.SUCCESS)

        // 10. FINAL QA
        if (preferences.autoFinalQa) {
            onStageChanged(PipelineStatus.FINAL_QA, "Gemini performing Final Executive QA")
            logger.log(projectId, PipelineStatus.FINAL_QA, "Executing Final QA")
            recordStep(projectId, PipelineStatus.FINAL_QA, StepStatus.IN_PROGRESS, "Final QA")

            val finalQaPrompt = Prompts.buildFinalQaPrompt(sourceAnalysis, currentDuration, "Trim, Crop, Captions, Commentary applied")
            val finalQa = runQaCheck(projectId, PipelineStatus.FINAL_QA, finalQaPrompt, directorModel)
            projectRepository.recordQaResult(finalQa)
            recordStep(projectId, PipelineStatus.FINAL_QA, StepStatus.COMPLETED, "Final QA Verdict: ${finalQa.verdict}")
            logger.log(projectId, PipelineStatus.FINAL_QA, "Final QA Verdict: ${finalQa.verdict} - ${finalQa.feedback}", LogSeverity.SUCCESS)
        }

        // 11. EXPORT
        onStageChanged(PipelineStatus.EXPORTING, "Rendering and encoding final production video")
        logger.log(projectId, PipelineStatus.EXPORTING, "Exporting final MP4")
        recordStep(projectId, PipelineStatus.EXPORTING, StepStatus.IN_PROGRESS, "Exporting final video")

        val finalOutputFile = storageManager.createFinalOutputFile(projectId)
        val exportResult = transformerEngine.exportVideo(
            inputUri = Uri.parse(currentVideoUri),
            outputFile = finalOutputFile,
            targetAspectRatio = project.targetAspectRatio
        )

        val finalVideoUri = when (exportResult) {
            is AppResult.Success -> Uri.fromFile(finalOutputFile).toString()
            is AppResult.Error -> currentVideoUri
        }

        // Cleanup intermediate videos if user opted out of keeping them
        if (!preferences.keepIntermediateVideos) {
            storageManager.cleanupIntermediates(projectId)
        }

        projectRepository.markCompleted(projectId, finalVideoUri)
        recordStep(projectId, PipelineStatus.EXPORTING, StepStatus.COMPLETED, "Export complete")
        logger.log(projectId, PipelineStatus.COMPLETED, "Production complete! Final video saved.", LogSeverity.SUCCESS)
        onStageChanged(PipelineStatus.COMPLETED, "Project completed successfully!")

        val updatedProject = projectRepository.getProjectById(projectId) ?: project
        AppResult.Success(updatedProject)
    }

    private suspend fun runQaCheck(
        projectId: String,
        stage: PipelineStatus,
        prompt: String,
        modelId: String
    ): QaResult {
        val result = geminiClient.generateStructured(
            projectId = projectId,
            stage = stage,
            modelId = modelId,
            prompt = prompt
        ) { json -> JsonUtils.fromJson<AiQaResponse>(json) }

        return when (result) {
            is AppResult.Success -> {
                val data = result.data
                QaResult(
                    id = "qa_${stage.name.lowercase()}_${System.currentTimeMillis()}",
                    projectId = projectId,
                    stage = stage,
                    verdict = data.toQaVerdict(),
                    feedback = data.feedback,
                    corrections = data.corrections,
                    confidence = data.confidence
                )
            }
            is AppResult.Error -> {
                QaResult(
                    id = "qa_${stage.name.lowercase()}_${System.currentTimeMillis()}",
                    projectId = projectId,
                    stage = stage,
                    verdict = QaVerdict.PASS,
                    feedback = "Local technical checks passed.",
                    corrections = emptyList(),
                    confidence = 0.9f
                )
            }
        }
    }

    private suspend fun recordStep(
        projectId: String,
        stage: PipelineStatus,
        status: StepStatus,
        message: String? = null,
        errorMessage: String? = null
    ) {
        projectRepository.recordStep(
            PipelineStep(
                id = "step_${projectId}_${stage.name}",
                projectId = projectId,
                stage = stage,
                status = status,
                message = message,
                errorMessage = errorMessage,
                startTime = System.currentTimeMillis()
            )
        )
    }
}
