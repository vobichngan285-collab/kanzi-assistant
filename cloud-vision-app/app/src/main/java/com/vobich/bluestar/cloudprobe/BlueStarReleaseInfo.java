package com.vobich.bluestar.cloudprobe;

/** Update DATE and CHANGES on EVERY release. Version itself is read from installed package. */
final class BlueStarReleaseInfo {
    private BlueStarReleaseInfo(){}
    static final String DATE="2026-11-24";
    static final String VERSION="1.0.93-physical-shutter";
    static final String[] CHANGES={
        "新增运动相机实体快门自动朗读（主页前台）。",
        "新增100、200、300、500毫秒监听间隔，默认300毫秒。",
        "新增识别成功后自动删除相机单张原图（默认关闭，真机待验证）。",
        "设置底部新增关于软件，显示实际安装版本及本次更新内容。"
    };
}
