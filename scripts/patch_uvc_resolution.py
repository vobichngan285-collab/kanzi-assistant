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
print("Patched AUSBC size negotiation: prefer 16:9 and never exceed 1920x1080.")
