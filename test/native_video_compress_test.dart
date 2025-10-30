import 'package:flutter_test/flutter_test.dart';
import 'package:native_video_compress/native_video_compress.dart';
import 'package:native_video_compress/native_video_compress_platform_interface.dart';
import 'package:native_video_compress/native_video_compress_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockNativeVideoCompressPlatform
    with MockPlatformInterfaceMixin
    implements NativeVideoCompressPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final NativeVideoCompressPlatform initialPlatform = NativeVideoCompressPlatform.instance;

  test('$MethodChannelNativeVideoCompress is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelNativeVideoCompress>());
  });

  test('getPlatformVersion', () async {
    NativeVideoCompress nativeVideoCompressPlugin = NativeVideoCompress();
    MockNativeVideoCompressPlatform fakePlatform = MockNativeVideoCompressPlatform();
    NativeVideoCompressPlatform.instance = fakePlatform;

    expect(await nativeVideoCompressPlugin.getPlatformVersion(), '42');
  });
}
