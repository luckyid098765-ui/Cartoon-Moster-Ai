package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.ai.ProviderHealthResult
import com.example.ai.RemoteVideoProviderClient
import com.example.data.AdminRepository
import com.example.model.AdminProviderConfig
import com.example.model.ErrorLogEntry
import com.example.model.GenerationJob
import com.example.model.ProviderMode
import com.example.model.UserProfile
import com.example.security.SecureCredentialStore
import com.example.service.CartoonPipelineCoordinator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AdminDashboardUiState(
    val localStorageBytes: Long = 0L,
    val maskedTokenPreview: String = "Not set",
    val healthResult: ProviderHealthResult? = null,
    val isTestingHealth: Boolean = false,
    val isSavingConfig: Boolean = false,
    val statusFeedback: String? = null
)

class AdminViewModel(
    private val adminRepo: AdminRepository,
    private val credentialStore: SecureCredentialStore,
    private val coordinator: CartoonPipelineCoordinator,
    private val remoteClient: RemoteVideoProviderClient = RemoteVideoProviderClient()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AdminDashboardUiState(
            localStorageBytes = coordinator.calculateLocalStorageBytes(),
            maskedTokenPreview = credentialStore.maskedTokenPreview()
        )
    )
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    val providerConfig: StateFlow<AdminProviderConfig> = adminRepo.observeProviderConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AdminProviderConfig())

    val allJobs: StateFlow<List<GenerationJob>> = adminRepo.observeAllJobsForAdmin()
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val errorLogs: StateFlow<List<ErrorLogEntry>> = adminRepo.observeAllErrorLogsForAdmin()
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val users: StateFlow<List<UserProfile>> = adminRepo.observeAllUsersForAdmin()
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun refreshStorageMetrics() {
        _uiState.value = _uiState.value.copy(
            localStorageBytes = coordinator.calculateLocalStorageBytes(),
            maskedTokenPreview = credentialStore.maskedTokenPreview()
        )
    }

    fun cleanTempCache() {
        viewModelScope.launch {
            val freed = coordinator.cleanupAllTempCache()
            val kb = freed / 1024L
            _uiState.value = _uiState.value.copy(
                localStorageBytes = coordinator.calculateLocalStorageBytes(),
                statusFeedback = "Cleaned ${kb} KB of temporary working files."
            )
        }
    }

    fun saveProviderConfiguration(
        mode: ProviderMode,
        endpointUrl: String,
        newApiToken: String,
        timeoutSeconds: Long,
        maxRetries: Long,
        storageQuotaMb: Long,
        adMobEnabled: Boolean,
        adMobBannerUnitId: String,
        maintenanceMode: Boolean
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingConfig = true, statusFeedback = null)
            if (newApiToken.isNotBlank()) {
                credentialStore.saveProviderToken(newApiToken)
            }
            val updated = AdminProviderConfig(
                activeProvider = mode.name,
                providerLabel = mode.label,
                remoteEndpointUrl = endpointUrl.trim(),
                timeoutSeconds = timeoutSeconds.coerceIn(10L, 600L),
                maxRetries = maxRetries.coerceIn(0L, 10L),
                storageQuotaMb = storageQuotaMb.coerceIn(100L, 1_000_000L),
                adMobEnabled = adMobEnabled,
                adMobBannerUnitId = adMobBannerUnitId.trim(),
                maintenanceMode = maintenanceMode
            )
            val res = adminRepo.saveProviderConfig(updated)
            _uiState.value = _uiState.value.copy(
                isSavingConfig = false,
                maskedTokenPreview = credentialStore.maskedTokenPreview(),
                statusFeedback = res.fold(
                    onSuccess = { "Provider & system configuration saved securely." },
                    onFailure = { "Save failed: ${it.localizedMessage}" }
                )
            )
        }
    }

    fun clearEncryptedToken() {
        credentialStore.clearProviderToken()
        _uiState.value = _uiState.value.copy(
            maskedTokenPreview = credentialStore.maskedTokenPreview(),
            statusFeedback = "Encrypted provider API token cleared from KeyStore."
        )
    }

    fun testRemoteEndpointHealth(endpointUrl: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingHealth = true, healthResult = null)
            val token = credentialStore.getProviderToken()
            val result = remoteClient.checkHealth(endpointUrl, token)
            _uiState.value = _uiState.value.copy(
                isTestingHealth = false,
                healthResult = result
            )
        }
    }

    fun resolveErrorLog(logId: String) {
        viewModelScope.launch {
            adminRepo.resolveErrorLog(logId)
        }
    }

    fun toggleUserAccountStatus(user: UserProfile) {
        val nextStatus = if (user.accountStatus == "SUSPENDED") "ACTIVE" else "SUSPENDED"
        viewModelScope.launch {
            adminRepo.setUserAccountStatus(user.userId, nextStatus)
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(statusFeedback = null)
    }

    companion object {
        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                AdminViewModel(
                    adminRepo = AdminRepository(db),
                    credentialStore = SecureCredentialStore(app),
                    coordinator = CartoonPipelineCoordinator.getInstance(app)
                )
            }
        }
    }
}
