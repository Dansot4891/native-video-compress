import Flutter
import UIKit
import AVFoundation

public class NativeVideoCompressPlugin: NSObject, FlutterPlugin {

    private var methodChannel: FlutterMethodChannel?

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "native_video_compress", binaryMessenger: registrar.messenger())
        let instance = NativeVideoCompressPlugin()
        instance.methodChannel = channel
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "compressVideo":
            guard let args = call.arguments as? [String: Any],
                  let inputPath = args["input"] as? String,
                  let outputPath = args["output"] as? String,
                  let bitrate = args["bitrate"] as? Int else {
                result(FlutterError(code: "INVALID_ARGUMENTS", message: "Invalid arguments", details: nil))
                return
            }

            // 옵셔널 파라미터들 (기본값 설정)
            let width = args["width"] as? Int  // nil이면 원본 크기 사용
            let height = args["height"] as? Int  // nil이면 원본 크기 사용
            let videoCodec = args["videoCodec"] as? String ?? "h264"
            let audioCodec = args["audioCodec"] as? String ?? "aac"
            let audioBitrate = args["audioBitrate"] as? Int ?? 128_000
            let audioSampleRate = args["audioSampleRate"] as? Int ?? 44_100
            let audioChannels = args["audioChannels"] as? Int ?? 2

            compressVideo(
                inputPath: inputPath,
                outputPath: outputPath,
                targetBitrate: bitrate,
                width: width,
                height: height,
                videoCodec: videoCodec,
                audioCodec: audioCodec,
                audioBitrate: audioBitrate,
                audioSampleRate: audioSampleRate,
                audioChannels: audioChannels,
                result: result
            )

        default:
            result(FlutterMethodNotImplemented)
        }
    }

    // Send progress to Flutter
    private func sendProgress(_ progress: Int) {
        DispatchQueue.main.async {
            self.methodChannel?.invokeMethod("onProgress", arguments: ["progress": progress])
        }
    }

    private func compressVideo(
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
        result: @escaping FlutterResult
    ) {
        let inputURL = URL(fileURLWithPath: inputPath)
        let outputURL = URL(fileURLWithPath: outputPath)

        try? FileManager.default.removeItem(at: outputURL)

        let asset = AVAsset(url: inputURL)
        guard let videoTrack = asset.tracks(withMediaType: .video).first else {
            result(FlutterError(code: "NO_VIDEO_TRACK", message: "Video track not found", details: nil))
            return
        }

        // 원본 비디오 크기 가져오기
        let naturalSize = videoTrack.naturalSize
        let transform = videoTrack.preferredTransform

        // Transform을 고려한 실제 크기 계산 (회전 고려)
        let isPortrait = transform.a == 0 && abs(transform.b) == 1.0 &&
                        abs(transform.c) == 1.0 && transform.d == 0

        let originalWidth: Int
        let originalHeight: Int

        if isPortrait {
            // 90도 또는 270도 회전된 경우 width와 height 교체
            originalWidth = Int(naturalSize.height)
            originalHeight = Int(naturalSize.width)
        } else {
            originalWidth = Int(naturalSize.width)
            originalHeight = Int(naturalSize.height)
        }

        // width, height가 nil이면 원본 크기 사용
        let finalWidth = width ?? originalWidth
        let finalHeight = height ?? originalHeight

        print("📹 Video Size - Original: \(originalWidth)x\(originalHeight), Output: \(finalWidth)x\(finalHeight)")

        do {
            let reader = try AVAssetReader(asset: asset)
            let writer = try AVAssetWriter(outputURL: outputURL, fileType: .mp4)

            // 비디오 코덱 선택
            let codecType: AVVideoCodecType
            switch videoCodec.lowercased() {
            case "h265", "hevc":
                codecType = .hevc
            case "h264":
                codecType = .h264
            default:
                codecType = .h264
            }

            // 비디오 설정
            let videoSettings: [String: Any] = [
                AVVideoCodecKey: codecType,
                AVVideoWidthKey: finalWidth,
                AVVideoHeightKey: finalHeight,
                AVVideoCompressionPropertiesKey: [
                    AVVideoAverageBitRateKey: targetBitrate,
                    AVVideoProfileLevelKey: codecType == .hevc ?
                        AVVideoProfileLevelH264HighAutoLevel :
                        AVVideoProfileLevelH264HighAutoLevel
                ]
            ]

            let readerOutput = AVAssetReaderTrackOutput(
                track: videoTrack,
                outputSettings: [
                    kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
                ]
            )

            let writerInput = AVAssetWriterInput(mediaType: .video, outputSettings: videoSettings)
            writerInput.expectsMediaDataInRealTime = false
            writerInput.transform = videoTrack.preferredTransform

            guard writer.canAdd(writerInput) else {
                result(FlutterError(code: "WRITER_INPUT_ERROR", message: "Cannot add video input", details: nil))
                return
            }

            reader.add(readerOutput)
            writer.add(writerInput)

            // 오디오 처리
            var audioReaderOutput: AVAssetReaderTrackOutput?
            var audioWriterInput: AVAssetWriterInput?

            if let audioTrack = asset.tracks(withMediaType: .audio).first {
                audioReaderOutput = AVAssetReaderTrackOutput(
                    track: audioTrack,
                    outputSettings: [
                        AVFormatIDKey: kAudioFormatLinearPCM,
                        AVSampleRateKey: audioSampleRate,
                        AVNumberOfChannelsKey: audioChannels,
                        AVLinearPCMBitDepthKey: 16,
                        AVLinearPCMIsBigEndianKey: false,
                        AVLinearPCMIsFloatKey: false
                    ]
                )

                // 오디오 코덱 선택
                let audioFormatID: AudioFormatID
                switch audioCodec.lowercased() {
                case "aac":
                    audioFormatID = kAudioFormatMPEG4AAC
                case "alac":
                    audioFormatID = kAudioFormatAppleLossless
                case "mp3":
                    audioFormatID = kAudioFormatMPEGLayer3
                default:
                    audioFormatID = kAudioFormatMPEG4AAC // 기본값 AAC
                }

                // 오디오 출력 설정 (변수 적용)
                audioWriterInput = AVAssetWriterInput(mediaType: .audio, outputSettings: [
                    AVFormatIDKey: audioFormatID,
                    AVEncoderBitRateKey: audioBitrate,
                    AVNumberOfChannelsKey: audioChannels,
                    AVSampleRateKey: audioSampleRate
                ])

                if let audioWriterInput = audioWriterInput,
                   let audioReaderOutput = audioReaderOutput,
                   writer.canAdd(audioWriterInput) {
                    reader.add(audioReaderOutput)
                    writer.add(audioWriterInput)
                }
            }

            guard reader.startReading() else {
                result(FlutterError(code: "READER_START_FAILED",
                                message: reader.error?.localizedDescription ?? "Unknown",
                                details: nil))
                return
            }

            writer.startWriting()
            writer.startSession(atSourceTime: .zero)

            // Progress tracking variables
            let duration = asset.duration
            let totalSeconds = CMTimeGetSeconds(duration)
            var lastProgressUpdate = Date()

            let dispatchGroup = DispatchGroup()

            // 비디오 처리 (with progress tracking)
            dispatchGroup.enter()
            writerInput.requestMediaDataWhenReady(on: DispatchQueue(label: "videoQueue")) {
                while writerInput.isReadyForMoreMediaData {
                    guard reader.status == .reading else {
                        writerInput.markAsFinished()
                        dispatchGroup.leave()
                        return
                    }
                    if let buffer = readerOutput.copyNextSampleBuffer() {
                        writerInput.append(buffer)

                        // Calculate progress from timestamp
                        let timestamp = CMSampleBufferGetPresentationTimeStamp(buffer)
                        let currentSeconds = CMTimeGetSeconds(timestamp)
                        let progress = min(currentSeconds / totalSeconds, 1.0) * 100.0

                        // Throttle updates (max 5 per second = 200ms interval)
                        if Date().timeIntervalSince(lastProgressUpdate) > 0.2 {
                            self.sendProgress(Int(progress))
                            lastProgressUpdate = Date()
                        }
                    } else {
                        writerInput.markAsFinished()
                        dispatchGroup.leave()
                        return
                    }
                }
            }

            // 오디오 처리
            if let audioWriterInput = audioWriterInput,
               let audioReaderOutput = audioReaderOutput {
                dispatchGroup.enter()
                audioWriterInput.requestMediaDataWhenReady(on: DispatchQueue(label: "audioQueue")) {
                    while audioWriterInput.isReadyForMoreMediaData {
                        guard reader.status == .reading else {
                            audioWriterInput.markAsFinished()
                            dispatchGroup.leave()
                            return
                        }
                        if let buffer = audioReaderOutput.copyNextSampleBuffer() {
                            audioWriterInput.append(buffer)
                        } else {
                            audioWriterInput.markAsFinished()
                            dispatchGroup.leave()
                            return
                        }
                    }
                }
            }

            // 완료 처리
            dispatchGroup.notify(queue: .main) {
                // Send 100% progress before completion
                self.sendProgress(100)

                if reader.status == .failed {
                    result(FlutterError(code: "READ_FAILED",
                                    message: reader.error?.localizedDescription ?? "Unknown",
                                    details: nil))
                    return
                }

                writer.finishWriting {
                    if writer.status == .completed {
                        result(outputURL.path)
                    } else {
                        result(FlutterError(code: "WRITE_FAILED",
                                        message: writer.error?.localizedDescription ?? "Unknown",
                                        details: nil))
                    }
                }
            }

        } catch {
            result(FlutterError(code: "COMPRESSION_ERROR", message: error.localizedDescription, details: nil))
        }
    }
}
