import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:native_video_compress/enum/audio_setting.dart';
import 'package:native_video_compress/enum/video_setting.dart';
import 'package:path_provider/path_provider.dart';
import 'package:video_player/video_player.dart';

abstract class NativeVideoController {
  static const MethodChannel _channel = MethodChannel('native_video_compress');

  // Progress callback storage
  static Function(double)? _currentProgressCallback;
  static bool _handlerSetup = false;

  /// Setup method call handler for progress callbacks from native
  static void _setupProgressHandler() {
    if (_handlerSetup) return;

    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onProgress') {
        final progress = call.arguments['progress'] as int;
        // Convert 0-100 to 0.0-1.0
        _currentProgressCallback?.call(progress / 100.0);
      }
    });

    _handlerSetup = true;
  }

  /// Compress video
  ///
  /// [inputPath] : Input video path
  /// [outputPath] : Output video path
  /// [bitrate] : Video bitrate (e.g. 2000000 = 2Mbps)
  /// [width] : Output video width
  /// [height] : Output video height
  /// [videoCodec] : Video codec ("h264" or "h265"/"hevc"), default: "h264"
  /// [audioCodec] : Audio codec ("aac", "alac", "mp3"), default: "aac"
  /// [audioBitrate] : Audio bitrate (e.g. 128000 = 128kbps), default: 128000
  /// [audioSampleRate] : Audio sample rate (e.g. 44100), default: 44100
  /// [audioChannels] : Audio channels (1=mono, 2=stereo), default: 2
  /// [printingInfo] : Print video info, default: false
  /// [onProgress] : Progress callback (0.0 - 1.0), NEW parameter
  static Future<String?> compressVideo({
    required String inputPath,
    int bitrate = 2000000,
    int? width,
    int? height,
    VideoSetting videoSetting = VideoSetting.h264,
    AudioSetting audioSetting = AudioSetting.aac,
    int audioBitrate = 128000,
    int audioSampleRate = 44100,
    int audioChannels = 2,
    bool printingInfo = false,
    Function(double progress)? onProgress,
  }) async {
    try {
      // Setup progress handler
      _setupProgressHandler();
      _currentProgressCallback = onProgress;

      if (printingInfo) {
        debugPrint(
          '-------------------------------- Before compress video info --------------------------------',
        );
        await printVideoInfo(inputPath);
      }

      /// Output path creation
      final dir = await getTemporaryDirectory();
      final outputPath = '${dir.path}/$outputFileName';
      await _channel.invokeMethod('compressVideo', {
        'input': inputPath,
        'output': outputPath,
        'bitrate': bitrate,
        'width': width,
        'height': height,
        'videoCodec': videoSetting.value,
        'audioCodec': audioSetting.value,
        'audioBitrate': audioBitrate,
        'audioSampleRate': audioSampleRate,
        'audioChannels': audioChannels,
      });
      if (printingInfo) {
        debugPrint(
          '-------------------------------- After compress video info --------------------------------',
        );
        await printVideoInfo(outputPath);
      }
      return outputPath;
    } on PlatformException catch (e) {
      debugPrint('Failed to compress video: $e');
      return null;
    } finally {
      // Clean up callback reference to prevent memory leaks
      _currentProgressCallback = null;
    }
  }

  /// Clear cache
  static Future<void> clearCache() async {
    final tempDir = await getTemporaryDirectory();
    final fileName = '${tempDir.path}/$outputFileName';
    if (File(fileName).existsSync()) {
      File(fileName).deleteSync();
      debugPrint("✅ Cache deleted");
    }
  }

  /// File name
  static String get outputFileName => 'compressed.mp4';

  /// Set output file name
  static set outputFileName(String inputFileName) {
    outputFileName = inputFileName;
  }

  /// Print video size and resolution
  static Future<void> printVideoInfo(String path) async {
    final file = File(path);

    if (!await file.exists()) {
      debugPrint('⚠️ File not found: $path');
      return;
    }

    final size = await file.length();
    debugPrint('📏 File size: ${(size / 1024 / 1024).toStringAsFixed(2)} MB');

    final controller = VideoPlayerController.file(file);
    try {
      await controller.initialize();

      final width = controller.value.size.width;
      final height = controller.value.size.height;
      final duration = controller.value.duration;

      debugPrint('📐 Resolution: ${width.toInt()}x${height.toInt()}');
      debugPrint('⏱ Video duration: ${duration.inSeconds}s');
    } catch (e) {
      debugPrint('❌ Failed to read video info: $e');
    } finally {
      await controller.dispose();
    }
  }
}
