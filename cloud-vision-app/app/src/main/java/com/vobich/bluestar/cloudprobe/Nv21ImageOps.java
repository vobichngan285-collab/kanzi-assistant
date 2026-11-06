package com.vobich.bluestar.cloudprobe;

/** Pure NV21 transformations, used only when the user asks for one camera frame. */
final class Nv21ImageOps {
    private Nv21ImageOps(){}
    static final class Image {
        final byte[] bytes;
        final int width,height;
        Image(byte[] b,int w,int h){bytes=b;width=w;height=h;}
    }
    static Image orient(byte[] src,int w,int h,int angle,boolean mirror,boolean flip){
        check(src,w,h);
        int degrees=((angle%360)+360)%360;
        int outW=degrees%180==0?w:h,outH=degrees%180==0?h:w;
        byte[] changed=degrees==0?src:Nv21Rotation.rotate(src,w,h,degrees);
        // Mirror/vertical-flip are expressed in DISPLAY coordinates after rotation.
        if(mirror||flip)changed=flipNV21(changed,outW,outH,mirror,flip);
        return new Image(changed,outW,outH);
    }
    static Image centerCrop(Image image,float zoom){
        float factor=Math.max(1f,Math.min(3f,zoom));
        if(factor<=1.001f)return image;
        int cropW=Math.max(2,((int)(image.width/factor))&~1);
        int cropH=Math.max(2,((int)(image.height/factor))&~1);
        cropW=Math.min(cropW,image.width);cropH=Math.min(cropH,image.height);
        int x=(((image.width-cropW)/2)&~1),y=(((image.height-cropH)/2)&~1);
        byte[] dst=new byte[cropW*cropH*3/2];
        for(int row=0;row<cropH;row++)
            System.arraycopy(image.bytes,(y+row)*image.width+x,dst,row*cropW,cropW);
        int srcUV=image.width*image.height, dstUV=cropW*cropH;
        for(int row=0;row<cropH/2;row++)
            System.arraycopy(image.bytes,srcUV+((y/2)+row)*image.width+x,
                dst,dstUV+row*cropW,cropW);
        return new Image(dst,cropW,cropH);
    }
    static Image resizeLimit(Image img,int limit){
        int longest=Math.max(img.width,img.height);
        if(longest<=limit)return img; // never synthesize extra source pixels
        int outW=Math.max(2,(Math.round((float)img.width*limit/longest))&~1);
        int outH=Math.max(2,(Math.round((float)img.height*limit/longest))&~1);
        if(outW==img.width&&outH==img.height)return img;
        byte[] dst=new byte[outW*outH*3/2];
        // Bilinear luma sampling gives readable Chinese strokes at 720p.
        for(int y=0;y<outH;y++){
            float sy=Math.max(0,(y+.5f)*img.height/outH-.5f);
            int y0=Math.min(img.height-1,(int)sy),y1=Math.min(img.height-1,y0+1);
            float yf=sy-y0;
            for(int x=0;x<outW;x++){
                float sx=Math.max(0,(x+.5f)*img.width/outW-.5f);
                int x0=Math.min(img.width-1,(int)sx),x1=Math.min(img.width-1,x0+1);
                float xf=sx-x0;
                int p00=img.bytes[y0*img.width+x0]&255;
                int p01=img.bytes[y0*img.width+x1]&255;
                int p10=img.bytes[y1*img.width+x0]&255;
                int p11=img.bytes[y1*img.width+x1]&255;
                float v=(1-yf)*((1-xf)*p00+xf*p01)
                    +yf*((1-xf)*p10+xf*p11);
                dst[y*outW+x]=(byte)Math.round(v);
            }
        }
        // Nearest chroma is sufficient for color and avoids NV21 VU corruption.
        int uvOffset=img.width*img.height, outOffset=outW*outH;
        for(int y=0;y<outH/2;y++){
            int sy=Math.min(img.height/2-1,(int)((y+.5f)*img.height/outH));
            for(int x=0;x<outW/2;x++){
                int sx=Math.min(img.width/2-1,(int)((x+.5f)*img.width/outW));
                int in=uvOffset+sy*img.width+sx*2;
                int out=outOffset+y*outW+x*2;
                dst[out]=img.bytes[in];dst[out+1]=img.bytes[in+1];
            }
        }
        return new Image(dst,outW,outH);
    }
    private static byte[] flipNV21(byte[] source,int w,int h,boolean mirror,boolean vertical){
        byte[] out=new byte[source.length];
        for(int y=0;y<h;y++){
            int sy=vertical?h-1-y:y;
            for(int x=0;x<w;x++){
                int sx=mirror?w-1-x:x;
                out[y*w+x]=source[sy*w+sx];
            }
        }
        int srcBase=w*h;
        for(int y=0;y<h/2;y++){
            int sy=vertical?h/2-1-y:y;
            for(int x=0;x<w/2;x++){
                int sx=mirror?w/2-1-x:x;
                int src=srcBase+sy*w+sx*2,outIndex=srcBase+y*w+x*2;
                out[outIndex]=source[src];out[outIndex+1]=source[src+1];
            }
        }
        return out;
    }
    private static void check(byte[] pixels,int w,int h){
        if(pixels==null||w<2||h<2||(w&1)!=0||(h&1)!=0
            ||pixels.length!=(long)w*h*3/2)
            throw new IllegalArgumentException("NV21图片尺寸无效");
    }
}
