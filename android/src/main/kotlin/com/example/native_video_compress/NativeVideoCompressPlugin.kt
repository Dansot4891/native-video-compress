package com.example.native_video_compress

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File

class NativeVideoCompressPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "native_video_compress")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "compressVideo" -> {
                val inputPath = call.argument<String>("input")
                val outputPath = call.argument<String>("output")
                val bitrate = call.argument<Int>("bitrate")

                if (inputPath == null || outputPath == null || bitrate == null) {
                    result.error("INVALID_ARGUMENTS", "Invalid arguments", null)
                    return
                }

                val width = call.argument<Int>("width")
                val height = call.argument<Int>("height")
                val videoCodec = call.argument<String>("videoCodec") ?: "h264"
                val audioCodec = call.argument<String>("audioCodec") ?: "aac"
                val audioBitrate = call.argument<Int>("audioBitrate") ?: 128_000
                val audioSampleRate = call.argument<Int>("audioSampleRate") ?: 44_100
                val audioChannels = call.argument<Int>("audioChannels") ?: 2

                compressVideo(
                    inputPath = inputPath,
                    outputPath = outputPath,
                    targetBitrate = bitrate,
                    width = width,
                    height = height,
                    videoCodec = videoCodec,
                    audioCodec = audioCodec,
                    audioBitrate = audioBitrate,
                    audioSampleRate = audioSampleRate,
                    audioChannels = audioChannels,
                    result = result
                )
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun compressVideo(
        inputPath: String,
        outputPath: String,
        targetBitrate: Int,
        width: Int?,
        height: Int?,
        videoCodec: String,
        audioCodec: String,
        audioBitrate: Int,
        audioSampleRate: Int,
        audioChannels: Int,
        result: Result
    ) {
        Log.d("VideoCompress", "========== Compress Start ==========")
        Log.d("VideoCompress", "Input: $inputPath")
        Log.d("VideoCompress", "Output: $outputPath")

        try {
            val outputFile = File(outputPath)
            if (outputFile.exists()) {
                outputFile.delete()
            }

            // 원본 비디오 정보 가져오기
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(inputPath)
            val originalWidth = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            val originalHeight = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            val originalBitrate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: targetBitrate
            retriever.release()

            Log.d("VideoCompress", "📹 Original Size: ${originalWidth}x${originalHeight}, ${originalBitrate / 1000}kbps")

            // 해상도 결정 - 사용자 지정이 없으면 원본 유지
            val finalWidth: Int
            val finalHeight: Int

            if (width != null && height != null) {
                // 사용자가 명시적으로 해상도 지정
                finalWidth = roundTo16(width)
                finalHeight = roundTo16(height)
                Log.d("VideoCompress", "🎯 Custom Resolution: ${finalWidth}x${finalHeight}")
            } else {
                // 해상도 지정 없으면 원본 그대로
                finalWidth = roundTo16(originalWidth)
                finalHeight = roundTo16(originalHeight)
                Log.d("VideoCompress", "🎯 Original resolution: ${finalWidth}x${finalHeight}")
            }

            // 비트레이트가 원본보다 낮은데 해상도는 그대로면 경고
            if (targetBitrate < originalBitrate && width == null && height == null) {
                Log.w("VideoCompress", "⚠️ The resolution remains the same, but only the beat rate is lowered → Compression effects may be limited ⚠️")
                Log.w("VideoCompress", "💡 If you want a smaller file, set width/height together")
            }

            val videoMimeType = when (videoCodec.lowercase()) {
                "h265", "hevc" -> MimeTypes.VIDEO_H265
                else -> MimeTypes.VIDEO_H264
            }

            val audioMimeType = when (audioCodec.lowercase()) {
                "aac" -> MimeTypes.AUDIO_AAC
                else -> MimeTypes.AUDIO_AAC
            }

            val videoEncoderSettings = VideoEncoderSettings.Builder()
                .setBitrate(targetBitrate)
                .build()

            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(videoEncoderSettings)
                .setEnableFallback(false)
                .build()

            val mediaItem = MediaItem.fromUri(inputPath)

            val effects = Effects(
                emptyList(),
                listOf(
                    Presentation.createForWidthAndHeight(
                        finalWidth,
                        finalHeight,
                        Presentation.LAYOUT_SCALE_TO_FIT
                    )
                )
            )

            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setEffects(effects)
                .build()

            // Progress tracking setup
            val progressHolder = ProgressHolder()
            val progressHandler = Handler(Looper.getMainLooper())
            var progressRunnable: Runnable? = null

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(videoMimeType)
                .setAudioMimeType(audioMimeType)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        // Stop progress polling
                        progressRunnable?.let { progressHandler.removeCallbacks(it) }

                        // Send 100% progress
                        sendProgressToFlutter(100)

                        val inputSize = File(inputPath).length()
                        val outputSize = File(outputPath).length()

                        // 압축했는데 원본보다 크면 원본 사용
                        if (outputSize >= inputSize) {
                            Log.w("VideoCompress", "⚠️ Files get bigger after compression → Using the Original Source ⚠️")

                            // 압축된 파일 삭제
                            File(outputPath).delete()

                            // 원본을 출력 경로로 복사
                            File(inputPath).copyTo(File(outputPath), overwrite = true)

                            result.success(outputPath)
                            return
                        }

                        val compressionRatio = ((inputSize - outputSize).toFloat() / inputSize * 100)
                        val inputSizeMB = String.format("%.2f", inputSize / 1024.0 / 1024.0)
                        val outputSizeMB = String.format("%.2f", outputSize / 1024.0 / 1024.0)

                        Log.d("VideoCompress", "========== Compress Complete! ==========")
                        Log.d("VideoCompress", "⏱ Duration: ${exportResult.durationMs}ms")
                        Log.d("VideoCompress", "📏 Input size: $inputSizeMB MB")
                        Log.d("VideoCompress", "📏 Output size: $outputSizeMB MB")
                        Log.d("VideoCompress", "📊 Compression: ${compressionRatio.toInt()}%")
                        Log.d("VideoCompress", "📐 Output resolution: ${finalWidth}x${finalHeight}")

                        result.success(outputPath)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        // Stop progress polling
                        progressRunnable?.let { progressHandler.removeCallbacks(it) }

                        Log.e("VideoCompress", "❌ 압축 실패", exportException)
                        result.error(
                            "COMPRESSION_ERROR",
                            exportException.message ?: "Unknown error",
                            exportException.toString()
                        )
                    }
                })
                .build()

            Log.d("VideoCompress", "🚀 Transformer Start...")
            transformer.start(editedMediaItem, outputPath)

            // Setup and start progress polling
            progressRunnable = object : Runnable {
                override fun run() {
                    when (transformer.getProgress(progressHolder)) {
                        Transformer.PROGRESS_STATE_AVAILABLE -> {
                            val progress = progressHolder.progress // 0-100
                            sendProgressToFlutter(progress)
                            progressHandler.postDelayed(this, 200) // Poll every 200ms
                        }
                        Transformer.PROGRESS_STATE_UNAVAILABLE -> {
                            // Not started yet, keep polling
                            progressHandler.postDelayed(this, 200)
                        }
                        Transformer.PROGRESS_STATE_NO_TRANSFORMATION -> {
                            // No transformation needed (edge case)
                            sendProgressToFlutter(100)
                        }
                        else -> {
                            // Unknown state, keep polling
                            progressHandler.postDelayed(this, 200)
                        }
                    }
                }
            }

            // Start progress polling
            progressRunnable?.let { progressHandler.post(it) }

        } catch (e: Exception) {
            Log.e("VideoCompress", "❌ 압축 설정 실패", e)
            result.error("COMPRESSION_ERROR", e.message ?: "Unknown error", e.toString())
        }
    }

    // Send progress to Flutter
    private fun sendProgressToFlutter(progress: Int) {
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod("onProgress", mapOf("progress" to progress))
        }
    }

    // 16의 배수로 반올림하는 함수
    private fun roundTo16(value: Int): Int {
        return (value + 8) / 16 * 16
    }
}
