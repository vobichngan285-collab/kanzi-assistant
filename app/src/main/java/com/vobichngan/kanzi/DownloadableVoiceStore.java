package com.vobichngan.kanzi;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.util.Log;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User-invoked offline TTS packages. A tap on a voice LIST ITEM starts
 * HTTPS download + safe archive extraction + selection. No model is bundled
 * in the base APK and no extra download button or web browser is required.
 */
public final class DownloadableVoiceStore {
    private static final String TAG = "KanziVoicePacks";
    private static final String PREFS = "kanzi_settings";
    private static final String KEY = "offline_voice_id";
    private static final String RELEASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/";
    private static final long MAX_DOWNLOAD = 560L * 1024 * 1024;
    private static final long MAX_UNPACKED = 1250L * 1024 * 1024;
    private static final int MAX_FILES = 12000;
    public static final class Voice {
        public final String id, title, pack, folder, archive;
        public final int speakerId, approxMb;
        Voice(String id, String title, String pack, String folder,
              String archive, int speakerId, int approxMb) {
            this.id=id; this.title=title; this.pack=pack;
            this.folder=folder; this.archive=archive;
            this.speakerId=speakerId; this.approxMb=approxMb;
        }
        public boolean isSystem() { return "system".equals(id); }
    }

    public static final Voice SYSTEM = new Voice("system", "系统中文语音",
        "system", "", "", 0, 0);
    public static final Voice KOKORO_F = new Voice("kokoro_f", "Kokoro · 自然女声",
        "kokoro", "kokoro-multi-lang-v1_1", "kokoro-multi-lang-v1_1.tar.bz2", 3, 340);
    public static final Voice KOKORO_M = new Voice("kokoro_m", "Kokoro · 自然男声",
        "kokoro", "kokoro-multi-lang-v1_1", "kokoro-multi-lang-v1_1.tar.bz2", 58, 340);
    public static final Voice PIPER = new Voice("piper_chaowen", "Piper · 轻量中文声",
        "piper", "vits-piper-zh_CN-chaowen-medium",
        "vits-piper-zh_CN-chaowen-medium.tar.bz2", 0, 70);
    public static final Voice MELO = new Voice("melo", "MeloTTS · 中英混读",
        "melo", "vits-melo-tts-zh_en", "vits-melo-tts-zh_en.tar.bz2", 0, 190);
    public static final List<Voice> VOICES =
        Arrays.asList(SYSTEM, KOKORO_F, KOKORO_M, PIPER, MELO);

    public interface Listener {
        void onStatus(Voice voice, String message);
        void onSelected(Voice voice);
        void onFailure(Voice voice, String reason);
    }

    private final Context context;
    private final File root;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService downloader = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "KanziVoiceDownload");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger selectionSerial = new AtomicInteger(0);
    private volatile String selectedId;

    public DownloadableVoiceStore(Context context) {
        this.context = context.getApplicationContext();
        root = new File(this.context.getFilesDir(), "offline_voices");
        selectedId = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "system");
        if (find(selectedId) == null || !isInstalled(find(selectedId)))
            selectedId = "system";
    }

    public static Voice find(String id) {
        for (Voice voice : VOICES) if (voice.id.equals(id)) return voice;
        return null;
    }
    public File packageRoot() { return root; }
    public String selectedId() { return selectedId; }
    public Voice selectedVoice() {
        Voice voice = find(selectedId);
        return voice == null ? SYSTEM : voice;
    }
    public File modelDir(Voice voice) { return new File(root, voice.folder); }
    public boolean isInstalled(Voice voice) {
        if (voice == null) return false;
        if (voice.isSystem()) return true;
        File dir = modelDir(voice);
        String model = "kokoro".equals(voice.pack) || "melo".equals(voice.pack)
            ? "model.onnx" : "zh_CN-chaowen-medium.onnx";
        if (!new File(dir, model).isFile() ||
            new File(dir, model).length() < 10_000_000L ||
            !new File(dir, "tokens.txt").isFile() ||
            !new File(dir, "lexicon.txt").isFile() &&
                 !"kokoro".equals(voice.pack)) return false;
        return !"kokoro".equals(voice.pack) ||
            (new File(dir, "voices.bin").isFile() &&
             new File(dir, "lexicon-zh.txt").isFile() &&
             new File(dir, "lexicon-us-en.txt").isFile());
    }

    public void forceSystem() {
        selectionSerial.incrementAndGet();
        persist(SYSTEM);
    }
    private void persist(Voice voice) {
        selectedId=voice.id;
        SharedPreferences settings=context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        settings.edit().putString(KEY,voice.id).apply();
    }

    public void choose(Voice voice, Listener listener) {
        if (voice == null) return;
        final int ticket=selectionSerial.incrementAndGet();
        if (voice.isSystem() || isInstalled(voice)) {
            persist(voice);
            main.post(() -> { if (selectionSerial.get()==ticket)
                listener.onSelected(voice); });
            return;
        }
        notifyStatus(ticket,voice,listener,"等待下载安装…");
        downloader.execute(() -> {
            try {
                install(voice, ticket, listener);
                if (ticket != selectionSerial.get()) return;
                persist(voice);
                main.post(() -> { if (ticket == selectionSerial.get())
                    listener.onSelected(voice); });
            } catch (Throwable error) {
                Log.e(TAG,"Voice download/install failed: "+voice.id,error);
                main.post(() -> { if (ticket == selectionSerial.get())
                    listener.onFailure(voice,error.getMessage()==null
                        ? "语音包安装失败" : error.getMessage()); });
            }
        });
    }

    private void notifyStatus(int ticket,Voice voice,Listener listener,String status) {
        main.post(() -> { if (ticket == selectionSerial.get())
            listener.onStatus(voice,status); });
    }

    private void install(Voice voice,int ticket,Listener listener) throws IOException {
        if (isInstalled(voice)) return;
        if (!root.isDirectory() && !root.mkdirs())
            throw new IOException("无法创建离线语音包目录");
        // We never pre-download a package. The user explicitly taps to install.
        long available = new StatFs(root.getAbsolutePath()).getAvailableBytes();
        if (available < voice.approxMb*2L*1024*1024)
            throw new IOException("手机剩余空间不足，请释放存储后重试");
        File incoming = new File(root,voice.pack+".downloading");
        File staging = new File(root,voice.pack+".staging");
        try {
            deleteTree(staging);
            if (!staging.mkdirs()) throw new IOException("无法创建语音临时目录");
            URL url=new URL(RELEASE+voice.archive);
            if (!"https".equalsIgnoreCase(url.getProtocol()))
                throw new IOException("仅允许 HTTPS 语音资源");
            HttpURLConnection conn=(HttpURLConnection)url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            try {
                int status=conn.getResponseCode();
                if(status!=200) throw new IOException("语音模型服务器错误："+status);
                long expected=conn.getContentLengthLong();
                if(expected>MAX_DOWNLOAD) throw new IOException("模型文件过大，拒绝下载");
                notifyStatus(ticket,voice,listener,"正在下载语音包…");
                try(InputStream input=new BufferedInputStream(conn.getInputStream());
                    FileOutputStream output=new FileOutputStream(incoming)) {
                    byte[] buffer=new byte[65536];
                    long count=0, lastUpdate=0;
                    int n;
                    while((n=input.read(buffer))!=-1){
                        if(ticket!=selectionSerial.get())throw new IOException("下载已取消");
                        count+=n;
                        if(count>MAX_DOWNLOAD)throw new IOException("下载大小超出安全限制");
                        output.write(buffer,0,n);
                        if(count-lastUpdate>2L*1024*1024){
                            lastUpdate=count;
                            int pct=expected>0?(int)Math.min(99,100*count/expected):-1;
                            notifyStatus(ticket,voice,listener,pct>=0
                                ?"下载中 "+pct+"%" : "已下载 "+(count/1048576)+" MB");
                        }
                    }
                    if (expected > 0 && count != expected)
                        throw new IOException("语音包下载不完整");
                }
            } finally { conn.disconnect(); }
            notifyStatus(ticket,voice,listener,"正在解压并检查模型…");
            unpack(incoming,staging,voice,ticket);
            if(ticket!=selectionSerial.get())throw new IOException("安装已取消");
            File candidate=new File(staging,voice.folder);
            if(!candidate.isDirectory())throw new IOException("压缩包缺少模型文件夹");
            File installed=modelDir(voice);
            if(!validate(candidate,voice)) throw new IOException("语音包文件缺失或不完整");
            deleteTree(installed);
            if(!candidate.renameTo(installed))
                throw new IOException("语音模型安装失败，请重试");
        } finally {
            if(incoming.exists()) incoming.delete();
            deleteTree(staging);
        }
    }

    private static boolean validate(File dir,Voice voice){
        String model="piper".equals(voice.pack)
            ?"zh_CN-chaowen-medium.onnx":"model.onnx";
        if(!new File(dir,model).isFile() ||
           new File(dir,model).length()<10_000_000 ||
           !new File(dir,"tokens.txt").isFile())return false;
        if("kokoro".equals(voice.pack))
            return new File(dir,"voices.bin").isFile() &&
                new File(dir,"lexicon-us-en.txt").isFile() &&
                new File(dir,"lexicon-zh.txt").isFile();
        return new File(dir,"lexicon.txt").isFile();
    }

    private void unpack(File input,File staging,Voice voice,int ticket) throws IOException {
        long expanded=0; int entries=0;
        String prefix=voice.folder+"/";
        try(TarArchiveInputStream tar=new TarArchiveInputStream(
                new BZip2CompressorInputStream(
                    new BufferedInputStream(new FileInputStream(input))))) {
            TarArchiveEntry entry;
            byte[] buffer=new byte[65536];
            while((entry=tar.getNextTarEntry())!=null){
                if(ticket!=selectionSerial.get())throw new IOException("安装已取消");
                if(++entries>MAX_FILES)throw new IOException("语音包包含过多文件");
                String name=entry.getName();
                while(name.startsWith("./"))name=name.substring(2);
                if(name.equals(voice.folder) && entry.isDirectory())continue;
                if(!name.startsWith(prefix) || name.contains("\\") ||
                   name.contains("..") || name.startsWith("/"))
                    throw new IOException("语音包路径不安全");
                if(entry.isSymbolicLink()||entry.isLink())
                    throw new IOException("不允许语音包内的链接");
                File destination=new File(staging,name);
                if(!destination.getCanonicalPath().startsWith(
                    staging.getCanonicalPath()+File.separator))
                    throw new IOException("语音包越界路径");
                if(entry.isDirectory()){
                    if(!destination.isDirectory()&&!destination.mkdirs())
                        throw new IOException("无法创建解压目录");
                    continue;
                }
                if(!entry.isFile())continue;
                expanded+=entry.getSize();
                if(expanded>MAX_UNPACKED)throw new IOException("语音包解压内容过大");
                File parent=destination.getParentFile();
                if(!parent.isDirectory()&&!parent.mkdirs())
                    throw new IOException("无法创建文件目录");
                try(BufferedOutputStream output=new BufferedOutputStream(
                    new FileOutputStream(destination))){
                    int n;
                    while((n=tar.read(buffer))!=-1){
                        if(ticket!=selectionSerial.get())throw new IOException("安装已取消");
                        output.write(buffer,0,n);
                    }
                }
            }
        }
    }

    static void deleteTree(File file) {
        if(file==null||!file.exists())return;
        if(file.isDirectory()){
            File[] children=file.listFiles();
            if(children!=null)for(File child:children)deleteTree(child);
        }
        file.delete();
    }
    public void shutdown() { downloader.shutdownNow(); }
}
