import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:native_video_compress/controller/native_video_compressor.dart';
import 'dart:io';
import 'package:video_player/video_player.dart';

void main() async {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  // This widget is the root of your application.
  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(home: const NativeVideoCompressPage());
  }
}

/// ------------------------------------------------------------
/// Native Video Compress Page
/// ------------------------------------------------------------
class NativeVideoCompressPage extends StatefulWidget {
  const NativeVideoCompressPage({super.key});

  @override
  State<NativeVideoCompressPage> createState() =>
      _NativeVideoCompressPageState();
}

class _NativeVideoCompressPageState extends State<NativeVideoCompressPage> {
  String? path;
  String? outputPath;
  final picker = ImagePicker();
  double? _progress; // 0.0 ~ 1.0
  String? _ratioText;

  void _selectVideo() async {
    final result = await picker.pickVideo(source: ImageSource.gallery);
    if (result != null) {
      setState(() {
        path = result.path;
        _progress = null;
        _ratioText = null;
      });
      debugPrint('path: $path');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Video Compress Page')),
      body: SafeArea(
        child: SingleChildScrollView(
          child: Column(
            children: [
              ElevatedButton(
                onPressed: _selectVideo,
                child: Text('Select Video'),
              ),
              if (path != null) VideoFileWidget(videoPath: path!),
              if (outputPath != null) VideoFileWidget(videoPath: outputPath!),
              if (_progress != null)
                Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 8,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      LinearProgressIndicator(
                        value: _progress!.clamp(0.0, 1.0),
                        minHeight: 10,
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Progress: ${((_progress ?? 0) * 100).toStringAsFixed(0)}%',
                      ),
                    ],
                  ),
                ),
              if (_ratioText != null)
                Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 4,
                  ),
                  child: Text(
                    _ratioText!,
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                ),
              ElevatedButton(
                onPressed: () async {
                  if (path == null) return;
                  setState(() {
                    _progress = 0.0;
                    _ratioText = null;
                    outputPath = null;
                  });
                  final result = await NativeVideoController.compressVideo(
                    inputPath: path!,
                    bitrate: 1000000,
                    printingInfo: true,
                    onProgress: (p) {
                      if (!mounted) return;
                      setState(() {
                        _progress = p;
                      });
                    },
                  );
                  if (!mounted) return;
                  if (result != null) {
                    final stats =
                        await NativeVideoController.calculateCompressionStats(
                          inputPath: path!,
                          outputPath: result,
                        );
                    setState(() {
                      outputPath = result;
                      _ratioText =
                          'Compression: ${stats.ratioPercent.toStringAsFixed(1)}% (in: ${(stats.inputBytes / 1024 / 1024).toStringAsFixed(2)}MB → out: ${(stats.outputBytes / 1024 / 1024).toStringAsFixed(2)}MB)';
                      _progress = 1.0;
                    });
                  } else {
                    setState(() {
                      _progress = null;
                    });
                  }
                },
                child: Text('Compress Video'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// ------------------------------------------------------------
/// Video File Widget
/// ------------------------------------------------------------
class VideoFileWidget extends StatefulWidget {
  final String videoPath;

  const VideoFileWidget({super.key, required this.videoPath});

  @override
  State<VideoFileWidget> createState() => _VideoFileWidgetState();
}

class _VideoFileWidgetState extends State<VideoFileWidget> {
  late final VideoPlayerController _player;
  bool isError = false;

  @override
  void initState() {
    super.initState();

    /// 비디오 캐싱 초기화
    _player = VideoPlayerController.file(File(widget.videoPath));

    _player
        .initialize()
        .then((_) {
          if (mounted) {
            /// 무한 반복 설정
            _player.setLooping(true);
            setState(() {});
            _player.play();
          }
        })
        .catchError((error) {
          if (mounted) {
            setState(() {
              isError = true;
            });
          }
        });
  }

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    /// 영상 로딩 오류 처리
    if (isError) {
      return const Center(
        child: Text(
          'Video loading failed',
          style: TextStyle(fontSize: 20, fontWeight: FontWeight.w600),
          textAlign: TextAlign.center,
        ),
      );
    }

    /// 영상 로딩 중 처리
    if (!_player.value.isInitialized) {
      return SizedBox.shrink();
    }

    return ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: SizedBox(
        width: double.infinity,
        child: FittedBox(
          fit: BoxFit.cover, // 또는 BoxFit.fill, BoxFit.contain 등
          child: SizedBox(
            width: _player.value.size.width,
            height: _player.value.size.height,
            child: VideoPlayer(_player),
          ),
        ),
      ),
    );
  }
}
