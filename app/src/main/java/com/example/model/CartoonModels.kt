package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class CartoonStyle(val label: String, val visualDirective: String) {
    TWO_D("2D Classic", "vibrant 2D hand-drawn cartoon animation, clean bold ink outlines, expressive cel shading"),
    THREE_D("3D Pixar", "high-end 3D animated film render, soft global illumination, tactile stylized characters"),
    FUNNY("Comedy", "slapstick expressive cartoon style, exaggerated poses, bright saturated comedy palette"),
    HORROR("Spooky", "atmospheric eerie gothic cartoon style, dramatic moonlight shadows, glowing mist, kid-safe spooky mood"),
    ADVENTURE("Adventure", "cinematic heroic animated adventure style, sweeping vistas, dynamic lighting"),
    FANTASY("Fantasy", "enchanted storybook animation style, magical glowing particles, rich emerald and violet hues"),
    EDUCATIONAL("Educational", "clear colorful friendly animated explainer style, expressive visual props, crisp readability"),
    KIDS_FRIENDLY("Kids Friendly", "soft rounded pastel storybook cartoon style, warm cheerful characters, gentle lighting");

    companion object {
        fun safeValueOf(raw: String?): CartoonStyle =
            entries.firstOrNull { it.name == raw } ?: THREE_D
    }
}

enum class CartoonLanguage(val label: String, val nativeName: String, val localeTag: String) {
    ENGLISH("English", "English", "en-US"),
    HINDI("Hindi", "हिन्दी", "hi-IN"),
    BENGALI("Bengali", "বাংলা", "bn-IN");

    companion object {
        fun safeValueOf(raw: String?): CartoonLanguage =
            entries.firstOrNull { it.name == raw } ?: ENGLISH
    }
}

enum class VideoDurationOption(val minutes: Int, val label: String) {
    MIN_1(1, "1 Min"),
    MIN_5(5, "5 Min"),
    MIN_10(10, "10 Min"),
    MIN_20(20, "20 Min"),
    MIN_30(30, "30 Min");

    companion object {
        fun fromMinutes(minutes: Int): VideoDurationOption =
            entries.firstOrNull { it.minutes == minutes } ?: MIN_1
    }
}

enum class VideoQuality(val label: String, val width: Int, val height: Int, val bitrateBps: Int) {
    HD_720P("HD 720p", 1280, 720, 2_800_000),
    FHD_1080P("Full HD 1080p", 1920, 1080, 5_000_000);

    companion object {
        fun safeValueOf(raw: String?): VideoQuality =
            entries.firstOrNull { it.name == raw } ?: HD_720P
    }
}

enum class ProjectStatus(val label: String) {
    DRAFT("Draft"),
    GENERATING_STORY("Writing Story & Characters"),
    GENERATING_ART("Generating Cartoon Artwork"),
    SYNTHESIZING_AUDIO("Creating Voiceover & Audio"),
    RENDERING_VIDEO("Rendering MP4 Video"),
    VERIFYING("Verifying MP4 Output"),
    COMPLETED("Ready"),
    FAILED("Failed");

    val isRunning: Boolean
        get() = this == GENERATING_STORY ||
            this == GENERATING_ART ||
            this == SYNTHESIZING_AUDIO ||
            this == RENDERING_VIDEO ||
            this == VERIFYING

    companion object {
        fun safeValueOf(raw: String?): ProjectStatus =
            entries.firstOrNull { it.name == raw } ?: DRAFT
    }
}

enum class SceneStatus {
    PENDING,
    ART_READY,
    AUDIO_READY,
    READY,
    FAILED;

    companion object {
        fun safeValueOf(raw: String?): SceneStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class JobStatus {
    QUEUED,
    RUNNING,
    RETRYING,
    COMPLETED,
    FAILED;

    companion object {
        fun safeValueOf(raw: String?): JobStatus =
            entries.firstOrNull { it.name == raw } ?: QUEUED
    }
}

enum class ProviderMode(val label: String) {
    FIREBASE_AI_NATIVE("Firebase AI Logic + Studio MP4 Engine"),
    CUSTOM_REST_BACKEND("External AI Video Backend API");

    companion object {
        fun safeValueOf(raw: String?): ProviderMode =
            entries.firstOrNull { it.name == raw } ?: FIREBASE_AI_NATIVE
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val preferredLanguage: String = CartoonLanguage.ENGLISH.name,
    val preferredQuality: String = VideoQuality.HD_720P.name,
    val totalVideosCreated: Long = 0L,
    val totalDownloads: Long = 0L,
    val storageUsedBytes: Long = 0L,
    val accountStatus: String = "ACTIVE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CartoonProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val storyPrompt: String = "",
    val characterPrompt: String = "",
    val characterBible: String = "",
    val style: String = CartoonStyle.THREE_D.name,
    val language: String = CartoonLanguage.ENGLISH.name,
    val durationMinutes: Long = 1L,
    val quality: String = VideoQuality.HD_720P.name,
    val includeVoiceover: Boolean = true,
    val includeBgm: Boolean = true,
    val includeSfx: Boolean = true,
    val status: String = ProjectStatus.DRAFT.name,
    val progressPercent: Long = 0L,
    val statusMessage: String = "",
    val sceneCount: Long = 0L,
    val completedScenes: Long = 0L,
    val localVideoPath: String? = null,
    val cloudVideoUrl: String? = null,
    val thumbnailPath: String? = null,
    val fileSizeBytes: Long = 0L,
    val verifiedMp4: Boolean = false,
    val retryCount: Long = 0L,
    val errorMessage: String? = null,
    val providerUsed: String = ProviderMode.FIREBASE_AI_NATIVE.name,
    val downloadCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun styleEnum(): CartoonStyle = CartoonStyle.safeValueOf(style)
    fun languageEnum(): CartoonLanguage = CartoonLanguage.safeValueOf(language)
    fun qualityEnum(): VideoQuality = VideoQuality.safeValueOf(quality)
    fun statusEnum(): ProjectStatus = ProjectStatus.safeValueOf(status)
}

@Keep
@IgnoreExtraProperties
data class CartoonScene(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val sceneIndex: Long = 0L,
    val title: String = "",
    val visualPrompt: String = "",
    val narrationText: String = "",
    val sfxCue: String = "ambient_sparkle",
    val cameraMotion: String = "ZOOM_IN",
    val durationSeconds: Long = 15L,
    val artFilePath: String? = null,
    val audioFilePath: String? = null,
    val status: String = SceneStatus.PENDING.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): SceneStatus = SceneStatus.safeValueOf(status)
}

@Keep
@IgnoreExtraProperties
data class DownloadRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val projectTitle: String = "",
    val quality: String = VideoQuality.HD_720P.name,
    val fileSizeBytes: Long = 0L,
    val mediaStoreUri: String = "",
    val verified: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GenerationJob(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val projectTitle: String = "",
    val style: String = CartoonStyle.THREE_D.name,
    val language: String = CartoonLanguage.ENGLISH.name,
    val durationMinutes: Long = 1L,
    val quality: String = VideoQuality.HD_720P.name,
    val providerName: String = ProviderMode.FIREBASE_AI_NATIVE.name,
    val status: String = JobStatus.QUEUED.name,
    val progressPercent: Long = 0L,
    val retryCount: Long = 0L,
    val errorMessage: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): JobStatus = JobStatus.safeValueOf(status)
}

@Keep
@IgnoreExtraProperties
data class ErrorLogEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val component: String = "",
    val severity: String = "ERROR",
    val message: String = "",
    val projectId: String? = null,
    val resolved: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminProviderConfig(
    @DocumentId val id: String = "",
    val activeProvider: String = ProviderMode.FIREBASE_AI_NATIVE.name,
    val providerLabel: String = "Firebase AI Logic + Studio MP4 Engine",
    val remoteEndpointUrl: String = "",
    val timeoutSeconds: Long = 60L,
    val maxRetries: Long = 3L,
    val storageQuotaMb: Long = 4096L,
    val adMobEnabled: Boolean = false,
    val adMobBannerUnitId: String = "",
    val maintenanceMode: Boolean = false,
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun providerModeEnum(): ProviderMode = ProviderMode.safeValueOf(activeProvider)
}

data class CartoonDraftRequest(
    val existingProjectId: String? = null,
    val title: String,
    val storyPrompt: String,
    val characterPrompt: String,
    val style: CartoonStyle,
    val language: CartoonLanguage,
    val durationOption: VideoDurationOption,
    val quality: VideoQuality,
    val includeVoiceover: Boolean,
    val includeBgm: Boolean,
    val includeSfx: Boolean
)
