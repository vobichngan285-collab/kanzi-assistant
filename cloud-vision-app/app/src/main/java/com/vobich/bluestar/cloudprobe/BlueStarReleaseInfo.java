package com.vobich.bluestar.cloudprobe;

/** Update DATE and CHANGES on EVERY release. Version itself is read from installed package. */
final class BlueStarReleaseInfo {
    private BlueStarReleaseInfo(){}
    static final String DATE="2026-11-24";
    static final String VERSION="1.0.94-physical-ai-handoff";
    static final String[] CHANGES={
        "修复AI寻找目标与实体快门监听冲突，不再因AI对话重建照片基线。",
        "语音指令期间持续接收相机新图，指令完成后优先处理最新照片。",
        "增加寻找目标的上传、耗时、结果、取消及失败诊断；失败时语音提示。",
        "保留原实体快门速度、自动删除安全规则、原有功能与设置。"
    };
}
