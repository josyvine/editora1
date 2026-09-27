package com.vineyard.aivideostudio.ai.prompt

import com.vineyard.aivideostudio.ai.model.SourceAnalysis
import com.vineyard.aivideostudio.core.model.TimelineMap
import com.vineyard.aivideostudio.core.model.VideoMetadata

object Prompts {

    fun buildSourceAnalysisPrompt(metadata: VideoMetadata, sourceYoutubeUrl: String? = null): String = """
        You are an elite video editing director analyzing a raw source video for an automated transformative production pipeline.
        ${if (!sourceYoutubeUrl.isNullOrBlank()) "SOURCE YOUTUBE REFERENCE: $sourceYoutubeUrl\nUse this reference context to understand the exact setting, subjects, and topic of this video.\n" else ""}
        TECHNICAL METADATA:
        - Duration: ${metadata.durationSeconds} seconds
        - Resolution: ${metadata.width}x${metadata.height}
        - Orientation: ${if (metadata.isPortrait) "PORTRAIT" else "LANDSCAPE"}
        - FPS: ${metadata.frameRate}
        
        TASK:
        Perform deep semantic source analysis. Accurately identify the main subject, setting, dialogue, and comedic/dramatic punchlines.
        CRUCIAL: Ground your analysis strictly in the actual content (e.g. sports interview, meme, basketball press conference). Do NOT hallucinate unrelated prank or drinking actions if they are not in the video.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "duration": ${metadata.durationSeconds},
          "resolution": "${metadata.width}x${metadata.height}",
          "orientation": "${if (metadata.isPortrait) "PORTRAIT" else "LANDSCAPE"}",
          "summary": "Accurate, grounded summary of the actual subject, topic, and comedic/hype hook",
          "scenes": [
            {
              "start": 0.0,
              "end": ${metadata.durationSeconds},
              "description": "Scene overview grounded in actual visual action",
              "importance": "CRITICAL / HIGH / MEDIUM / LOW / REMOVABLE",
              "keySubjects": ["main subject or speaker"]
            }
          ],
          "dialogueSegments": [
            {
              "start": 0.0,
              "end": ${metadata.durationSeconds.coerceAtMost(5.0)},
              "speaker": "Speaker",
              "text": "Spoken dialogue"
            }
          ],
          "criticalContent": ["Key comedic punchline or highlight moment that must be emphasized"],
          "editingCandidates": [
            {
              "start": 0.0,
              "end": 0.8,
              "recommendation": "TRIM",
              "reason": "Eliminate dead-air intro to speed up pacing and create a transformative derivative edit"
            }
          ],
          "suggestedEditingStrategy": "High-retention pacing cut with energetic voiceover commentary"
        }
    """.trimIndent()

    fun buildTrimDecisionPrompt(
        sourceAnalysis: SourceAnalysis,
        currentDuration: Double,
        timelineMap: TimelineMap
    ): String = """
        You are an elite video editing director executing a MANDATORY transformative pacing cut.
        
        PRODUCTION MANDATE:
        This video is being transformed into a copyright-safe derivative work. Leaving the video unedited or uncut is STRICTLY FORBIDDEN.
        You MUST identify sections to cut to tighten pacing, eliminate dead air, remove awkward silence, or accelerate into the core action.
        
        CURRENT TIMELINE:
        - Video Duration: $currentDuration seconds
        - Subject / Summary: ${sourceAnalysis.summary}
        - Critical Highlight: ${sourceAnalysis.criticalContent.joinToString()}
        
        RULES:
        1. You MUST specify at least one segment to remove (e.g., cutting the first 0.5s–1.2s dead-air intro, or trimming trailing pause).
        2. Set "isNecessary": true.
        3. All timestamps MUST fall within the current timeline (0.0 to $currentDuration).
        4. Preserve the core punchline or main action.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "trim",
          "isNecessary": true,
          "segmentsToRemove": [
            {
              "start": 0.0,
              "end": 0.8,
              "reason": "Cut lead-in pause to instantly jump into the punchline and ensure transformative derivative editing"
            }
          ],
          "explanation": "Tightening intro dead air to boost viewer retention and establish a transformative cut"
        }
    """.trimIndent()

    fun buildTrimQaPrompt(
        sourceAnalysis: SourceAnalysis,
        expectedCutsCount: Int,
        newDuration: Double
    ): String = """
        You are a video editing QA inspector reviewing the result of the TRIM operation.
        
        VALIDATION CRITERIA:
        - Original Duration: ${sourceAnalysis.duration}s
        - New Trimmed Duration: ${newDuration}s
        - Cuts Applied: $expectedCutsCount
        
        RULE:
        Verify that the cut successfully tightened pacing without cutting the core punchline: "${sourceAnalysis.criticalContent.joinToString()}".
        If duration is shorter and punchline remains intact, return PASS.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.98,
          "feedback": "Pacing successfully tightened. Transformative cut applied cleanly.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildCropDecisionPrompt(
        sourceAnalysis: SourceAnalysis,
        targetAspectRatio: String,
        currentWidth: Int,
        currentHeight: Int
    ): String = """
        You are a video editing director evaluating framing and aspect ratio reframing.
        
        SPECS:
        - Current Dimensions: ${currentWidth}x${currentHeight}
        - Target Aspect Ratio: $targetAspectRatio
        - Summary: ${sourceAnalysis.summary}
        
        RULE:
        Use normalized coordinates (0.0 to 1.0).
        Ensure the primary speaker or subject is centered with clean headroom.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "crop",
          "isNecessary": true,
          "x": 0.0,
          "y": 0.0,
          "width": 1.0,
          "height": 1.0,
          "targetAspectRatio": "$targetAspectRatio",
          "explanation": "Framing verified for $targetAspectRatio presentation"
        }
    """.trimIndent()

    fun buildCropQaPrompt(
        targetAspectRatio: String,
        appliedCrop: String
    ): String = """
        Inspect the CROP/REFRAME operation.
        - Target Aspect Ratio: $targetAspectRatio
        - Crop Parameters: $appliedCrop
        
        Verify: Subject is properly centered without awkward cropping.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.95,
          "feedback": "Framing aligns with target aspect ratio.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildZoomDecisionPrompt(
        sourceAnalysis: SourceAnalysis,
        currentDuration: Double
    ): String = """
        You are a video editing director deciding on a dynamic punch-in zoom for comedic or dramatic emphasis.
        
        CURRENT TIMELINE:
        - Duration: $currentDuration seconds
        - Context: ${sourceAnalysis.summary}
        - Key Moment: ${sourceAnalysis.criticalContent.joinToString()}
        
        RULE:
        Apply a punch-in zoom scale (e.g., 1.15x to 1.25x) timed to the climax or reaction to heighten visual impact.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "zoom",
          "isNecessary": true,
          "start": 0.0,
          "end": $currentDuration,
          "fromScale": 1.0,
          "toScale": 1.18,
          "centerX": 0.5,
          "centerY": 0.5,
          "explanation": "Dynamic punch-in to emphasize speaker expression and comedic reaction"
        }
    """.trimIndent()

    fun buildZoomQaPrompt(zoomDetails: String): String = """
        Inspect the ZOOM operation: $zoomDetails.
        Check that zoom scale provides visual emphasis while preserving video quality.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.95,
          "feedback": "Zoom punch-in successfully enhances visual engagement.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildCaptionDecisionPrompt(
        sourceAnalysis: SourceAnalysis,
        currentDuration: Double
    ): String = """
        You are an elite subtitle director creating high-retention burned-in captions for this video.
        
        TIMELINE:
        - Duration: $currentDuration seconds
        - Spoken Dialogue / Context: ${sourceAnalysis.dialogueSegments.joinToString { "[${it.start}-${it.end}] ${it.text}" }}
        
        RULES:
        1. Generate short, punchy, high-impact captions formatted for short-form retention (1 to 4 words per segment).
        2. Timestamps MUST fall between 0.0 and $currentDuration seconds.
        3. Use high-contrast colors (e.g. #FFD700 Gold, #00FFCC Cyan, #FFFFFF White).
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "caption",
          "isNecessary": true,
          "captions": [
            {
              "text": "LOOK AT THIS!",
              "start": 0.0,
              "end": ${currentDuration.coerceAtMost(2.5)},
              "x": 0.5,
              "y": 0.82,
              "style": "BOLD",
              "colorHex": "#FFD700"
            }
          ],
          "explanation": "High retention synchronized subtitles"
        }
    """.trimIndent()

    fun buildCaptionQaPrompt(captionCount: Int): String = """
        Inspect the rendered captions ($captionCount caption segments).
        Check: readability, timestamp synchronization, and visual clarity.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.98,
          "feedback": "Captions are correctly timed, high contrast, and formatted for retention.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildCommentaryPrompt(
        sourceAnalysis: SourceAnalysis,
        currentDuration: Double
    ): String = """
        You are a world-class, high-octane viral sports and meme voiceover commentator.
        
        CRUCIAL MANDATE:
        The original copyrighted audio of this video is COMPLETELY STRIPPED AND PURGED.
        Your voiceover commentary will be the ONLY audio track on the final video.
        
        VIDEO CONTEXT:
        - Duration: $currentDuration seconds
        - Actual Subject & Scene: ${sourceAnalysis.summary}
        - Key Action / Punchline: ${sourceAnalysis.criticalContent.joinToString()}
        
        EXPRESSIVE VOCAL ACTING INSTRUCTIONS:
        This script will be performed by an expressive neural voice actor capable of screaming, laughing, and shouting.
        You MUST include emotional vocal acting cues in square brackets throughout the text, such as:
        [SCREAMING], [LOUD HYPE], [SHOCKED GASP], [DISBELIEF], [LAUGHING], [FAST-PACED].
        
        RULES:
        1. Ground your commentary strictly in what is happening (e.g. basketball, press conference meme, hilarious reaction). Do NOT hallucinate drinking/prank actions that do not exist.
        2. Keep the entire voiceover script timed to finish naturally within $currentDuration seconds.
        3. Start speaking the hype commentary immediately without introductory greetings like "Hello folks".
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "commentary",
          "isNecessary": true,
          "tone": "hyper-energetic, hyped, screaming meme commentator",
          "commentarySegments": [
            {
              "start": 0.0,
              "end": $currentDuration,
              "text": "[LOUD HYPE]: OHHH MY GOODNESS! Look at the confidence right here! [LAUGHING]: He literally said he feels like the meme itself! [SCREAMING]: UNBELIEVABLE!"
            }
          ],
          "explanation": "High-octane viral commentary with emotional acting cues replacing copyrighted original audio"
        }
    """.trimIndent()

    fun buildAudioQaPrompt(details: String): String = """
        Inspect the audio replacement operation: $details.
        
        CHECKS:
        1. Was the original copyrighted audio purged? (YES)
        2. Is the replacement AI commentary soundtrack active, clear, and synchronized? (YES)
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.98,
          "feedback": "Original copyrighted audio successfully purged. Live commentary soundtrack active and clean.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildFinalQaPrompt(
        sourceAnalysis: SourceAnalysis,
        finalDuration: Double,
        pipelineHistorySummary: String
    ): String = """
        You are the Executive QA Director performing the FINAL production signoff.
        
        PRODUCTION AUDIT:
        - Original Source Duration: ${sourceAnalysis.duration}s
        - Final Output Duration: ${finalDuration}s
        - Applied Transformations: $pipelineHistorySummary
        
        CRITERIA FOR PRODUCTION APPROVAL:
        1. The video was visibly transformed (pacing tightened, duration adjusted from original).
        2. The original copyrighted audio track was purged and replaced with original AI voiceover.
        3. High-retention captions are burned directly into the visual frames.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.99,
          "feedback": "Production ready. Derivative transformation complete, copyrighted audio purged, captions burned in.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()
}