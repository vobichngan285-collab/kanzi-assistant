#!/usr/bin/env python3
"""BSG 1.0.8 - fix USB SurfaceTexture *producer* buffer size, not screen viewport.

Pinned AndroidUSBCamera 3.6.0 passes source camera dimensions to
RenderManager(context, previewWidth, previewHeight, ...), while startRenderScreen
receives the output TextureView dimensions. These MUST NOT be conflated.
The original code set the producer buffer size to the output view size BOTH
on creation and on subsequent view-size changes, although native UVCCamera
writes 1920x1080 pixels using ANativeWindow_setBuffersGeometry(1920,1080,...).
Build-time fail-closed patch AFTER 1.0.7 fps and GL scheduler patches.
"""
from pathlib import Path

path = Path("third_party/AndroidUSBCamera/libausbc/src/main/java/com/jiangdg/ausbc/render/RenderManager.kt")
s = path.read_text(encoding="utf-8")
if "BSG_USB_INPUT_BUFFER_GEOMETRY_108" in s:
    raise SystemExit("REFUSE double application of BSG USB buffer geometry patch")
guards = (
    "private val surfaceWidth: Int",
    "private val surfaceHeight: Int",
    "mCameraRender?.setSize(mWidth, mHeight)",
    "mScreenRender?.setSize(mWidth, mHeight)",
    "mCaptureRender?.setSize(mWidth, mHeight)",
    "mCameraSurfaceTexture?.updateTexImage()",
    "BSG_GL_DRAW_COALESCE_107",
)
for guard in guards:
    if guard not in s:
        raise SystemExit(f"REFUSE unrecognised render source: {guard!r}")

original_resize = "mCameraSurfaceTexture?.setDefaultBufferSize(mWidth, mHeight)"
new_resize = "mCameraSurfaceTexture?.setDefaultBufferSize(surfaceWidth, surfaceHeight)"
original_create = "            setDefaultBufferSize(w, h)\n            setOnFrameAvailableListener(this@RenderManager)"
new_create = (
    "            // Camera producer is always source-sized. Display output is independently sized.\n"
    "            setDefaultBufferSize(surfaceWidth, surfaceHeight)\n"
    '            Logger.i(TAG, "BSG_USB_INPUT_BUFFER_GEOMETRY_108 camera=${surfaceWidth}x${surfaceHeight} display=${w}x${h}")\n'
    "            setOnFrameAvailableListener(this@RenderManager)"
)
if s.count(original_resize) != 1 or s.count(original_create) != 1:
    raise SystemExit("REFUSE unsafe patch: expected exactly one producer-resize and one producer-create anchor")
s = s.replace(original_resize, new_resize, 1).replace(original_create, new_create, 1)
if s.count("setDefaultBufferSize(surfaceWidth, surfaceHeight)") != 2:
    raise SystemExit("REFUSE unsafe patch: source dimensions must control BOTH native producer calls")
if "setDefaultBufferSize(mWidth, mHeight)" in s or "setDefaultBufferSize(w, h)" in s:
    raise SystemExit("REFUSE unsafe patch: screen-sized producer call still exists")
if "mScreenRender?.setSize(mWidth, mHeight)" not in s:
    raise SystemExit("REFUSE unsafe patch: output viewport accidentally changed")
path.write_text(s, encoding="utf-8")
print("BSG_USB_INPUT_BUFFER_GEOMETRY_108=PASS")
print("Native producer source size fixed; screen viewport and OCR unchanged.")
