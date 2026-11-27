package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.model.CartoonScene
import com.example.model.ProjectStatus
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.CyanSpark
import com.example.ui.theme.EmeraldSuccess
import com.example.viewmodel.ExportUiState
import com.example.viewmodel.LibraryUiState
import com.example.viewmodel.StudioViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    projectId: String,
    viewModel: StudioViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val libraryState by viewModel.projectsState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val scenes by remember(projectId) { viewModel.observeScenesForProject(projectId) }
        .collectAsStateWithLifecycle()

    val project = (libraryState as? LibraryUiState.Success)
        ?.projects
        ?.firstOrNull { it.id == projectId }

    val playableUri: Uri? = remember(project?.localVideoPath, project?.cloudVideoUrl) {
        val localFile = project?.localVideoPath?.let { File(it) }
        when {
            localFile != null && localFile.exists() -> Uri.fromFile(localFile)
            !project?.cloudVideoUrl.isNullOrBlank() -> Uri.parse(project!!.cloudVideoUrl)
            else -> null
        }
    }

    val exoPlayer = remember(playableUri) {
        if (playableUri != null) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(playableUri))
                prepare()
            }
        } else null
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.title ?: "Cartoon Player") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (exoPlayer != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            player = exoPlayer
                                            useController = true
                                        }
                                    },
                                    update = { view ->
                                        view.player = exoPlayer
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = project?.statusMessage ?: "Preparing cartoon video...",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    if (project != null && project.statusEnum().isRunning) {
                                        LinearProgressIndicator(
                                            progress = { project.progressPercent / 100f },
                                            modifier = Modifier.fillMaxWidth(0.8f),
                                            color = CoralPrimary
                                        )
                                        Text(
                                            text = "${project.progressPercent}% complete",
                                            color = CyanSpark,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (project != null) {
                    item {
                        val isVerifiedReady = project.statusEnum() == ProjectStatus.COMPLETED &&
                            project.verifiedMp4 &&
                            !project.localVideoPath.isNullOrBlank() &&
                            File(project.localVideoPath).exists()
                        val isExporting = exportState is ExportUiState.Exporting &&
                            (exportState as ExportUiState.Exporting).projectId == project.id

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isVerifiedReady) {
                                Button(
                                    onClick = { viewModel.downloadProjectMp4(project) },
                                    enabled = !isExporting,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("player_download_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (isExporting) {
                                            "Downloading ${(exportState as ExportUiState.Exporting).percent}%..."
                                        } else {
                                            "${stringResource(R.string.download_video_cta)} (${project.qualityEnum().label})"
                                        }
                                    )
                                }
                            } else if (project.statusEnum() == ProjectStatus.FAILED) {
                                Button(
                                    onClick = { viewModel.retryProject(project) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.retry_generation_cta))
                                }
                            }
                        }

                        if (isVerifiedReady) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Verified, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Verified MP4 • ${project.durationMinutes} min • ${project.qualityEnum().label} • ${project.languageEnum().label}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldSuccess
                                )
                            }
                        }

                        if (exportState is ExportUiState.Done && (exportState as ExportUiState.Done).projectId == project.id) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                                Text((exportState as ExportUiState.Done).message, color = EmeraldSuccess)
                            }
                        }
                    }

                    if (project.characterBible.isNotBlank()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Consistent Character Bible", style = MaterialTheme.typography.titleMedium)
                                    Text(project.characterBible, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                if (scenes.isNotEmpty()) {
                    item {
                        Text(
                            text = "Generated Storyboard (${scenes.size} Scenes)",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    items(scenes, key = { it.id }) { scene ->
                        SceneStoryboardRow(scene)
                    }
                }
            }
        }
    }
}

@Composable
private fun SceneStoryboardRow(scene: CartoonScene) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val artFile = scene.artFilePath?.let { File(it) }?.takeIf { it.exists() }
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (artFile != null) {
                    AsyncImage(
                        model = artFile,
                        contentDescription = scene.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("#${scene.sceneIndex + 1}", style = MaterialTheme.typography.labelLarge)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Scene ${scene.sceneIndex + 1}: ${scene.title}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = scene.narrationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
                Text(
                    text = "${scene.durationSeconds}s • ${scene.cameraMotion} • SFX: ${scene.sfxCue}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanSpark
                )
            }
        }
    }
}
