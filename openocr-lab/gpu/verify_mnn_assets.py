import hashlib,json
from pathlib import Path
a=Path("openocr-lab/app/src/main/assets")
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
for name,src in [("rep","rep.onnx"),("v2","openocrv2_ctc.onnx")]:
    d=Path("mnn-artifacts")/name
    report=d/"report.json";r=json.loads(report.read_text())
    assert r["status"]=="cpu_parity_failed" and r["model"]==name
    assert r["source_sha256"]==sha(a/src)
    assert len(r["tests"])==(8 if name=="rep" else 13)
    assert all(t["ctc_match"] and t["argmax_step_mismatches"]==0 for t in r["tests"])
    assert any(not t["allclose"] for t in r["tests"])
    f=d/(name+".mnn");assert sha(f)==r["mnn_sha256"]
    (a/f.name).write_bytes(f.read_bytes())
    print(name,r["mnn_sha256"],"strict parity FAILED; experimental use only")
