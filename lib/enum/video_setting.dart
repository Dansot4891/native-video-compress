/// Video Codec Setting
enum VideoSetting {
  h264('h264', 'H.264/AVC'),
  h265('h265', 'H.265/HEVC'),
  hevc('hevc', 'H.265/HEVC');

  const VideoSetting(this.value, this.description);
  final String value;
  final String description;
}
