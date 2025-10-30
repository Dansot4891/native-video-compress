import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'native_video_compress_method_channel.dart';

abstract class NativeVideoCompressPlatform extends PlatformInterface {
  /// Constructs a NativeVideoCompressPlatform.
  NativeVideoCompressPlatform() : super(token: _token);

  static final Object _token = Object();

  static NativeVideoCompressPlatform _instance = MethodChannelNativeVideoCompress();

  /// The default instance of [NativeVideoCompressPlatform] to use.
  ///
  /// Defaults to [MethodChannelNativeVideoCompress].
  static NativeVideoCompressPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [NativeVideoCompressPlatform] when
  /// they register themselves.
  static set instance(NativeVideoCompressPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
