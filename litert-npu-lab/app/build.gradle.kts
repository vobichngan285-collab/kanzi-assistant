plugins {
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.3.20"
}
android {
    namespace = "com.vobich.bluestar.npulab"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.vobich.bluestar.npulab.quality"
        minSdk = 28
        targetSdk = 36
        versionCode = 5
        versionName = "0.1.8"
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { jniLibs { useLegacyPackaging = true } }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")
    implementation("com.google.ai.edge.litert:litert-npu-runtime-mediatek:2.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
