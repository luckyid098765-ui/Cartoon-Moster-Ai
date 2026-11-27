package com.example.data

import android.content.Context
import com.example.R
import com.example.model.CartoonDraftRequest
import com.example.model.CartoonLanguage
import com.example.model.CartoonProject
import com.example.model.CartoonScene
import com.example.model.DownloadRecord
import com.example.model.GenerationJob
import com.example.model.JobStatus
import com.example.model.ProjectStatus
import com.example.model.ProviderMode
import com.example.model.SceneStatus
import com.example.model.UserProfile
import com.example.model.VideoQuality
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File

class CartoonRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google before continuing.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeProjects(userId: String): Flow<List<CartoonProject>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/projects"
        emitAll(
            db.collection("users").document(userId)
                .collection("projects")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        CartoonProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProject(userId: String, projectId: String): Flow<CartoonProject?> = flow {
        require(userId.isNotBlank() && projectId.isNotBlank()) { "IDs must not be blank" }
        val path = "users/$userId/projects/$projectId"
        emitAll(
            db.collection("users").document(userId)
                .collection("projects").document(projectId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            CartoonProject::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeScenes(userId: String, projectId: String): Flow<List<CartoonScene>> = flow {
        require(userId.isNotBlank() && projectId.isNotBlank()) { "IDs must not be blank" }
        val path = "users/$userId/projects/$projectId/scenes"
        emitAll(
            db.collection("users").document(userId)
                .collection("projects").document(projectId)
                .collection("scenes")
                .orderBy("sceneIndex", Query.Direction.ASCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        CartoonScene::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeDownloads(userId: String): Flow<List<DownloadRecord>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/downloads"
        emitAll(
            db.collection("users").document(userId)
                .collection("downloads")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        DownloadRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<UserProfile> {
        val path = "users/${user.uid}"
        return try {
            val docRef = db.collection("users").document(user.uid)
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val existing = snapshot.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(userId = user.uid, displayName = user.displayName ?: "Creator")
                return Result.success(existing)
            }
            val displayName = (user.displayName?.takeIf { it.isNotBlank() } ?: "Cartoon Creator").take(100)
            val payload = mapOf(
                "userId" to user.uid,
                "displayName" to displayName,
                "email" to user.email?.take(150),
                "photoUrl" to user.photoUrl?.toString()?.take(500),
                "preferredLanguage" to CartoonLanguage.ENGLISH.name,
                "preferredQuality" to VideoQuality.HD_720P.name,
                "totalVideosCreated" to 0L,
                "totalDownloads" to 0L,
                "storageUsedBytes" to 0L,
                "accountStatus" to "ACTIVE",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            val created = docRef.get().await().toObject(
                UserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: UserProfile(userId = user.uid, displayName = displayName)
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun createOrResetProject(
        request: CartoonDraftRequest,
        providerMode: ProviderMode
    ): Result<CartoonProject> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/projects"
        return try {
            val uid = requireUserId()
            val safeTitle = request.title.trim().ifBlank { "Untitled Cartoon" }.take(140)
            val safeStory = request.storyPrompt.trim().take(5000)
            require(safeStory.isNotBlank()) { "Please enter a story prompt." }
            val safeChars = request.characterPrompt.trim().take(2000)

            val projectsCol = db.collection("users").document(uid).collection("projects")
            val docRef = if (!request.existingProjectId.isNullOrBlank()) {
                projectsCol.document(request.existingProjectId)
            } else {
                projectsCol.document()
            }
            docPath = docRef.path

            val existingSnap = if (!request.existingProjectId.isNullOrBlank()) docRef.get().await() else null
            if (existingSnap != null && existingSnap.exists()) {
                val updates = mapOf(
                    "title" to safeTitle,
                    "storyPrompt" to safeStory,
                    "characterPrompt" to safeChars,
                    "style" to request.style.name,
                    "language" to request.language.name,
                    "durationMinutes" to request.durationOption.minutes.toLong(),
                    "quality" to request.quality.name,
                    "includeVoiceover" to request.includeVoiceover,
                    "includeBgm" to request.includeBgm,
                    "includeSfx" to request.includeSfx,
                    "status" to ProjectStatus.GENERATING_STORY.name,
                    "progressPercent" to 2L,
                    "statusMessage" to "Resuming cartoon studio pipeline...",
                    "providerUsed" to providerMode.name,
                    "retryCount" to ((existingSnap.getLong("retryCount") ?: 0L) + 1L).coerceAtMost(20L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "title" to safeTitle,
                    "storyPrompt" to safeStory,
                    "characterPrompt" to safeChars,
                    "characterBible" to "",
                    "style" to request.style.name,
                    "language" to request.language.name,
                    "durationMinutes" to request.durationOption.minutes.toLong(),
                    "quality" to request.quality.name,
                    "includeVoiceover" to request.includeVoiceover,
                    "includeBgm" to request.includeBgm,
                    "includeSfx" to request.includeSfx,
                    "status" to ProjectStatus.GENERATING_STORY.name,
                    "progressPercent" to 2L,
                    "statusMessage" to "Initializing AI story director...",
                    "sceneCount" to 0L,
                    "completedScenes" to 0L,
                    "fileSizeBytes" to 0L,
                    "verifiedMp4" to false,
                    "retryCount" to 0L,
                    "providerUsed" to providerMode.name,
                    "downloadCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }

            val saved = docRef.get().await().toObject(
                CartoonProject::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: throw IllegalStateException("Project created but could not be reloaded.")
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun getProject(projectId: String): CartoonProject? {
        return try {
            val uid = requireUserId()
            db.collection("users").document(uid)
                .collection("projects").document(projectId)
                .get().await()
                .toObject(CartoonProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/${auth.currentUser?.uid}/projects/$projectId")
            null
        }
    }

    suspend fun getScenes(projectId: String): List<CartoonScene> {
        return try {
            val uid = requireUserId()
            db.collection("users").document(uid)
                .collection("projects").document(projectId)
                .collection("scenes")
                .orderBy("sceneIndex", Query.Direction.ASCENDING)
                .get().await()
                .toObjects(CartoonScene::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/${auth.currentUser?.uid}/projects/$projectId/scenes")
            emptyList()
        }
    }

    suspend fun updateProjectProgress(
        projectId: String,
        status: ProjectStatus,
        progressPercent: Int,
        statusMessage: String,
        title: String? = null,
        characterBible: String? = null,
        sceneCount: Int? = null,
        completedScenes: Int? = null,
        thumbnailPath: String? = null
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauth"}/projects/$projectId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("projects").document(projectId)
            path = ref.path
            val updates = mutableMapOf<String, Any>(
                "status" to status.name,
                "progressPercent" to progressPercent.coerceIn(0, 100).toLong(),
                "statusMessage" to statusMessage.take(300),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            title?.takeIf { it.isNotBlank() }?.let { updates["title"] = it.take(140) }
            characterBible?.let { updates["characterBible"] = it.take(4000) }
            sceneCount?.let { updates["sceneCount"] = it.coerceIn(0, 200).toLong() }
            completedScenes?.let { updates["completedScenes"] = it.coerceIn(0, 200).toLong() }
            thumbnailPath?.takeIf { it.isNotBlank() }?.let { updates["thumbnailPath"] = it.take(500) }
            ref.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertScene(projectId: String, scene: CartoonScene): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauth"}/projects/$projectId/scenes/${scene.id}"
        return try {
            val uid = requireUserId()
            val sceneId = scene.id.ifBlank { "scene_${scene.sceneIndex}" }
            val ref = db.collection("users").document(uid)
                .collection("projects").document(projectId)
                .collection("scenes").document(sceneId)
            path = ref.path
            val existing = ref.get().await()
            if (existing.exists()) {
                val updates = mapOf(
                    "title" to scene.title.ifBlank { "Scene ${scene.sceneIndex + 1}" }.take(140),
                    "visualPrompt" to scene.visualPrompt.ifBlank { "Cartoon scene" }.take(2000),
                    "narrationText" to scene.narrationText.take(3000),
                    "sfxCue" to scene.sfxCue.take(100),
                    "cameraMotion" to scene.cameraMotion.take(60),
                    "durationSeconds" to scene.durationSeconds.coerceIn(1L, 180L),
                    "artFilePath" to scene.artFilePath?.take(500),
                    "audioFilePath" to scene.audioFilePath?.take(500),
                    "status" to scene.status,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                ref.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "projectId" to projectId,
                    "sceneIndex" to scene.sceneIndex.coerceIn(0L, 500L),
                    "title" to scene.title.ifBlank { "Scene ${scene.sceneIndex + 1}" }.take(140),
                    "visualPrompt" to scene.visualPrompt.ifBlank { "Cartoon scene" }.take(2000),
                    "narrationText" to scene.narrationText.take(3000),
                    "sfxCue" to scene.sfxCue.take(100),
                    "cameraMotion" to scene.cameraMotion.take(60),
                    "durationSeconds" to scene.durationSeconds.coerceIn(1L, 180L),
                    "artFilePath" to scene.artFilePath?.take(500),
                    "audioFilePath" to scene.audioFilePath?.take(500),
                    "status" to scene.status,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                ref.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun markProjectCompleted(
        projectId: String,
        localVideoPath: String,
        cloudVideoUrl: String?,
        thumbnailPath: String?,
        fileSizeBytes: Long,
        providerUsed: ProviderMode
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauth"}/projects/$projectId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("projects").document(projectId)
            path = ref.path
            val updates = mutableMapOf<String, Any>(
                "status" to ProjectStatus.COMPLETED.name,
                "progressPercent" to 100L,
                "statusMessage" to "Verified MP4 ready for playback & download",
                "localVideoPath" to localVideoPath.take(500),
                "fileSizeBytes" to fileSizeBytes.coerceAtLeast(0L),
                "verifiedMp4" to true,
                "providerUsed" to providerUsed.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            cloudVideoUrl?.takeIf { it.isNotBlank() }?.let { updates["cloudVideoUrl"] = it.take(1000) }
            thumbnailPath?.takeIf { it.isNotBlank() }?.let { updates["thumbnailPath"] = it.take(500) }
            ref.update(updates).await()

            db.collection("users").document(uid).update(
                mapOf(
                    "totalVideosCreated" to FieldValue.increment(1L),
                    "storageUsedBytes" to FieldValue.increment(fileSizeBytes.coerceAtLeast(0L)),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markProjectFailed(
        projectId: String,
        errorMessage: String,
        retryCount: Int
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauth"}/projects/$projectId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("projects").document(projectId)
            path = ref.path
            ref.update(
                mapOf(
                    "status" to ProjectStatus.FAILED.name,
                    "statusMessage" to "Generation paused: tap Retry to resume from checkpoint",
                    "errorMessage" to errorMessage.take(1000),
                    "retryCount" to retryCount.coerceIn(0, 20).toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markProjectPaused(
        projectId: String,
        progressPercent: Int,
        statusMessage: String
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauth"}/projects/$projectId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("projects").document(projectId)
            path = ref.path
            ref.update(
                mapOf(
                    "status" to ProjectStatus.PAUSED.name,
                    "progressPercent" to progressPercent.coerceIn(0, 99).toLong(),
                    "statusMessage" to statusMessage.take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordDownload(
        project: CartoonProject,
        mediaStoreUri: String,
        fileSizeBytes: Long
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauth"}/downloads"
        return try {
            val uid = requireUserId()
            val downloadRef = db.collection("users").document(uid).collection("downloads").document()
            path = downloadRef.path
            downloadRef.set(
                mapOf(
                    "userId" to uid,
                    "projectId" to project.id,
                    "projectTitle" to project.title.ifBlank { "Cartoon" }.take(140),
                    "quality" to project.quality,
                    "fileSizeBytes" to fileSizeBytes.coerceAtLeast(0L),
                    "mediaStoreUri" to mediaStoreUri.take(500),
                    "verified" to true,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("users").document(uid)
                .collection("projects").document(project.id)
                .update(
                    mapOf(
                        "downloadCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            db.collection("users").document(uid).update(
                mapOf(
                    "totalDownloads" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProjectAndAssets(project: CartoonProject): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauth"}/projects/${project.id}"
        return try {
            val uid = requireUserId()
            val projRef = db.collection("users").document(uid)
                .collection("projects").document(project.id)
            path = projRef.path

            val scenesSnap = projRef.collection("scenes").get().await()
            for (sceneDoc in scenesSnap.documents) {
                sceneDoc.getString("artFilePath")?.let { runCatching { File(it).delete() } }
                sceneDoc.getString("audioFilePath")?.let { runCatching { File(it).delete() } }
                sceneDoc.reference.delete().await()
            }
            project.localVideoPath?.let { runCatching { File(it).delete() } }
            project.thumbnailPath?.let { runCatching { File(it).delete() } }

            projRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertJob(
        jobId: String,
        project: CartoonProject,
        status: JobStatus,
        progressPercent: Int,
        retryCount: Int,
        providerName: String,
        errorMessage: String? = null
    ): Result<Unit> {
        val path = "generationJobs/$jobId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("generationJobs").document(jobId)
            val snap = ref.get().await()
            if (snap.exists()) {
                val updates = mapOf(
                    "projectTitle" to project.title.ifBlank { "Untitled" }.take(140),
                    "providerName" to providerName.take(100),
                    "status" to status.name,
                    "progressPercent" to progressPercent.coerceIn(0, 100).toLong(),
                    "retryCount" to retryCount.coerceIn(0, 20).toLong(),
                    "errorMessage" to errorMessage?.take(1000),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                ref.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "projectId" to project.id,
                    "projectTitle" to project.title.ifBlank { "Untitled" }.take(140),
                    "style" to project.style,
                    "language" to project.language,
                    "durationMinutes" to project.durationMinutes,
                    "quality" to project.quality,
                    "providerName" to providerName.take(100),
                    "status" to status.name,
                    "progressPercent" to progressPercent.coerceIn(0, 100).toLong(),
                    "retryCount" to retryCount.coerceIn(0, 20).toLong(),
                    "errorMessage" to errorMessage?.take(1000),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                ref.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun logPipelineError(
        component: String,
        severity: String,
        message: String,
        projectId: String? = null
    ): Result<Unit> {
        val path = "errorLogs"
        return try {
            val uid = requireUserId()
            val ref = db.collection("errorLogs").document()
            val payload = mapOf(
                "userId" to uid,
                "component" to component.ifBlank { "Pipeline" }.take(100),
                "severity" to if (severity in listOf("WARN", "ERROR", "CRITICAL")) severity else "ERROR",
                "message" to message.ifBlank { "Unknown pipeline error" }.take(1000),
                "projectId" to projectId?.take(128),
                "resolved" to false,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            ref.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
