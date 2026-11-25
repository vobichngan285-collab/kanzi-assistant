"""Verify actual encoder/adapter signatures; correct only Gemma4 metadata in a verified local copy."""
import hashlib, os, sys, json
from pathlib import Path
import tflite
import litert_lm
from litert_lm_builder import litertlm_peek as peek
from google.protobuf.internal.encoder import _VarintBytes
try:
    from litert_lm_builder.runtime.proto import llm_metadata_pb2
except ImportError:
    from runtime.proto import llm_metadata_pb2

path=Path(sys.argv[1])
def hash_file():
    with path.open("rb") as f:
        return hashlib.file_digest(f,"sha256").hexdigest()
assert path.stat().st_size==1708468392
assert hash_file()=="4f52793970e74cae45b971e447cd5834dac6b9a70b687a5fe0f968c6e1c26b9e"
with litert_lm.ModelInfo(path) as m:
    assert m.is_llm_model and m.input_modalities.vision
    assert m.max_vision_token_budget==280 and 1120 in (m.vision_signature_selection or [])
header=peek.read_litertlm_header(str(path),sys.stdout)
sections=header.SectionMetadata()
metadata=[]
encoders=[]; adapters=[]
with path.open("rb") as f:
    for i in range(sections.ObjectsLength()):
        s=sections.Objects(i)
        typ=peek.get_model_type(s) or ""
        begin,end=s.BeginOffset(),s.EndOffset()
        assert 0 <= begin < end <= path.stat().st_size
        print("section",i,typ,s.DataType(),begin,end,flush=True)
        if s.DataType()==peek.schema.AnySectionDataType.LlmMetadataProto:
            f.seek(begin);metadata.append((begin,f.read(end-begin)))
        if s.DataType()!=peek.schema.AnySectionDataType.TFLiteModel or not ("vision_encoder" in typ or "vision_adapter" in typ):
            continue
        f.seek(begin);data=f.read(end-begin)
        graph=tflite.Model.GetRootAsModel(data,0)
        signatures=[]
        for j in range(graph.SignatureDefsLength()):
            sig=graph.SignatureDefs(j); key=sig.SignatureKey().decode()
            sub=graph.Subgraphs(sig.SubgraphIndex())
            shapes=[]
            for k in range(sig.InputsLength()):
                tensor=sub.Tensors(sig.Inputs(k).TensorIndex())
                shapes.append([tensor.Shape(x) for x in range(tensor.ShapeLength())])
            print("graph",typ,key,shapes,flush=True)
            signatures.append((key,shapes))
        if "vision_encoder" in typ:
            encoders.extend(signatures)
        else:
            adapters.extend(signatures)
assert any("1120" in key or any(10080 in shape for shape in shapes) for key,shapes in encoders),encoders
assert any("1120" in key or any(1120 in shape for shape in shapes) for key,shapes in adapters),adapters
assert len(metadata)==1,metadata
begin,raw=metadata[0]
before=llm_metadata_pb2.LlmMetadata.FromString(raw)
g=before.llm_model_type.gemma4
print("gemma4 before",g.max_num_patches,g.pooling_kernel_size,flush=True)
assert g.max_num_patches==2520 and g.pooling_kernel_size in (0,3)
desired=llm_metadata_pb2.LlmMetadata()
desired.CopyFrom(before)
desired.llm_model_type.gemma4.max_num_patches=10080
field=g.DESCRIPTOR.fields_by_name["max_num_patches"].number
tag=_VarintBytes(field<<3)
old=tag+_VarintBytes(2520)
new=tag+_VarintBytes(10080)
assert len(old)==len(new)
hits=[]
for offset in range(len(raw)-len(old)+1):
    if raw[offset:offset+len(old)]!=old:continue
    candidate=raw[:offset]+new+raw[offset+len(old):]
    try:
        if llm_metadata_pb2.LlmMetadata.FromString(candidate)==desired:hits.append(offset)
    except Exception:pass
assert len(hits)==1,hits
offset=begin+hits[0]
with path.open("r+b") as f:
    f.seek(offset);assert f.read(len(old))==old
    f.seek(offset);f.write(new);f.flush();os.fsync(f.fileno())
with litert_lm.ModelInfo(path) as m:
    print("AFTER",m.max_vision_token_budget,m.vision_signature_selection,flush=True)
    assert m.max_vision_token_budget==1120 and 1120 in (m.vision_signature_selection or [])
digest=hash_file()
text=f"offset={offset}\nbefore={old.hex()}\nafter={new.hex()}\nsha256={digest}\n"
Path("patch.properties").write_text(text)
print(text,flush=True)
