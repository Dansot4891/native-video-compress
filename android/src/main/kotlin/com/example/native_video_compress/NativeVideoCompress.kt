package com.example.native_video_compress

import android.media.*
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class NativeVideoCompressPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "native_video_compress")
        channel.setMethodCallHandler(this)
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

                // 옵셔널 파라미터들 (기본값 설정)
                val width = call.argument<Int>("width") // null이면 원본 크기 사용
                val height = call.argument<Int>("height") // null이면 원본 크기 사용
                val videoCodec = call.argument<String>("videoCodec") ?: "h264"
                val audioCodec = call.argument<String>("audioCodec") ?: "aac"
                val audioBitrate = call.argument<Int>("audioBitrate") ?: 128_000
                val audioSampleRate = call.argument<Int>("audioSampleRate") ?: 44_100
                val audioChannels = call.argument<Int>("audioChannels") ?: 2

                CoroutineScope(Dispatchers.IO).launch {
                    try {
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
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            result.error("COMPRESSION_ERROR", e.message, null)
                        }
                    }
                }
            }
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private suspend fun compressVideo(
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
        val inputFile = File(inputPath)
        val outputFile = File(outputPath)

        // 기존 파일 삭제
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            extractor.setDataSource(inputFile.absolutePath)

            // 비디오 및 오디오 트랙 찾기
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                when {
                    mime.startsWith("video/") -> {
                        videoTrackIndex = i
                        videoFormat = format
                    }
                    mime.startsWith("audio/") -> {
                        audioTrackIndex = i
                        audioFormat = format
                    }
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                withContext(Dispatchers.Main) {
                    result.error("NO_VIDEO_TRACK", "Video track not found", null)
                }
                return
            }

            // 원본 비디오 크기 및 회전 정보 가져오기
            val originalWidth = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val originalHeight = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val rotation = if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                videoFormat.getInteger(MediaFormat.KEY_ROTATION)
            } else {
                0
            }

            // 회전을 고려한 최종 크기 결정
            val isRotated = rotation == 90 || rotation == 270
            val finalWidth = width ?: if (isRotated) originalHeight else originalWidth
            val finalHeight = height ?: if (isRotated) originalWidth else originalHeight

            Log.d("VideoCompressor", "📹 비디오 크기 - 원본: ${originalWidth}x${originalHeight}, 출력: ${finalWidth}x${finalHeight}, 회전: ${rotation}°")

            // MediaMuxer 생성
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (rotation != 0) {
                muxer.setOrientationHint(rotation)
            }

            // 비디오 압축
            val videoOutputTrack = compressVideoTrack(
                extractor = extractor,
                videoTrackIndex = videoTrackIndex,
                muxer = muxer,
                targetBitrate = targetBitrate,
                finalWidth = finalWidth,
                finalHeight = finalHeight,
                videoCodec = videoCodec
            )

            // 오디오 처리
            var audioOutputTrack = -1
            if (audioTrackIndex != -1 && audioFormat != null) {
                audioOutputTrack = processAudioTrack(
                    extractor = extractor,
                    audioTrackIndex = audioTrackIndex,
                    audioFormat = audioFormat,
                    muxer = muxer,
                    audioCodec = audioCodec,
                    audioBitrate = audioBitrate,
                    audioSampleRate = audioSampleRate,
                    audioChannels = audioChannels
                )
            }

            withContext(Dispatchers.Main) {
                result.success(outputFile.absolutePath)
            }

        } catch (e: Exception) {
            Log.e("VideoCompressor", "압축 실패: ${e.message}", e)
            withContext(Dispatchers.Main) {
                result.error("COMPRESSION_ERROR", e.message, null)
            }
        } finally {
            try {
                extractor.release()
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {
                Log.e("VideoCompressor", "리소스 해제 실패: ${e.message}")
            }
        }
    }

    private fun compressVideoTrack(
        extractor: MediaExtractor,
        videoTrackIndex: Int,
        muxer: MediaMuxer,
        targetBitrate: Int,
        finalWidth: Int,
        finalHeight: Int,
        videoCodec: String
    ): Int {
        extractor.selectTrack(videoTrackIndex)
        val inputFormat = extractor.getTrackFormat(videoTrackIndex)

        // 비디오 코덱 선택
        val mimeType = when (videoCodec.lowercase()) {
            "h265", "hevc" -> MediaFormat.MIMETYPE_VIDEO_HEVC
            "h264" -> MediaFormat.MIMETYPE_VIDEO_AVC
            else -> MediaFormat.MIMETYPE_VIDEO_AVC
        }

        // 인코더 생성 및 설정
        val outputFormat = MediaFormat.createVideoFormat(mimeType, finalWidth, finalHeight).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }

        val encoder = MediaCodec.createEncoderByType(mimeType)
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        // 디코더 생성
        val originalMimeType = inputFormat.getString(MediaFormat.KEY_MIME)!!
        val decoder = MediaCodec.createDecoderByType(originalMimeType)
        decoder.configure(inputFormat, inputSurface, null, 0)
        decoder.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var outputTrackIndex = -1
        var muxerStarted = false
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            // 디코더에 입력
            if (!inputDone) {
                val inputBufferId = decoder.dequeueInputBuffer(TIMEOUT_USEC)
                if (inputBufferId >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferId)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }
            }

            // 디코더 출력 (자동으로 인코더 입력으로 전달됨)
            val decoderStatus = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            if (decoderStatus >= 0) {
                val doRender = bufferInfo.size != 0
                decoder.releaseOutputBuffer(decoderStatus, doRender)
            }

            // 인코더 출력
            val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            when {
                encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = encoder.outputFormat
                    outputTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                }
                encoderStatus >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(encoderStatus)!!
                    
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(outputTrackIndex, encodedData, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(encoderStatus, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        }

        decoder.stop()
        decoder.release()
        encoder.stop()
        encoder.release()

        return outputTrackIndex
    }

    private fun processAudioTrack(
        extractor: MediaExtractor,
        audioTrackIndex: Int,
        audioFormat: MediaFormat,
        muxer: MediaMuxer,
        audioCodec: String,
        audioBitrate: Int,
        audioSampleRate: Int,
        audioChannels: Int
    ): Int {
        extractor.selectTrack(audioTrackIndex)

        // 오디오 코덱 선택
        val mimeType = when (audioCodec.lowercase()) {
            "aac" -> MediaFormat.MIMETYPE_AUDIO_AAC
            "mp3" -> MediaFormat.MIMETYPE_AUDIO_MPEG
            else -> MediaFormat.MIMETYPE_AUDIO_AAC
        }

        // 인코더 설정
        val outputFormat = MediaFormat.createAudioFormat(mimeType, audioSampleRate, audioChannels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }

        val encoder = MediaCodec.createEncoderByType(mimeType)
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        // 디코더 설정
        val originalMimeType = audioFormat.getString(MediaFormat.KEY_MIME)!!
        val decoder = MediaCodec.createDecoderByType(originalMimeType)
        decoder.configure(audioFormat, null, null, 0)
        decoder.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var outputTrackIndex = -1
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            // 디코더에 입력
            if (!inputDone) {
                val inputBufferId = decoder.dequeueInputBuffer(TIMEOUT_USEC)
                if (inputBufferId >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferId)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }
            }

            // 디코더 출력
            var decoderOutputAvailable = true
            while (decoderOutputAvailable) {
                val decoderStatus = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
                if (decoderStatus >= 0) {
                    val decodedData = decoder.getOutputBuffer(decoderStatus)!!
                    
                    if (bufferInfo.size != 0) {
                        // 인코더에 입력
                        val encoderInputBufferId = encoder.dequeueInputBuffer(TIMEOUT_USEC)
                        if (encoderInputBufferId >= 0) {
                            val encoderInputBuffer = encoder.getInputBuffer(encoderInputBufferId)!!
                            encoderInputBuffer.clear()
                            encoderInputBuffer.put(decodedData)
                            
                            encoder.queueInputBuffer(
                                encoderInputBufferId,
                                0,
                                bufferInfo.size,
                                bufferInfo.presentationTimeUs,
                                bufferInfo.flags
                            )
                        }
                    }
                    
                    decoder.releaseOutputBuffer(decoderStatus, false)
                    
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        decoderOutputAvailable = false
                    }
                } else {
                    decoderOutputAvailable = false
                }
            }

            // 인코더 출력
            val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            when {
                encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = encoder.outputFormat
                    outputTrackIndex = muxer.addTrack(newFormat)
                }
                encoderStatus >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(encoderStatus)!!
                    
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(outputTrackIndex, encodedData, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(encoderStatus, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        }

        decoder.stop()
        decoder.release()
        encoder.stop()
        encoder.release()

        return outputTrackIndex
    }

    companion object {
        private const val TIMEOUT_USEC = 10000L
    }
}