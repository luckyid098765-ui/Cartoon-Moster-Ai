package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.CartoonLanguage
import com.example.ui.components.Tactile3DCard
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.CyanSpark
import com.example.ui.theme.ElectricViolet
import com.example.viewmodel.ChatMessageItem
import com.example.viewmodel.ChatViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onApplyStoryToStudio: (storyText: String, language: CartoonLanguage) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 3D Header Panel
            Tactile3DCard(
                cornerRadius = 22.dp,
                accentGlow = CyanSpark,
                contentPadding = 14.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .shadow(8.dp, CircleShape, ambientColor = CyanSpark)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(ElectricViolet, CoralPrimary)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SmartToy,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.ai_chat_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Cloud AI Assistant • EN / हिन्दी / বাংলা",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanSpark
                            )
                        }
                    }

                    IconButton(
                        onClick = viewModel::clearChat,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "New Chat"
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Language selector pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CartoonLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = uiState.selectedLanguage == lang,
                            onClick = { viewModel.selectLanguage(lang) },
                            label = { Text("${lang.label} (${lang.nativeName})") },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("chat_lang_${lang.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricViolet,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Quick AI prompt starters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        viewModel.sendMessage("Write a 1-minute 3D adventure cartoon story about a brave little robot and a glowing owl.")
                    },
                    label = { Text("3D Robot Story (EN)") },
                    leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                )
                AssistChip(
                    onClick = {
                        viewModel.selectLanguage(CartoonLanguage.HINDI)
                        viewModel.sendMessage("बच्चों के लिए एक मज़ेदार 3D कार्टून कहानी लिखिए जिसमें एक छोटा शेर और एक जादुई पतंग हो।")
                    },
                    label = { Text("हिन्दी कहानी विचार") },
                    modifier = Modifier.minimumInteractiveComponentSize()
                )
                AssistChip(
                    onClick = {
                        viewModel.selectLanguage(CartoonLanguage.BENGALI)
                        viewModel.sendMessage("ছোটদের জন্য একটি সুন্দর বাংলা কার্টুন গল্প ও চরিত্রের বিবরণ তৈরি করে দাও।")
                    },
                    label = { Text("বাংলা কার্টুন গল্প") },
                    modifier = Modifier.minimumInteractiveComponentSize()
                )
            }

            // Conversation Thread
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.messages, key = { it.id }) { msg ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { it / 4 }
                    ) {
                        ChatBubble(
                            item = msg,
                            onRetry = viewModel::retryLastMessage,
                            onUseInStudio = {
                                onApplyStoryToStudio(msg.text, msg.language)
                            }
                        )
                    }
                }

                if (uiState.isSending) {
                    item {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = CyanSpark
                            )
                            Text(
                                text = "Cloud AI is thinking...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Message Input Bar
            Tactile3DCard(
                cornerRadius = 20.dp,
                accentGlow = CoralPrimary,
                contentPadding = 10.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.inputDraft,
                        onValueChange = viewModel::updateDraft,
                        placeholder = { Text(stringResource(R.string.ai_chat_hint)) },
                        maxLines = 4,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input"),
                        shape = RoundedCornerShape(16.dp)
                    )
                    FilledIconButton(
                        onClick = { viewModel.sendMessage() },
                        enabled = uiState.inputDraft.isNotBlank() && !uiState.isSending,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("chat_send_button"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = CoralPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    item: ChatMessageItem,
    onRetry: () -> Unit,
    onUseInStudio: () -> Unit
) {
    val alignEnd = item.isFromUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
    ) {
        val containerColor = when {
            item.isError -> MaterialTheme.colorScheme.errorContainer
            item.isFromUser -> ElectricViolet
            else -> MaterialTheme.colorScheme.surface
        }
        val contentColor = when {
            item.isError -> MaterialTheme.colorScheme.onErrorContainer
            item.isFromUser -> Color.White
            else -> MaterialTheme.colorScheme.onSurface
        }
        val bubbleShape = RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = if (alignEnd) 18.dp else 4.dp,
            bottomEnd = if (alignEnd) 4.dp else 18.dp
        )

        Surface(
            shape = bubbleShape,
            color = containerColor,
            shadowElevation = if (item.isFromUser) 8.dp else 5.dp,
            modifier = Modifier.widthIn(max = 620.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!item.isFromUser && !item.isError) {
                    Text(
                        text = "Cartoon Master AI (${item.language.nativeName})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyanSpark
                    )
                }
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
                if (item.isError) {
                    OutlinedButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retry")
                    }
                } else if (!item.isFromUser && item.text.length >= 60) {
                    TextButton(
                        onClick = onUseInStudio,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("use_idea_in_studio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MovieCreation,
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.use_in_studio_cta),
                            color = CoralPrimary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
