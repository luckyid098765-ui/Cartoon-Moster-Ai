package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.CartoonChatAssistant
import com.example.model.CartoonLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessageItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val language: CartoonLanguage,
    val isError: Boolean = false
)

data class ChatUiState(
    val messages: List<ChatMessageItem> = emptyList(),
    val inputDraft: String = "",
    val selectedLanguage: CartoonLanguage = CartoonLanguage.ENGLISH,
    val isSending: Boolean = false,
    val lastFailedPrompt: String? = null
)

class ChatViewModel(
    private val assistant: CartoonChatAssistant = CartoonChatAssistant()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            messages = listOf(
                ChatMessageItem(
                    text = "Welcome to Cartoon Master AI Chat! Ask me in English, हिन्दी, or বাংলা to brainstorm cartoon stories, characters, dialogue, or creative ideas.",
                    isFromUser = false,
                    language = CartoonLanguage.ENGLISH
                )
            )
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun updateDraft(text: String) {
        _uiState.value = _uiState.value.copy(inputDraft = text.take(2000))
    }

    fun selectLanguage(language: CartoonLanguage) {
        if (_uiState.value.selectedLanguage != language) {
            assistant.resetConversation()
            val greeting = when (language) {
                CartoonLanguage.ENGLISH -> "Switched to English. What cartoon idea or question can I help with?"
                CartoonLanguage.HINDI -> "भाषा हिन्दी में बदल दी गई है। बताइए, आज कौन-सी कार्टून कहानी या विचार तैयार करें?"
                CartoonLanguage.BENGALI -> "ভাষা বাংলায় পরিবর্তন করা হয়েছে। বলুন, আজ কোন কার্টুন গল্প বা আইডিয়া তৈরি করব?"
            }
            _uiState.value = _uiState.value.copy(
                selectedLanguage = language,
                messages = _uiState.value.messages + ChatMessageItem(
                    text = greeting,
                    isFromUser = false,
                    language = language
                )
            )
        }
    }

    fun sendMessage(overrideText: String? = null) {
        val current = _uiState.value
        val prompt = (overrideText ?: current.inputDraft).trim()
        if (prompt.isBlank() || current.isSending) return

        val userMsg = ChatMessageItem(
            text = prompt,
            isFromUser = true,
            language = current.selectedLanguage
        )

        _uiState.value = current.copy(
            messages = current.messages + userMsg,
            inputDraft = if (overrideText == null) "" else current.inputDraft,
            isSending = true,
            lastFailedPrompt = null
        )

        viewModelScope.launch {
            val result = assistant.sendMessage(prompt, current.selectedLanguage)
            result.onSuccess { reply ->
                val stateNow = _uiState.value
                _uiState.value = stateNow.copy(
                    messages = stateNow.messages + ChatMessageItem(
                        text = reply,
                        isFromUser = false,
                        language = stateNow.selectedLanguage
                    ),
                    isSending = false
                )
            }.onFailure { err ->
                val stateNow = _uiState.value
                _uiState.value = stateNow.copy(
                    messages = stateNow.messages + ChatMessageItem(
                        text = err.localizedMessage ?: "Cloud AI request failed. Tap Retry.",
                        isFromUser = false,
                        language = stateNow.selectedLanguage,
                        isError = true
                    ),
                    isSending = false,
                    lastFailedPrompt = prompt
                )
            }
        }
    }

    fun retryLastMessage() {
        val failedPrompt = _uiState.value.lastFailedPrompt ?: return
        sendMessage(failedPrompt)
    }

    fun clearChat() {
        assistant.resetConversation()
        val lang = _uiState.value.selectedLanguage
        _uiState.value = ChatUiState(
            selectedLanguage = lang,
            messages = listOf(
                ChatMessageItem(
                    text = when (lang) {
                        CartoonLanguage.ENGLISH -> "Started a fresh chat session. Tell me your cartoon idea!"
                        CartoonLanguage.HINDI -> "नया चैट सत्र शुरू हो गया है। अपनी कार्टून कहानी या सवाल लिखें!"
                        CartoonLanguage.BENGALI -> "নতুন চ্যাট শুরু হয়েছে। আপনার কার্টুন গল্প বা প্রশ্ন লিখুন!"
                    },
                    isFromUser = false,
                    language = lang
                )
            )
        )
    }

    companion object {
        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatViewModel()
            }
        }
    }
}
