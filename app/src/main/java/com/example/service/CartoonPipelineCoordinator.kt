package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import com.example.R
import com.example.ai.CartoonAiGenerator
import com.example.ai.RemoteVideoProviderClient
import com.example.data.AdminRepository
import com.example.data.CartoonRepository
import com.example.media.AudioSynthesizer
import com.example.media.Mp4CartoonRenderer
import com.example.model.CartoonDraftRequest
import com.example.model.CartoonProject
import com.example.model.CartoonScene
import com.example.model.JobStatus
import com.example.model.ProjectStatus
import com.example.model.ProviderMode
import com.example.model.SceneStatus
import com.example.security.SecureCredentialStore
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class ActivePipelineBanner(
    val projectId: String,
    val title: String,
    val status: ProjectStatus,
    val progressPercent: Int,
    val statusMessage: String,
    val retryAttempt: Int = 0
)

class CartoonPipelineCoordinator private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val activeJobs = mutableMapOf<String, Job>()

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))
    }
    private val cartoonRepo by lazy { CartoonRepository(db) }
    private val adminRepo by lazy { AdminRepository(db) }
    private val aiGenerator by lazy { CartoonAiGenerator() }
    private val remoteClient by lazy { RemoteVideoProviderClient() }
    private val audioSynthesizer by lazy { AudioSynthesizer(appContext) }
    private val mp4Renderer by lazy { Mp4CartoonRenderer() }
    private val credentialStore by lazy { SecureCredentialStore(appContext) }

    private val _activeBanner = MutableStateFlow<ActivePipelineBanner?>(null)
    val activeBanner: StateFlow<ActivePipelineBanner?> = _activeBanner.asStateFlow()

    suspend fun startOrResumeGeneration(
        request: CartoonDraftRequest
    ): Result<CartoonProject> {
        val currentUser = Firebase.auth.currentUser
            ?: return Result.failure(IllegalStateException("Please sign in with Google first."))

        val adminConfig = adminRepo.getProviderConfig()
        if (adminConfig.maintenanceMode && !adminRepo.checkAdminAccess()) {
            return Result.failure(
                IllegalStateException("Studio is temporarily in maintenance mode. Please try again shortly.")
            )
        }

        val providerMode = adminConfig.providerModeEnum()
        if (providerMode == ProviderMode.CUSTOM_REST_BACKEND && adminConfig.remoteEndpointUrl.isBlank()) {
            return Result.failure(
                IllegalStateException("External AI video backend URL is not configured in Admin Dashboard.")
            )
        }

        val projectResult = cartoonRepo.createOrResetProject(request, providerMode)
        val project = projectResult.getOrElse { return Result.failure(it) }

        val launched = mutex.withLock {
            if (activeJobs[project.id]?.isActive == true) {
                false
            } else {
                val job = scope.launch {
                    runPipeline(
                        uid = currentUser.uid,
                        initialProject = project,
                        request = request,
                        providerMode = providerMode,
                        remoteEndpoint = adminConfig.remoteEndpointUrl,
                        timeoutSeconds = adminConfig.timeoutSeconds,
                        maxRetries = adminConfig.maxRetries.toInt()
                    )
                }
                activeJobs[project.id] = job
                true
            }
        }

        if (!launched) {
            return Result.failure(
                IllegalStateException("This cartoon project is already generating. Please wait for it to finish.")
            )
        }

        startForegroundServiceSafe(project.title)
        return Result.success(project)
    }

    fun cancelGeneration(projectId: String) {
        scope.launch {
            mutex.withLock {
                activeJobs.remove(projectId)?.cancel()
            }
            _activeBanner.value = null
            stopForegroundServiceSafe()
        }
    }

    private suspend fun runPipeline(
        uid: String,
        initialProject: CartoonProject,
        request: CartoonDraftRequest,
        providerMode: ProviderMode,
        remoteEndpoint: String,
        timeoutSeconds: Long,
        maxRetries: Int
    ) {
        val projectId = initialProject.id
        val jobId = "job_$projectId"
        val workDir = File(appContext.filesDir, "projects/$projectId").apply { mkdirs() }
        val videosDir = File(appContext.filesDir, "videos").apply { mkdirs() }
        val finalMp4 = File(videosDir, "${projectId}.mp4")
        val thumbFile = File(videosDir, "${projectId}_thumb.jpg")
        var retryCount = initialProject.retryCount.toInt()

        try {
            updateBanner(projectId, initialProject.title, ProjectStatus.GENERATING_STORY, 5, "Starting AI cartoon director...")
            cartoonRepo.upsertJob(
                jobId = jobId,
                project = initialProject,
                status = JobStatus.RUNNING,
                progressPercent = 5,
                retryCount = retryCount,
                providerName = providerMode.name
            )

            if (providerMode == ProviderMode.CUSTOM_REST_BACKEND) {
                val token = credentialStore.getProviderToken()
                val cloudUrl = remoteClient.generateViaRemoteBackend(
                    endpointUrl = remoteEndpoint,
                    bearerToken = token,
                    draft = request,
                    outputMp4File = finalMp4,
                    timeoutSeconds = timeoutSeconds,
                    maxRetries = maxRetries
                ) { pct, msg ->
                    val status = if (pct < 90) ProjectStatus.RENDERING_VIDEO else ProjectStatus.VERIFYING
                    updateBanner(projectId, initialProject.title, status, pct, msg)
                    cartoonRepo.updateProjectProgress(projectId, status, pct, msg)
                    cartoonRepo.upsertJob(jobId, initialProject, JobStatus.RUNNING, pct, retryCount, providerMode.name)
                }

                updateBanner(projectId, initialProject.title, ProjectStatus.VERIFYING, 96, "Verifying cloud MP4...")
                if (!mp4Renderer.verifyMp4File(finalMp4)) {
                    throw IllegalStateException("Downloaded MP4 from remote provider failed integrity verification.")
                }
                cartoonRepo.markProjectCompleted(
                    projectId = projectId,
                    localVideoPath = finalMp4.absolutePath,
                    cloudVideoUrl = cloudUrl,
                    thumbnailPath = thumbFile.takeIf { it.exists() }?.absolutePath,
                    fileSizeBytes = finalMp4.length(),
                    providerUsed = providerMode
                )
                cartoonRepo.upsertJob(jobId, initialProject, JobStatus.COMPLETED, 100, retryCount, providerMode.name)
                _activeBanner.value = null
                return
            }

            // Built-in Firebase AI Logic + MediaCodec Studio Pipeline
            val timeoutMs = (timeoutSeconds * 1000L).coerceIn(45_000L, 120_000L)
            var currentTitle = initialProject.title
            var characterBible = initialProject.characterBible
            var scenes = cartoonRepo.getScenes(projectId)

            // Phase 1: Story & Character Bible (resume-aware!)
            if (scenes.isEmpty() || characterBible.isBlank()) {
                updateBanner(projectId, currentTitle, ProjectStatus.GENERATING_STORY, 10, "Writing story & character bible with Gemini...")
                val plan = aiGenerator.generateStoryPlan(
                    projectId = projectId,
                    userId = uid,
                    titleHint = request.title,
                    storyPrompt = request.storyPrompt,
                    characterPrompt = request.characterPrompt,
                    style = request.style,
                    language = request.language,
                    duration = request.durationOption,
                    maxRetries = maxRetries,
                    timeoutMs = timeoutMs,
                    onRetry = { attempt, reason ->
                        retryCount++
                        updateBanner(projectId, currentTitle, ProjectStatus.GENERATING_STORY, 10, "Retrying story ($attempt): $reason", attempt)
                        cartoonRepo.upsertJob(jobId, initialProject, JobStatus.RETRYING, 10, retryCount, providerMode.name, reason)
                    }
                )
                currentTitle = plan.title
                characterBible = plan.characterBible
                scenes = plan.scenes
                cartoonRepo.updateProjectProgress(
                    projectId = projectId,
                    status = ProjectStatus.GENERATING_STORY,
                    progressPercent = 18,
                    statusMessage = "Story ready (${scenes.size} scenes). Saving checkpoints...",
                    title = currentTitle,
                    characterBible = characterBible,
                    sceneCount = scenes.size,
                    completedScenes = 0
                )
                for (scene in scenes) {
                    cartoonRepo.upsertScene(projectId, scene)
                }
            }

            // Phase 2: Lead Character Sheet for visual consistency
            val charSheetFile = File(workDir, "character_sheet.jpg")
            var charSheetBitmap: Bitmap? = if (charSheetFile.exists() && charSheetFile.length() > 1024L) {
                BitmapFactory.decodeFile(charSheetFile.absolutePath)
            } else null

            if (charSheetBitmap == null) {
                updateBanner(projectId, currentTitle, ProjectStatus.GENERATING_ART, 22, "Designing consistent cartoon characters...")
                cartoonRepo.updateProjectProgress(
                    projectId, ProjectStatus.GENERATING_ART, 22, "Generating character reference sheet..."
                )
                charSheetBitmap = aiGenerator.generateCharacterSheet(
                    characterBible = characterBible,
                    style = request.style,
                    outputFile = charSheetFile,
                    maxRetries = maxRetries,
                    timeoutMs = timeoutMs,
                    onRetry = { attempt, reason ->
                        retryCount++
                        updateBanner(projectId, currentTitle, ProjectStatus.GENERATING_ART, 22, "Retrying character design ($attempt)...", attempt)
                        cartoonRepo.upsertJob(jobId, initialProject, JobStatus.RETRYING, 22, retryCount, providerMode.name, reason)
                    }
                )
            }

            // Phase 3: Scene Artwork & Multilingual Audio Synthesis (checkpointed per scene!)
            val updatedScenes = mutableListOf<CartoonScene>()
            val totalScenes = scenes.size.coerceAtLeast(1)
            for ((idx, scene) in scenes.withIndex()) {
                val artFile = File(workDir, "scene_${scene.sceneIndex}.jpg")
                val audioFile = File(workDir, "scene_${scene.sceneIndex}.wav")

                val artReady = artFile.exists() && artFile.length() > 1024L
                if (!artReady) {
                    val pct = 25 + ((idx.toFloat() / totalScenes) * 30f).toInt()
                    val msg = "Drawing scene ${idx + 1}/$totalScenes: ${scene.title}"
                    updateBanner(projectId, currentTitle, ProjectStatus.GENERATING_ART, pct, msg)
                    cartoonRepo.updateProjectProgress(
                        projectId, ProjectStatus.GENERATING_ART, pct, msg, completedScenes = idx
                    )
                    cartoonRepo.upsertJob(jobId, initialProject, JobStatus.RUNNING, pct, retryCount, providerMode.name)

                    val sceneBmp = aiGenerator.generateSceneArtwork(
                        scene = scene,
                        characterBible = characterBible,
                        characterRefBitmap = charSheetBitmap,
                        style = request.style,
                        outputFile = artFile,
                        maxRetries = maxRetries,
                        timeoutMs = timeoutMs,
                        onRetry = { attempt, reason ->
                            retryCount++
                            updateBanner(projectId, currentTitle, ProjectStatus.GENERATING_ART, pct, "Retrying scene ${idx + 1} ($attempt)...", attempt)
                            cartoonRepo.upsertJob(jobId, initialProject, JobStatus.RETRYING, pct, retryCount, providerMode.name, reason)
                        }
                    )
                    sceneBmp.recycle()
                }

                val audioReady = audioFile.exists() && audioFile.length() > 44L
                if (!audioReady) {
                    val pct = 55 + ((idx.toFloat() / totalScenes) * 12f).toInt()
                    val msg = "Synthesizing ${request.language.label} voice & audio (${idx + 1}/$totalScenes)..."
                    updateBanner(projectId, currentTitle, ProjectStatus.SYNTHESIZING_AUDIO, pct, msg)
                    cartoonRepo.updateProjectProgress(projectId, ProjectStatus.SYNTHESIZING_AUDIO, pct, msg)
                    audioSynthesizer.synthesizeSceneAudio(
                        scene = scene,
                        language = request.language,
                        style = request.style,
                        includeVoiceover = request.includeVoiceover,
                        includeBgm = request.includeBgm,
                        includeSfx = request.includeSfx,
                        outputWavFile = audioFile
                    )
                }

                val readyScene = scene.copy(
                    artFilePath = artFile.absolutePath,
                    audioFilePath = audioFile.absolutePath,
                    status = SceneStatus.READY.name
                )
                cartoonRepo.upsertScene(projectId, readyScene)
                updatedScenes.add(readyScene)
            }
            charSheetBitmap?.recycle()

            // Phase 4: Hardware-Accelerated MP4 Rendering & Verification
            updateBanner(projectId, currentTitle, ProjectStatus.RENDERING_VIDEO, 70, "Encoding ${request.quality.label} MP4...")
            cartoonRepo.updateProjectProgress(
                projectId,
                ProjectStatus.RENDERING_VIDEO,
                70,
                "Rendering ${request.quality.label} MP4 video...",
                completedScenes = updatedScenes.size
            )
            val renderResult = mp4Renderer.renderCartoonMp4(
                scenes = updatedScenes,
                style = request.style,
                quality = request.quality,
                outputMp4File = finalMp4,
                thumbnailFile = thumbFile
            ) { pct, msg ->
                val stage = if (pct >= 95) ProjectStatus.VERIFYING else ProjectStatus.RENDERING_VIDEO
                updateBanner(projectId, currentTitle, stage, pct, msg)
                cartoonRepo.updateProjectProgress(projectId, stage, pct, msg)
                cartoonRepo.upsertJob(jobId, initialProject, JobStatus.RUNNING, pct, retryCount, providerMode.name)
            }

            // Cleanup bulky temporary WAV files safely after MP4 verification
            cleanupTemporaryAudioFiles(workDir)

            // Phase 5: Mark Verified & Completed in Firestore
            cartoonRepo.markProjectCompleted(
                projectId = projectId,
                localVideoPath = renderResult.mp4File.absolutePath,
                cloudVideoUrl = null,
                thumbnailPath = renderResult.thumbnailFile.takeIf { it.exists() }?.absolutePath,
                fileSizeBytes = renderResult.fileSizeBytes,
                providerUsed = providerMode
            )
            cartoonRepo.upsertJob(jobId, initialProject, JobStatus.COMPLETED, 100, retryCount, providerMode.name)
            _activeBanner.value = null
        } catch (e: CancellationException) {
            Log.w(TAG, "Pipeline cancelled for $projectId")
            cartoonRepo.markProjectFailed(projectId, "Generation cancelled by user. Tap Retry to resume.", retryCount)
            cartoonRepo.upsertJob(jobId, initialProject, JobStatus.FAILED, 0, retryCount, providerMode.name, "Cancelled")
            _activeBanner.value = null
            throw e
        } catch (e: Exception) {
            val friendlyMsg = e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Unexpected error during cartoon video generation."
            Log.e(TAG, "Pipeline failed for $projectId", e)
            cartoonRepo.markProjectFailed(projectId, friendlyMsg, retryCount)
            cartoonRepo.upsertJob(jobId, initialProject, JobStatus.FAILED, 0, retryCount, providerMode.name, friendlyMsg)
            cartoonRepo.logPipelineError("CartoonPipeline", "ERROR", friendlyMsg, projectId)
            _activeBanner.value = null
        } finally {
            mutex.withLock {
                activeJobs.remove(projectId)
                if (activeJobs.isEmpty()) {
                    stopForegroundServiceSafe()
                }
            }
        }
    }

    private fun updateBanner(
        projectId: String,
        title: String,
        status: ProjectStatus,
        progress: Int,
        message: String,
        retryAttempt: Int = 0
    ) {
        _activeBanner.value = ActivePipelineBanner(
            projectId = projectId,
            title = title,
            status = status,
            progressPercent = progress,
            statusMessage = message,
            retryAttempt = retryAttempt
        )
        updateServiceNotification(title, progress, message)
    }

    private fun cleanupTemporaryAudioFiles(workDir: File) {
        runCatching {
            workDir.listFiles()?.forEach { file ->
                if (file.extension.equals("wav", ignoreCase = true) || file.name == "character_sheet.jpg") {
                    file.delete()
                }
            }
        }
    }

    fun cleanupAllTempCache(): Long {
        var reclaimed = 0L
        val projectsRoot = File(appContext.filesDir, "projects")
        projectsRoot.walkBottomUp().forEach { f ->
            if (f.isFile && (f.extension == "wav" || f.name == "character_sheet.jpg")) {
                reclaimed += f.length()
                f.delete()
            }
        }
        return reclaimed
    }

    fun calculateLocalStorageBytes(): Long {
        var total = 0L
        File(appContext.filesDir, "videos").walkTopDown().forEach { if (it.isFile) total += it.length() }
        File(appContext.filesDir, "projects").walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }

    private fun startForegroundServiceSafe(title: String) {
        runCatching {
            val intent = Intent(appContext, VideoGenerationService::class.java).apply {
                action = VideoGenerationService.ACTION_START
                putExtra(VideoGenerationService.EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }.onFailure { Log.w(TAG, "Could not start foreground service: ${it.message}") }
    }

    private fun updateServiceNotification(title: String, progress: Int, message: String) {
        runCatching {
            val intent = Intent(appContext, VideoGenerationService::class.java).apply {
                action = VideoGenerationService.ACTION_PROGRESS
                putExtra(VideoGenerationService.EXTRA_TITLE, title)
                putExtra(VideoGenerationService.EXTRA_PROGRESS, progress)
                putExtra(VideoGenerationService.EXTRA_MESSAGE, message)
            }
            appContext.startService(intent)
        }
    }

    private fun stopForegroundServiceSafe() {
        runCatching {
            appContext.stopService(Intent(appContext, VideoGenerationService::class.java))
        }
    }

    companion object {
        private const val TAG = "PipelineCoordinator"

        @Volatile
        private var instance: CartoonPipelineCoordinator? = null

        fun getInstance(context: Context): CartoonPipelineCoordinator {
            return instance ?: synchronized(this) {
                instance ?: CartoonPipelineCoordinator(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
