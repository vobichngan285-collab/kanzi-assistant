"""One bounded pass: SDK loader verification, official relative path repair,
control AOT, and only then the real MiniCPM graph. No model edits or device claims."""
import os, sys, pathlib, subprocess, tempfile, json, re, shutil, hashlib
import ai_edge_litert_sdk_mediatek as sdk
root=pathlib.Path.cwd()
report=root/"npu-report"; report.mkdir(exist_ok=True)
package=pathlib.Path(sdk.__file__).resolve().parent
def cmd(args,env=None,timeout=120):
    r=subprocess.run(args,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,errors="replace",timeout=timeout)
    return r.returncode,r.stdout
def save(name,s):
    (report/name).write_text(s)
    print(name+"\n"+s[-24000:],flush=True)
expected=sdk.path_to_sdk_libs("v8")
paths=[p for p in package.rglob("libneuron_adapter.so") if "/host/lib/" in str(p) and re.search(r"/v8[^/]*/",str(p))]
if expected and (expected/"libneuron_adapter.so").is_file():
    lib=(expected/"libneuron_adapter.so")
elif len(paths)==1:
    lib=paths[0]
else:
    save("sdk-paths.txt",f"SDK expected: {expected}\nPackage: {package}\nCandidates: {paths}\n")
    # The official installer may silently ignore a download failure. Do not
    # substitute an arbitrary library or architecture. Stop with useful data.
    raise SystemExit("No unique official NeuroPilot v8 host library installed")
lib=lib.resolve()
dirs=[str(lib.parent)]+[str(p.parent) for p in lib.parent.rglob("*.so*") if p.is_file()]
dirs=list(dict.fromkeys(dirs))
env=dict(os.environ,LD_LIBRARY_PATH=os.pathsep.join(dirs+([os.environ["LD_LIBRARY_PATH"]] if os.environ.get("LD_LIBRARY_PATH") else [])))
# Native Linux loader in upstream tries third_party/neuro_pilot/v8_latest/host/lib
# when shared_library_dir is null. Provide an alias to the same verified SDK.
for name in ("v8_latest","v8_0_10"):
    alias=root/"third_party/neuro_pilot"/name/"host/lib"
    alias.parent.mkdir(parents=True,exist_ok=True)
    if not alias.exists():
        alias.symlink_to(lib.parent,target_is_directory=True)
    assert alias.resolve()==lib.parent
rc,ldd=cmd(["ldd",str(lib)],env)
_,arch=cmd(["file",str(lib)],env)
save("sdk-loader.txt",f"SDK path: {expected}\nResolved: {lib}\n{arch}\nldd exit={rc}\n{ldd}")
# Verify actual dlopen, not just package installation. This may reveal a missing
# dependency or incompatible GLIBC rather than a model-related problem.
rc,dl=cmd([sys.executable,"-c","import ctypes,sys; ctypes.CDLL(sys.argv[1]); print('DL_OPEN_OK')",str(lib)],env)
save("dlopen.txt",dl)
if rc:
    raise SystemExit("SDK library cannot be dlopened; see ldd and dlopen reports")
control_code="""from ai_edge_litert.aot import aot_compile
from ai_edge_litert.aot.vendors.mediatek import target
c=aot_compile.aot_compile('control.tflite',target=[target.Target(target.SocModel.MT6991)],keep_going=False)
print(c.compilation_report(),flush=True)
"""
def run_graph(name,args):
    with tempfile.TemporaryDirectory(prefix="mt6991-"+name+"-") as td:
        rc,stdout=cmd(args,dict(env,TMPDIR=td),timeout=1200)
        save(name+"-stdout.txt",stdout)
        errors=[]
        for i,p in enumerate(sorted(pathlib.Path(td).rglob("*.error"))):
            data=p.read_text(errors="replace")
            save(f"{name}-plugin-{i}.txt",data);errors.append(data)
        joined=stdout+"\n"+"\n".join(errors)
        return {"graph":name,"exit_code":rc,"loader_failure":"Failed to load NeuronAdapter shared library" in joined,"error_files":len(errors)}
results=[]
control=run_graph("control",[sys.executable,"-c",control_code])
results.append(control)
(report/"results.json").write_text(json.dumps(results,indent=2))
if control["loader_failure"]:
    raise SystemExit("Plugin still cannot load SDK despite successful direct dlopen; avoid repeating large model compilation")
# If control fails for another reason, still test the actual vision graph.
# Model bundle is only downloaded once the environment reaches this point.
subprocess.run(["bash","npu-recovery/fetch_vision.sh"],check=True,timeout=1800)
vision=run_graph("vision",[sys.executable,"direct-npu-probe/npu_aot.py"])
results.append(vision)
(report/"results.json").write_text(json.dumps(results,indent=2))
assert vision["exit_code"]==0,"Vision compile failed; see actual native report"
