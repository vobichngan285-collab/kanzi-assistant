import onnxruntime as ort, numpy as np, pathlib, json
root=pathlib.Path('openocr-lab/app/src/main/assets')
results={}
for name in ['yolo26n-text','yolo26s-text','yolo26m-text','yolo26l-text','yolo26x-text','repvit-db']:
    opt=ort.SessionOptions();opt.intra_op_num_threads=4
    session=ort.InferenceSession(str(root/(name+'.onnx')),sess_options=opt,providers=['CPUExecutionProvider'])
    inp=session.get_inputs()[0]; shape=inp.shape
    assert len(shape)==4 and shape[1]==3, (name,shape)
    if name!='repvit-db':
        assert shape[0]==1 and isinstance(shape[2],int) and shape[2]==shape[3] and 320<=shape[2]<=1920, (name,shape)
        dims=shape
    else:
        dims=[1,3,shape[2] if isinstance(shape[2],int) else 640,shape[3] if isinstance(shape[3],int) else 960]
    out=session.run(None,{inp.name:np.zeros(dims,np.float32)})[0]
    assert np.isfinite(out).all()
    if name!='repvit-db':
        assert out.ndim==3 and out.shape[0]==1 and out.shape[2]==7,(name,out.shape)
    else:
        assert out.ndim==4 and out.shape[0]==1 and out.shape[1]>=1 and out.min()>=-0.001 and out.max()<=1.001,(name,out.shape)
    results[name]={'input':shape,'test_input':dims,'output':list(out.shape),'min':float(out.min()),'max':float(out.max())}
    print(name,results[name],flush=True)
    del session,out
pathlib.Path('arena-validation.json').write_text(json.dumps(results,indent=2))
