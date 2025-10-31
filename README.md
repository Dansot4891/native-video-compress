# native_video_compress

A Flutter plugin that compresses video files using native encoders on iOS and Android.

---

## ⭐️ Features
- **Target bitrate**: reduce file size by setting an average video bitrate
- **Resize**: specify output `width`/`height` (defaults to original if omitted)
- **Video codecs**: `h264`, `h265`/`hevc`
- **Audio codecs/quality**: `aac`, `alac`, `mp3` with bitrate/sample rate/channels
- **Info logging**: print size, resolution, and duration before/after compression
- **Temp file management**: result is written to the temporary directory; Function to clear cache

---

## 🔶 Requirements
- **Flutter**: SDK ^3.8.1, Flutter >= 3.3.0
- **Android**: minSdk 21, compileSdk 33, ndkVersion 27.0.12077973
- **iOS**: iOS 12.0+

---

## 😎 Installation
```bash
flutter pub add native_video_compress
```

`pubspec.yaml` example
```yaml
dependencies:
  native_video_compress: ^${latest_version}
```

No additional platform setup is required. Note that H.265 (HEVC) encoding requires device/OS support.

## ⚠️ Android Setup (NDK version)
Some Android environments require an explicit NDK version to build Media3/Transformer properly. If you encounter NDK/transformer build errors, set the NDK version in your app module's Gradle (Kotlin DSL):

```kotlin
// android/app/build.gradle.kts
android {
    compileSdk = flutter.compileSdkVersion
    ndkVersion = "27.0.12077973"
    // ...
}
```

Notes:
- This plugin already sets `ndkVersion = "27.0.12077973"` in its own library module. Some projects still need the app module to match it explicitly.
- Ensure your Android Gradle Plugin and Gradle wrapper are up-to-date, or align the NDK version as above if builds fail.

---

## 🚀 Quick Start
### Compress Video
```dart
import 'package:native_video_compress/controller/native_video_compressor.dart';
import 'package:native_video_compress/enum/video_setting.dart';
import 'package:native_video_compress/enum/audio_setting.dart';

final output = await NativeVideoController.compressVideo(
  inputPath: "/path/to/input.mp4",
  bitrate: 2_000_000, // 2 Mbps
  width: 1280,        // optional (Nullable)
  height: 720,        // optional (Nullable)
  videoSetting: VideoSetting.h264, // h265/hevc also supported (Default)
  audioSetting: AudioSetting.aac,  // alac, mp3 also supported (Default)
  audioBitrate: 128000, // optional (Default)
  audioSampleRate: 44100, // optional (Default)
  audioChannels: 2, // optional (Default)
  printingInfo: false, // log info before/after
);
```

### Clear Cache
``` dart
await NativeVideoController.clearCache();
```

### Priniting Info
``` dart
await NativeVideoController.printVideoInfo("/path/to/input.mp4");
```

---

## 🧩 Parameters
[inputPath] : Input video path
[outputPath] : Output video path
[bitrate] : Video bitrate (e.g. 2000000 = 2Mbps)
[width] : Output video width (Nullable)
[height] : Output video height (Nullable)
[videoCodec] : Video codec ("h264" or "h265"/"hevc"), default: "h264"
[audioCodec] : Audio codec ("aac", "alac", "mp3"), default: "aac"
[audioBitrate] : Audio bitrate (e.g. 128000 = 128kbps), default: 128000
[audioSampleRate] : Audio sample rate (e.g. 44100), default: 44100
[audioChannels] : Audio channels (1=mono, 2=stereo), default: 2
[printingInfo] : Print video info, default: false

### Enums
```dart
enum VideoSetting { h264('h264', 'H.264/AVC'), h265('h265', 'H.265/HEVC'), hevc('hevc', 'H.265/HEVC') }
enum AudioSetting { aac('aac', false), alac('alac', true), mp3('mp3', false) }
```

---

## ✅ Cache Management
`NativeVideoController.clearCache() -> Future<void>`
- Deletes the `compressed.mp4` from the temporary directory.
- Call this after you copy/move the output to a permanent location, or when the compressed file is no longer needed.

Example:
```dart
// If you don't need the temporary output anymore
await NativeVideoController.clearCache();
```

---

## Platform Notes
- **Androi**
  - If the compressed file becomes larger than the input, the plugin returns the original by copying it to the output path.
  - Resolution is rounded to a multiple of 16 internally (encoder constraints).
  - `h265`/`hevc` requires a supported device encoder; otherwise it may fail.
  - Lowering bitrate without resizing may have limited effect; consider setting `width/height` as well.

- **iOS**
  - Handles rotation (preferred transform) to preserve the correct orientation.
  - `h265`/`hevc` requires HEVC support on the device/OS.
  - `alac` is lossless.

---

## 👀 Example App
See the `example/` project in this repository.
- Page: `example/lib/page/native_video_compress_page.dart`
- Player widget: `example/lib/component/video_file_widget.dart`

Notes:
- The example uses `image_picker` purely to demonstrate selecting a video file. The plugin itself does not depend on it.
- Typical workflow: pick a video (any method) → compress → use or move the output → run the cache clearing Function if the temporary file is no longer needed.

## 🪪 License
See the `LICENSE` file in the repository.
