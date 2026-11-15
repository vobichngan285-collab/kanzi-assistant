# M02S/TK8 Wi-Fi在线高清拍照与固件可行性深度核查
日期：2026-11-15。范围：只读、静态分析、官方文档交叉核对。**没有修改眼镜、蓝星App源码、固件，也没有发BLE/OTA/刷机命令。**

## 1. 实验真实结论（不可重做已经失败的实验）
- 用户M02S在BLE上实测 `AA12/AA13/AA14`；已通过合法`0x64`查询读到项目号 **TK8**（`main/PROJECT_HANDOFF.md`历史记录），已通过`0x95`读出硬件能力掩码`0x000012AB`、bit0直播支持，但用户实机视频卡顿、不接受直播。
- v0.1.31在两次独立AP在线会话中，镜端`isImport=1`、Wi-Fi已连接、原厂HTTP列表稳定（29、30张），用户点一次官方`0x22/0x31`高质量拍照：BLE GATT写status0；随后各11秒、15次GET列表，均无新图，Wi-Fi在取证期间保持可用。说明**普通/高质量远程快门目前不能在AP导入状态下得到3200×2400新图**。第一次BLE约15秒后另行掉线，Wi-Fi更晚断，不能混淆为拍照立刻令Wi-Fi断开；第二次原厂ZERO`0x44/0x30/0x01`结束后isImport转0，设备恢复。
- 眼镜在**收到蓝牙0x39/0x30开AP命令时**就报`isImport=1`，早于Android实际连上Wi-Fi。这支持“固件主动切换ISP工作状态”的解释，而不是Android网络配置触发禁拍。仍是机制推断，不是芯片级栈追踪。
- 原厂静态APK有`ISP_TAKE_PHOTO=0`,`ISP_IMPORTING=3`状态常量；原厂手机拍照按钮根据镜端`isImport`禁用。即便修改手机按钮或强发命令，真机两轮仍无照片，因此App端单改无法证明解锁固件模式。

## 2. 直接重新复核原始私有 Star Glasses APK
- 用户上传 `70465ff3819258a6218e7871da0a23f8.apk`，包`com.starglasses.app`（284656626字节，SHA-256：`92f103b9d473d4cdf7424407ddfc851cac1700d15a06e55dfa0009ccf962ad57`）已从用户私有Library经工具读取，重新检查ZIP条目、7个DEX、classes2.dex字段、方法表和关键命令体。**APK原始二进制不上传公开GitHub，仅本报告描述非机密结构**。
- DEX明确存在`SendCommandViaBle.appGetDevicdInfo()`：Dalvik`const/16 0x55` + 参数`0x00` + `configSendCommand`。E09公开协议确认**只读GET_DEVICE_INFO 0x55**可返回`btVersion`、`ispVersion`、`deviceVersion`；本APK DEX `DeviceValueBean`恰有这3个字段。这是目前最有增量价值、尚未针对用户眼镜取证的只读查询，不需要先开AP，不需要拍照，不需要改ISP模式。**未实际发送0x55，不能捏造这副眼镜的任何版本号**。
- `SendCommandViaBle.getSupportFunction()`发`0x95`：已经用户真机读到过，**绝不重复实验**。读项目`0x64`已经获得TK8，也不必重查。
- 已知`takePhoto(I)`仅发`0x22`+已知普通/高质量参数，`setCameraMode`是`0x50` Standard/Classic拍摄风格参数，而不是`ISP_IMPORTING → ISP_TAKE_PHOTO`的安全解锁指令；`startLive`发`0x67`且已经用户实测卡顿。
- DEX包含蓝牙OTA / Wi-Fi OTA的App接口`StarOtaActivity`、`StarTOtaActivity`，以及`libjl_ota_auth.so`辅助库，但APK ZIP中**没有任何可确认适合本机TK8的镜端固件镜像**。拥有升级传输接口≠拥有可修改的固件、签名秘钥或变砖恢复方法，绝不可把试验性.bin/swu刷上去。
- 官方应用的正确对应信息优先：`com.starglasses.app` 的Google Play开发者是**东莞市成星电子科技有限公司**，支持邮箱`a1191282346@gmail.com`。这不证明他们掌握所有TK8主板的固件签名或能提供SDK，但比联系与此APK不相干的通用眼镜供应商更精确。

## 3. 平台可行性与重要“同名不同设备”陷阱
- 部分AI眼镜卖家/开发板搭配`杰理JL7018F + 全志Allwinner V821/V821L2`，全志官方V821 `sample_smartIPC_demo`公开展示**Wi-Fi RTSP + JPEG抓拍 + 文件存储**同一Smart IPC应用，存在`main_2nd_take_picture=1`配置（参考 https://docs.aw-ol.com/docs/soc/v821/solution/ipc-camera/ 和 https://docs.aw-ol.com/docs/soc/v821/solution/mpp-demo-intro/）。**说明候选平台硬件上存在并行数据管线基础**，但官方示范有些抓拍走480p的次码流，**不能类比证明3200x2400 JPEG与当前M02S的Wi-Fi AP可并行**，更不能确定用户眼镜就是V821。
- 公开另有`M02S/A02S`设备使用`AE30`和`DE5B` BLE UUID及`A02S_V1.0/WIFIA02S...`固件，不是用户`AA12/AA13/AA14`+TK8家族（例 https://github.com/pepebot-space/nisaetus/blob/main/ARCHITECTURE.md）。**禁止直接使用任何WIFIA02S_*.swu或厂商不明OTA包**。
- 非官方第三方`thetopham/manfred`报告一副**TK8/0201**设备的只读版本为BT 1.1.9、ISP 3.3.7、device 2；这是**他人的设备版本号、仅提示0x55可能可用，不是用户M02S版本号**（https://github.com/thetopham/manfred/blob/main/docs/eyevue-photos.md）。该项目仍依赖拍照状态与AP导入状态切换，未证实常连高清快门。
- 已检验的公开E09 BLE协议文档（https://github.com/sctg-development/ai-smart-glasses-e09/blob/main/REVERSE_ENGINEERING_EYEVUE_BLE_PROTOCOL.md）也没有找到已被验证的`isImport=1`且保持WiFi的原生JPEG拍照命令。因此此时不能把“手机发0x50 / 0x22”或“常驻WIFI”说成解决方案。

## 4. 技术决策：要实现Wi-Fi常连高清拍照，至少必须验证以下镜端条件
1. 镜端是否允许AP/HTTP server正常运行时继续保留相机ISP/JPEG管线和物理快门事件处理；当前用户样机数据**倾向否定**，但硬件是否支持仍未知。
2. JPEG生成是否能真正达到 **3200×2400**，而不是RTSP/AA15的1600×1200或320×180；次码流抓拍也不能替代高清图。
3. 同一个Wi-Fi Network无需断开/重认证的情况下，原厂HTTP server是否能够立即列出新文件并GET它；必须做稳定列表前后差异、原图格式/尺寸验证，不误取旧图。
4. 固件是否能在保留ISP拍照的同时维持可靠低功耗Wi-Fi与BLE；如果功耗/存储带宽或镜端ISP状态机强制互斥，Android App无法消除。
5. 若只有固件修改可行，必须先有精确的主板/芯片/镜端固件版本、相符的签名构建产物及可证实的恢复流程。**缺任一项绝对不OTA试错**。

## 5. 优先级与下一步（需要用户授权，未执行）
**A：最低风险的信息闭环。** 用户已实测项目TK8、0x95支持位。下一项只需要在一份新的独立**只读**诊断App通过已订阅AA14的BLE待机环境显式单次发送OEM原有`GET_DEVICE_INFO=0x55`、参数0x00，在协议长度/校验通过时只报告**BT版本、ISP版本、设备版本**；超时只报告未知，不重复发；没有WiFi/AP/照片/HTTP/OTA操作。拿到本机版本后，再与同族TK8文档和厂家确切适配包核对。
**B：联系对应OEM软件支持。** 请东莞市成星电子科技有限公司明确：TK8设备型号/SoC、BT/ISP版本是否支持**拍照工作模式（ISP_TAKE_PHOTO）同时保持Wi-Fi AP+HTTP媒体服务**；能否提供官方SDK、独立快门/原生高清图事件接口、原厂固件镜像/校验/签名与恢复方法；如果是固件硬限制，请明确告知。
**C：只有获得厂家镜端支持证据与恢复路径后**，才在实验装置上考虑固件层面的“相机ISP不退出 + AP传输持续运行 + 本地JPEG给HTTP新图通知”；未经供应商明确提供API/源码/安全恢复手段，不向用户承诺可以单纯改Android解除限制。
**D：若B/C不可行**，保留经过多轮真实验证的v0.1.29离线快门→开AP→最新高清GET→原厂ZERO/FULL退出基线；目标≤5秒在现有硬件模式上不能保证，需要评估允许第三方快门API的其他硬件而不是重试失败路线。

**本轮仅研究，没有新实验版本和安装包，也没有改变正式Windows OCR/蓝星向导或v0.1.29~31成功版本。**
