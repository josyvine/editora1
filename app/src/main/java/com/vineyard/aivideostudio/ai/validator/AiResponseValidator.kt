package com.vineyard.aivideostudio.ai.validator

import com.vineyard.aivideostudio.ai.model.CaptionDecision
import com.vineyard.aivideostudio.ai.model.CommentaryDecision
import com.vineyard.aivideostudio.ai.model.CropDecision
import com.vineyard.aivideostudio.ai.model.SourceAnalysis
import com.vineyard.aivideostudio.ai.model.TrimDecision
import com.vineyard.aivideostudio.ai.model.ZoomDecision
import com.vineyard.aivideostudio.core.validation.ValidationResult

object AiResponseValidator {

    fun validateSourceAnalysis(analysis: SourceAnalysis): ValidationResult {
        if (analysis.duration <= 0.0) {
            return ValidationResult.Invalid("Source analysis duration must be positive", "duration")
        }
        for (scene in analysis.scenes) {
            if (scene.start < 0.0 || scene.end <= scene.start || scene.end > analysis.duration + 0.5) {
                return ValidationResult.Invalid("Invalid scene timestamp: [${scene.start}, ${scene.end}] for duration ${analysis.duration}", "scenes")
            }
        }
        return ValidationResult.Valid
    }

    fun validateTrim(decision: TrimDecision, currentDuration: Double): ValidationResult {
        if (!decision.isNecessary || decision.segmentsToRemove.isEmpty()) {
            return ValidationResult.Valid
        }

        val sorted = decision.segmentsToRemove.sortedBy { it.start }
        var lastEnd = 0.0

        for (segment in sorted) {
            if (segment.start < 0.0) {
                return ValidationResult.Invalid("Trim start timestamp must be >= 0 (got ${segment.start})", "start")
            }
            if (segment.end <= segment.start) {
                return ValidationResult.Invalid("Trim end must be > start (got ${segment.start} to ${segment.end})", "end")
            }
            if (segment.end > currentDuration + 0.1) {
                return ValidationResult.Invalid("Trim end exceeds video duration ${currentDuration} (got ${segment.end})", "end")
            }
            if (segment.start < lastEnd) {
                return ValidationResult.Invalid("Overlapping trim segments detected at ${segment.start}", "segments")
            }
            lastEnd = segment.end
        }

        // Check that trim doesn't remove 100% of the video
        val totalRemoved = sorted.sumOf { it.end - it.start }
        if (totalRemoved >= currentDuration - 0.5) {
            return ValidationResult.Invalid("Trim plan would remove entire video ($totalRemoved of $currentDuration s)", "segments")
        }

        return ValidationResult.Valid
    }

    fun validateCrop(decision: CropDecision): ValidationResult {
        if (!decision.isNecessary) return ValidationResult.Valid

        if (decision.x < 0f || decision.x >= 1f) {
            return ValidationResult.Invalid("Crop x must be between 0 and 1 (got ${decision.x})", "x")
        }
        if (decision.y < 0f || decision.y >= 1f) {
            return ValidationResult.Invalid("Crop y must be between 0 and 1 (got ${decision.y})", "y")
        }
        if (decision.width <= 0f || decision.width > 1f) {
            return ValidationResult.Invalid("Crop width must be in (0, 1] (got ${decision.width})", "width")
        }
        if (decision.height <= 0f || decision.height > 1f) {
            return ValidationResult.Invalid("Crop height must be in (0, 1] (got ${decision.height})", "height")
        }
        if (decision.x + decision.width > 1.05f) {
            return ValidationResult.Invalid("Crop x + width exceeds 1.0 (got ${decision.x + decision.width})", "width")
        }
        if (decision.y + decision.height > 1.05f) {
            return ValidationResult.Invalid("Crop y + height exceeds 1.0 (got ${decision.y + decision.height})", "height")
        }

        return ValidationResult.Valid
    }

    fun validateZoom(decision: ZoomDecision, currentDuration: Double): ValidationResult {
        if (!decision.isNecessary) return ValidationResult.Valid

        if (decision.start < 0.0 || decision.start >= currentDuration) {
            return ValidationResult.Invalid("Zoom start outside duration range: ${decision.start}", "start")
        }
        if (decision.end <= decision.start || decision.end > currentDuration + 0.1) {
            return ValidationResult.Invalid("Zoom end must be > start and <= duration (got ${decision.end})", "end")
        }
        if (decision.fromScale <= 0f || decision.toScale <= 0f) {
            return ValidationResult.Invalid("Zoom scale must be positive", "scale")
        }
        if (decision.centerX !in 0f..1f || decision.centerY !in 0f..1f) {
            return ValidationResult.Invalid("Zoom center must be normalized within [0, 1]", "center")
        }

        return ValidationResult.Valid
    }

    fun validateCaptions(decision: CaptionDecision, currentDuration: Double): ValidationResult {
        if (!decision.isNecessary || decision.captions.isEmpty()) return ValidationResult.Valid

        for (caption in decision.captions) {
            if (caption.text.isBlank()) {
                return ValidationResult.Invalid("Caption text cannot be blank", "text")
            }
            if (caption.start < 0.0 || caption.end <= caption.start || caption.end > currentDuration + 0.2) {
                return ValidationResult.Invalid("Invalid caption timing [${caption.start}, ${caption.end}] for duration $currentDuration", "timing")
            }
            if (caption.x !in 0f..1f || caption.y !in 0f..1f) {
                return ValidationResult.Invalid("Caption coordinates must be within [0, 1]", "position")
            }
        }
        return ValidationResult.Valid
    }

    fun validateCommentary(decision: CommentaryDecision, currentDuration: Double): ValidationResult {
        if (!decision.isNecessary || decision.commentarySegments.isEmpty()) return ValidationResult.Valid

        for (seg in decision.commentarySegments) {
            if (seg.text.isBlank()) {
                return ValidationResult.Invalid("Commentary text cannot be blank", "text")
            }
            if (seg.start < 0.0 || seg.end <= seg.start || seg.end > currentDuration + 0.5) {
                return ValidationResult.Invalid("Invalid commentary timing [${seg.start}, ${seg.end}]", "timing")
            }
        }
        return ValidationResult.Valid
    }
}
