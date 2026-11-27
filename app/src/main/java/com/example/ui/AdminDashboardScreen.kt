package com.example.ui

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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WorkHistory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ProviderMode
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.CyanSpark
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldenAmber
import com.example.viewmodel.AdminViewModel

@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val config by viewModel.providerConfig.collectAsStateWithLifecycle()
    val jobs by viewModel.allJobs.collectAsStateWithLifecycle()
    val logs by viewModel.errorLogs.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()

    var selectedMode by remember(config.activeProvider) { mutableStateOf(config.providerModeEnum()) }
    var endpointUrl by remember(config.remoteEndpointUrl) { mutableStateOf(config.remoteEndpointUrl) }
    var apiTokenInput by remember { mutableStateOf("") }
    var timeoutStr by remember(config.timeoutSeconds) { mutableStateOf(config.timeoutSeconds.toString()) }
    var maxRetriesStr by remember(config.maxRetries) { mutableStateOf(config.maxRetries.toString()) }
    var quotaMbStr by remember(config.storageQuotaMb) { mutableStateOf(config.storageQuotaMb.toString()) }
    var adMobEnabled by remember(config.adMobEnabled) { mutableStateOf(config.adMobEnabled) }
    var adMobBannerId by remember(config.adMobBannerUnitId) { mutableStateOf(config.adMobBannerUnitId) }
    var maintenanceMode by remember(config.maintenanceMode) { mutableStateOf(config.maintenanceMode) }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageMetrics()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Filled.AdminPanelSettings,
                        contentDescription = null,
                        tint = CyanSpark,
                        modifier = Modifier.size(30.dp)
                    )
                    Column {
                        Text("Admin Studio Console", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "API providers, storage, database, jobs, errors & user governance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Usage & Health Metrics Row
            item {
                val localMb = String.format("%.2f MB", uiState.localStorageBytes / (1024.0 * 1024.0))
                val completedJobs = jobs.count { it.status == "COMPLETED" }
                val activeJobs = jobs.count { it.status == "RUNNING" || it.status == "RETRYING" }
                val unresolvedErrors = logs.count { !it.resolved }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile("Users", users.size.toString(), Icons.Filled.People, Modifier.weight(1f))
                    MetricTile("Jobs ($activeJobs live)", "$completedJobs/${jobs.size}", Icons.Filled.WorkHistory, Modifier.weight(1f))
                    MetricTile("Errors", unresolvedErrors.toString(), Icons.Filled.ErrorOutline, Modifier.weight(1f))
                    MetricTile("Storage", localMb, Icons.Filled.Storage, Modifier.weight(1f))
                }
            }

            // Storage & Cache Management
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Storage & Temp File Monitor", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Quota: ${config.storageQuotaMb} MB • Clean temporary WAV/work caches safely",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = viewModel::cleanTempCache,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clean Temp")
                        }
                    }
                }
            }

            // AI Provider & Monetization Config Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.CloudDone, contentDescription = null, tint = CyanSpark)
                            Text("AI Video Provider & AdMob Configuration", style = MaterialTheme.typography.titleMedium)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProviderMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = selectedMode == mode,
                                    onClick = { selectedMode = mode },
                                    label = { Text(if (mode == ProviderMode.FIREBASE_AI_NATIVE) "Firebase AI Studio" else "Custom REST API") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                )
                            }
                        }

                        OutlinedTextField(
                            value = endpointUrl,
                            onValueChange = { endpointUrl = it },
                            label = { Text("Remote Video Backend URL (Optional)") },
                            placeholder = { Text("https://video-engine.example.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = apiTokenInput,
                            onValueChange = { apiTokenInput = it },
                            label = { Text("Encrypted Provider API Key (AES-GCM KeyStore)") },
                            placeholder = { Text("Current: ${uiState.maskedTokenPreview}") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                            trailingIcon = {
                                TextButton(onClick = viewModel::clearEncryptedToken) {
                                    Text("Clear")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = timeoutStr,
                                onValueChange = { timeoutStr = it.filter(Char::isDigit) },
                                label = { Text("Timeout (s)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = maxRetriesStr,
                                onValueChange = { maxRetriesStr = it.filter(Char::isDigit) },
                                label = { Text("Max Retries") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = quotaMbStr,
                                onValueChange = { quotaMbStr = it.filter(Char::isDigit) },
                                label = { Text("Quota (MB)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Optional Google AdMob Integration", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = adMobEnabled, onCheckedChange = { adMobEnabled = it })
                        }

                        if (adMobEnabled) {
                            OutlinedTextField(
                                value = adMobBannerId,
                                onValueChange = { adMobBannerId = it },
                                label = { Text("AdMob Banner Unit ID (Policy-Approved)") },
                                placeholder = { Text("ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Maintenance Mode (Pause non-admin jobs)", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = maintenanceMode, onCheckedChange = { maintenanceMode = it })
                        }

                        uiState.healthResult?.let { health ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (health.reachable) Color(0xFF0E3A2F) else MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = "Health Check: ${health.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (health.reachable) EmeraldSuccess else MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        uiState.statusFeedback?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = CyanSpark
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.testRemoteEndpointHealth(endpointUrl) },
                                enabled = !uiState.isTestingHealth,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                if (uiState.isTestingHealth) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.HealthAndSafety, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Ping API")
                                }
                            }
                            Button(
                                onClick = {
                                    viewModel.saveProviderConfiguration(
                                        mode = selectedMode,
                                        endpointUrl = endpointUrl,
                                        newApiToken = apiTokenInput,
                                        timeoutSeconds = timeoutStr.toLongOrNull() ?: 60L,
                                        maxRetries = maxRetriesStr.toLongOrNull() ?: 3L,
                                        storageQuotaMb = quotaMbStr.toLongOrNull() ?: 4096L,
                                        adMobEnabled = adMobEnabled,
                                        adMobBannerUnitId = adMobBannerId,
                                        maintenanceMode = maintenanceMode
                                    )
                                    apiTokenInput = ""
                                },
                                enabled = !uiState.isSavingConfig,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("admin_save_config_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save Config")
                            }
                        }
                    }
                }
            }

            // Generation Job Monitoring
            item {
                Text("Generation Job Monitor (${jobs.size})", style = MaterialTheme.typography.titleLarge)
            }
            if (jobs.isEmpty()) {
                item {
                    Text(
                        "No generation jobs recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(jobs.take(15), key = { it.id }) { job ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(job.projectTitle, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${job.status} (${job.progressPercent}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (job.status) {
                                        "COMPLETED" -> EmeraldSuccess
                                        "FAILED" -> MaterialTheme.colorScheme.error
                                        else -> GoldenAmber
                                    }
                                )
                            }
                            Text(
                                "${job.durationMinutes}m • ${job.quality} • ${job.language} • Retries: ${job.retryCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            job.errorMessage?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Error Logs
            item {
                Text("Pipeline Error Logs (${logs.size})", style = MaterialTheme.typography.titleLarge)
            }
            if (logs.isEmpty()) {
                item {
                    Text(
                        "No pipeline errors logged. System healthy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldSuccess
                    )
                }
            } else {
                items(logs.take(15), key = { it.id }) { entry ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "${entry.severity} • ${entry.component}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (entry.resolved) EmeraldSuccess else MaterialTheme.colorScheme.error
                                )
                                Text(entry.message, style = MaterialTheme.typography.bodySmall)
                            }
                            if (!entry.resolved) {
                                OutlinedButton(onClick = { viewModel.resolveErrorLog(entry.id) }) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Resolve")
                                }
                            }
                        }
                    }
                }
            }

            // User Management
            item {
                Text("User Management (${users.size})", style = MaterialTheme.typography.titleLarge)
            }
            items(users.take(20), key = { it.userId }) { user ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${user.email ?: user.userId} • ${user.totalVideosCreated} videos • ${user.accountStatus}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { viewModel.toggleUserAccountStatus(user) }) {
                            Text(if (user.accountStatus == "SUSPENDED") "Activate" else "Suspend")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = CyanSpark, modifier = Modifier.size(18.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
