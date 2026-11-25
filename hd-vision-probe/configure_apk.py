#!/usr/bin/env python3
import argparse,json
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument("--report",type=Path,required=True)
p.add_argument("--arm",choices=["detail448","hd896"],required=True)
a=p.parse_args()
r=json.loads(a.report.read_text())
assert r["status"]=="pass" and r["size"]==(448 if a.arm=="detail448" else 896)
assert len(r["sha256"])==64 and 0<r["bytes"]<3_000_000_000
root=Path("litert-npu-lab/app")
k=root/"src/main/java/com/vobich/bluestar/npulab"
def edit(path,old,new):
 s=path.read_text();assert old in s,(path,old);path.write_text(s.replace(old,new))
m=k/"MainActivity.kt";s=k/"LabService.kt"
edit(m,'const val V="0.1.11"','const val V="0.1.12"')
edit(m,'20c73b5a1e48a315974cff1853535ff5d017100b6420d5ef221b9dd1b955dd7c',r["sha256"])
edit(m,'首次使用：下载约1.70 GB LiteRT模型后导入，旧GGUF不可复用','首次使用：导入同一实验产物中的新模型，旧模型不可复用')
edit(m,'LiteRT 0.20.0；紧贴裁剪、按文字行分区及状态对照。固定448模型未更换；主文字区可能漏掉其他区域。对照不朗读。',f'LiteRT 0.20.0；底层 {r["size"]}×{r["size"]} / 256视觉Token；请先测试原图识别。主文字区可能漏掉其他区域。对照不朗读。')
edit(m,'        button("下载指定模型（浏览器）"){runCatching{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(URL)))}.onFailure{status.text="请从发布说明打开模型链接"}}\n','')
edit(m,'append("v=$V phase=model_verified bytes=$bytes")',f'append("v=$V phase=model_verified bytes=$bytes vision_size={r["size"]} vision_tokens=256")')
edit(m,'bytes<=2_000_000_000L',f'bytes<={r["bytes"]}L')
edit(s,'1698202736L',str(r["bytes"])+"L")
edit(s,'runtime=0.20.0 phase=loaded',f'runtime=0.20.0 vision_size={r["size"]} vision_tokens=256 phase=loaded')
edit(root/"build.gradle.kts","npulab.detail","npulab."+a.arm)
edit(root/"build.gradle.kts","versionCode = 8","versionCode = 9")
edit(root/"build.gradle.kts",'versionName = "0.1.11"','versionName = "0.1.12"')
edit(root/"src/main/AndroidManifest.xml",'蓝星 OCR 精细识别实验','蓝星 OCR '+str(r["size"])+'-256实验')
