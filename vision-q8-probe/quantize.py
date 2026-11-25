"""Conservative, same-source Q8_0 vision transformer matrix probe.
Only allowlisted attention and MLP weight matrices; keep embeddings, mergers,
normalization, biases, projection and all architecture parameters untouched.
"""
import argparse, hashlib, json, pathlib, re, sys
import numpy as np
import gguf

p=argparse.ArgumentParser()
p.add_argument("source",type=pathlib.Path)
p.add_argument("output",type=pathlib.Path)
p.add_argument("report",type=pathlib.Path)
args=p.parse_args()
assert args.source.resolve()!=args.output.resolve()
assert not args.output.exists()
r=gguf.GGUFReader(args.source,"r")
assert r.endianess == gguf.GGUFEndian.LITTLE
def field(reader,k):
    f=reader.get_field(k)
    return None if f is None else f.contents()
assert field(r,"general.architecture")=="clip"
w=gguf.GGUFWriter(args.output,arch="clip",endianess=r.endianess,use_temp_file=True)
alignment=field(r,"general.alignment")
if alignment is not None:
    w.data_alignment=alignment
changed_meta={"general.file_type","general.quantization_version"}
for f in r.fields.values():
    if f.name.startswith("GGUF.") or f.name=="general.architecture" or f.name in changed_meta:
        continue
    typ=f.types[0]
    w.add_key_value(f.name,f.contents(),typ,sub_type=f.types[-1] if typ==gguf.GGUFValueType.ARRAY else None)
w.add_uint32("general.file_type",int(gguf.LlamaFileType.MOSTLY_Q8_0))
w.add_uint32("general.quantization_version",gguf.GGML_QUANT_VERSION)
allow=re.compile(r"v\.blk\.\d+\.(?:attn_q|attn_k|attn_v|attn_qkv|attn_out|ffn_up|ffn_down|ffn_gate)\.weight")
converted=[]
qtype=gguf.GGMLQuantizationType.Q8_0
for t in r.tensors:
    if allow.fullmatch(t.name) and t.data.ndim==2 and t.tensor_type in (gguf.GGMLQuantizationType.F16,gguf.GGMLQuantizationType.F32) and t.data.shape[-1]%32==0:
        f=t.data.astype(np.float32)
        assert np.isfinite(f).all(),t.name
        q=gguf.quantize(f,qtype)
        w.add_tensor(t.name,q,raw_dtype=qtype)
        converted.append(t.name)
    else:
        w.add_tensor(t.name,t.data,raw_dtype=t.tensor_type,tensor_endianess=r.endianess)
assert len(converted)>=27, "insufficient eligible weights"
w.write_header_to_file()
w.write_kv_data_to_file()
w.write_tensors_to_file()
w.close()
out=gguf.GGUFReader(args.output,"r")
assert [x.name for x in r.tensors]==[x.name for x in out.tensors]
for f in r.fields.values():
    if not f.name.startswith("GGUF.") and f.name not in changed_meta:
        assert f.contents()==field(out,f.name),f.name
for a,b in zip(r.tensors,out.tensors):
    assert np.array_equal(a.shape,b.shape),a.name
    if a.name in converted:
        assert b.tensor_type==qtype
        assert np.isfinite(gguf.dequantize(b.data,qtype)).all()
    else:
        assert a.tensor_type==b.tensor_type and a.data.tobytes()==b.data.tobytes(),a.name
assert args.output.stat().st_size < args.source.stat().st_size
def sha(path):
    with path.open("rb") as f:return hashlib.file_digest(f,"sha256").hexdigest()
report={"source_size":args.source.stat().st_size,"source_sha256":sha(args.source),
        "q8_size":args.output.stat().st_size,"q8_sha256":sha(args.output),
        "quantized_tensors":converted,"tensor_count":len(r.tensors),
        "preserved_metadata_except":sorted(changed_meta),"validation":"tensor names/shapes, unmodified tensors bitwise equal, metadata equality, finite Q8 decode"}
args.report.write_text(json.dumps(report,indent=2))
print(json.dumps({k:v for k,v in report.items() if k!="quantized_tensors"},indent=2))
