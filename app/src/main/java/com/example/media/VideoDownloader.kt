package com.example.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

sealed interface DownloadProgress {
    data class Running(val percent: Int) : DownloadProgress
    data class Completed(val uriString: String, val fileSizeBytes: Long) : DownloadProgress
    data class Failed(val message: String) : DownloadProgress
}

class VideoDownloader(private val context: Context) {

    fun exportMp4ToMovies(
        sourceMp4: File,
        projectTitle: String,
        qualityLabel: String
    ): Flow<DownloadProgress> = flow {
        if (!sourceMp4.exists() || sourceMp4.length() < 4096L) {
            emit(DownloadProgress.Failed("Source MP4 file is missing or incomplete. Please regenerate."))
            return@flow
        }

        val renderer = Mp4CartoonRenderer()
        if (!renderer.verifyMp4File(sourceMp4)) {
            emit(DownloadProgress.Failed("MP4 integrity check failed before download."))
            return@flow
        }

        val safeTitle = projectTitle.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").trim()
            .ifBlank { "CartoonMaster" }
            .replace(" ", "_")
            .take(40)
        val fileName = "${safeTitle}_${qualityLabel.replace(" ", "_")}_${System.currentTimeMillis()}.mp4"
        val totalBytes = sourceMp4.length()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/CartoonMasterAI"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Could not create MediaStore entry in Movies/CartoonMasterAI.")

                var copied = 0L
                val buf = ByteArray(64 * 1024)
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceMp4).use { input ->
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            copied += read
                            val pct = ((copied.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(0, 99)
                            emit(DownloadProgress.Running(pct))
                        }
                        out.flush()
                    }
                } ?: throw IllegalStateException("Could not open MediaStore output stream.")

                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                // Verify exported file size via ContentResolver
                val verifiedSize = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: copied
                if (verifiedSize < totalBytes) {
                    throw IllegalStateException("Downloaded MP4 verification failed (size mismatch).")
                }
                emit(DownloadProgress.Completed(uri.toString(), verifiedSize))
            } else {
                val moviesDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                    "CartoonMasterAI"
                ).apply { mkdirs() }
                val destFile = File(moviesDir, fileName)
                var copied = 0L
                val buf = ByteArray(64 * 1024)
                FileOutputStream(destFile).use { out ->
                    FileInputStream(sourceMp4).use { input ->
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            copied += read
                            val pct = ((copied.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(0, 99)
                            emit(DownloadProgress.Running(pct))
                        }
                        out.flush()
                    }
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
                if (destFile.length() != totalBytes) {
                    throw IllegalStateException("Exported MP4 size verification failed.")
                }
                emit(DownloadProgress.Completed(destFile.absolutePath, destFile.length()))
            }
        } catch (e: Exception) {
            emit(DownloadProgress.Failed(e.localizedMessage ?: "Failed to save MP4 to device Movies folder."))
        }
    }.flowOn(Dispatchers.IO)
}
