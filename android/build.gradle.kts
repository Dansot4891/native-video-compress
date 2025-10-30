plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.example.native_video_compress"
    compileSdk = 33
    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = 21
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.media3:media3-transformer:1.5.0")
    implementation("androidx.media3:media3-effect:1.5.0")
    implementation("androidx.media3:media3-common:1.5.0")
}