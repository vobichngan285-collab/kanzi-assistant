plugins { id("com.android.application") version "8.13.2" }
android {
    namespace = "com.vobich.bluestar.nativelab"
    compileSdk = 36
    ndkVersion = "27.0.12077973"
    defaultConfig {
        applicationId = "com.vobich.bluestar.nativelab"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake {
            arguments += listOf("-DANDROID_STL=c++_shared", "-DENGINE_DIR=" + (System.getenv("ENGINE_DIR") ?: error("ENGINE_DIR required")))
            targets += "bluestar_native"
        } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "4.1.2" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("debug"); isMinifyEnabled = false } }
    packaging { jniLibs { useLegacyPackaging = true; pickFirsts += "**/libomp.so" } }
}
