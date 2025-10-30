package com.example.native_video_compress

import android.content.Context
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

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
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
        Log.d("VideoCompress", "========== 압축 시작 ==========")
        Log.d("VideoCompress", "Input: $inputPath")
        Log.d("VideoCompress", "Output: $outputPath")
        Log.d("VideoCompress", "Target Video Bitrate: ${targetBitrate / 1000}kbps")
        Log.d("VideoCompress", "Target Resolution: ${width ?: "원본"}x${height ?: "원본"}")

        try {
            val outputFile = File(outputPath)
            if (outputFile.exists()) {
                outputFile.delete()
            }

            val videoMimeType = when (videoCodec.lowercase()) {
                "h265", "hevc" -> MimeTypes.VIDEO_H265
                else -> MimeTypes.VIDEO_H264
            }

            val audioMimeType = when (audioCodec.lowercase()) {
                "aac" -> MimeTypes.AUDIO_AAC
                else -> MimeTypes.AUDIO_AAC
            }

            // 비디오 인코더 설정 - 더 강력하게
            val videoEncoderSettings = VideoEncoderSettings.Builder()
                .setBitrate(targetBitrate)
                .build()

            Log.d("VideoCompress", "✅ VideoEncoderSettings: bitrate=${targetBitrate}")

            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(videoEncoderSettings)
                .setEnableFallback(false) // 폴백 비활성화 - 강제로 설정 적용
                .build()

            val mediaItem = MediaItem.fromUri(inputPath)

            // 해상도가 지정되지 않으면 원본의 80%로 축소
            val effects = if (width != null && height != null) {
                Effects(
                    emptyList(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            width,
                            height,
                            Presentation.LAYOUT_SCALE_TO_FIT
                        )
                    )
                )
            } else {
                // 해상도 지정 안하면 기본적으로 축소
                Effects(
                    emptyList(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            1280,
                            720,
                            Presentation.LAYOUT_SCALE_TO_FIT
                        )
                    )
                )
            }

            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setEffects(effects)
                .build()

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(videoMimeType)
                .setAudioMimeType(audioMimeType)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        val inputSize = File(inputPath).length()
                        val outputSize = exportResult.fileSizeBytes
                        val compressionRatio = ((inputSize - outputSize).toFloat() / inputSize * 100)
                        
                        Log.d("VideoCompress", "========== 압축 완료! ==========")
                        Log.d("VideoCompress", "⏱ Duration: ${exportResult.durationMs}ms")
                        Log.d("VideoCompress", "📦 Input size: ${inputSize / 1024 / 1024}MB")
                        Log.d("VideoCompress", "📦 Output size: ${outputSize / 1024 / 1024}MB")
                        Log.d("VideoCompress", "📊 Compression: ${compressionRatio.toInt()}%")
                        Log.d("VideoCompress", "📊 Target bitrate: ${targetBitrate / 1000}kbps")
                        Log.d("VideoCompress", "📊 Actual video bitrate: ${exportResult.averageVideoBitrate / 1000}kbps")
                        Log.d("VideoCompress", "🎵 Actual audio bitrate: ${exportResult.averageAudioBitrate / 1000}kbps")
                        
                        // 비트레이트가 제대로 적용되었는지 확인
                        if (exportResult.averageVideoBitrate > targetBitrate * 1.2) {
                            Log.w("VideoCompress", "⚠️ 실제 비트레이트가 목표보다 높습니다!")
                        }
                        
                        result.success(outputPath)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        Log.e("VideoCompress", "❌ 압축 실패", exportException)
                        result.error(
                            "COMPRESSION_ERROR",
                            exportException.message ?: "Unknown error",
                            exportException.toString()
                        )
                    }
                })
                .build()

            Log.d("VideoCompress", "🚀 Transformer 시작...")
            transformer.start(editedMediaItem, outputPath)

        } catch (e: Exception) {
            Log.e("VideoCompress", "❌ 압축 설정 실패", e)
            result.error("COMPRESSION_ERROR", e.message ?: "Unknown error", e.toString())
        }
    }
}