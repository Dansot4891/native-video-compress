import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:video_player/video_player.dart';

abstract class VideoInfoController {
  /// Print video size and resolution
  static Future<void> printVideoInfo(String path) async {
    debugPrint('printVideoInfo: $path');
    final file = File(path);

    if (!await file.exists()) {
      debugPrint('⚠️ File not found: $path');
      return;
    }

    final size = await file.length();
    debugPrint('📏 파일 크기: ${(size / 1024 / 1024).toStringAsFixed(2)} MB');

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
