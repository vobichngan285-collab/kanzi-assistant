package com.vobich.bluestar.nativelab;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.graphics.BitmapFactory;
import android.speech.tts.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static { System.loadLibrary("bluestar_native"); }
    private static final String[] NAMES={"MiniCPM-V-4_7-1B-Q4_K_M.gguf","mmproj-model-f16.gguf","mmproj-model-f16-4x.gguf"};
    private static final String[] SHA={"f18f594747e5317ab490cff9c06339c9cfb89acbe2f02ebc48a4e167bc23576d","fb9cc047a3cdad5317fd8433a4aeda2d22f00f02ca4bd93b34ad84165b25a900","1e0ab439877e6896ded59ff24cdd242fdffb236394f6fc4a5b019cbd60bb5f4a"};
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<View> controls=new ArrayList<>();
    private final StringBuilder speechBuffer=new StringBuilder(), logs=new StringBuilder();
    private TextView status,output,info,log;
    private Spinner projector,threads,resolution;
    private static final int[] PIXEL_CAPS={0,3145728,2239488,1769472,786432,442368};
    private final Handler timer=new Handler(Looper.getMainLooper());
    private boolean running=false;
    private String currentPhase="";
    private long phaseStart=0;
    private final Runnable ticker=new Runnable(){public void run(){if(!running||destroyed)return;showProgress();timer.postDelayed(this,500);}};
    private String progressText(){long now=SystemClock.elapsedRealtime();return (stopped?"正在取消 · ":"阶段：")+currentPhase+" · 本阶段 "+((now-phaseStart)/1000)+"秒 · 总计 "+((now-runStart)/1000)+"秒";}
    private void showProgress(){status.setText(progressText());}
    private void endProgress(){running=false;timer.removeCallbacks(ticker);}
    private CheckBox speak;
    private Button stop;
    private File photo;
    private long photoReadMs=0;
    private int photoOrientation=1;
    private String photoInfo="";
    private boolean busy=false,speakRun=false,ttsReady=false;
    private volatile boolean destroyed=false,stopped=false;
    private TextToSpeech tts;
    private long utterances=0,firstTts=-1,runStart=0;
    private native String nativeRun(String model,String projector,String file,int threads,int orientation,int pixelCap);
    private native void nativeCancel();
    private native void nativeResetCancel();
    private native void nativeRelease();
    private File weight(int i){return new File(getFilesDir(),NAMES[i]);}
    private Button button(LinearLayout l,String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(v->r.run());l.addView(b);controls.add(b);return b;}
    private TextView text(LinearLayout l,String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextIsSelectable(true);l.addView(t);return t;}
    private Spinner spinner(LinearLayout l,String[] a){Spinner s=new Spinner(this);ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,a);s.setAdapter(ad);l.addView(s);controls.add(s);return s;}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView scroll=new ScrollView(this);LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(16*getResources().getDisplayMetrics().density);l.setPadding(pad,pad,pad,pad);
        scroll.addView(l);setContentView(scroll);
        text(l,"蓝星原厂模型实验室 0.2.1",23);
        text(l,"MiniCPM-V 4.7 1B · 原厂GGUF · 全图动态切片（上限9）",17);
        info=text(l,"",16);
        for(int i=0;i<NAMES.length;i++){final int k=i;
            button(l,"导入 "+(i==0?"语言模型":i==1?"标准视觉模型 16x":"精细视觉模型 4x"),()->pick(100+k,"*/*"));
            button(l,"浏览器下载 "+(i==0?"语言模型":i==1?"标准视觉模型":"精细视觉模型"),()->{
                try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://huggingface.co/openbmb/MiniCPM-V-4.7-1B-gguf/resolve/main/"+NAMES[k])));}
                catch(Exception ex){status.setText("无法启动浏览器");}
            });
        }
        projector=spinner(l,new String[]{"原厂16x标准（默认）","原厂4x精细（可选）"});
        threads=spinner(l,new String[]{"CPU 6线程（默认）","CPU 4线程（基线）"});
        text(l,"图片分辨率（保持整图及比例，不裁剪）",16);
        resolution=spinner(l,new String[]{"原图（不缩放）","高清 ≤315万像素","标准 ≤224万像素（默认）","均衡 ≤177万像素","极速 ≤79万像素","极限 ≤44万像素"});resolution.setSelection(2);
        speak=new CheckBox(this);speak.setText("流式离线朗读");speak.setChecked(true);l.addView(speak);controls.add(speak);
        button(l,"选择相册原图",()->pick(200,"image/*"));
        button(l,"开始识别",this::start);
        stop=button(l,"停止识别",()->{stopped=true;nativeCancel();if(tts!=null)tts.stop();showProgress();});controls.remove(stop);stop.setEnabled(false);
        status=text(l,"就绪",18);output=text(l,"",19);
        Button copy=button(l,"复制诊断日志",()->{String snapshot=logs.toString();if(running)snapshot+="v=0.2.1 current_phase="+currentPhase+" phase_elapsed_ms="+(SystemClock.elapsedRealtime()-phaseStart)+" elapsed_ms="+(SystemClock.elapsedRealtime()-runStart)+" "+photoInfo+"\n";((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("诊断",snapshot));Toast.makeText(this,"已复制",Toast.LENGTH_SHORT).show();});controls.remove(copy);
        log=text(l,"",14);
        refresh();
        tts=new TextToSpeech(this, code->{
            if(code!=TextToSpeech.SUCCESS||tts==null||destroyed)return;
            try{Voice chosen=null;Set<Voice> voices=tts.getVoices();if(voices!=null)for(Voice v:voices)if(!v.isNetworkConnectionRequired()&&"zh".equals(v.getLocale().getLanguage())){chosen=v;break;}
                ttsReady=chosen!=null&&tts.setVoice(chosen)==TextToSpeech.SUCCESS;
            }catch(Exception ignored){}
        });
    }
    private void refresh(){info.setText("模型："+(weight(0).isFile()?"语言✓ ":"语言未导入 ")+(weight(1).isFile()?"16x✓ ":"16x未导入 ")+(weight(2).isFile()?"4x✓":"4x未导入")+"\n"+(photo==null?"尚未选图":photoInfo));}
    private void pick(int r,String mime){if(busy)return;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);startActivityForResult(i,r);}
    private void setBusy(boolean value,boolean canStop){busy=value;for(View v:controls)v.setEnabled(!value);stop.setEnabled(value&&canStop);if(value)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private void post(Runnable r){runOnUiThread(()->{if(!destroyed)r.run();});}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null||busy)return;
        Uri uri=data.getData();final int index=request-100;final boolean image=request==200;
        if(!image&&(index<0||index>=NAMES.length))return;
        setBusy(true,false);status.setText(image?"正在读取原始文件":"正在导入并校验官方权重");
        worker.execute(()->{
            File target=image?new File(getCacheDir(),"source.image"):weight(index);
            File tmp=new File(target.getPath()+".part");
            try{
                if(!image)nativeRelease();
                long t=SystemClock.elapsedRealtime(), n=0;
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(tmp)){
                    if(in==null)throw new IOException("无法读取文件");
                    byte[] buf=new byte[1024*1024];int k;
                    while((k=in.read(buf))!=-1){n+=k;if(n>(image?40L*1024*1024:2L*1024*1024*1024))throw new IOException("文件过大");digest.update(buf,0,k);out.write(buf,0,k);}
                    out.getFD().sync();
                }
                StringBuilder h=new StringBuilder();for(byte a:digest.digest())h.append(String.format(Locale.ROOT,"%02x",a&255));
                if(!image&&!h.toString().equals(SHA[index]))throw new IOException("SHA256不匹配：请选择准确的原厂文件");
                String desc="";int orientation=1;
                if(image){
                    BitmapFactory.Options opt=new BitmapFactory.Options();opt.inJustDecodeBounds=true;BitmapFactory.decodeFile(tmp.getPath(),opt);
                    if(opt.outWidth<=0||opt.outHeight<=0||((long)opt.outWidth*opt.outHeight)>24000000)throw new IOException("图片无效或超过2400万像素");
                    if(!"image/jpeg".equals(opt.outMimeType)&&!"image/png".equals(opt.outMimeType))throw new IOException("请选择JPEG或PNG原图");
                    try{orientation=new android.media.ExifInterface(tmp.getPath()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
                    desc=opt.outWidth+"×"+opt.outHeight+" bytes="+n+" sha="+h.substring(0,12)+" exif="+orientation;
                }
                if(!tmp.renameTo(target))throw new IOException("写入失败");
                final String d=desc;final int ori=orientation;long duration=SystemClock.elapsedRealtime()-t;
                post(()->{if(image){photo=target;photoInfo=d;photoReadMs=duration;photoOrientation=ori;}refresh();status.setText(image?"原图已就绪":"原厂权重校验通过");setBusy(false,false);});
            }catch(Exception ex){tmp.delete();post(()->{status.setText("失败："+ex.getMessage());setBusy(false,false);});}
        });
    }
    private void start(){
        if(busy)return;
        int v=projector.getSelectedItemPosition()+1,n=threads.getSelectedItemPosition()==0?6:4, cap=PIXEL_CAPS[resolution.getSelectedItemPosition()];
        if(photo==null||!weight(0).isFile()||!weight(v).isFile()){status.setText("先导入对应语言和视觉权重，再选择原图");return;}
        stopped=false;speakRun=speak.isChecked()&&ttsReady;utterances=0;firstTts=-1;speechBuffer.setLength(0);if(tts!=null)tts.stop();
        output.setText("");setBusy(true,true);status.setText("开始识别");runStart=SystemClock.elapsedRealtime();phaseStart=runStart;currentPhase="prepare";running=true;timer.removeCallbacks(ticker);timer.post(ticker);
        final String image=photo.getAbsolutePath(), desc=photoInfo;
        final long read=photoReadMs;final int orientation=photoOrientation;
        final String m=weight(0).getAbsolutePath(),mm=weight(v).getAbsolutePath();
        worker.execute(()->{
            try{
                if(destroyed||stopped){post(()->{endProgress();status.setText("已停止");setBusy(false,false);});return;}
                nativeResetCancel();
                if(destroyed||stopped){nativeCancel();post(()->{endProgress();status.setText("已停止");setBusy(false,false);});return;}
                String r=nativeRun(m,mm,image,n,orientation,cap);
                Debug.MemoryInfo mem=new Debug.MemoryInfo();Debug.getMemoryInfo(mem);
                post(()->{endProgress();if(!stopped&&r!=null&&r.startsWith("status=ok"))flushSpeech(true);
                    logs.append("v=0.2.1 soc=").append(Build.VERSION.SDK_INT>=31?Build.SOC_MODEL:Build.HARDWARE).append(" projector=").append(v==1?"16x":"4x").append(" slice_cap=9 ").append(desc).append(" file_read_ms=").append(read).append(" ").append(r).append(" pss_kib=").append(mem.getTotalPss()).append(" tts_first_queue_ms=").append(firstTts).append(" tts_queued=").append(utterances).append('\n');log.setText(logs);
                    status.setText(stopped?"已停止":r!=null&&r.startsWith("status=ok")?"识别完成":"识别失败，查看上方阶段与日志");setBusy(false,false);});
            }catch(Throwable e){post(()->{endProgress();status.setText("推理失败："+e.getClass().getSimpleName());setBusy(false,false);});}
        });
    }
    // JNI callbacks are on the single inference executor. UI and TTS stay on main.
    public void onPhase(String phase){post(()->{if(running&&!stopped){currentPhase=phase;phaseStart=SystemClock.elapsedRealtime();showProgress();}});}
    public void onPiece(byte[] bytes){
        String chunk=new String(bytes,StandardCharsets.UTF_8);
        post(()->{if(stopped)return;output.append(chunk);if(speakRun){speechBuffer.append(chunk);flushSpeech(false);}});
    }
    private void flushSpeech(boolean end){
        if(!speakRun||tts==null||stopped)return;
        while(speechBuffer.length()>0){
            int cut=-1;for(int i=0;i<speechBuffer.length();i++){if("，。！？；\n,.!?;".indexOf(speechBuffer.charAt(i))>=0){cut=i+1;break;}}
            if(cut<0){if(end)cut=speechBuffer.length();else if(speechBuffer.length()>=24)cut=24;else break;}
            String s=speechBuffer.substring(0,cut);speechBuffer.delete(0,cut);
            if(!s.trim().isEmpty()&&tts.speak(s,TextToSpeech.QUEUE_ADD,null,"native-"+utterances)==TextToSpeech.SUCCESS){if(firstTts<0)firstTts=SystemClock.elapsedRealtime()-runStart;utterances++;}
        }
    }
    @Override protected void onDestroy(){destroyed=true;stopped=true;endProgress();nativeCancel();if(tts!=null){tts.stop();tts.shutdown();}worker.execute(this::nativeRelease);super.onDestroy();}
}
