package com.vobich.bluestar.cloudprobe;
public final class UsbPictureLevelsTest {
    private static void ok(boolean x,String m){if(!x)throw new AssertionError(m);}
    public static void main(String[] args){
        ok(UsbPictureLevels.COUNT==4,"exactly four hardware controls");
        ok(UsbPictureLevels.clamp(-1)==0,"low clamp");
        ok(UsbPictureLevels.clamp(140)==100,"high clamp");
        ok(UsbPictureLevels.clamp(57)==57,"unchanged");
        for(int i=0;i<UsbPictureLevels.COUNT;i++)
            ok(!UsbPictureLevels.key(i).isEmpty(),"key");
        ok(!UsbPictureLevels.valid(4),"invalid unsupported ID");
        System.out.println("RC14_USB_PICTURE_CONTROL_BOUNDS=PASS");
    }
}
