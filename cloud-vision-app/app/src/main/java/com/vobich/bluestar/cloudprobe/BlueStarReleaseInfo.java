package com.vobich.bluestar.cloudprobe;

/** Update DATE and CHANGES on EVERY release. Version itself is read from installed package. */
final class BlueStarReleaseInfo {
    private BlueStarReleaseInfo(){}
    static final String DATE="2026-11-24";
    static final String VERSION="1.0.96-oem-battery";
    static final String[] CHANGES={
        "根据原厂SJCAM X的3019电量档位显示四格至低电量、充电状态，不再估算百分比。",
        "首页相机电量图标放大，仅电量格使用绿、黄、红色，无额外文字。",
        "保留原无障碍电量播报、60秒读取周期及断线未知处理。",
        "保留v1.0.95自动删除原图、AI寻找、相机和其他功能。"
    };
}
