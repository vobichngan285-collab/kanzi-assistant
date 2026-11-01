#!/usr/bin/env python3
"""Keep AUSBC camera negotiation at or below the requested 1080p mode."""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_uvc_resolution.py PATH_TO_MultiCameraClient.kt")

path = Path(sys.argv[1])
source = path.read_text(encoding="utf-8")
if "Do not select a camera mode larger than the requested dimensions." in source:
    print("AUSBC resolution cap is already applied.")
    raise SystemExit(0)

start_marker = "        fun getSuitableSize(maxWidth: Int, maxHeight: Int): PreviewSize {"
end_marker = "        fun isPreviewSizeSupported(previewSize: PreviewSize): Boolean {"
start = source.find(start_marker)
end = source.find(end_marker, start + len(start_marker))
if start < 0 or end < 0:
    raise SystemExit("ERROR: AUSBC 3.6.0 getSuitableSize method not found; refusing unsafe patch.")

replacement = """        fun getSuitableSize(maxWidth: Int, maxHeight: Int): PreviewSize {
            val sizeList = getAllPreviewSizes().filter { it.width > 0 && it.height > 0 }
            if (sizeList.isEmpty()) {
                return PreviewSize(DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT)
            }

            sizeList.firstOrNull {
                it.width == maxWidth && it.height == maxHeight
            }?.let { return it }

            // Do not select a camera mode larger than the requested dimensions.
            // Prefer 16:9 (1920x1080) when available, then the closest supported
            // aspect ratio below the cap. If every advertised mode is larger,
            // fall back to the smallest one rather than unexpectedly opening 4K.
            val withinLimit = sizeList.filter {
                it.width <= maxWidth && it.height <= maxHeight
            }
            if (withinLimit.isEmpty()) {
                return sizeList.minBy { it.width.toLong() * it.height.toLong() }
            }

            val targetRatio = maxWidth.toDouble() / maxHeight.toDouble()
            val widescreen = withinLimit.filter {
                kotlin.math.abs(it.width.toDouble() / it.height.toDouble() - targetRatio) <= 0.03
            }
            if (widescreen.isNotEmpty()) {
                return widescreen.maxBy { it.width.toLong() * it.height.toLong() }
            }

            return withinLimit.minWithOrNull(
                compareBy<PreviewSize> {
                    kotlin.math.abs(it.width.toDouble() / it.height.toDouble() - targetRatio)
                }.thenByDescending {
                    it.width.toLong() * it.height.toLong()
                }
            ) ?: withinLimit.maxBy { it.width.toLong() * it.height.toLong() }
        }

"""
patched = source[:start] + replacement + source[end:]
path.write_text(patched, encoding="utf-8")
camera_path = path.parent / "camera" / "CameraUVC.kt"
if not camera_path.is_file():
    raise SystemExit(f"ERROR: expected AUSBC CameraUVC source not found: {camera_path}")
camera_source = camera_path.read_text(encoding="utf-8")
old_fps = "private const val MAX_FPS = 61"
new_fps = "private const val MAX_FPS = 30"
if old_fps in camera_source:
    camera_source = camera_source.replace(old_fps, new_fps, 1)
elif new_fps not in camera_source:
    raise SystemExit("ERROR: expected AUSBC MAX_FPS setting not found; refusing unsafe patch.")
camera_path.write_text(camera_source, encoding="utf-8")
print("Patched AUSBC: prefer 16:9, cap resolution at 1920x1080 and maximum frame rate at 30 FPS.")
