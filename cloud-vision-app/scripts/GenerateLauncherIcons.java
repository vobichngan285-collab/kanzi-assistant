import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Run in CI before Gradle, without ImageMagick/Pillow/webp codec dependencies.
 * Decodes the source PNG, renders one icon for every density, immediately decodes
 * each output again and checks non-empty pixel content. Fails CLOSED.
 */
public final class GenerateLauncherIcons {
    public static void main(String[] args) throws Exception {
        Path original=Path.of("app/src/main/res/drawable-nodpi/bluestar_globe.png");
        if(!Files.isRegularFile(original))throw new IllegalStateException("缺少蓝星原图: "+original);
        BufferedImage src=ImageIO.read(original.toFile());
        if(src==null||src.getWidth()!=144||src.getHeight()!=144)
            throw new IllegalStateException("蓝星原图不可解码或不是144x144");
        Map<String,Integer> sizes=new LinkedHashMap<>();
        sizes.put("mdpi",48);sizes.put("hdpi",72);sizes.put("xhdpi",96);
        sizes.put("xxhdpi",144);sizes.put("xxxhdpi",192);
        for(Map.Entry<String,Integer> item:sizes.entrySet()){
            int size=item.getValue();
            BufferedImage out=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src,0,0,size,size,null);
            g.dispose();
            Path folder=Path.of("app/src/main/res/mipmap-"+item.getKey());
            Files.createDirectories(folder);
            Path target=folder.resolve("ic_launcher.png");
            if(!ImageIO.write(out,"png",target.toFile()))
                throw new IllegalStateException("无法保存 PNG: "+target);
            BufferedImage verify=ImageIO.read(target.toFile());
            if(verify==null||verify.getWidth()!=size||verify.getHeight()!=size)
                throw new IllegalStateException("生成的图标无法解码: "+target);
            long brightness=0;
            for(int y=0;y<size;y++)for(int x=0;x<size;x++){
                int rgb=verify.getRGB(x,y);
                brightness+=((rgb>>16)&255)+((rgb>>8)&255)+(rgb&255);
            }
            double mean=(double)brightness/(size*size*3);
            if(mean<20||mean>220)throw new IllegalStateException(
                "图标疑似全黑/全白/空白: "+target+" 平均亮度："+mean);
            System.out.printf("LAUNCHER_PNG_DECODE_OK density=%s size=%dx%d bytes=%d meanRgb=%.1f%n",
                item.getKey(),size,size,Files.size(target),mean);
        }
        System.out.println("ALL_FIVE_LAUNCHER_DENSITIES_DECODED=PASS");
    }
}
