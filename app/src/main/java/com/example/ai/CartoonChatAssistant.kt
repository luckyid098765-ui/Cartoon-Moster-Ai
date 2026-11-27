package com.example.ai

import com.example.model.CartoonLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class CartoonChatAssistant {

    private var activeLanguage: CartoonLanguage? = null
    private var chatSession: Chat? = null

    private fun getOrCreateChat(language: CartoonLanguage): Chat {
        val existing = chatSession
        if (existing != null && activeLanguage == language) {
            return existing
        }

        val langRule = when (language) {
            CartoonLanguage.ENGLISH -> "Respond in clear, engaging English."
            CartoonLanguage.HINDI -> "Respond primarily in natural Hindi using Devanagari script (हिन्दी)."
            CartoonLanguage.BENGALI -> "Respond primarily in natural Bengali using Bengali script (বাংলা)."
        }

        val model = Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    You are the Creative AI Assistant inside Cartoon Master AI.
                    Help users brainstorm cartoon stories, memorable characters, 2D/3D/Cinematic scene ideas, dialogue, and general creative questions.
                    $langRule
                    Keep replies vivid, helpful, well-structured, and ready to turn into a cartoon video script when the user wishes.
                    """.trimIndent()
                )
            },
            generationConfig = generationConfig {
                temperature = 0.85f
            }
        )
        val created = model.startChat()
        activeLanguage = language
        chatSession = created
        return created
    }

    fun resetConversation() {
        chatSession = null
        activeLanguage = null
    }

    suspend fun sendMessage(
        userMessage: String,
        language: CartoonLanguage
    ): Result<String> {
        val clean = userMessage.trim()
        if (clean.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter a message."))
        }
        return try {
            val chat = getOrCreateChat(language)
            val response = withTimeout(45_000L) {
                chat.sendMessage(clean)
            }
            val reply = response.text?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("AI assistant returned an empty reply. Please try again.")
            Result.success(reply)
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("AI response timed out. Please retry."))
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    e.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "Unable to reach Cloud AI right now. Please check connection and retry.",
                    e
                )
            )
        }
    }
}
