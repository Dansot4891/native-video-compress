/// Audio Setting
enum AudioSetting {
  aac('aac', false),
  alac('alac', true),
  mp3('mp3', false);

  const AudioSetting(this.value, this.isLossless);
  final String value;
  final bool isLossless;
}
