"""Replace JNI and add its exact pinned upstream dependency; preserve every other original entry."""
import copy, pathlib, sys, zipfile, subprocess, hashlib, re, xml.etree.ElementTree as ET, shutil
source, binary, pom, dest=map(pathlib.Path,sys.argv[1:5])
provider=pathlib.Path(sys.argv[5])
provider_key="jni/arm64-v8a/libGemmaModelConstraintProvider.so"
provider_data=provider.read_bytes()
assert hashlib.sha256(provider_data).hexdigest()=="c7c7188b831ae75fcc7ec6fe613118365b311995805423a4ca265372f07f4783"
dest.mkdir(parents=True,exist_ok=True)
key="jni/arm64-v8a/liblitertlm_jni.so"
out=dest/"litertlm-android-0.18.1.aar"
data=binary.read_bytes()
assert data[:4]==b"\x7fELF" and b"BLUESTAR_VISION_HYBRID_014" in data
def command(*args):return subprocess.check_output(args,text=True)
def exports(p):
    return set(re.findall(r"\bJava_com_google_ai_edge_litertlm_\w+",command("nm","-D","--defined-only",str(p))))
def needed(p):
    return set(re.findall(r"Shared library: \[([^\]]+)\]",command("readelf","-d",str(p))))
def machine(p):return "AArch64" in command("readelf","-h",str(p))
with zipfile.ZipFile(source) as z:
    assert z.namelist().count(key)==1
    orig=dest/"original-jni.check"
    orig.write_bytes(z.read(key))
    assert machine(orig) and machine(binary)
    a,b=exports(orig),exports(binary)
    print("JNI exports",len(a),len(b),"missing",sorted(a-b))
    assert a and a<=b
    old_deps,new_deps=needed(orig),needed(binary)
    print("original NEEDED",old_deps,"patched NEEDED",new_deps)
    packaged={x.split("/")[-1] for x in z.namelist() if x.startswith("jni/arm64-v8a/")}
    system={"libc.so","libm.so","libdl.so","liblog.so","libandroid.so","libz.so","libEGL.so","libGLESv2.so","libGLESv3.so","libjnigraphics.so","libnativewindow.so"}
    assert machine(provider) and provider_data[:4]==b"\x7fELF"
    assert provider.name in new_deps and provider_key not in z.namelist()
    assert new_deps <= packaged|system|{provider.name}, new_deps-(packaged|system|{provider.name})
    provider_deps=needed(provider)
    print("pinned provider NEEDED",provider_deps)
    assert provider_deps <= packaged|system|{provider.name}, provider_deps-(packaged|system|{provider.name})
    orig.unlink()
    with zipfile.ZipFile(out,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=1) as o:
        for i in z.infolist():o.writestr(copy.copy(i),data if i.filename==key else z.read(i.filename))
        o.writestr(provider_key,provider_data)
    with zipfile.ZipFile(out) as o:
        assert o.read(key)==data and o.read(provider_key)==provider_data
        for i in z.infolist():
            if i.filename!=key:assert hashlib.sha256(z.read(i.filename)).digest()==hashlib.sha256(o.read(i.filename)).digest()
        assert o.testzip() is None
        assert set(o.namelist())==set(z.namelist())|{provider_key}
# Preserve POM transitives. Remove Gradle metadata hint because no .module is provided.
p=pom.read_text().replace("<!-- do_not_remove: published-with-gradle-metadata -->","")
ET.fromstring(p)
(dest/"litertlm-android-0.18.1.pom").write_text(p)
(dest/"hybrid-jni.sha256").write_text(hashlib.sha256(data).hexdigest()+"\n")
print("repacked AAR and verified all original non-JNI entries unchanged; added pinned provider",hashlib.sha256(data).hexdigest())
