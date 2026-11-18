# C110 RTSP 轨道解析诊断 v0.1.6（2026-11-18）

## 真机依据
- v0.1.5 的 22:49:01 和 22:49:09：3001/par1=0、2015/par1=0、554连通、OPTIONS/DESCRIBE=200、SDP flags=999，rtsp_track=-2，SETUP/PLAY/RTP=-9，宽高=0、未出现真实帧。
- SETUP=-9 是未执行，不是相机拒绝TCP。原版解析只认视频段内的非星号control，DESCRIBE缺Content-Length也会得到空SDP。

## v0.1.6 单变量修复
- DESCRIBE有Content-Length继续定长读取；没有长度时最多读16KiB，并使用短暂空闲超时。
- 首个video媒体段的control优先；没有时使用全局control；星号只回退到已知的RTSP主地址；相对或绝对URI合法归一化、默认端口554。
- 固定192.168.1.254:554并拒绝跨主机、危险URI或路径穿越。日志仅记录数字，不保存SDP、轨道URL、RTP。
- rtsp_track: 1合法、-2无控制、-3正文空、-4无video、-5控制URI非法、-6 DESCRIBE非200。
- 新增Java回归用例，测试全局/媒体控制、wildcard、缺Content-Length、默认端口、非法地址。
- 不改Media3播放、TCP SETUP/PLAY指令、已经成功的高清JPEG→蜂窝OCR/TTS、正式RC25、M02S。

## 验收
安装隔离包com.vobich.bluestar.c110track，连接C110 Wi-Fi，仅点一次C110预览，复制完整最新c110_live。实时预览仍未证明成功。
