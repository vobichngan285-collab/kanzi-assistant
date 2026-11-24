package com.vobich.bluestar.cloudprobe;

/** Update DATE and CHANGES on EVERY release. Version itself is read from installed package. */
final class BlueStarReleaseInfo {
    private BlueStarReleaseInfo(){}
    static final String DATE="2026-11-24";
    static final String VERSION="1.0.97-lockscreen-listener";
    static final String[] CHANGES={
        "首页移除重复连接相机入口，原厂电量图标进一步放大。",
        "首页电量旁增加实体快门监听开关，开启即支持锁屏及后台。",
        "使用单一前台连接设备服务，关闭监听释放后台资源。",
        "保留原厂电量档位、自动删图、AI寻找与其他功能；锁屏效果待真机验证。"
    };
}
