package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.model.CartoonProject
import com.example.model.ProjectStatus
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.CyanSpark
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldenAmber
import com.example.viewmodel.ExportUiState
import com.example.viewmodel.LibraryUiState
import com.example.viewmodel.StudioViewModel
import java.io.File

@Composable
fun ProjectsScreen(
    viewModel: StudioViewModel,
    onOpenStudio: () -> Unit,
    onOpenPlayer: (String) -> Unit
) {
    val libraryState by viewModel.projectsState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<CartoonProject?>(null) }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Cartoon Project?") },
            text = { Text("This deletes \"${target.title}\" and its local MP4 file from your library.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(target)
                        pendingDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        when (val state = libraryState) {
            is LibraryUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is LibraryUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            is LibraryUiState.Success -> {
                if (state.projects.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 500.dp)
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_library),
                            contentDescription = "Empty cartoon library",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(24.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.empty_projects_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.empty_projects_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = onOpenStudio,
                            modifier = Modifier.height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                        ) {
                            Text("Create First Cartoon")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.nav_projects),
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    Text(
                                        text = "${state.projects.size} projects • ${downloads.size} MP4 downloads",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        when (val exp = exportState) {
                            is ExportUiState.Done -> {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E3A2F)),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldSuccess)
                                                Text(exp.message, color = Color.White, style = MaterialTheme.typography.bodySmall)
                                            }
                                            TextButton(onClick = viewModel::resetExportState) {
                                                Text("Dismiss", color = CyanSpark)
                                            }
                                        }
                                    }
                                }
                            }
                            is ExportUiState.Failed -> {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                exp.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(onClick = viewModel::resetExportState) {
                                                Text("Dismiss")
                                            }
                                        }
                                    }
                                }
                            }
                            else -> Unit
                        }

                        items(state.projects, key = { it.id }) { project ->
                            ProjectCard(
                                project = project,
                                exportState = exportState,
                                onPlay = { onOpenPlayer(project.id) },
                                onDownload = { viewModel.downloadProjectMp4(project) },
                                onRetry = { viewModel.retryProject(project) },
                                onDelete = { pendingDelete = project }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: CartoonProject,
    exportState: ExportUiState,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val status = project.statusEnum()
    val isExportingThis = exportState is ExportUiState.Exporting && exportState.projectId == project.id
    val hasLocalMp4 = project.verifiedMp4 && !project.localVideoPath.isNullOrBlank() && File(project.localVideoPath).exists()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("project_card_${project.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 112.dp, height = 74.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val thumb = project.thumbnailPath?.takeIf { File(it).exists() }
                    if (thumb != null) {
                        AsyncImage(
                            model = File(thumb),
                            contentDescription = project.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = null,
                            tint = CyanSpark,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = project.title.ifBlank { "Untitled Cartoon" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${project.durationMinutes} min • ${project.qualityEnum().label} • ${project.languageEnum().label} • ${project.styleEnum().label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusPill(status = status, verified = hasLocalMp4, sizeBytes = project.fileSizeBytes)
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_project_${project.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete_project_cta),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (status.isRunning) {
                LinearProgressIndicator(
                    progress = { project.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = CoralPrimary
                )
                Text(
                    text = "${project.progressPercent}% — ${project.statusMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (status == ProjectStatus.FAILED && !project.errorMessage.isNullOrBlank()) {
                Text(
                    text = project.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (isExportingThis) {
                val pct = (exportState as ExportUiState.Exporting).percent
                LinearProgressIndicator(
                    progress = { pct / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Exporting verified MP4 to Movies ($pct%)...", style = MaterialTheme.typography.labelSmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (status == ProjectStatus.COMPLETED && hasLocalMp4) {
                    Button(
                        onClick = onPlay,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("play_project_${project.id}"),
                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.play_video_cta))
                    }
                    OutlinedButton(
                        onClick = onDownload,
                        enabled = !isExportingThis,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("download_project_${project.id}")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.download_video_cta))
                    }
                } else if (status == ProjectStatus.FAILED) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("retry_project_${project.id}")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.retry_generation_cta))
                    }
                    OutlinedButton(
                        onClick = onPlay,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Details")
                    }
                } else {
                    OutlinedButton(
                        onClick = onPlay,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("View Live Storyboard & Progress")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: ProjectStatus, verified: Boolean, sizeBytes: Long) {
    val bg = when {
        status == ProjectStatus.COMPLETED && verified -> EmeraldSuccess.copy(alpha = 0.18f)
        status == ProjectStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        else -> GoldenAmber.copy(alpha = 0.18f)
    }
    val fg = when {
        status == ProjectStatus.COMPLETED && verified -> EmeraldSuccess
        status == ProjectStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> GoldenAmber
    }
    val sizeMb = if (sizeBytes > 0L) String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0)) else ""
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (status == ProjectStatus.COMPLETED && verified) {
                Icon(Icons.Filled.Verified, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
            }
            Text(
                text = if (status == ProjectStatus.COMPLETED && verified) "Verified MP4 $sizeMb" else status.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = fg
            )
        }
    }
}
