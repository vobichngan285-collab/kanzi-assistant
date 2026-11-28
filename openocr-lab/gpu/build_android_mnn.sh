#!/usr/bin/env bash
set -euo pipefail
test "$(git -C mnn-src rev-parse HEAD)" = c35f14f3ab5cb65094863b9a0e888370b027a670
python openocr-lab/gpu/instrument_mnn.py
python openocr-lab/gpu/fix_reduce_mean.py
NDK="$ANDROID_HOME/ndk/27.2.12479018"
cmake -S mnn-src -B mnn-build -G Ninja \
 -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
 -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_shared \
 -DCMAKE_BUILD_TYPE=Release -DMNN_BUILD_FOR_ANDROID_COMMAND=ON \
 -DMNN_BUILD_SHARED_LIBS=ON -DMNN_SEP_BUILD=OFF -DMNN_OPENCL=ON -DMNN_VULKAN=OFF \
 -DMNN_VULKAN_IMAGE=OFF -DMNN_USE_SYSTEM_LIB=OFF -DMNN_BUILD_TOOLS=OFF \
 -DMNN_BUILD_DEMO=OFF -DMNN_BUILD_TEST=OFF -DMNN_BUILD_CONVERTER=OFF \
 -DMNN_BUILD_LLM=OFF -DMNN_BUILD_OPENCV=OFF -DMNN_BUILD_AUDIO=OFF -DMNN_OPENGL=OFF
cmake --build mnn-build --target MNN -j 3
LIB=$(find mnn-build -type f -name libMNN.so -print -quit)
test -n "$LIB"
DST=openocr-lab/app/src/main/jniLibs/arm64-v8a
mkdir -p "$DST"
cp "$LIB" "$DST/"
TOOL="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
"$TOOL/bin/aarch64-linux-android28-clang++" -std=c++17 -O2 -fPIC -shared \
 -I mnn-src/include openocr-lab/gpu/ocr_mnn_jni.cpp -L "$DST" -lMNN -ldl -Wl,--no-undefined \
 -o "$DST/libocr_mnn_jni.so"
cp "$TOOL/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" "$DST/"
"$TOOL/bin/llvm-readelf" -d "$DST/libocr_mnn_jni.so"
"$TOOL/bin/llvm-nm" -D "$DST/libMNN.so" | grep -E 'ocr_trace_reset|ocr_trace_get|ocr_device_get'
cp mnn-src/LICENSE.txt MNN-LICENSE.txt
git -C mnn-src diff > MNN-diagnostics.patch
