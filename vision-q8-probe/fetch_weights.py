"""Fetch only the two files from the previously validated model revision."""
import json, pathlib, hashlib, subprocess, urllib.request
root=pathlib.Path("probe-models");root.mkdir(exist_ok=True)
repo="tc-mb/MiniCPM-V-Apps-gguf"
rev="2a3a3ddfbfc3d8e98c1070680b2fa98df88c3452"
prefix="MiniCPM-V-4.7-1B"
base="https://huggingface.co"
with urllib.request.urlopen(f"{base}/api/models/{repo}/tree/{rev}/{prefix}?recursive=true&expand=false",timeout=90) as f:
    tree=json.load(f)
names=["MiniCPM-V-4_7-1B-Q4_K_M.gguf","mmproj-model-f16.gguf"]
entries={i["path"]:i for i in tree}
total=0
for name in names:
    item=entries[prefix+"/"+name]
    target=root/name
    subprocess.run(["curl","--fail","--location","--retry","4","--retry-all-errors","--connect-timeout","30","--max-time","1800","--output",str(target),f"{base}/{repo}/resolve/{rev}/{prefix}/{name}"],check=True)
    with target.open("rb") as f:digest=hashlib.file_digest(f,"sha256").hexdigest()
    assert digest==item["lfs"]["oid"]
    assert target.stat().st_size==item["size"]==item["lfs"]["size"]
    total+=item["size"]
assert total==1780750400
