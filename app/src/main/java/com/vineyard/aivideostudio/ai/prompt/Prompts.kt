package com.vineyard.aivideostudio.ai.prompt

import com.vineyard.aivideostudio.ai.model.SourceAnalysis
import com.vineyard.aivideostudio.core.model.TimelineMap
import com.vineyard.aivideostudio.core.model.VideoMetadata

object Prompts {

    fun buildSourceAnalysisPrompt(metadata: VideoMetadata, sourceYoutubeUrl: String? = null): String = """
        You are an elite video editing director analyzing a raw source video.
        ${if (!sourceYoutubeUrl.isNullOrBlank()) "SOURCE YOUTUBE REFERENCE URL: $sourceYoutubeUrl\nAnalyze the YouTube video content at this URL (transcript, chapters, pacing, dialogue, and highlight moments) to inform the editing decisions for the downloaded local video file.\n" else ""}
        VIDEO TECHNICAL METADATA:
        - Duration: ${metadata.durationSeconds} seconds
        - Resolution: ${metadata.width}x${metadata.height}
        - Orientation: ${if (metadata.isPortrait) "PORTRAIT" else "LANDSCAPE"}
        - FPS: ${metadata.frameRate}
        
        TASK:
        Perform comprehensive semantic source analysis. Identify important scenes, key actions, dialogue opportunities, and potential sections to trim or highlight.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "duration": ${metadata.durationSeconds},
          "resolution": "${metadata.width}x${metadata.height}",
          "orientation": "${if (metadata.isPortrait) "PORTRAIT" else "LANDSCAPE"}",
          "summary": "Brief summary of the video content and tone",
          "scenes": [
            {
              "start": 0.0,
              "end": 10.0,
              "description": "Scene overview",
              "importance": "CRITICAL / HIGH / MEDIUM / LOW / REMOVABLE",
              "keySubjects": ["subject name"]
            }
          ],
          "dialogueSegments": [
            {
              "start": 0.0,
              "end": 5.0,
              "speaker": "Speaker",
              "text": "Spoken dialogue"
            }
          ],
          "criticalContent": ["Key moments that must NOT be removed"],
          "editingCandidates": [
            {
              "start": 12.0,
              "end": 15.0,
              "recommendation": "TRIM / REFRAME / ZOOM",
              "reason": "Redundant pause or shaky camera"
            }
          ],
          "suggestedEditingStrategy": "Recommended editing arc"
        }
    """.trimIndent()

    fun buildTrimDecisionPrompt(
        sourceAnalysis: SourceAnalysis,
        currentDuration: Double,
        timelineMap: TimelineMap
    ): String = """
        You are an elite video editing director deciding whether TRIM operations are needed.
        
        CURRENT TIMELINE:
        - Current Video Duration: $currentDuration seconds
        - Original Duration: ${sourceAnalysis.duration} seconds
        - Critical Content to Preserve: ${sourceAnalysis.criticalContent.joinToString()}
        
        RULE:
        Only trim if there are redundant, dead, or low-value pauses.
        If no trimming is required, set "isNecessary": false and "operation": "skip".
        DO NOT trim critical content.
        All timestamps MUST be in the CURRENT video timeline (0 to $currentDuration).
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "trim",
          "isNecessary": true,
          "segmentsToRemove": [
            {
              "start": 12.4,
              "end": 15.8,
              "reason": "Awkward silence or dead air"
            }
          ],
          "explanation": "Why these cuts elevate the pacing"
        }
    """.trimIndent()

    fun buildTrimQaPrompt(
        sourceAnalysis: SourceAnalysis,
        expectedCutsCount: Int,
        newDuration: Double
    ): String = """
        You are a video editing QA inspector reviewing the result of a TRIM operation.
        
        ORIGINAL ANALYSIS:
        - Original Duration: ${sourceAnalysis.duration}s
        - Critical Elements: ${sourceAnalysis.criticalContent.joinToString()}
        - Expected Cuts Performed: $expectedCutsCount
        - Resulting Duration: ${newDuration}s
        
        TASK:
        Inspect whether critical content was preserved and the pacing is clean.
        Return PASS if successful, or FAIL with correction guidance.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.95,
          "feedback": "Pacing improved cleanly, no critical scenes dropped.",
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
        You are a video editing director deciding whether CROP/REFRAME is needed.
        
        SPECS:
        - Current Dimensions: ${currentWidth}x${currentHeight}
        - Target Aspect Ratio: $targetAspectRatio
        - Summary: ${sourceAnalysis.summary}
        
        RULE:
        Use normalized coordinates (0.0 to 1.0).
        If the video already matches the target framing or crop is unnecessary, set "isNecessary": false.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "crop",
          "isNecessary": true,
          "x": 0.12,
          "y": 0.05,
          "width": 0.76,
          "height": 0.90,
          "targetAspectRatio": "$targetAspectRatio",
          "explanation": "Reframing to center subject for $targetAspectRatio"
        }
    """.trimIndent()

    fun buildCropQaPrompt(
        targetAspectRatio: String,
        appliedCrop: String
    ): String = """
        Inspect the CROP/REFRAME operation.
        - Target Aspect Ratio: $targetAspectRatio
        - Applied Crop Parameters: $appliedCrop
        
        Verify: Subject is well-framed, no heads cut off, no distorted proportions.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.98,
          "feedback": "Subject properly centered with clean headroom.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildZoomDecisionPrompt(
        sourceAnalysis: SourceAnalysis,
        currentDuration: Double
    ): String = """
        You are a video editing director deciding whether a dynamic ZOOM/PUNCH-IN is needed for emphasis.
        
        CURRENT TIMELINE:
        - Duration: $currentDuration seconds
        - Critical Scenes: ${sourceAnalysis.criticalContent.joinToString()}
        
        RULE:
        Only zoom if a punch-in creates visual impact on a key moment (e.g. 1.0x to 1.15x).
        If unnecessary, set "isNecessary": false.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "zoom",
          "isNecessary": false,
          "start": 0.0,
          "end": 0.0,
          "fromScale": 1.0,
          "toScale": 1.15,
          "centerX": 0.5,
          "centerY": 0.5,
          "explanation": "Zoom skipped or applied to punch-in on reaction"
        }
    """.trimIndent()

    fun buildZoomQaPrompt(zoomDetails: String): String = """
        Inspect the ZOOM operation: $zoomDetails.
        Check that zoom scale is smooth, does not blur subject, and stays within framing bounds.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.95,
          "feedback": "Dynamic scale looks natural without distortion.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildCaptionDecisionPrompt(
        sourceAnalysis: SourceAnalysis,
        currentDuration: Double
    ): String = """
        You are a caption director creating high-retention subtitles for the current edited video.
        
        TIMELINE:
        - Duration: $currentDuration seconds
        - Dialogue & Highlights: ${sourceAnalysis.dialogueSegments.joinToString { "[${it.start}-${it.end}] ${it.text}" }}
        
        RULE:
        Generate readable, high-impact caption segments. Timestamps MUST fall between 0 and $currentDuration.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "caption",
          "isNecessary": true,
          "captions": [
            {
              "text": "WHAT AN INCREDIBLE MOMENT!",
              "start": 1.0,
              "end": 3.2,
              "x": 0.5,
              "y": 0.82,
              "style": "BOLD",
              "colorHex": "#FFD700"
            }
          ],
          "explanation": "High retention lower-third captions"
        }
    """.trimIndent()

    fun buildCaptionQaPrompt(captionCount: Int): String = """
        Inspect the rendered captions ($captionCount caption segments).
        Check: readability, no subject occlusion, correct timing.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.95,
          "feedback": "Captions are readable and well-positioned.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildCommentaryPrompt(
        sourceAnalysis: SourceAnalysis,
        currentDuration: Double
    ): String = """
        You are an engaging documentary and social-video commentator.
        Create voiceover commentary for the final video timeline ($currentDuration seconds).
        
        SUMMARY: ${sourceAnalysis.summary}
        
        RULE:
        Generate timestamped narration segments that complement the visual without talking over dialogue.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "commentary",
          "isNecessary": true,
          "tone": "enthusiastic and professional",
          "commentarySegments": [
            {
              "start": 0.5,
              "end": 4.0,
              "text": "Welcome to an exclusive inside look at the production process."
            }
          ],
          "explanation": "Engaging hook and context narrative"
        }
    """.trimIndent()

    fun buildAudioQaPrompt(details: String): String = """
        Inspect the audio mix: $details.
        Verify balance between original audio, voiceover narration commentary, and clarity.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.95,
          "feedback": "Audio levels balanced with clean vocal clarity.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildFinalQaPrompt(
        sourceAnalysis: SourceAnalysis,
        finalDuration: Double,
        pipelineHistorySummary: String
    ): String = """
        You are the Executive QA Director performing the FINAL semantic and technical signoff.
        
        ORIGINAL REFERENCE:
        - Original Duration: ${sourceAnalysis.duration}s
        - Critical Scenes: ${sourceAnalysis.criticalContent.joinToString()}
        
        FINAL OUTPUT:
        - Final Duration: ${finalDuration}s
        - Transformations: $pipelineHistorySummary
        
        CHECKS:
        1. All critical content retained?
        2. No unexpected audio drops or black frames?
        3. Pacing and composition polished?
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.98,
          "feedback": "Production ready. All criteria satisfied.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()
}
