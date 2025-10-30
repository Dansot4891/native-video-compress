import 'dart:io';
import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

class VideoFileWidget extends StatefulWidget {
  final String videoPath;
  final bool isCache;
  final bool isLoop;
  final bool isAutoPlay;
  final double width;
  final double? height;

  const VideoFileWidget({
    super.key,
    required this.videoPath,
    this.isCache = true,
    this.isLoop = true,
    this.isAutoPlay = true,
    required this.width,
    this.height,
  });

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
            _player.setLooping(widget.isLoop);
            setState(() {});
            if (widget.isAutoPlay) {
              _player.play();
            }
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
          '영상을 불러오는 중\n오류가 발생했습니다.',
          style: TextStyle(fontSize: 20, fontWeight: FontWeight.w600),
          textAlign: TextAlign.center,
        ),
      );
    }

    /// 영상 로딩 중 처리
    if (!_player.value.isInitialized) {
      return Stack(
        children: [
          Container(
            width: double.infinity,
            height: widget.height,
            color: Colors.grey[300],
          ),
          const Positioned.fill(
            child: Center(child: CircularProgressIndicator()),
          ),
        ],
      );
    }

    return ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: SizedBox(
        width: widget.width,
        height: widget.height,
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
