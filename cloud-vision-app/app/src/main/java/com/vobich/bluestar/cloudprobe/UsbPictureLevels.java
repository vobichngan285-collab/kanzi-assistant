package com.vobich.bluestar.cloudprobe;

/** UVC device hardware picture controls, values normalized to 0..100. */
final class UsbPictureLevels {
    static final int BRIGHTNESS=0,CONTRAST=1,SATURATION=2,GAIN=3,COUNT=4;
    static final String[] LABELS={"亮度","对比度","饱和度","增益"};
    static final String[] KEYS={"brightness","contrast","saturation","gain"};
    private UsbPictureLevels(){}
    static boolean valid(int kind){return kind>=0&&kind<COUNT;}
    static int clamp(int value){return Math.max(0,Math.min(100,value));}
    static String key(int kind){if(!valid(kind))throw new IllegalArgumentException("kind");return KEYS[kind];}
}
