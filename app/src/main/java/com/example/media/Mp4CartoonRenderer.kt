package com.example.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.view.Surface
import com.example.model.CartoonScene
import com.example.model.CartoonStyle
import com.example.model.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class RenderedMp4Result(
    val mp4File: File,
    val thumbnailFile: File,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val verified: Boolean
)

private data class QueuedSample(
    val info: MediaCodec.BufferInfo,
    val data: ByteArray
)

class Mp4CartoonRenderer {

    suspend fun renderCartoonMp4(
        scenes: List<CartoonScene>,
        style: CartoonStyle,
        quality: VideoQuality,
        outputMp4File: File,
        thumbnailFile: File,
        onProgress: suspend (percent: Int, status: String) -> Unit
    ): RenderedMp4Result = withContext(Dispatchers.Default) {
        require(scenes.isNotEmpty()) { "No scenes available to render." }
        outputMp4File.parentFile?.mkdirs()
        val tempMp4File = File(outputMp4File.parentFile, "${outputMp4File.nameWithoutExtension}.tmp.mp4")
        if (tempMp4File.exists()) tempMp4File.delete()
        if (outputMp4File.exists()) outputMp4File.delete()

        val width = quality.width
        val height = quality.height
        val totalDurationSec = scenes.sumOf { it.durationSeconds.coerceIn(5L, 180L) }
        val fps = when {
            totalDurationSec <= 120L -> 15
            totalDurationSec <= 360L -> 12
            totalDurationSec <= 700L -> 10
            totalDurationSec <= 1300L -> 8
            else -> 6
        }

        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, quality.bitrateBps)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }

        val audioSampleRate = AudioSynthesizer.SAMPLE_RATE
        val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, audioSampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var muxer: MediaMuxer? = null
        var eglHelper: EglSurfaceRenderer? = null
        val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val frameCanvas = Canvas(frameBitmap)

        try {
            videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = videoEncoder.createInputSurface()
            eglHelper = EglSurfaceRenderer(inputSurface, width, height)
            videoEncoder.start()

            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.start()

            muxer = MediaMuxer(tempMp4File.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioBufferInfo = MediaCodec.BufferInfo()
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var muxerStarted = false
            val pendingVideoSamples = ArrayDeque<QueuedSample>()
            val pendingAudioSamples = ArrayDeque<QueuedSample>()

            fun flushPendingSamples() {
                if (!muxerStarted) return
                while (pendingVideoSamples.isNotEmpty()) {
                    val sample = pendingVideoSamples.removeFirst()
                    muxer.writeSampleData(videoTrackIndex, ByteBuffer.wrap(sample.data), sample.info)
                }
                while (pendingAudioSamples.isNotEmpty()) {
                    val sample = pendingAudioSamples.removeFirst()
                    muxer.writeSampleData(audioTrackIndex, ByteBuffer.wrap(sample.data), sample.info)
                }
            }

            fun tryStartMuxer() {
                if (!muxerStarted && videoTrackIndex >= 0 && audioTrackIndex >= 0) {
                    muxer.start()
                    muxerStarted = true
                    flushPendingSamples()
                }
            }

            fun copyBufferInfo(src: MediaCodec.BufferInfo, size: Int): MediaCodec.BufferInfo {
                return MediaCodec.BufferInfo().apply {
                    set(0, size, src.presentationTimeUs, src.flags)
                }
            }

            fun drainVideoEncoder(endOfStream: Boolean) {
                if (endOfStream) videoEncoder.signalEndOfInputStream()
                while (true) {
                    val outIdx = videoEncoder.dequeueOutputBuffer(videoBufferInfo, if (endOfStream) 10_000L else 0L)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) break
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (videoTrackIndex < 0) {
                                videoTrackIndex = muxer.addTrack(videoEncoder.outputFormat)
                                tryStartMuxer()
                            }
                        }
                        outIdx >= 0 -> {
                            val encoded = videoEncoder.getOutputBuffer(outIdx)
                            if (encoded != null && videoBufferInfo.size > 0 &&
                                (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            ) {
                                encoded.position(videoBufferInfo.offset)
                                encoded.limit(videoBufferInfo.offset + videoBufferInfo.size)
                                if (muxerStarted) {
                                    flushPendingSamples()
                                    muxer.writeSampleData(videoTrackIndex, encoded, videoBufferInfo)
                                } else {
                                    val bytes = ByteArray(videoBufferInfo.size)
                                    encoded.get(bytes)
                                    pendingVideoSamples.addLast(
                                        QueuedSample(copyBufferInfo(videoBufferInfo, bytes.size), bytes)
                                    )
                                }
                            }
                            videoEncoder.releaseOutputBuffer(outIdx, false)
                            if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                        }
                    }
                }
            }

            fun drainAudioEncoder(endOfStream: Boolean) {
                while (true) {
                    val outIdx = audioEncoder.dequeueOutputBuffer(audioBufferInfo, if (endOfStream) 10_000L else 0L)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) break
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (audioTrackIndex < 0) {
                                audioTrackIndex = muxer.addTrack(audioEncoder.outputFormat)
                                tryStartMuxer()
                            }
                        }
                        outIdx >= 0 -> {
                            val encoded = audioEncoder.getOutputBuffer(outIdx)
                            if (encoded != null && audioBufferInfo.size > 0 &&
                                (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            ) {
                                encoded.position(audioBufferInfo.offset)
                                encoded.limit(audioBufferInfo.offset + audioBufferInfo.size)
                                if (muxerStarted) {
                                    flushPendingSamples()
                                    muxer.writeSampleData(audioTrackIndex, encoded, audioBufferInfo)
                                } else {
                                    val bytes = ByteArray(audioBufferInfo.size)
                                    encoded.get(bytes)
                                    pendingAudioSamples.addLast(
                                        QueuedSample(copyBufferInfo(audioBufferInfo, bytes.size), bytes)
                                    )
                                }
                            }
                            audioEncoder.releaseOutputBuffer(outIdx, false)
                            if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                        }
                    }
                }
            }

            val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = (height * 0.033f)
                setShadowLayer(6f, 0f, 2f, Color.BLACK)
            }
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(170, 14, 13, 29)
            }
            val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

            val bytesPerSecond = audioSampleRate * 2L
            val chunkSamples = 2048
            val pcmBuffer = ByteArray(chunkSamples * 2)
            var audioPtsUs = 0L
            var globalFrame = 0L
            val totalFrames = scenes.sumOf { it.durationSeconds.coerceIn(5L, 180L) * fps }
            var lastReportedPct = 69

            onProgress(70, "Initializing ${quality.label} MP4 encoder & synchronized audio...")

            for ((idx, scene) in scenes.withIndex()) {
                coroutineContext.ensureActive()
                val sceneDurSec = scene.durationSeconds.coerceIn(5L, 180L)
                val sceneFrames = (sceneDurSec * fps).toInt()
                val targetAudioBytes = (sceneDurSec * bytesPerSecond).toInt()
                var sceneAudioBytesWritten = 0

                val artBitmap = scene.artFilePath?.let { path ->
                    runCatching { decodeScaledBitmap(File(path), width, height) }.getOrNull()
                }
                val wavFile = scene.audioFilePath?.let { File(it) }
                val wavStream: BufferedInputStream? = if (wavFile != null && wavFile.exists() && wavFile.length() > 44) {
                    BufferedInputStream(FileInputStream(wavFile)).also { it.skip(44) }
                } else null

                try {
                    for (f in 0 until sceneFrames) {
                        coroutineContext.ensureActive()
                        val t = f.toFloat() / sceneFrames.toFloat().coerceAtLeast(1f)
                        drawAnimatedSceneFrame(
                            canvas = frameCanvas,
                            width = width,
                            height = height,
                            artBitmap = artBitmap,
                            scene = scene,
                            style = style,
                            progress = t,
                            bitmapPaint = bitmapPaint,
                            barPaint = barPaint,
                            subtitlePaint = subtitlePaint,
                            particlePaint = particlePaint
                        )

                        val videoPtsUs = globalFrame * 1_000_000L / fps
                        eglHelper.renderBitmap(frameBitmap, videoPtsUs * 1000L)
                        drainVideoEncoder(false)
                        globalFrame++

                        val targetPtsUsForFrame = globalFrame * 1_000_000L / fps
                        while (sceneAudioBytesWritten < targetAudioBytes && audioPtsUs < targetPtsUsForFrame) {
                            val toRead = minOf(pcmBuffer.size, targetAudioBytes - sceneAudioBytesWritten)
                            var bytesRead = 0
                            if (wavStream != null) {
                                bytesRead = wavStream.read(pcmBuffer, 0, toRead)
                                if (bytesRead < 0) bytesRead = 0
                            }
                            if (bytesRead < toRead) {
                                pcmBuffer.fill(0, bytesRead, toRead)
                            }
                            if (feedAudioChunk(audioEncoder, pcmBuffer, toRead, audioPtsUs, 0)) {
                                audioPtsUs += (toRead.toLong() / 2L) * 1_000_000L / audioSampleRate
                                sceneAudioBytesWritten += toRead
                            }
                            drainAudioEncoder(false)
                        }

                        val pct = 70 + ((globalFrame.toFloat() / totalFrames.coerceAtLeast(1L)) * 24f).toInt()
                        val clampedPct = pct.coerceIn(70, 94)
                        if (clampedPct > lastReportedPct) {
                            lastReportedPct = clampedPct
                            onProgress(
                                clampedPct,
                                "Rendering MP4 scene ${idx + 1}/${scenes.size} (${quality.label})..."
                            )
                        }
                    }

                    // Flush any trailing audio samples for this scene
                    while (sceneAudioBytesWritten < targetAudioBytes) {
                        coroutineContext.ensureActive()
                        val toRead = minOf(pcmBuffer.size, targetAudioBytes - sceneAudioBytesWritten)
                        var bytesRead = 0
                        if (wavStream != null) {
                            bytesRead = wavStream.read(pcmBuffer, 0, toRead)
                            if (bytesRead < 0) bytesRead = 0
                        }
                        if (bytesRead < toRead) {
                            pcmBuffer.fill(0, bytesRead, toRead)
                        }
                        if (feedAudioChunk(audioEncoder, pcmBuffer, toRead, audioPtsUs, 0)) {
                            audioPtsUs += (toRead.toLong() / 2L) * 1_000_000L / audioSampleRate
                            sceneAudioBytesWritten += toRead
                        }
                        drainAudioEncoder(false)
                    }
                } finally {
                    runCatching { wavStream?.close() }
                    artBitmap?.recycle()
                }
            }

            // Signal EOS on both encoders so both formats and all remaining buffers drain cleanly
            val eosInIdx = audioEncoder.dequeueInputBuffer(10_000L)
            if (eosInIdx >= 0) {
                audioEncoder.queueInputBuffer(eosInIdx, 0, 0, audioPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drainVideoEncoder(true)
            drainAudioEncoder(true)
            if (muxerStarted) {
                flushPendingSamples()
                muxer.stop()
            }

            onProgress(96, "Verifying MP4 integrity & extracting thumbnail...")
            val verified = verifyMp4File(tempMp4File)
            if (!verified) {
                throw IllegalStateException("MP4 verification failed: generated output file is corrupted or incomplete.")
            }

            if (!tempMp4File.renameTo(outputMp4File)) {
                tempMp4File.copyTo(outputMp4File, overwrite = true)
                tempMp4File.delete()
            }

            val durationMs = extractThumbnailAndDuration(outputMp4File, thumbnailFile, scenes.firstOrNull()?.artFilePath)
            RenderedMp4Result(
                mp4File = outputMp4File,
                thumbnailFile = thumbnailFile,
                fileSizeBytes = outputMp4File.length(),
                durationMs = durationMs,
                verified = true
            )
        } catch (e: Exception) {
            runCatching { tempMp4File.delete() }
            runCatching { outputMp4File.delete() }
            throw e
        } finally {
            frameBitmap.recycle()
            runCatching { eglHelper?.release() }
            runCatching { videoEncoder.stop() }
            runCatching { videoEncoder.release() }
            runCatching { audioEncoder.stop() }
            runCatching { audioEncoder.release() }
            runCatching { muxer?.release() }
        }
    }

    private fun feedAudioChunk(
        audioEncoder: MediaCodec,
        data: ByteArray,
        length: Int,
        ptsUs: Long,
        flags: Int
    ): Boolean {
        val inIdx = audioEncoder.dequeueInputBuffer(10_000L)
        if (inIdx >= 0) {
            val buf: ByteBuffer? = audioEncoder.getInputBuffer(inIdx)
            buf?.clear()
            buf?.put(data, 0, length)
            audioEncoder.queueInputBuffer(inIdx, 0, length, ptsUs, flags)
            return true
        }
        return false
    }

    private fun drawAnimatedSceneFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        artBitmap: Bitmap?,
        scene: CartoonScene,
        style: CartoonStyle,
        progress: Float,
        bitmapPaint: Paint,
        barPaint: Paint,
        subtitlePaint: TextPaint,
        particlePaint: Paint
    ) {
        canvas.drawColor(Color.rgb(14, 13, 29))

        if (artBitmap != null) {
            val scale = when (scene.cameraMotion) {
                "ZOOM_OUT" -> 1.14f - 0.12f * progress
                "PAN_LEFT", "PAN_RIGHT" -> 1.12f
                else -> 1.02f + 0.12f * progress
            }
            val dx = when (scene.cameraMotion) {
                "PAN_LEFT" -> -width * 0.05f * progress
                "PAN_RIGHT" -> width * 0.05f * progress
                else -> 0f
            }
            val dy = sin(progress * PI * 2.0).toFloat() * (height * 0.008f)

            canvas.save()
            canvas.translate(width / 2f + dx, height / 2f + dy)
            canvas.scale(scale, scale)
            val dest = RectF(-width / 2f, -height / 2f, width / 2f, height / 2f)
            canvas.drawBitmap(artBitmap, null, dest, bitmapPaint)
            canvas.restore()
        } else {
            val gradient = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    Color.rgb(34, 28, 72), Color.rgb(14, 13, 29), Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradient)
        }

        // Subtle atmospheric motion particles
        particlePaint.color = when (style) {
            CartoonStyle.HORROR -> Color.argb(65, 160, 210, 255)
            CartoonStyle.FANTASY -> Color.argb(120, 255, 225, 120)
            else -> Color.argb(85, 255, 255, 255)
        }
        for (p in 0 until 12) {
            val px = ((p * 0.17f + progress * (0.25f + (p % 3) * 0.1f)) % 1f) * width
            val py = ((p * 0.29f + cos((progress * 4.0 + p)).toFloat() * 0.06f) % 1f) * height * 0.78f
            canvas.drawCircle(px, py.coerceAtLeast(20f), 3.5f + (p % 3) * 2f, particlePaint)
        }

        // Smooth fade in/out at scene boundaries
        val fadeAlpha = when {
            progress < 0.06f -> ((1f - progress / 0.06f) * 200f).toInt().coerceIn(0, 255)
            progress > 0.94f -> (((progress - 0.94f) / 0.06f) * 200f).toInt().coerceIn(0, 255)
            else -> 0
        }
        if (fadeAlpha > 0) {
            canvas.drawColor(Color.argb(fadeAlpha, 10, 10, 22))
        }

        // Localized subtitle / narration bar
        val caption = scene.narrationText.trim()
        if (caption.isNotBlank()) {
            val boxWidth = (width * 0.86f).toInt()
            val layout = StaticLayout.Builder
                .obtain(caption, 0, caption.length.coerceAtMost(220), subtitlePaint, boxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(3)
                .build()
            val padX = 28f
            val padY = 16f
            val top = height - layout.height - height * 0.08f
            val rect = RectF(
                (width - boxWidth) / 2f - padX,
                top - padY,
                (width + boxWidth) / 2f + padX,
                top + layout.height + padY
            )
            canvas.drawRoundRect(rect, 18f, 18f, barPaint)
            canvas.save()
            canvas.translate((width - boxWidth) / 2f, top)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    private fun decodeScaledBitmap(file: File, targetW: Int, targetH: Int): Bitmap? {
        if (!file.exists()) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        var sample = 1
        while (opts.outWidth / sample > targetW * 2 || opts.outHeight / sample > targetH * 2) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
        val scaled = Bitmap.createScaledBitmap(raw, targetW, targetH, true)
        if (scaled != raw) raw.recycle()
        return scaled
    }

    fun verifyMp4File(file: File): Boolean {
        if (!file.exists() || file.length() < 4096L) return false
        val header = ByteArray(12)
        val read = FileInputStream(file).use { it.read(header) }
        if (read < 8) return false
        val boxType = String(header, 4, 4, Charsets.US_ASCII)
        if (boxType != "ftyp") {
            Log.e(TAG, "MP4 verification failed: missing ftyp box, found '$boxType'")
            return false
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            hasVideo == "yes" && hasAudio == "yes" && durationMs > 0L
        } catch (e: Exception) {
            Log.e(TAG, "MediaMetadataRetriever verification failed", e)
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractThumbnailAndDuration(
        mp4File: File,
        thumbnailFile: File,
        fallbackArtPath: String?
    ): Long {
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        try {
            retriever.setDataSource(mp4File.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: fallbackArtPath?.let { BitmapFactory.decodeFile(it) }
            if (frame != null) {
                thumbnailFile.parentFile?.mkdirs()
                FileOutputStream(thumbnailFile).use { fos ->
                    frame.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                }
                frame.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail extraction fallback: ${e.message}")
        } finally {
            runCatching { retriever.release() }
        }
        return durationMs
    }

    private class EglSurfaceRenderer(
        private val surface: Surface,
        private val width: Int,
        private val height: Int
    ) {
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var programId = 0
        private var textureId = 0
        private var textureInitialized = false
        private val vertexBuffer: FloatBuffer

        init {
            val vertices = floatArrayOf(
                -1f, -1f, 0f, 1f,
                1f, -1f, 1f, 1f,
                -1f, 1f, 0f, 0f,
                1f, 1f, 1f, 0f
            )
            vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(vertices); position(0) }

            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            require(eglDisplay != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL14 display" }

            val version = IntArray(2)
            require(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                "Unable to initialize EGL14"
            }

            val configAttribsRecordable = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
            )
            val configAttribsFallback = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, configAttribsRecordable, 0, configs, 0, 1, numConfigs, 0) ||
                numConfigs[0] == 0
            ) {
                require(EGL14.eglChooseConfig(eglDisplay, configAttribsFallback, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0) {
                    "No suitable EGLConfig found for MP4 encoder surface"
                }
            }

            val eglConfig = configs[0]!!
            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            val surfAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfAttribs, 0)
            require(eglSurface != EGL14.EGL_NO_SURFACE) { "Failed to create EGL window surface" }

            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            setupGl()
        }

        private fun setupGl() {
            val vertexShaderSrc = """
                attribute vec2 aPos;
                attribute vec2 aTex;
                varying vec2 vTex;
                void main() {
                    gl_Position = vec4(aPos, 0.0, 1.0);
                    vTex = aTex;
                }
            """.trimIndent()

            val fragmentShaderSrc = """
                precision mediump float;
                varying vec2 vTex;
                uniform sampler2D uTexture;
                void main() {
                    gl_FragColor = texture2D(uTexture, vTex);
                }
            """.trimIndent()

            val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSrc)
            val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSrc)
            programId = GLES20.glCreateProgram()
            GLES20.glAttachShader(programId, vs)
            GLES20.glAttachShader(programId, fs)
            GLES20.glLinkProgram(programId)
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)

            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            textureId = tex[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        private fun compileShader(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            return shader
        }

        fun renderBitmap(bitmap: Bitmap, ptsNs: Long) {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0.05f, 0.05f, 0.11f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(programId)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

            if (!textureInitialized) {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                textureInitialized = true
            } else {
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
            }

            val posLoc = GLES20.glGetAttribLocation(programId, "aPos")
            val texLoc = GLES20.glGetAttribLocation(programId, "aTex")
            val samplerLoc = GLES20.glGetUniformLocation(programId, "uTexture")
            GLES20.glUniform1i(samplerLoc, 0)

            vertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(posLoc)
            GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)

            vertexBuffer.position(2)
            GLES20.glEnableVertexAttribArray(texLoc)
            GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(posLoc)
            GLES20.glDisableVertexAttribArray(texLoc)

            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun release() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                if (programId != 0) GLES20.glDeleteProgram(programId)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
            surface.release()
        }

        companion object {
            private const val EGL_RECORDABLE_ANDROID = 0x3142
        }
    }

    companion object {
        private const val TAG = "Mp4CartoonRenderer"
    }
}
