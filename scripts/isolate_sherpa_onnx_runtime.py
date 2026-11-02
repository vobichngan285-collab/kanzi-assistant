#!/usr/bin/env python3
"""Keep sherpa-onnx's native ONNX Runtime separate from PaddleOCR's official runtime.

Android's APK cannot have two JNI files named libonnxruntime.so in a single
ABI. Gradle pickFirst silently replaced the OCR's 1.21.1 runtime in build #303,
breaking OCR initialization. Rewrite the verified sherpa AAR so its JNI
dependencies use libonnxruntime_sherpa.so, and keep the OCR .so untouched.

This patches ONLY the pre-verified upstream AAR at build time. No neural voice
models are preinstalled and no network service is used for speech synthesis.
"""
import os
import subprocess
import sys
import tempfile
from pathlib import Path
from zipfile import ZipFile, ZipInfo

AAR = Path(sys.argv[1] if len(sys.argv) > 1 else "app/libs/sherpa-onnx-2.0.1.aar")
OLD = "libonnxruntime.so"
NEW = "libonnxruntime_sherpa.so"
abis = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
changed = set()
with tempfile.TemporaryDirectory(prefix="kanzi-isolate-sherpa-") as d:
    root = Path(d)
    patched = root / "patched.aar"
    with ZipFile(AAR, "r") as source, ZipFile(patched, "w") as dest:
        for item in source.infolist():
            if item.is_dir():
                dest.writestr(item, b"")
                continue
            blob = source.read(item)
            original_name = item.filename
            parts = original_name.split("/")
            if len(parts) == 3 and parts[0] == "jni" and parts[1] in abis and parts[2].endswith(".so"):
                abi = parts[1]
                name = parts[2]
                tmp = root / abi
                tmp.mkdir(exist_ok=True)
                native = tmp / name
                native.write_bytes(blob)
                if name == OLD:
                    subprocess.run(["patchelf", "--set-soname", NEW, str(native)], check=True)
                    original_name = "/".join(parts[:2] + [NEW])
                    changed.add(abi)
                else:
                    needed = subprocess.check_output(["patchelf", "--print-needed", str(native)], text=True)
                    if OLD in needed.splitlines():
                        subprocess.run(["patchelf", "--replace-needed", OLD, NEW, str(native)], check=True)
                blob = native.read_bytes()
            info = ZipInfo(original_name)
            info.date_time = item.date_time
            info.compress_type = item.compress_type
            info.external_attr = item.external_attr
            info.create_system = item.create_system
            dest.writestr(info, blob)
    if set(abis) != changed:
        raise SystemExit(f"Expected sherpa native ORT for all {abis}; found {sorted(changed)}")
    # Confirm every sherpa dependency points to its private SONAME.
    with ZipFile(patched) as z:
        for abi in abis:
            assert f"jni/{abi}/{NEW}" in z.namelist()
            assert f"jni/{abi}/{OLD}" not in z.namelist()
            for name in z.namelist():
                if name.startswith(f"jni/{abi}/") and name.endswith(".so"):
                    native = root / abi / "check.so"
                    native.write_bytes(z.read(name))
                    dependencies = subprocess.check_output(
                        ["patchelf", "--print-needed", str(native)], text=True
                    ).splitlines()
                    assert OLD not in dependencies, f"Unisolated sherpa dependency: {name}"
    os.replace(patched, AAR)
print("NATIVE_TTS_ONNX_RUNTIME_ISOLATED=PASS")
