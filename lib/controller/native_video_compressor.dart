import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:native_video_compress/controller/video_info_controller.dart';
import 'package:native_video_compress/enum/audio_setting.dart';
import 'package:native_video_compress/enum/video_setting.dart';
import 'package:path_provider/path_provider.dart';

abstract class NativeVideoController {
  static const MethodChannel _channel = MethodChannel('native_video_compress');

  /// 비디오 압축
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
  }) async {
    try {
      debugPrint(
        '-------------------------------- Before compress video info --------------------------------',
      );
      if (printingInfo) {
        await VideoInfoController.printVideoInfo(inputPath);
      }
      // 출력 경로 생성
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
      debugPrint(
        '-------------------------------- After compress video info --------------------------------',
      );
      if (printingInfo) {
        await VideoInfoController.printVideoInfo(outputPath);
      }
      return outputPath;
    } on PlatformException catch (e) {
      debugPrint('Failed to compress video: $e');
      return null;
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
}
