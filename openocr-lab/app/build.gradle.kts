plugins {
 id("com.android.application") version "8.13.2"
 id("org.jetbrains.kotlin.android") version "2.3.20"
}
android {
 namespace = "com.vobich.bluestar.openocrlab"
 compileSdk = 36
 defaultConfig {
  applicationId = "com.vobich.bluestar.openocrlab.fielddb"
  minSdk = 28
  targetSdk = 35
  versionCode = 16
  versionName = "0.4.7"
  ndk { abiFilters += "arm64-v8a" }
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("debug"); isMinifyEnabled = false } }
 packaging { jniLibs { useLegacyPackaging = true; pickFirsts += "**/libc++_shared.so" } }
}
kotlin { jvmToolchain(17) }
dependencies {
 implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
 implementation("org.opencv:opencv:4.12.0")
 implementation("androidx.core:core:1.15.0")
 implementation("androidx.exifinterface:exifinterface:1.4.1")
 testImplementation("junit:junit:4.13.2")
}
