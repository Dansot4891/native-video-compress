Pod::Spec.new do |s|
    s.name             = 'native_video_compress'
    s.version          = '0.0.1'
    s.summary          = 'A Flutter plugin for native video compression.'
    s.description      = <<-DESC
  A Flutter plugin for native video compression on iOS and Android.
                         DESC
    s.homepage         = 'https://github.com/Dansot4891/native-video-compress'
    s.license          = { :file => '../LICENSE' }
    s.author           = { 'Dansot4891' => 'cnctlim94@gmail.com' }
    s.source           = { :path => '.' }
    s.source_files = 'Classes/**/*'
    s.dependency 'Flutter'
    s.platform = :ios, '12.0'
  
    # Flutter.framework does not contain a i386 slice.
    s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
    s.swift_version = '5.0'
  end
