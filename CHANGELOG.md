## 0.0.1
- Initial release
- Video compression using native encoders on iOS/Android
- Video codecs: `h264`, `h265`/`hevc`
- Audio codecs: `aac`, `alac` (lossless), `mp3`
- Supports bitrate and resolution (`width`, `height`); defaults to original when omitted
- Supports audio bitrate/sample rate/channels
- Optional info logging (size/resolution/duration) before and after compression
- Outputs to temporary directory; provides cache clearing Function
- Android: Media3 Transformer, rounds resolution to multiples of 16, returns original if compressed file is larger
- iOS: AVAssetReader/Writer, handles rotation via preferred transform


## 0.0.2
- Write READEME.md => Supported platforms: Android and iOS only

## 0.0.3
- Implementing by integrating example code into the main.dart

## 0.0.4
- Add realtime progress callback (0.0 → 1.0) via `onProgress` parameter
- Android: add `preserveResolution` (default: true) — when `width/height` are omitted, no scaling is applied
- Android: add `avoidLargerOutput` (default: true) — if output gets larger, keep original instead
- Android: when resizing (or preserveResolution=false), align dimensions to even numbers (×2) for encoder compatibility
- Docs: README updated with progress usage, parameters table, platform notes
- Dart: expose `calculateCompressionStats` helper to compute the compression ratio