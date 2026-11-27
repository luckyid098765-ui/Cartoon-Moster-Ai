package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.AdminRepository
import com.example.data.CartoonRepository
import com.example.data.PreferencesRepository
import com.example.media.DownloadProgress
import com.example.media.VideoDownloader
import com.example.model.AdminProviderConfig
import com.example.model.CartoonDraftRequest
import com.example.model.CartoonLanguage
import com.example.model.CartoonProject
import com.example.model.CartoonScene
import com.example.model.CartoonStyle
import com.example.model.DownloadRecord
import com.example.model.UserProfile
import com.example.model.VideoDurationOption
import com.example.model.VideoQuality
import com.example.service.ActivePipelineBanner
import com.example.service.CartoonPipelineCoordinator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Success(val projects: List<CartoonProject>) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

data class StudioFormState(
    val title: String = "",
    val storyPrompt: String = "",
    val characterPrompt: String = "",
    val selectedStyle: CartoonStyle = CartoonStyle.THREE_D,
    val selectedLanguage: CartoonLanguage = CartoonLanguage.ENGLISH,
    val selectedDuration: VideoDurationOption = VideoDurationOption.MIN_1,
    val selectedQuality: VideoQuality = VideoQuality.HD_720P,
    val includeVoiceover: Boolean = true,
    val includeBgm: Boolean = true,
    val includeSfx: Boolean = true,
    val isSubmitting: Boolean = false,
    val feedbackError: String? = null,
    val feedbackSuccess: String? = null
)

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Exporting(val projectId: String, val percent: Int) : ExportUiState
    data class Done(val projectId: String, val message: String) : ExportUiState
    data class Failed(val projectId: String, val message: String) : ExportUiState
}

class StudioViewModel(
    private val currentUserId: String,
    private val cartoonRepo: CartoonRepository,
    private val adminRepo: AdminRepository,
    private val prefsRepo: PreferencesRepository,
    private val coordinator: CartoonPipelineCoordinator,
    private val downloader: VideoDownloader
) : ViewModel() {

    private val _formState = MutableStateFlow(StudioFormState())
    val formState: StateFlow<StudioFormState> = _formState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    private val _isAdmin = MutableStateFlow(adminRepo.isCurrentUserAdmin())
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    val activeBanner: StateFlow<ActivePipelineBanner?> = coordinator.activeBanner

    val userProfile: StateFlow<UserProfile?> = cartoonRepo.observeUserProfile(currentUserId)
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            Log.w(TAG, "Profile stream error", fsError ?: error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val projectsState: StateFlow<LibraryUiState> = cartoonRepo.observeProjects(currentUserId)
        .map<List<CartoonProject>, LibraryUiState> { LibraryUiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Projects stream error", root)
            emit(LibraryUiState.Error(root.message ?: "Failed to load projects."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), LibraryUiState.Loading)

    val downloads: StateFlow<List<DownloadRecord>> = cartoonRepo.observeDownloads(currentUserId)
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminConfig: StateFlow<AdminProviderConfig> = adminRepo.observeProviderConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AdminProviderConfig())

    init {
        viewModelScope.launch {
            Firebase.auth.currentUser?.let { user ->
                cartoonRepo.ensureUserProfile(user)
            }
            _isAdmin.value = adminRepo.checkAdminAccess()
        }
        viewModelScope.launch {
            prefsRepo.preferencesFlow.collect { prefs ->
                _formState.value = _formState.value.copy(
                    selectedStyle = prefs.defaultStyle,
                    selectedLanguage = prefs.defaultLanguage,
                    selectedQuality = prefs.defaultQuality
                )
            }
        }
    }

    fun observeScenesForProject(projectId: String): StateFlow<List<CartoonScene>> =
        cartoonRepo.observeScenes(currentUserId, projectId)
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun updateTitle(value: String) {
        _formState.value = _formState.value.copy(title = value.take(140), feedbackError = null)
    }

    fun updateStoryPrompt(value: String) {
        _formState.value = _formState.value.copy(storyPrompt = value.take(5000), feedbackError = null)
    }

    fun updateCharacterPrompt(value: String) {
        _formState.value = _formState.value.copy(characterPrompt = value.take(2000), feedbackError = null)
    }

    fun selectStyle(style: CartoonStyle) {
        _formState.value = _formState.value.copy(selectedStyle = style)
        persistDefaults()
    }

    fun selectLanguage(language: CartoonLanguage) {
        _formState.value = _formState.value.copy(selectedLanguage = language)
        persistDefaults()
    }

    fun selectDuration(duration: VideoDurationOption) {
        _formState.value = _formState.value.copy(selectedDuration = duration)
    }

    fun selectQuality(quality: VideoQuality) {
        _formState.value = _formState.value.copy(selectedQuality = quality)
        persistDefaults()
    }

    fun toggleVoiceover(enabled: Boolean) {
        _formState.value = _formState.value.copy(includeVoiceover = enabled)
    }

    fun toggleBgm(enabled: Boolean) {
        _formState.value = _formState.value.copy(includeBgm = enabled)
    }

    fun toggleSfx(enabled: Boolean) {
        _formState.value = _formState.value.copy(includeSfx = enabled)
    }

    fun clearFeedback() {
        _formState.value = _formState.value.copy(feedbackError = null, feedbackSuccess = null)
    }

    fun resetExportState() {
        _exportState.value = ExportUiState.Idle
    }

    private fun persistDefaults() {
        val state = _formState.value
        viewModelScope.launch {
            prefsRepo.saveDefaults(state.selectedStyle, state.selectedLanguage, state.selectedQuality)
        }
    }

    fun generateCartoonVideo(onProjectStarted: (String) -> Unit = {}) {
        val s = _formState.value
        if (s.storyPrompt.isBlank()) {
            _formState.value = s.copy(feedbackError = "Please enter your cartoon story before generating.")
            return
        }
        if (s.isSubmitting) return

        viewModelScope.launch {
            _formState.value = _formState.value.copy(
                isSubmitting = true,
                feedbackError = null,
                feedbackSuccess = null
            )
            val request = CartoonDraftRequest(
                title = s.title.trim().ifBlank { s.storyPrompt.trim().take(36) },
                storyPrompt = s.storyPrompt,
                characterPrompt = s.characterPrompt,
                style = s.selectedStyle,
                language = s.selectedLanguage,
                durationOption = s.selectedDuration,
                quality = s.selectedQuality,
                includeVoiceover = s.includeVoiceover,
                includeBgm = s.includeBgm,
                includeSfx = s.includeSfx
            )
            val res = coordinator.startOrResumeGeneration(request)
            res.onSuccess { project ->
                _formState.value = _formState.value.copy(
                    isSubmitting = false,
                    feedbackSuccess = "Cartoon job started: ${project.title}"
                )
                onProjectStarted(project.id)
            }.onFailure { err ->
                _formState.value = _formState.value.copy(
                    isSubmitting = false,
                    feedbackError = err.localizedMessage ?: "Failed to start cartoon generation."
                )
            }
        }
    }

    fun retryProject(project: CartoonProject) {
        viewModelScope.launch {
            val request = CartoonDraftRequest(
                existingProjectId = project.id,
                title = project.title,
                storyPrompt = project.storyPrompt,
                characterPrompt = project.characterPrompt,
                style = project.styleEnum(),
                language = project.languageEnum(),
                durationOption = VideoDurationOption.fromMinutes(project.durationMinutes.toInt()),
                quality = project.qualityEnum(),
                includeVoiceover = project.includeVoiceover,
                includeBgm = project.includeBgm,
                includeSfx = project.includeSfx
            )
            val res = coordinator.startOrResumeGeneration(request)
            res.onFailure { err ->
                _formState.value = _formState.value.copy(
                    feedbackError = err.localizedMessage ?: "Could not retry project."
                )
            }
        }
    }

    fun cancelGeneration(projectId: String) {
        coordinator.cancelGeneration(projectId)
    }

    fun downloadProjectMp4(project: CartoonProject) {
        val localPath = project.localVideoPath
        if (!project.verifiedMp4 || localPath.isNullOrBlank()) {
            _exportState.value = ExportUiState.Failed(
                project.id,
                "MP4 file is not verified yet. Please wait for generation to complete."
            )
            return
        }
        val sourceFile = File(localPath)
        viewModelScope.launch {
            downloader.exportMp4ToMovies(
                sourceMp4 = sourceFile,
                projectTitle = project.title,
                qualityLabel = project.qualityEnum().label
            ).collect { progress ->
                when (progress) {
                    is DownloadProgress.Running -> {
                        _exportState.value = ExportUiState.Exporting(project.id, progress.percent)
                    }
                    is DownloadProgress.Completed -> {
                        cartoonRepo.recordDownload(project, progress.uriString, progress.fileSizeBytes)
                        _exportState.value = ExportUiState.Done(
                            project.id,
                            "Saved verified MP4 to Movies/CartoonMasterAI"
                        )
                    }
                    is DownloadProgress.Failed -> {
                        _exportState.value = ExportUiState.Failed(project.id, progress.message)
                    }
                }
            }
        }
    }

    fun deleteProject(project: CartoonProject) {
        viewModelScope.launch {
            coordinator.cancelGeneration(project.id)
            cartoonRepo.deleteProjectAndAssets(project)
        }
    }

    companion object {
        private const val TAG = "StudioViewModel"

        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                StudioViewModel(
                    currentUserId = currentUserId,
                    cartoonRepo = CartoonRepository(db),
                    adminRepo = AdminRepository(db),
                    prefsRepo = PreferencesRepository(app),
                    coordinator = CartoonPipelineCoordinator.getInstance(app),
                    downloader = VideoDownloader(app)
                )
            }
        }
    }
}
