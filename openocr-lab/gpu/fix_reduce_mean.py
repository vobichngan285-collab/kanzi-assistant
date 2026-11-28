"""Pinned MNN OpenCL ReduceMean fix. FP32 accumulators only for floating mean
in a low precision session. Preserve input/output storage and reduction order.
Regenerate only the affected embedded kernel and its program digest.
"""
from pathlib import Path
import hashlib, json, math, re, shutil, struct, subprocess, sys, tempfile
root=Path("mnn-src").resolve()
cldir=root/"source/backend/opencl/execution/cl"
kernel=cldir/"reduction_buf.cl"
embedded=cldir/"reduction_buf_mnn_cl.cpp"
header=cldir/"opencl_source_map.hpp"
executor=root/"source/backend/opencl/execution/buffer/ReductionBufExecution.cpp"
original=kernel.read_text()
old_header=header.read_text()
def replace(s,old,new,n=1):
    assert s.count(old)==n,(old,s.count(old),n)
    return s.replace(old,new)
def generate(source):
    with tempfile.TemporaryDirectory() as tmp:
        d=Path(tmp)
        (d/"reduction_buf.cl").write_text(source)
        subprocess.run([sys.executable,str(cldir/"opencl_codegen.py"),str(d)],cwd=d,check=True)
        h=(d/"opencl_source_map.hpp").read_text()
        digest=re.search(r'\{ "reduction_buf", "([0-9a-f]{32})" \}',h).group(1)
        return (d/"reduction_buf_mnn_cl.cpp").read_text(),digest
old_generated,old_md5=generate(original)
assert old_generated==embedded.read_text(),"pinned generated kernel mismatch"
assert old_md5=="c16506adcebf7760a1a3c96ce0d386ee"
s=replace(original,"__kernel void reduct_buf(",'''// The original INPUT_TYPE is half in both low-precision storage modes.
// Promote only floating ReduceMean accumulation; cast back once after division.
#ifdef OCR_MEAN_FP32_ACCUM
#define OCR_ACCUM float
#define OCR_ACCUM4 float4
#define OCR_LOAD4(v) convert_float4(v)
#else
#define OCR_ACCUM INPUT_TYPE
#define OCR_ACCUM4 INPUT_TYPE4
#define OCR_LOAD4(v) (v)
#endif

__kernel void reduct_buf(''')
s=replace(s,"INPUT_TYPE out = (INPUT_TYPE)VALUE;","OCR_ACCUM out = (OCR_ACCUM)VALUE;")
s=replace(s,"INPUT_TYPE local sum_mnn","OCR_ACCUM local sum_mnn")
s=replace(s,"INPUT_TYPE in = (INPUT_TYPE)input","OCR_ACCUM in = (OCR_ACCUM)input",2)
s=replace(s,"INPUT_TYPE4 out = (INPUT_TYPE4)VALUE;","OCR_ACCUM4 out = (OCR_ACCUM4)VALUE;")
s=replace(s,"INPUT_TYPE4 local sum_mnn","OCR_ACCUM4 local sum_mnn")
s=replace(s,"INPUT_TYPE4 in = vload4(0, input + offset + i * inside);","OCR_ACCUM4 in = OCR_LOAD4(vload4(0, input + offset + i * inside));",2)
s=replace(s,"out = out / (INPUT_TYPE4)dim;","out = out / (OCR_ACCUM4)dim;")
# Scalar division uses float accumulator, so dim is converted to float implicitly.
# Final scalar cast and vector CONVERT_OUTPUT4 are the original storage conversion.
x=executor.read_text()
x=replace(x,'    std::set<std::string> buildOptions = mBuildOptions;', '''    std::set<std::string> buildOptions = mBuildOptions;
    if (mBuildOptions.count("-DGET_AVG") &&
        input->getType().code == halide_type_float &&
        output->getType().code == halide_type_float &&
        openCLBackend->getPrecision() != BackendConfig::Precision_High) {
        buildOptions.emplace("-DOCR_MEAN_FP32_ACCUM");
    }''')
new_generated,new_md5=generate(s)
assert new_md5!=old_md5
assert "OCR_ACCUM float" in new_generated and "OCR_LOAD4(v) convert_float4(v)" in new_generated
new_header=replace(old_header,'"'+old_md5+'"','"'+new_md5+'"')
# Compile all scalar/vector branches, including original high/int paths, using Clang's OpenCL frontend.
clang=shutil.which("clang")
assert clang,"clang required for kernel syntax gate"
with tempfile.TemporaryDirectory() as tmp:
    candidate=Path(tmp)/"reduction_buf.cl";candidate.write_text(s)
    cases=0
    for local in (1,4,32,256):
        for dtype,vec,conv,mean,promote in [
            ("half","half4","convert_half4",True,True),
            ("float","float4","convert_float4",True,False),
            ("int","int4","convert_int4",True,False),
            ("half","half4","convert_half4",False,False),
            ("float","float4","convert_float4",False,False),
        ]:
            defs=["-DINPUT_TYPE="+dtype,"-DINPUT_TYPE4="+vec,"-DOUTPUT_TYPE="+dtype,"-DCONVERT_OUTPUT4="+conv,"-DREDUCT_LOCAL_SIZE="+str(local)]
            if dtype=="half":defs.append("-DMNN_SUPPORT_FP16")
            if mean:defs+=["-DGET_AVG","-DVALUE=0","-DOPERATE(a,b)=(a+b)"]
            else:defs+=["-DVALUE=-FLT_MAX","-DOPERATE(a,b)=max(a,b)"]
            if promote:defs.append("-DOCR_MEAN_FP32_ACCUM")
            subprocess.run([clang,"-x","cl","-cl-std=CL1.2","-fsyntax-only",*defs,str(candidate)],check=True)
            cases+=1
# Host arithmetic demonstration, not a GPU test or the captured camera tensor.
def half(x):
    try:return struct.unpack("e",struct.pack("e",x))[0]
    except OverflowError:return math.copysign(math.inf,x)
def reduce_tree(values,local,cast):
    if local<=4:
        total=cast(0)
        for value in values:total=cast(total+value)
    else:
        sums=[]
        for i in range(local):
            a=cast(0)
            for value in values[i::local]:a=cast(a+value)
            sums.append(a)
        stride=local//2
        while stride:
            for i in range(stride):sums[i]=cast(sums[i]+sums[i+stride])
            stride//=2
        total=sums[0]
    return half(total/len(values))
checks=[]
for value in (64.,-64.,1.):
    for local in (1,4,32,256):
        vals=[half(value)]*1272
        old=reduce_tree(vals,local,half)
        new=reduce_tree(vals,local,float)
        assert new==value
        assert math.isinf(old) if abs(value)==64 else math.isfinite(old)
        checks.append({"dim":1272,"input":value,"local":local,"old":str(old),"fixed":new})
kernel.write_text(s);embedded.write_text(new_generated);header.write_text(new_header);executor.write_text(x)
report={"scope":"host arithmetic and OpenCL syntax only; real Mali GPU validation pending","old_program_md5":old_md5,"new_program_md5":new_md5,"kernel_sha256":hashlib.sha256(s.encode()).hexdigest(),"syntax_cases":cases,"host_checks":checks}
Path("mean-fix-report.json").write_text(json.dumps(report,indent=2)+"\n")
print("MEAN_FP32_ACCUM_PATCH_VERIFIED",new_md5,"syntax_cases",cases,"host_checks",len(checks))
