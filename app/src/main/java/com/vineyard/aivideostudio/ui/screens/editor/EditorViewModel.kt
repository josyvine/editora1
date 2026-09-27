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
    val qaResults: List<QaResult> = emptyList()
)

class EditorViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    val uiState: StateFlow<EditorUiState> = combine(
        projectRepository.getProjectByIdFlow(projectId),
        projectRepository.getTimelineSegments(projectId),
        projectRepository.getTranscript(projectId),
        projectRepository.getCaptions(projectId),
        projectRepository.getCommentary(projectId),
        projectRepository.getSteps(projectId),
        projectRepository.getArtifacts(projectId),
        projectRepository.getQaResults(projectId)
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        EditorUiState(
            project = args[0] as? Project,
            timelineSegments = (args[1] as? List<TimelineSegment>) ?: emptyList(),
            transcriptSegments = (args[2] as? List<TranscriptSegment>) ?: emptyList(),
            captions = (args[3] as? List<Caption>) ?: emptyList(),
            commentary = (args[4] as? List<CommentarySegment>) ?: emptyList(),
            steps = (args[5] as? List<PipelineStep>) ?: emptyList(),
            artifacts = (args[6] as? List<MediaArtifact>) ?: emptyList(),
            qaResults = (args[7] as? List<QaResult>) ?: emptyList()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditorUiState()
    )
}
