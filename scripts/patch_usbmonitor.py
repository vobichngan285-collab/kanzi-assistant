#!/usr/bin/env python3
"""Patch AUSBC 3.6.0 USB permission PendingIntent for Android 14+."""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_usbmonitor.py PATH_TO_USBMonitor.java")

path = Path(sys.argv[1])
source = path.read_text(encoding="utf-8")
old = """\t\t\tif (Build.VERSION.SDK_INT >= 34) {
\t\t\t\t// Android 14+ requires IMMUTABLE flag for security
\t\t\t\tXLogWrapper.i(TAG, "register: Android 14+ detected, using FLAG_IMMUTABLE");
\t\t\t\tflags |= PendingIntent.FLAG_IMMUTABLE;
\t\t\t} else if (Build.VERSION.SDK_INT >= 31) {
\t\t\t\t// Android 12+ requires explicit package name and MUTABLE flag
\t\t\t\tXLogWrapper.i(TAG, "register: Android 12+ detected, using FLAG_MUTABLE");
\t\t\t\tflags |= PendingIntent.FLAG_MUTABLE;
\t\t\t}
"""
new = """\t\t\tif (Build.VERSION.SDK_INT >= 31) {
\t\t\t\t// UsbManager fills EXTRA_DEVICE and EXTRA_PERMISSION_GRANTED into
\t\t\t\t// this PendingIntent. It MUST be mutable. The action is package-
\t\t\t\t// scoped above, which satisfies Android 14+ mutable-intent rules.
\t\t\t\tXLogWrapper.i(TAG, "register: Android 12+ detected, using package-scoped FLAG_MUTABLE for USB permission result");
\t\t\t\tflags |= PendingIntent.FLAG_MUTABLE;
\t\t\t}
"""
if old not in source:
    if new in source:
        print("USBMonitor already contains the expected mutable permission fix.")
        raise SystemExit(0)
    raise SystemExit("ERROR: expected AUSBC 3.6.0 PendingIntent block not found; refusing an unsafe patch.")
patched = source.replace(old, new, 1)
if "intent.setPackage(context.getPackageName());" not in patched:
    raise SystemExit("ERROR: USB permission PendingIntent is not package-scoped.")
if "flags |= PendingIntent.FLAG_IMMUTABLE;" in patched:
    raise SystemExit("ERROR: immutable USB permission PendingIntent remains.")
path.write_text(patched, encoding="utf-8")
print("Patched USBMonitor: Android 12+ USB permission PendingIntent is mutable and package-scoped.")
