#!/usr/bin/env python3
"""One-time repair of the original Blue Star Guide launcher WebP.

The first Dev 1.0 binary had a 1-character base64 transcription error.
The WebP still had its proper RIFF header, but decoders failed near byte 4114.
This script accepts ONLY that exact broken blob or the original clean blob;
it never silently rewrites a different image. Build and post-build APK checks
must both confirm the known-good SHA-256 and actual image decoding.
"""
from pathlib import Path
import hashlib

p = Path("app/src/main/res/drawable-nodpi/bluestar_icon.webp")
b = bytearray(p.read_bytes())
def sha(x): return hashlib.sha256(x).hexdigest()
BROKEN = "050d7b158516af1bda1c5adcd8de0f4edd8f4bfe041b85e628098fe389ad0ad6"
GOOD = "331e312e838a922292d099809e608c511046b84034b3630b11e0827414b41e96"
if sha(b) == BROKEN:
    assert len(b) == 4492 and b[4114:4116] == bytes.fromhex("9b15")
    b[4114:4116] = bytes.fromhex("9cd5")
    assert sha(b) == GOOD
    p.write_bytes(b)
    print("ONE_TIME_GLOBE_RESOURCE_TRANSCRIPTION_ERROR_REPAIRED")
elif sha(b) != GOOD:
    raise SystemExit("Globe icon differs from user-confirmed image; refuse build")
assert p.read_bytes()[:4] == b"RIFF"
assert sha(p.read_bytes()) == GOOD
# This imports Pillow, installed in the CI before the Gradle build.
from PIL import Image
with Image.open(p) as im:
    im.load()
    assert im.size == (144, 144) and im.format == "WEBP"
print("BLUE_STAR_REAL_IMAGE_DECODE=PASS", p.stat().st_size, GOOD)
