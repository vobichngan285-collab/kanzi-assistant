#!/usr/bin/env python3
"""Inject isolated row repartition into official SDK after checkout, before compile."""
from pathlib import Path
import shutil
sdk=Path("third_party/PaddleOCR/deploy/ppocr-android/ppocr-sdk")
source=Path("scripts/crossrow_sdk/CrossRowFirstPass.kt")
target=sdk/"src/main/java/com/paddle/ocr/postprocess/CrossRowFirstPass.kt"
engine=sdk/"src/main/java/com/paddle/ocr/engine/OCREngine.kt"
assert source.is_file() and engine.is_file(),"SDK source checkout missing"
shutil.copyfile(source,target)
text=engine.read_text(encoding="utf-8")
needle="import com.paddle.ocr.postprocess.QuadTextCrop\n"
assert text.count(needle)==1
text=text.replace(needle,needle+"import com.paddle.ocr.postprocess.CrossRowFirstPass\n",1)
needle="        val boxes = detResult.boxes\n"
assert text.count(needle)==1,"upstream first pass changed"
text=text.replace(needle,"        val boxes = CrossRowFirstPass.repartition(detResult.boxes, srcMat.cols(), srcMat.rows())\n",1)
engine.write_text(text,encoding="utf-8")
print("CROSSROW_FIRST_PASS_SDK_PATCH=PASS; one DET, no extra REC",flush=True)
