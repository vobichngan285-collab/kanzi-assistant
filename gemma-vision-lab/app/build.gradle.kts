plugins {
 id("com.android.application") version "8.13.2"
 id("org.jetbrains.kotlin.android") version "2.3.20"
}
android {
 namespace = "com.vobich.bluestar.gemmalab"
 compileSdk = 36
 buildToolsVersion = "36.0.0"
 defaultConfig {
  applicationId = "com.vobich.bluestar.gemmalab.vision560.recovery"
  minSdk = 28
  targetSdk = 35
  versionCode = 7
  versionName = "0.1.6"
  ndk { abiFilters += "arm64-v8a" }
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("debug"); isMinifyEnabled = false } }
 packaging { jniLibs { useLegacyPackaging = true; keepDebugSymbols += "**/liblitertlm_jni.so" } }
}
kotlin { jvmToolchain(17) }
dependencies {
 implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")
 implementation("androidx.core:core:1.15.0")
 implementation("androidx.exifinterface:exifinterface:1.4.1")
 testImplementation("junit:junit:4.13.2")
}
