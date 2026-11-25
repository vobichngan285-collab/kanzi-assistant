#!/usr/bin/env bash
set -euo pipefail
curl --fail --location --retry 4 --retry-all-errors --connect-timeout 30 --max-time 1200 --output vision.litertlm https://huggingface.co/litert-community/MiniCPM-V-4.7-1B/resolve/main/MiniCPM-V-4.7-1B-VL_int8vis.litertlm
echo '20c73b5a1e48a315974cff1853535ff5d017100b6420d5ef221b9dd1b955dd7c  vision.litertlm' | sha256sum -c -
unpack-env/bin/litert-lm unpack vision.litertlm --output-dir unpacked
test -s unpacked/model.toml
find unpacked -type f -printf '%f %s\n' | tee npu-report/sections.txt
