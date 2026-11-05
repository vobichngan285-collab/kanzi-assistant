#!/usr/bin/env python3
"""Instrumentation ONLY against pinned ernestp/AndroidUSBCamera 3.6.0.
No USB stream, GL lifecycle, frame buffer or OCR output behavior changes.
Swap attempt counter is explicitly NOT proof of successful presentation.
"""
from pathlib import Path
root=Path("third_party/AndroidUSBCamera/libausbc/src/main/java/com/jiangdg/ausbc")
src=Path("scripts/DiagnosticRenderHeartbeat.java")
dst=root/"render/DiagnosticRenderHeartbeat.java"
assert src.is_file() and not dst.exists()
dst.write_bytes(src.read_bytes())

p=root/"render/RenderManager.kt"
s=p.read_text(encoding="utf-8")
old="                mScreenRender?.swapBuffers(mCameraSurfaceTexture?.timestamp ?: 0)"
new=old+"\n                // Passive health counter: only GL submission attempt, not display.\n                DiagnosticRenderHeartbeat.noteSwapAttempt()"
assert s.count(old)==1,"Unexpected pinned AUSBC GL render loop, abort"
p.write_text(s.replace(old,new,1),encoding="utf-8")

p=root/"render/env/EGLEvn.kt"
s=p.read_text(encoding="utf-8")
old="        if (! EGL14.eglSwapBuffers(mEglDisplay, mEglSurface)) {\n            loggerError(\"Swap buffers\")"
new="        if (! EGL14.eglSwapBuffers(mEglDisplay, mEglSurface)) {\n            com.jiangdg.ausbc.render.DiagnosticRenderHeartbeat.noteEglError()\n            loggerError(\"Swap buffers\")"
assert s.count(old)==1,"Unexpected pinned AUSBC EGL path, abort"
p.write_text(s.replace(old,new,1),encoding="utf-8")
print("PASS: passive USB GL swap attempts & EGL failures instrumented")
