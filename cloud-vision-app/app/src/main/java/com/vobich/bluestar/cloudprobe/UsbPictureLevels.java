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
    static int read(com.jiangdg.ausbc.camera.CameraUVC camera,int kind){
        switch(kind){
            case BRIGHTNESS:return camera.getBrightness()==null?-1:camera.getBrightness();
            case CONTRAST:return camera.getContrast()==null?-1:camera.getContrast();
            case SATURATION:return camera.getSaturation()==null?-1:camera.getSaturation();
            case GAIN:return camera.getGain()==null?-1:camera.getGain();
            default:return -1;
        }
    }
    static void write(com.jiangdg.ausbc.camera.CameraUVC camera,int kind,int value){
        int percent=clamp(value);
        switch(kind){
            case BRIGHTNESS:camera.setBrightness(percent);break;
            case CONTRAST:camera.setContrast(percent);break;
            case SATURATION:camera.setSaturation(percent);break;
            case GAIN:camera.setGain(percent);break;
            default:throw new IllegalArgumentException("kind");
        }
    }
    static void reset(com.jiangdg.ausbc.camera.CameraUVC camera,int kind){
        switch(kind){
            case BRIGHTNESS:camera.resetBrightness();break;
            case CONTRAST:camera.resetContrast();break;
            case SATURATION:camera.resetSaturation();break;
            case GAIN:camera.resetGain();break;
            default:throw new IllegalArgumentException("kind");
        }
    }
}
