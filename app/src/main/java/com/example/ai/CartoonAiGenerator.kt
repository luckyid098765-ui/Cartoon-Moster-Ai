package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.CartoonLanguage
import com.example.model.CartoonScene
import com.example.model.CartoonStyle
import com.example.model.SceneStatus
import com.example.model.VideoDurationOption
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class GeneratedStoryPlan(
    val title: String,
    val characterBible: String,
    val scenes: List<CartoonScene>
)

class CartoonAiGenerator {

    private val storySchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Catchy cartoon episode title"),
            "characterBible" to Schema.string(
                description = "Detailed visual character design bible describing exact species, hair, face, clothing, colors, and proportions so every scene looks identical"
            ),
            "scenes" to Schema.array(
                description = "Ordered list of cartoon scenes covering the requested duration",
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Short scene title"),
                        "visualPrompt" to Schema.string(
                            description = "Visual scene description referencing characters by exact look, setting, lighting, and pose"
                        ),
                        "narrationText" to Schema.string(
                            description = "Spoken voiceover narration and character dialogue in the requested language"
                        ),
                        "sfxCue" to Schema.enumeration(
                            values = listOf("sparkle", "whoosh", "thunder", "boing", "footsteps", "chime"),
                            description = "Primary sound effect cue for this scene"
                        ),
                        "cameraMotion" to Schema.enumeration(
                            values = listOf("ZOOM_IN", "ZOOM_OUT", "PAN_LEFT", "PAN_RIGHT"),
                            description = "Animated camera movement for this scene"
                        )
                    )
                )
            )
        )
    )

    private val storyModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = storySchema
                temperature = 0.8f
            }
        )
    }

    private val artModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(AspectRatio.LANDSCAPE_16x9)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }
        )
    }

    suspend fun generateStoryPlan(
        projectId: String,
        userId: String,
        titleHint: String,
        storyPrompt: String,
        characterPrompt: String,
        style: CartoonStyle,
        language: CartoonLanguage,
        duration: VideoDurationOption,
        maxRetries: Int,
        timeoutMs: Long,
        onRetry: suspend (attempt: Int, reason: String) -> Unit
    ): GeneratedStoryPlan {
        val cleanStory = storyPrompt.trim()
        require(cleanStory.length >= 10) {
            "Story prompt is too short. Please enter at least 10 characters describing your cartoon."
        }
        val targetScenes = targetSceneCountFor(duration)
        val totalSeconds = duration.minutes * 60
        val secondsPerScene = (totalSeconds / targetScenes).coerceAtLeast(10)

        val languageInstruction = when (language) {
            CartoonLanguage.ENGLISH -> "Write title and narrationText in natural, expressive English."
            CartoonLanguage.HINDI -> "Write title and narrationText in natural Hindi using Devanagari script (हिन्दी)."
            CartoonLanguage.BENGALI -> "Write title and narrationText in natural Bengali using Bengali script (বাংলা)."
        }

        val prompt = buildString {
            appendLine("You are an award-winning cartoon director and screenplay writer.")
            appendLine("Create a complete, original animated cartoon episode plan.")
            appendLine("Title hint: ${titleHint.ifBlank { "Auto-generate an exciting title" }}")
            appendLine("Story premise: $cleanStory")
            if (characterPrompt.isNotBlank()) {
                appendLine("User-defined characters (MUST preserve faithfully): $characterPrompt")
            }
            appendLine("Cartoon style: ${style.label} (${style.visualDirective})")
            appendLine("Target duration: ${duration.minutes} minutes ($totalSeconds seconds total).")
            appendLine("Generate EXACTLY $targetScenes distinct sequential scenes.")
            appendLine("LANGUAGE & VOICE RULE: $languageInstruction Maintain a single consistent narrator voice tone across every scene.")
            appendLine("VISUAL & BACKGROUND CONSISTENCY RULE: In characterBible, lock exact character colors, outfits, accessories, facial traits, AND recurring world palette/lighting/background architecture. Keep visualPrompt in English for the image generator, explicitly repeating each character's visual traits and background continuity.")
            appendLine("Ensure narrationText per scene takes roughly $secondsPerScene seconds to speak clearly and tells a cohesive story with beginning, rising action, climax, and resolution.")
        }

        return retryTransient(
            maxRetries = maxRetries,
            onRetry = onRetry
        ) {
            val response = withTimeout(timeoutMs.coerceIn(30_000L, 120_000L)) {
                storyModel.generateContent(prompt)
            }
            val rawJson = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("AI story director returned an empty response.")

            val json = JSONObject(rawJson)
            val resolvedTitle = titleHint.trim().ifBlank { json.optString("title", "Cartoon Episode") }.take(140)
            val characterBible = json.optString("characterBible", "").take(4000)
            val scenesArray = json.optJSONArray("scenes")
                ?: throw IllegalStateException("AI response did not contain any scenes.")
            if (scenesArray.length() == 0) {
                throw IllegalStateException("AI generated 0 scenes for this story.")
            }

            val parsedScenes = (0 until scenesArray.length()).map { idx ->
                val obj = scenesArray.getJSONObject(idx)
                CartoonScene(
                    id = "scene_$idx",
                    userId = userId,
                    projectId = projectId,
                    sceneIndex = idx.toLong(),
                    title = obj.optString("title", "Scene ${idx + 1}").take(140),
                    visualPrompt = obj.optString("visualPrompt", storyPrompt).take(2000),
                    narrationText = obj.optString("narrationText", "").take(3000),
                    sfxCue = obj.optString("sfxCue", "sparkle").take(100),
                    cameraMotion = obj.optString("cameraMotion", "ZOOM_IN").take(60),
                    durationSeconds = secondsPerScene.toLong(),
                    status = SceneStatus.PENDING.name
                )
            }

            GeneratedStoryPlan(
                title = resolvedTitle,
                characterBible = characterBible,
                scenes = parsedScenes
            )
        }
    }

    suspend fun generateCharacterSheet(
        characterBible: String,
        style: CartoonStyle,
        outputFile: File,
        maxRetries: Int,
        timeoutMs: Long,
        onRetry: suspend (attempt: Int, reason: String) -> Unit
    ): Bitmap {
        val prompt = """
            Cartoon character turnaround reference sheet, clean neutral studio backdrop,
            front and three-quarter expressive poses of the characters described below.
            Style: ${style.visualDirective}.
            Characters: $characterBible.
            Crisp edges, vibrant colors, no text or labels.
        """.trimIndent()

        val bitmap = retryTransient(maxRetries = maxRetries, onRetry = onRetry) {
            val response = withTimeout(timeoutMs.coerceIn(45_000L, 120_000L)) {
                artModel.generateContent(prompt)
            }
            extractBitmapOrThrow(response)
        }
        saveBitmapJpeg(bitmap, outputFile)
        return bitmap
    }

    suspend fun generateSceneArtwork(
        scene: CartoonScene,
        characterBible: String,
        characterRefBitmap: Bitmap?,
        style: CartoonStyle,
        outputFile: File,
        maxRetries: Int,
        timeoutMs: Long,
        onRetry: suspend (attempt: Int, reason: String) -> Unit
    ): Bitmap {
        val sceneInstruction = """
            Create a cinematic 16:9 cartoon animation still frame for Scene ${scene.sceneIndex + 1}: ${scene.title}.
            Art style: ${style.visualDirective}.
            Consistent character reference bible: $characterBible.
            Scene action and environment: ${scene.visualPrompt}.
            Maintain exact character colors, outfits, faces, and proportions from the reference image.
            No speech bubbles, no watermarks, no text overlays.
        """.trimIndent()

        val bitmap = retryTransient(maxRetries = maxRetries, onRetry = onRetry) {
            val response = withTimeout(timeoutMs.coerceIn(45_000L, 120_000L)) {
                if (characterRefBitmap != null) {
                    artModel.generateContent(
                        content {
                            image(characterRefBitmap)
                            text(sceneInstruction)
                        }
                    )
                } else {
                    artModel.generateContent(sceneInstruction)
                }
            }
            extractBitmapOrThrow(response)
        }
        saveBitmapJpeg(bitmap, outputFile)
        return bitmap
    }

    private fun extractBitmapOrThrow(response: com.google.firebase.ai.type.GenerateContentResponse): Bitmap {
        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()
        return imagePart?.image
            ?: throw IllegalStateException("AI image generator returned no image frames.")
    }

    private fun saveBitmapJpeg(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
    }

    private suspend fun <T> retryTransient(
        maxRetries: Int,
        onRetry: suspend (attempt: Int, reason: String) -> Unit,
        block: suspend () -> T
    ): T {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt <= maxRetries) {
            try {
                return block()
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    lastError = e
                } else {
                    throw e
                }
            } catch (e: QuotaExceededException) {
                // Quota errors should still be reported clearly if persistent
                lastError = e
                if (attempt >= maxRetries) {
                    throw IllegalStateException(
                        "AI service quota reached (${e.localizedMessage ?: "rate limit"}). Please retry shortly or verify Firebase Blaze billing.",
                        e
                    )
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "AI attempt ${attempt + 1} failed: ${e.message}", e)
            }

            if (attempt >= maxRetries) break
            attempt++
            val backoffMs = (1500L * attempt).coerceAtMost(8000L)
            onRetry(attempt, lastError.localizedMessage ?: "Temporary AI service error")
            delay(backoffMs)
        }
        throw lastError ?: IllegalStateException("AI generation failed after retries.")
    }

    companion object {
        private const val TAG = "CartoonAiGenerator"

        fun targetSceneCountFor(duration: VideoDurationOption): Int = when (duration) {
            VideoDurationOption.MIN_1 -> 4
            VideoDurationOption.MIN_5 -> 8
            VideoDurationOption.MIN_10 -> 12
            VideoDurationOption.MIN_20 -> 16
            VideoDurationOption.MIN_30 -> 20
        }
    }
}
