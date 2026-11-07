package com.vobich.bluestar.cloudprobe;

import com.jiangdg.ausbc.camera.CameraUVC;

/** Thin bridge to EXISTING UVC hardware processing-unit controls. */
final class UsbPictureHardware {
    private UsbPictureHardware(){}
    static boolean supported(CameraUVC camera,int kind){
        return UsbPictureLevels.valid(kind)&&
            camera!=null&&camera.supportsBlueStarPictureControl(kind);
    }
    static int read(CameraUVC camera,int kind){
        Integer value;
        switch(kind){
            case UsbPictureLevels.BRIGHTNESS:value=camera.getBrightness();break;
            case UsbPictureLevels.CONTRAST:value=camera.getContrast();break;
            case UsbPictureLevels.SATURATION:value=camera.getSaturation();break;
            case UsbPictureLevels.GAIN:value=camera.getGain();break;
            default:return -1;
        }
        return value==null?-1:UsbPictureLevels.clamp(value);
    }
    static void write(CameraUVC camera,int kind,int value){
        int percent=UsbPictureLevels.clamp(value);
        switch(kind){
            case UsbPictureLevels.BRIGHTNESS:camera.setBrightness(percent);break;
            case UsbPictureLevels.CONTRAST:camera.setContrast(percent);break;
            case UsbPictureLevels.SATURATION:camera.setSaturation(percent);break;
            case UsbPictureLevels.GAIN:camera.setGain(percent);break;
            default:throw new IllegalArgumentException("kind");
        }
    }
    static void reset(CameraUVC camera,int kind){
        switch(kind){
            case UsbPictureLevels.BRIGHTNESS:camera.resetBrightness();break;
            case UsbPictureLevels.CONTRAST:camera.resetContrast();break;
            case UsbPictureLevels.SATURATION:camera.resetSaturation();break;
            case UsbPictureLevels.GAIN:camera.resetGain();break;
            default:throw new IllegalArgumentException("kind");
        }
    }
}
