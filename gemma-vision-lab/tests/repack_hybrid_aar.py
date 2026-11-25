"""Replace only the JNI library in same-version Maven AAR, retain official Kotlin/GPU libs."""
import pathlib, sys, zipfile, subprocess, hashlib, re, xml.etree.ElementTree as ET, shutil
source, binary, pom, dest=map(pathlib.Path,sys.argv[1:5])
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
    # Reject newly required libraries not already shipped or public Android system libs.
    assert new_deps-old_deps <= packaged|{"libc.so","libm.so","libdl.so","liblog.so","libandroid.so","libz.so","libEGL.so","libGLESv2.so","libjnigraphics.so","libnativewindow.so"}
    orig.unlink()
    with zipfile.ZipFile(out,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=1) as o:
        for i in z.infolist():o.writestr(i,data if i.filename==key else z.read(i.filename))
    with zipfile.ZipFile(out) as o:
        assert o.read(key)==data
        for i in z.infolist():
            if i.filename!=key:assert hashlib.sha256(z.read(i)).digest()==hashlib.sha256(o.read(i)).digest()
# Preserve POM transitives. Remove Gradle metadata hint because no .module is provided.
p=pom.read_text().replace("<!-- do_not_remove: published-with-gradle-metadata -->","")
ET.fromstring(p)
(dest/"litertlm-android-0.18.1.pom").write_text(p)
(dest/"hybrid-jni.sha256").write_text(hashlib.sha256(data).hexdigest()+"\n")
print("repacked AAR and verified all non-JNI entries unchanged",hashlib.sha256(data).hexdigest())
