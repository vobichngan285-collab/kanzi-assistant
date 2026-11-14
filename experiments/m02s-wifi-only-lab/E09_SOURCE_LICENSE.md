# 开源参考及许可

本独立纯Wi-Fi实验 **不包含、不调用、也不发送任何蓝牙命令**。仅参考开源逆向研究所描述的原厂AP媒体列表设计、照片目录结构与读取方式：

- [sctg-development/ai-smart-glasses-e09-reverse](https://github.com/sctg-development/ai-smart-glasses-e09-reverse)，尤其是 `Sources/e09_reverse/WifiMediaManager.swift`：M02S相似OEM设备的 `/app/getfilelist` JSON格式和目录/原图GET思路；相应协议与本项目在M02S上的实测核对。
- **重要局限**：该开源项目主要通过 `0x39/0x30` BLE命令要求眼镜开启AP，**没有证明M02S可以在完全不使用蓝牙的情况下自行打开热点**；本实验的「纯Wi-Fi」只探测手机系统中已经连接的Wi-Fi，绝不假装能开启眼镜热点。
- 实际使用的 `M02sPhotoTransport.java`、`M02sPhotoListReader.java`、`M02sPhotoPolicy.java`、`M02sFreshPhotoPolicy.java`、`ProbePolicy.java` 来自同仓库先前已经在M02S实机验证的Android独立实验代码，经安全重用。没有复制上游Swift在本机运行的假象。

为保留开源来源和许可，下方附MIT License：

---

MIT License

Copyright (c) 2026 Ronan Le Meillat - SCTG Development

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
