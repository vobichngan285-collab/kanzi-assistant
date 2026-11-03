package com.vobich.kanzi.craftprobe;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;

/** CI-only command line bridge to compare identical CRAFT raw heatmaps in Java and Python/OpenCV. */
public final class CraftBoxDecoderParityCli {
    public static void main(String[] args) throws Exception {
        if(args.length!=7) throw new IllegalArgumentException(
            "score.bin scoreHeight scoreWidth sourceWidth sourceHeight inputWidth inputHeight");
        int h=Integer.parseInt(args[1]),w=Integer.parseInt(args[2]);
        int sourceW=Integer.parseInt(args[3]),sourceH=Integer.parseInt(args[4]);
        int inputW=Integer.parseInt(args[5]),inputH=Integer.parseInt(args[6]);
        byte[] bytes=Files.readAllBytes(Paths.get(args[0]));
        if(bytes.length!=h*w*2*4) throw new IllegalArgumentException("invalid raw score bytes");
        FloatBuffer floats=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        CraftBoxDecoder.Result result=CraftBoxDecoder.decode(
            floats,h,w,sourceW,sourceH,inputW,inputH);
        System.out.println("META "+result.rawComponents+" "+result.survivingComponents+" "+result.boxes.size());
        for(CraftBoxDecoder.Box box:result.boxes) {
            StringBuilder line=new StringBuilder("BOX");
            line.append(String.format(Locale.ROOT," %.8f",box.confidence));
            for(float v:box.quad)line.append(String.format(Locale.ROOT," %.4f",v));
            System.out.println(line.toString());
        }
    }
}
