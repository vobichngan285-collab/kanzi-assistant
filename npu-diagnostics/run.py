"""Reproduce the exact MT6991 graph failure, retaining native plugin stderr.
Compare to the official LiteRT tutorial's selfie segmentation reference graph.
No device claims: compile only, no APK, no model files in report.
"""
import json, os, pathlib, subprocess, sys, tempfile
root=pathlib.Path.cwd()
report=root/"npu-report"
report.mkdir(exist_ok=True)
results=[]
control_code = """from ai_edge_litert.aot import aot_compile
from ai_edge_litert.aot.vendors.mediatek import target
c=aot_compile.aot_compile('control.tflite',target=[target.Target(target.SocModel.MT6991)],keep_going=False)
print(c.compilation_report(),flush=True)
"""
for name,cmd in [("vision",[sys.executable,"direct-npu-probe/npu_aot.py"]),("control",[sys.executable,"-c",control_code])]:
    with tempfile.TemporaryDirectory(prefix="mt6991-"+name+"-") as td:
        env=dict(os.environ,TMPDIR=td)
        r=subprocess.run(cmd,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,errors="replace",timeout=1200)
        (report/(name+"-stdout.txt")).write_text(r.stdout)
        print(name,"exit",r.returncode,flush=True)
        print(r.stdout[-24000:],flush=True)
        errors=[]
        for i,p in enumerate(sorted(pathlib.Path(td).rglob("*.error"))):
            data=p.read_text(errors="replace")
            filename=f"{name}-plugin-{i}.txt"
            (report/filename).write_text(data)
            errors.append(filename)
            print(filename,data[-24000:],flush=True)
        results.append(dict(graph=name,exit_code=r.returncode,plugin_error_files=errors))
        (report/"diagnostic-results.json").write_text(json.dumps(results,indent=2))
# Keep both results; do not mask model compilation failure.
assert all(r["exit_code"]==0 for r in results),"one or more AOT graphs failed; inspect saved plugin logs"
