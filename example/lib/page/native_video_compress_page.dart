import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:native_video_compress/controller/native_video_compressor.dart';
import '../component/video_file_widget.dart';

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

  void _selectVideo() async {
    final result = await picker.pickVideo(source: ImageSource.gallery);
    if (result != null) {
      setState(() {
        path = result.path;
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
              if (path != null)
                VideoFileWidget(
                  videoPath: path!,
                  width: double.infinity,
                  isAutoPlay: true,
                ),
              if (outputPath != null)
                VideoFileWidget(
                  videoPath: outputPath!,
                  width: double.infinity,
                  isAutoPlay: true,
                ),
              ElevatedButton(
                onPressed: () async {
                  final result = await NativeVideoController.compressVideo(
                    inputPath: path!,
                  );
                  setState(() {
                    outputPath = result;
                  });
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
