package com.vineyard.aivideostudio.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vineyard.aivideostudio.core.model.Caption
import com.vineyard.aivideostudio.core.model.CommentarySegment
import com.vineyard.aivideostudio.core.model.MediaArtifact
import com.vineyard.aivideostudio.core.model.PipelineStep
import com.vineyard.aivideostudio.core.model.Project
import com.vineyard.aivideostudio.core.model.QaResult
import com.vineyard.aivideostudio.core.model.TimelineSegment
import com.vineyard.aivideostudio.core.model.TranscriptSegment
import com.vineyard.aivideostudio.project.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class EditorUiState(
    val project: Project? = null,
    val timelineSegments: List<TimelineSegment> = emptyList(),
    val transcriptSegments: List<TranscriptSegment> = emptyList(),
    val captions: List<Caption> = emptyList(),
    val commentary: List<CommentarySegment> = emptyList(),
    val steps: List<PipelineStep> = emptyList(),
    val artifacts: List<MediaArtifact> = emptyList(),
    val qaResults: List<QaResult> = emptyList(),
    val activeCommentaryAudioUri: String? = null,
    val isOriginalAudioMuted: Boolean = true,
    val totalTimeTrimmedSeconds: Double = 0.0,
    val isCopyrightTransformed: Boolean = true
)

class EditorViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _isOriginalAudioMuted = MutableStateFlow(true)

    val uiState: StateFlow<EditorUiState> = combine(
        projectRepository.getProjectByIdFlow(projectId),
        projectRepository.getTimelineSegments(projectId),
        projectRepository.getTranscript(projectId),
        projectRepository.getCaptions(projectId),
        projectRepository.getCommentary(projectId),
        projectRepository.getSteps(projectId),
        projectRepository.getArtifacts(projectId),
        projectRepository.getQaResults(projectId),
        _isOriginalAudioMuted
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val project = args[0] as? Project
        @Suppress("UNCHECKED_CAST")
        val timeline = (args[1] as? List<TimelineSegment>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val transcript = (args[2] as? List<TranscriptSegment>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val captions = (args[3] as? List<Caption>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val commentary = (args[4] as? List<CommentarySegment>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val steps = (args[5] as? List<PipelineStep>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val artifacts = (args[6] as? List<MediaArtifact>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val qaResults = (args[7] as? List<QaResult>) ?: emptyList()
        val isMuted = (args[8] as? Boolean) ?: true

        // Extract synchronized commentary track
        val activeAudioUri = commentary.firstOrNull { !it.audioArtifactUri.isNullOrBlank() }?.audioArtifactUri
            ?: artifacts.firstOrNull { it.stage.name.contains("TTS") || it.stage.name.contains("COMMENTARY") }?.fileUri

        // Calculate total trimmed duration to show transformative editing impact
        val totalTrimmed = timeline.sumOf { seg: TimelineSegment ->
            (seg.originalEnd - seg.originalStart) - (seg.currentEnd - seg.currentStart)
        }.coerceAtLeast(0.0)

        EditorUiState(
            project = project,
            timelineSegments = timeline,
            transcriptSegments = transcript,
            captions = captions,
            commentary = commentary,
            steps = steps,
            artifacts = artifacts,
            qaResults = qaResults,
            activeCommentaryAudioUri = activeAudioUri,
            isOriginalAudioMuted = isMuted,
            totalTimeTrimmedSeconds = totalTrimmed,
            isCopyrightTransformed = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditorUiState()
    )

    /**
     * Toggles whether the source audio is muted during editor preview inspection.
     */
    fun toggleOriginalAudioMute() {
        _isOriginalAudioMuted.value = !_isOriginalAudioMuted.value
    }
}