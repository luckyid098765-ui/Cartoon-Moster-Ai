package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.CartoonLanguage
import com.example.model.CartoonProject
import com.example.model.CartoonStyle
import com.example.model.ProjectStatus
import com.example.model.VideoDurationOption
import com.example.model.VideoQuality
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.CyanSpark
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.GoldenAmber
import com.example.viewmodel.LibraryUiState
import com.example.viewmodel.StudioViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    onSignOut: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenPlayer: (String) -> Unit
) {
    val context = LocalContext.current
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val activeBanner by viewModel.activeBanner.collectAsStateWithLifecycle()
    val adminConfig by viewModel.adminConfig.collectAsStateWithLifecycle()
    val libraryState by viewModel.projectsState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Optional notification visibility; generation proceeds regardless */ }
    )

    val latestFailedProject: CartoonProject? = (libraryState as? LibraryUiState.Success)
        ?.projects
        ?.firstOrNull { it.statusEnum() == ProjectStatus.FAILED }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Studio Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_app_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, CyanSpark.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${userProfile?.displayName ?: "Creator"} • ${adminConfig.providerLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Sign out"
                    )
                }
            }

            // Hero Studio Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_studio_hero),
                        contentDescription = "Cartoon Studio Hero",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xDC0E0D1D), Color(0x550E0D1D))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.create_new_cartoon),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Text(
                            text = "Unlimited studio creation • Up to 30 min • Verified MP4",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanSpark
                        )
                    }
                }
            }

            // Live Generation Progress Card
            AnimatedVisibility(visible = activeBanner != null) {
                activeBanner?.let { banner ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${banner.title} (${banner.progressPercent}%)",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = banner.statusMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.cancelGeneration(banner.projectId) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Cancel,
                                        contentDescription = "Cancel generation"
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = { banner.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = CoralPrimary
                            )
                            if (banner.retryAttempt > 0) {
                                Text(
                                    text = "Auto-recovered via retry #${banner.retryAttempt}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoldenAmber
                                )
                            }
                        }
                    }
                }
            }

            // Recoverable Failure Notice (if last project failed)
            if (activeBanner == null && latestFailedProject != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Last job paused: ${latestFailedProject.title}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = latestFailedProject.errorMessage ?: "Temporary error occurred.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.retryProject(latestFailedProject) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry")
                        }
                    }
                }
            }

            // Quick Starter Ideas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StarterIdeaChip("Fox Skyship (EN)") {
                    viewModel.updateTitle("Pip's Skyship Quest")
                    viewModel.updateStoryPrompt("Pip, a brave little red fox with cyan aviator goggles, builds a wooden flying ship to rescue lost fireflies above Misty Valley.")
                    viewModel.updateCharacterPrompt("Pip: small amber fox wearing oversized cyan goggles and a bright red scarf.")
                    viewModel.selectStyle(CartoonStyle.THREE_D)
                    viewModel.selectLanguage(CartoonLanguage.ENGLISH)
                }
                StarterIdeaChip("जादुई जंगल (HI)") {
                    viewModel.updateTitle("नन्हा हाथी और जादुई तारा")
                    viewModel.updateStoryPrompt("एक नन्हा नीला हाथी गोलू रात के जंगल में गिरे हुए चमकीले तारे को वापस आसमान तक पहुँचाने की मजेदार यात्रा पर निकलता है।")
                    viewModel.updateCharacterPrompt("Golu: cute round sky-blue baby elephant with a yellow explorer hat.")
                    viewModel.selectStyle(CartoonStyle.KIDS_FRIENDLY)
                    viewModel.selectLanguage(CartoonLanguage.HINDI)
                }
                StarterIdeaChip("ভুতুড়ে প্রাসাদ (BN)") {
                    viewModel.updateTitle("টুনটুনি ও রহস্যময় দুর্গ")
                    viewModel.updateStoryPrompt("দুই বন্ধু তপু আর মিনা বৃষ্টির রাতে এক পুরোনো জাদুর প্রাসাদে ঢুকে কথা বলা লণ্ঠনের রহস্য সমাধান করে।")
                    viewModel.updateCharacterPrompt("Topu: brave boy with green jacket; Mina: smart girl holding a glowing golden lantern.")
                    viewModel.selectStyle(CartoonStyle.HORROR)
                    viewModel.selectLanguage(CartoonLanguage.BENGALI)
                }
            }

            // Story & Character Form
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = formState.title,
                        onValueChange = viewModel::updateTitle,
                        label = { Text("Cartoon Title (Optional)") },
                        placeholder = { Text("Auto-generated from story if blank") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("title_input")
                    )

                    OutlinedTextField(
                        value = formState.storyPrompt,
                        onValueChange = viewModel::updateStoryPrompt,
                        label = { Text(stringResource(R.string.story_prompt_label)) },
                        placeholder = { Text(stringResource(R.string.story_prompt_hint)) },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("story_prompt_input")
                    )

                    OutlinedTextField(
                        value = formState.characterPrompt,
                        onValueChange = viewModel::updateCharacterPrompt,
                        label = { Text(stringResource(R.string.character_prompt_label)) },
                        placeholder = { Text(stringResource(R.string.character_prompt_hint)) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("character_prompt_input")
                    )
                }
            }

            // Language Selector (English, Hindi, Bengali)
            SectionCard(
                title = stringResource(R.string.select_language_label),
                icon = Icons.Filled.Translate
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CartoonLanguage.entries.forEach { lang ->
                        val selected = formState.selectedLanguage == lang
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectLanguage(lang) },
                            label = { Text("${lang.label} (${lang.nativeName})") },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("lang_${lang.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricViolet,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Cartoon Style Selector
            SectionCard(
                title = stringResource(R.string.select_style_label),
                icon = Icons.Filled.AutoAwesome
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CartoonStyle.entries.forEach { style ->
                        FilterChip(
                            selected = formState.selectedStyle == style,
                            onClick = { viewModel.selectStyle(style) },
                            label = { Text(style.label) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("style_${style.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CoralPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Duration Selector (1, 5, 10, 20, 30 min — Max 30 min)
            SectionCard(
                title = stringResource(R.string.select_duration_label),
                icon = Icons.Filled.Schedule
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoDurationOption.entries.forEach { dur ->
                        FilterChip(
                            selected = formState.selectedDuration == dur,
                            onClick = { viewModel.selectDuration(dur) },
                            label = { Text(dur.label) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("duration_${dur.minutes}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanSpark,
                                selectedLabelColor = Color(0xFF002730)
                            )
                        )
                    }
                }
            }

            // Video Quality & Audio Mix
            SectionCard(
                title = stringResource(R.string.select_quality_label),
                icon = Icons.Filled.HighQuality
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    VideoQuality.entries.forEach { q ->
                        val selected = formState.selectedQuality == q
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) CyanSpark else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { viewModel.selectQuality(q) }
                                .testTag("quality_${q.name.lowercase()}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Hd, contentDescription = null)
                                Column {
                                    Text(q.label, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${q.width}x${q.height} MP4",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                AudioToggleRow(
                    icon = Icons.Filled.RecordVoiceOver,
                    label = "AI Multilingual Voiceover",
                    checked = formState.includeVoiceover,
                    onCheckedChange = viewModel::toggleVoiceover
                )
                AudioToggleRow(
                    icon = Icons.Filled.MusicNote,
                    label = "Mood-Matched Background Music",
                    checked = formState.includeBgm,
                    onCheckedChange = viewModel::toggleBgm
                )
                AudioToggleRow(
                    icon = Icons.Filled.SurroundSound,
                    label = "Cartoon Sound Effects (SFX)",
                    checked = formState.includeSfx,
                    onCheckedChange = viewModel::toggleSfx
                )
            }

            // Feedback messages
            formState.feedbackError?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            formState.feedbackSuccess?.let { ok ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ok,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = onOpenProjects) {
                            Text("View Library")
                        }
                    }
                }
            }

            // Generate Video CTA
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    viewModel.generateCartoonVideo { projectId ->
                        onOpenPlayer(projectId)
                    }
                },
                enabled = !formState.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("generate_video_button"),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CoralPrimary,
                    contentColor = Color.White
                )
            ) {
                if (formState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Launching Studio Pipeline...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${stringResource(R.string.generate_video_cta)} (${formState.selectedDuration.label} • ${formState.selectedQuality.label})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // Optional Admin-configured AdMob Banner slot (Policy-compliant: only shown when enabled by Admin)
            if (adminConfig.adMobEnabled && adminConfig.adMobBannerUnitId.isNotBlank()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ad Slot Configured (${adminConfig.adMobBannerUnitId.take(18)}…)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "AdMob Ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanSpark
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StarterIdeaChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.minimumInteractiveComponentSize()
    )
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = CyanSpark, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun AudioToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
