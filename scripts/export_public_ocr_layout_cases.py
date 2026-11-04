#!/usr/bin/env python3
"""Read the openly licensed Apache-2.0 sdcb/simdpaddleocr-dataset-v1.

Requires the actual 16.3MB Hugging Face parquet downloaded in CI.
Verifies embedded JPEG SHA256 against published GT, then streams *only*
ground-truth layout/text into Java tests. No user images or private files.
This is a ground-truth layout policy experiment, NOT model OCR accuracy.
"""
from pathlib import Path
import sys
import base64
import hashlib
import pyarrow.parquet as pq

def main():
    source=Path(sys.argv[1])
    rows=pq.read_table(source).to_pylist()
    if len(rows)!=100: raise SystemExit(f"expected 100 public images, got {len(rows)}")
    images=lines=vertical=0
    for record in rows:
        image=record["image"]
        pixels=image["bytes"] if isinstance(image,dict) else None
        if not pixels or hashlib.sha256(pixels).hexdigest()!=record["sha256"]:
            raise SystemExit(f"public source JPEG mismatch on {record['image_id']}")
        images+=1
        items=record["lines"]
        print("S\t{}\t{}\t{}".format(record["image_id"],record["width"],record["height"]))
        for item in items:
            coords=item["bbox"]
            if len(coords)!=4:raise SystemExit("invalid bbox")
            orientation=int(item["orientation_degrees"])
            vertical+=int(abs(orientation)==90)
            text=base64.b64encode(item["text"].encode("utf-8")).decode("ascii")
            print("B\t{}\t{}\t{}\t{}\t{}\t{}".format(*map(int,coords),orientation,text))
            lines+=1
        print("E")
    print(f"PUBLIC_LAYOUT_GROUND_TRUTH_VERIFIED images={images} lines={lines} rotated_lines={vertical}",
          file=sys.stderr)

if __name__=="__main__":
    main()
