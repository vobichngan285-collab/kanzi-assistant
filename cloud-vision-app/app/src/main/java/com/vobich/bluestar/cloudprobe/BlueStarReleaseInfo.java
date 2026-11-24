package com.vobich.bluestar.cloudprobe;

/** Update DATE and CHANGES on EVERY release. Version itself is read from installed package. */
final class BlueStarReleaseInfo {
    private BlueStarReleaseInfo(){}
    static final String DATE="2026-11-24";
    static final String VERSION="1.0.95-acquired-photo-delete";
    static final String[] CHANGES={
        "自动删除统一到C110完整取图：实体快门、手机遥控和预览高清拍照。",
        "删除不等待云端识别；AI寻找、识别失败、取消也适用。",
        "后台单张删除，保留原图完整性、同一网络与精确文件校验。",
        "保留v1.0.94 AI寻找与实体快门衔接、原有功能和设置。"
    };
}
