import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'native_video_compress_platform_interface.dart';

/// An implementation of [NativeVideoCompressPlatform] that uses method channels.
class MethodChannelNativeVideoCompress extends NativeVideoCompressPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('native_video_compress');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
