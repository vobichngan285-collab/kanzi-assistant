package com.vobich.bluestar.cloudprobe;
final class BlueStarReleaseInfo {
    private BlueStarReleaseInfo(){}
    static final String DATE="2026-11-25";
    static final String VERSION="1.0.98-event-listener";
    static final String[] CHANGES={
        "实体拍照改用C110主动通知，停止空闲SD卡轮询。",
        "移除监听间隔与自动删图功能，原图保留在相机。",
        "保留锁屏朗读；异常时停止监听，避免反复请求相机。",
        "保留原厂电量、手动拍照、原图上传、AI寻找与语音设置。"
    };
}
