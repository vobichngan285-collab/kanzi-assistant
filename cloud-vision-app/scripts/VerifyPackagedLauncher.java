import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Opens the FINISHED APK and decodes every launcher asset, not just filenames. */
public final class VerifyPackagedLauncher {
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("APK required");
        Map<String,Integer> expected=new LinkedHashMap<>();
        expected.put("mdpi",48);expected.put("hdpi",72);expected.put("xhdpi",96);
        expected.put("xxhdpi",144);expected.put("xxxhdpi",192);
        try(ZipFile apk=new ZipFile(Path.of(args[0]).toFile())){
            for(Map.Entry<String,Integer> item:expected.entrySet()){
                String directory="res/mipmap-"+item.getKey();
                ZipEntry chosen=null;
                for(ZipEntry e:java.util.Collections.list(apk.entries())){
                    if(e.getName().startsWith(directory) &&
                       e.getName().endsWith("/ic_launcher.png")){
                        chosen=e;break;
                    }
                }
                if(chosen==null)throw new IllegalStateException(
                    "APK missing real "+item.getKey()+" launcher PNG");
                BufferedImage img=ImageIO.read(apk.getInputStream(chosen));
                if(img==null)throw new IllegalStateException(
                    "ANDROID_LAUNCHER_IMAGE_DECODE_FAILED: "+chosen.getName());
                int size=item.getValue();
                if(img.getWidth()!=size||img.getHeight()!=size)
                    throw new IllegalStateException("Incorrect DPI icon size at "+
                        chosen.getName()+": "+img.getWidth()+"x"+img.getHeight());
                long blue=0,red=0,alpha=0;
                for(int y=0;y<size;y++)for(int x=0;x<size;x++){
                    int c=img.getRGB(x,y);
                    red+=(c>>16)&255;blue+=c&255;alpha+=(c>>>24)&255;
                }
                if(blue<red*1.13||alpha<(long)size*size*220)
                    throw new IllegalStateException("Launcher has wrong pixels "+
                        chosen.getName()+" blue="+blue+" red="+red);
                System.out.println("PACKAGED_LAUNCHER_DECODE_PASS "+
                    item.getKey()+" "+size+"x"+size+" entry="+chosen.getName());
            }
            if(apk.getEntry("res/mipmap-anydpi-v26/ic_launcher.xml")==null)
                throw new IllegalStateException("Missing adaptive launcher icon XML");
            for(ZipEntry entry:java.util.Collections.list(apk.entries())){
                if(entry.getName().contains("bluestar_icon.webp")||
                    entry.getName().contains("/ic_launcher.webp"))
                    throw new IllegalStateException("CORRUPT_LEGACY_WEBP_IN_APK: "+entry.getName());
            }
        }
        System.out.println("PACKAGED_LAUNCHER_VISUAL_ASSET_VERIFIED=PASS");
    }
}
