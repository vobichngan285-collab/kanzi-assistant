package com.vobich.bluestar.cloudprobe;

import java.util.Arrays;

/** RC13: 4 rotations x 8 switch states, with unequal corner pixel markers. */
public final class UsbFrameOrientationTest {
    private static void check(boolean value,String why){
        if(!value)throw new AssertionError(why);
    }
    private static byte[] sample(int w,int h){
        byte[] image=new byte[w*h*3/2];
        // Each corner + every pixel is distinguishable. Use asymmetric Y and UV.
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)
            image[y*w+x]=(byte)(1+((y*47+x*13+y*x)%253));
        int base=w*h;
        for(int y=0;y<h/2;y++)for(int x=0;x<w/2;x++){
            image[base+y*w+2*x]=(byte)(100+y*7+x*17);
            image[base+y*w+2*x+1]=(byte)(220-y*13-x*5);
        }
        return image;
    }
    public static void main(String[] args){
        int w=8,h=6;
        byte[] raw=sample(w,h);
        int checked=0;
        for(int degrees:new int[]{0,90,180,270})
            for(int bits=0;bits<8;bits++){
                boolean hMirror=(bits&1)!=0;
                boolean vFlip=(bits&2)!=0;
                boolean usbCorrection=(bits&4)!=0;
                UsbFrameOrientation.Plan p=UsbFrameOrientation.forSettings(
                    degrees,hMirror,vFlip,usbCorrection);
                check(p.displayMirror==(hMirror^usbCorrection),"display X parity");
                check(p.displayFlip==vFlip,"display Y parity");
                check(p.quarterTurn==(degrees==90||degrees==270),"quarter turn");
                Nv21ImageOps.Image local=Nv21ImageOps.orient(raw,w,h,0,
                    p.previewLocalMirror,p.previewLocalFlip);
                Nv21ImageOps.Image preview=Nv21ImageOps.orient(
                    local.bytes,local.width,local.height,p.rotation,false,false);
                Nv21ImageOps.Image jpeg=Nv21ImageOps.orient(raw,w,h,p.rotation,
                    p.displayMirror,p.displayFlip);
                check(preview.width==jpeg.width&&preview.height==jpeg.height,
                    "size equality "+degrees+" switches="+bits);
                check(Arrays.equals(preview.bytes,jpeg.bytes),
                    "PREVIEW-JPEG PIXEL MISMATCH: rotation="+degrees+" switches="+bits);
                checked++;
            }
        // Reported real device state: 90 deg, no horizontal/vertical user flip,
        // USB correction enabled. It MUST flip screen X, NEVER screen Y.
        UsbFrameOrientation.Plan user=UsbFrameOrientation.forSettings(
            90,false,false,true);
        check(user.previewLocalFlip&&!user.previewLocalMirror,
            "90-degree USB correction must use PRE-rotation local Y axis");
        check(user.displayMirror&&!user.displayFlip,
            "90-degree USB correction must be POST-rotation screen X axis");
        Nv21ImageOps.Image baseline=Nv21ImageOps.orient(raw,w,h,90,false,false);
        Nv21ImageOps.Image changed=Nv21ImageOps.orient(raw,w,h,90,true,false);
        Nv21ImageOps.Image horizontal= Nv21ImageOps.orient(
            baseline.bytes,baseline.width,baseline.height,0,true,false);
        check(Arrays.equals(changed.bytes,horizontal.bytes),
            "USB 90 correction moves guitar left/right without turning upside down");
        boolean rejected=false;
        try{UsbFrameOrientation.forSettings(45,false,false,true);}
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"invalid rotation not silently accepted");
        System.out.println("RC13_USB_PREVIEW_JPEG_32_COMBINATIONS_AND_90_CORRECTION=PASS "
            +checked);
    }
}
