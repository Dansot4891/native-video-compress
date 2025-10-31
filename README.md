## native_video_compress

A Flutter plugin that compresses video files using native encoders on iOS and Android.

- Android: AndroidX Media3 Transformer
- iOS: AVAssetReader/AVAssetWriter

### Features
- **Target bitrate**: reduce file size by setting an average video bitrate
- **Resize**: specify output `width`/`height` (defaults to original if omitted)
- **Video codecs**: `h264`, `h265`/`hevc`
- **Audio codecs/quality**: `aac`, `alac`, `mp3` with bitrate/sample rate/channels
- **Info logging**: print size, resolution, and duration before/after compression
- **Temp file management**: result is written to the temporary directory; Function to clear cache

### Requirements
- **Flutter**: SDK ^3.8.1, Flutter >= 3.3.0
- **Android**: minSdk 21, compileSdk 33, Media3 1.5.0
- **iOS**: iOS 12.0+, Swift 5

### Installation
```bash
flutter pub add native_video_compress
```

`pubspec.yaml` example
```yaml
dependencies:
  native_video_compress: ^${latest_version}
```

No additional platform setup is required. Note that H.265 (HEVC) encoding requires device/OS support.

### Quick Start
```dart
import 'package:native_video_compress/controller/native_video_compressor.dart';
import 'package:native_video_compress/enum/video_setting.dart';
import 'package:native_video_compress/enum/audio_setting.dart';

final output = await NativeVideoController.compressVideo(
  inputPath: "/path/to/input.mp4",
  bitrate: 2_000_000, // 2 Mbps
  width: 1280,        // optional
  height: 720,        // optional
  videoSetting: VideoSetting.h264, // h265/hevc also supported
  audioSetting: AudioSetting.aac,  // alac, mp3 also supported
  audioBitrate: 128000,
  audioSampleRate: 44100,
  audioChannels: 2,
  printingInfo: true, // log info before/after
);

if (output != null) {
  print('Compressed file: $output');
}

await NativeVideoController.clearCache();
```

> Note: The output lives in the temporary directory. If you need to keep it, move/copy it to your app storage. If you don't need it anymore, call the cache clearing Function to remove it.

### API Reference

#### Compression

`NativeVideoController.compressVideo({...}) -> Future<String?>`
- **inputPath (required)**: source video path
- **bitrate (default: 2_000_000)**: average video bitrate in bps
- **width / height (optional)**: output resolution; defaults to original if omitted
- **videoSetting (default: VideoSetting.h264)**: video codec
  - `VideoSetting.h264` → value: `"h264"`
  - `VideoSetting.h265` → value: `"h265"`
  - `VideoSetting.hevc` → value: `"hevc"` (mapped to HEVC internally on iOS)
- **audioSetting (default: AudioSetting.aac)**: audio codec
  - `AudioSetting.aac` → lossy, value: `"aac"`
  - `AudioSetting.alac` → lossless, value: `"alac"`
  - `AudioSetting.mp3` → lossy, value: `"mp3"`
- **audioBitrate (default: 128000)**: audio bitrate in bps
- **audioSampleRate (default: 44100)**: sample rate in Hz
- **audioChannels (default: 2)**: number of channels (1=mono, 2=stereo)
- **printingInfo (default: false)**: log info before/after compression

Returns the output file path on success, or `null` on failure.

Output is written to the temporary directory as `compressed.mp4` and is overwritten on subsequent runs.

Example:
```dart
final out = await NativeVideoController.compressVideo(
  inputPath: path,
  bitrate: 2_000_000,
  width: 1280,
  height: 720,
  videoSetting: VideoSetting.h264,
  audioSetting: AudioSetting.aac,
  printingInfo: true,
);
```

#### Video Info

`NativeVideoController.printVideoInfo(path) -> Future<void>`
- Prints file size (MB), resolution, and duration to the log.
- Useful for debugging quality/size trade-offs before and after compression.

Example:
```dart
await NativeVideoController.printVideoInfo(inputPath);
// ... run compression ...
await NativeVideoController.printVideoInfo(outputPath);
```

#### Cache Management

`NativeVideoController.clearCache() -> Future<void>`
- Deletes the `compressed.mp4` from the temporary directory.
- Call this after you copy/move the output to a permanent location, or when the compressed file is no longer needed.

Example:
```dart
// If you don't need the temporary output anymore
await NativeVideoController.clearCache();
```

### Enums
```dart
enum VideoSetting { h264('h264', 'H.264/AVC'), h265('h265', 'H.265/HEVC'), hevc('hevc', 'H.265/HEVC') }
enum AudioSetting { aac('aac', false), alac('alac', true), mp3('mp3', false) }
```

### Platform Notes
- **Android (Media3 Transformer)**
  - If the compressed file becomes larger than the input, the plugin returns the original by copying it to the output path.
  - Resolution is rounded to a multiple of 16 internally (encoder constraints).
  - `h265`/`hevc` requires a supported device encoder; otherwise it may fail.
  - Lowering bitrate without resizing may have limited effect; consider setting `width/height` as well.

- **iOS (AVAssetWriter)**
  - Handles rotation (preferred transform) to preserve the correct orientation.
  - `h265`/`hevc` requires HEVC support on the device/OS.
  - `alac` is lossless.

### Example App
See the `example/` project in this repository.
- Page: `example/lib/page/native_video_compress_page.dart`
- Player widget: `example/lib/component/video_file_widget.dart`

Notes:
- The example uses `image_picker` purely to demonstrate selecting a video file. The plugin itself does not depend on it.
- Typical workflow: pick a video (any method) → compress → use or move the output → run the cache clearing Function if the temporary file is no longer needed.

```dart
// Simplified usage (from the example app)
final result = await NativeVideoController.compressVideo(
  inputPath: path!,
  bitrate: 1_000_000,
);
```

### License
See the `LICENSE` file in the repository.
