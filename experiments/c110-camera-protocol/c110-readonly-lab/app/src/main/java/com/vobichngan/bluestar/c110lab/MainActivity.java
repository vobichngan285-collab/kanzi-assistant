package com.vobichngan.bluestar.c110lab;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.SystemClock;
import android.content.*;
import android.net.*;
import android.provider.MediaStore;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Independent C110 JPEG photo experiment: explicit one-shot shutter, no mode changes. */
public final class MainActivity extends Activity {
 private final ExecutorService pool=Executors.newSingleThreadExecutor();
 private final AtomicBoolean busy=new AtomicBoolean();
 private final StringBuilder logText=new StringBuilder("蓝星C110拍照取图实验 v0.1.3\n联咏错误码专项：-256命令不存在；-13执行失败。只有点击②并确认，才会切到照片模式再单次拍摄。\n");
 private EditText ipInput;
 private TextView display, details;
 private Network wifi;
 private String ip;
 private Set<String> baseline;
 private String baselineIp;
 private static final int LIMIT_INDEX=1024*1024, LIMIT_PHOTO=40*1024*1024;

 @Override public void onCreate(Bundle b) {
  super.onCreate(b);
  ScrollView sc=new ScrollView(this);
  LinearLayout root=new LinearLayout(this); root.setOrientation(1);root.setPadding(18,16,18,24);
  sc.addView(root);setContentView(sc);
  TextView title=new TextView(this);title.setText("C110 拍照取图实验 v0.1.3");title.setTextSize(25);title.setTextColor(Color.BLACK);root.addView(title);
  TextView note=new TextView(this);
  note.setText("①只读检查。②明确确认后切到照片模式、单次拍照、获取完整JPEG；③不切模式直接拍照用于对照。");
  note.setTextSize(18);root.addView(note);
  ipInput=new EditText(this);ipInput.setTextSize(19);ipInput.setSingleLine(true);
  ipInput.setHint("相机IP（留空则用Wi-Fi网关）");
  root.addView(ipInput);
  button(root,"① 只读检测 / SD卡 / 旧图基线",()->start("检测与基线",this::check));
  button(root,"② 进入照片模式 → 拍照取高清原图",()->new android.app.AlertDialog.Builder(this)
   .setTitle("确认切照片模式并拍照")
   .setMessage("此操作将先发送3001/par=0切换相机到照片模式（可能打断实时预览），成功后才发送一次1001快门；不会自动反复拍照或切回录像模式。")
   .setNegativeButton("取消",null)
   .setPositiveButton("同意切模式并拍照",(d,w)->start("切照片模式并拍照",()->shutterAndFetch(true)))
   .show());
  button(root,"③ 直接拍照一次（不切模式对照）",()->new android.app.AlertDialog.Builder(this)
   .setTitle("确认不切模式拍照")
   .setMessage("仅发送一次1001快门，不改变相机模式。前一版已在该状态下返回-13，此项仅用作对照。")
   .setNegativeButton("取消",null)
   .setPositiveButton("直接拍照",(d,w)->start("直接拍照对照",()->shutterAndFetch(false)))
   .show());
  button(root,"④ 下载已有JPEG（只读对照）",()->start("下载现有图",()->downloadExisting(false)));
  button(root,"⑤ 已在原厂App进入回放：只读检查",()->start("回放只读对照",this::check));
  button(root,"复制诊断日志",()->{
   android.content.ClipboardManager c=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
   c.setPrimaryClip(ClipData.newPlainText("C110实验日志",logText.toString()));
   Toast.makeText(this,"已复制匿名诊断",Toast.LENGTH_LONG).show();
  });
  display=new TextView(this);display.setTextSize(20);display.setTextColor(Color.BLACK);
  display.setText("等待开始测试");root.addView(display);
  details=new TextView(this);details.setTextSize(16);details.setTextColor(0xff35404a);details.setText(logText.toString());root.addView(details);
 }
 private void button(LinearLayout root,String text,Runnable action){
  Button b=new Button(this);b.setText(text);b.setTextSize(17);b.setAllCaps(false);
  root.addView(b,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
  b.setOnClickListener(v->action.run());
 }
 private interface Work {void run() throws Exception;}
 private void start(String label,Work work){
  if(!busy.compareAndSet(false,true)){Toast.makeText(this,"上一步仍在执行",Toast.LENGTH_SHORT).show();return;}
  state(label+"…");
  pool.execute(()->{
   try{line("开始 "+label);work.run();}
   catch(Exception e){line("失败："+e.getClass().getSimpleName()+" "+message(e));state("失败。复制诊断文字反馈");}
   finally{busy.set(false);}
  });
 }
 private static String message(Exception e){
  String m=e.getMessage();
  if(m==null)return "";
  if(m.contains("http://")||m.contains("DCIM/"))return "文件或HTTP路径错误（详情已隐藏）";
  return m.length()>95?m.substring(0,95):m;
 }
 private void line(String s){
  runOnUiThread(()->{
   logText.append(android.text.format.DateFormat.format("HH:mm:ss",new Date())).append(" ").append(s).append("\n");
   if(logText.length()>16000)logText.delete(0,logText.length()-14000);
   details.setText(logText.toString());
  });
 }
 private void state(String s){runOnUiThread(()->display.setText(s));}
 private static boolean isPrivate(String x){
  String[] a=x.split("\\.");if(a.length!=4)return false;
  int[] n=new int[4];
  try{for(int i=0;i<4;i++){n[i]=Integer.parseInt(a[i]);if(n[i]<0||n[i]>255)return false;}}
  catch(Exception e){return false;}
  return n[0]==10||(n[0]==172&&n[1]>=16&&n[1]<=31)||(n[0]==192&&n[1]==168);
 }
 private void selectWifi() throws IOException {
  ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
  Network target=null;String gateway=null;
  for(Network n:cm.getAllNetworks()){
   NetworkCapabilities caps=cm.getNetworkCapabilities(n);
   if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;
   LinkProperties lp=cm.getLinkProperties(n);if(lp==null)continue;
   for(RouteInfo r:lp.getRoutes()){
    InetAddress addr=r.getGateway();
    if(r.isDefaultRoute()&&addr instanceof Inet4Address&&isPrivate(addr.getHostAddress())){
     target=n;gateway=addr.getHostAddress();break;
    }
   }
   if(target!=null)break;
  }
  if(target==null)throw new IOException("未发现带私有IPv4网关的Wi-Fi；请在系统设置中连接C110热点");
  String text=ipInput.getText().toString().trim();
  String chosen=text.isEmpty()?gateway:text;
  if(!isPrivate(chosen))throw new IOException("只允许使用相机局域网内的私有IPv4地址");
  wifi=target;ip=chosen;line("已发现Wi-Fi网络、IPv4网关；目标IP="+ip+"；不读取SSID/照片名");
 }
 private static final class Result {
  final int status;final byte[] body;final long ms;
  Result(int s,byte[] b,long t){status=s;body=b;ms=t;}
  String text(){return new String(body,StandardCharsets.UTF_8);}
 }
 private Result get(String request) throws IOException {
  boolean shutter=request.equals("/?custom=1&cmd=1001");
  boolean modeChange=request.equals("/?custom=1&cmd=3001&par=0");
  boolean command=shutter||modeChange||request.equals("/?custom=1&cmd=3012")
    ||request.equals("/?custom=1&cmd=3015")||request.equals("/?custom=1&cmd=3002")
    ||request.equals("/?custom=1&cmd=3024")||request.equals("/?custom=1&cmd=1003");
  boolean media=request.startsWith("/DCIM/")&&!request.contains("..")&&!request.contains("\\")&&!request.contains("?")&&!request.contains("#");
  if(!command&&!media)throw new IOException("只读请求白名单拒绝");
  if(wifi==null)throw new IOException("没有Wi-Fi Network");
  long start=SystemClock.elapsedRealtime();
  HttpURLConnection conn=null;
  try{
   conn=(HttpURLConnection)wifi.openConnection(new URL("http",ip,80,request));
   conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(2800);conn.setReadTimeout(shutter||modeChange?7000:2800);
   conn.setUseCaches(false);conn.setRequestProperty("Accept-Encoding","identity");
   int status=conn.getResponseCode();
   byte[] body=new byte[0];
   if(status==200){
    try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
     byte[] b=new byte[8192];int n,sz=0;
     while((n=in.read(b))!=-1){sz+=n;if(sz>LIMIT_INDEX)throw new IOException("列表太大，已限制在1MB");out.write(b,0,n);}
     body=out.toByteArray();
    }
   }
   return new Result(status,body,SystemClock.elapsedRealtime()-start);
  }finally{if(conn!=null)conn.disconnect();}
 }
 private Set<String> list(boolean verbose) throws IOException {
  LinkedHashSet<String> found=new LinkedHashSet<>();
  Result cmd=get("/?custom=1&cmd=3015");
  found.addAll(ProbeUtil.jpegs(cmd.text(),"/DCIM/",ip));
  if(verbose)line("媒体索引3015：HTTP"+cmd.status+"，"+cmd.ms+"ms；候选"+found.size());
  LinkedHashSet<String> scan=new LinkedHashSet<>(Arrays.asList("/DCIM/PHOTO/","/DCIM/"));
  for(String path:new ArrayList<>(scan)){
   try{
    Result r=get(path);
    found.addAll(ProbeUtil.jpegs(r.text(),path,ip));
    if(path.equals("/DCIM/")){
     for(String d:ProbeUtil.dirs(r.text(),path,ip))if(scan.size()<7)scan.add(d);
    }
    if(verbose)line("目录请求：HTTP"+r.status+"，"+r.ms+"ms");
   }catch(Exception e){if(verbose)line("目录无法读取："+e.getClass().getSimpleName());}
  }
  int extra=0;
  for(String path:scan){
   if(path.equals("/DCIM/PHOTO/")||path.equals("/DCIM/"))continue;
   if(extra++>=5)break;
   try{Result r=get(path);found.addAll(ProbeUtil.jpegs(r.text(),path,ip));
    if(verbose)line("子目录：HTTP"+r.status+"，"+r.ms+"ms");
   }catch(Exception e){if(verbose)line("子目录请求异常："+e.getClass().getSimpleName());}
  }
  if(verbose)line("共找到"+found.size()+"条JPEG候选，文件名不记录");
  return found;
 }
 private void check() throws Exception {
  selectWifi();
  Result device=get("/?custom=1&cmd=3012");
  Integer ds=ProbeUtil.tagInt(device.text(),"Status");
  line("设备3012：HTTP"+device.status+"，"+device.body.length+"字节，"+device.ms+"ms；Status="+ds);
  for(String cmd:new String[]{"3024","3002"}){
   try{
    Result r=get("/?custom=1&cmd="+cmd);
    Integer status=ProbeUtil.tagInt(r.text(),"Status");
    String extras=cmd.equals("3024")?("；卡状态Value="+ProbeUtil.tagInt(r.text(),"Value")):
      ("；列出1001="+r.text().contains("<Cmd>1001</Cmd>")+"；列出3001="+r.text().contains("<Cmd>3001</Cmd>"));
    line("只读"+cmd+"：HTTP"+r.status+"，"+r.body.length+"字节，"+r.ms+"ms；Status="+status+
      "（"+ProbeUtil.statusName(status)+"）"+extras+"；原文不记录");
   }catch(Exception e){line("只读"+cmd+"请求异常："+e.getClass().getSimpleName());}
  }
  baseline=list(true);baselineIp=ip;
  state("已记录旧图基线："+baseline.size()+"张；点击②测试拍照");
 }
 private void downloadExisting(boolean newOnly) throws Exception {
  selectWifi();
  Set<String> current=list(true);Set<String> candidates=new LinkedHashSet<>(current);
  if(newOnly){
   if(baseline==null||!ip.equals(baselineIp))throw new IOException("需先①建立同一相机的照片基线");
   candidates.removeAll(baseline);
  }
  if(candidates.isEmpty()){state(newOnly?"未发现新增JPEG":"列表没有可用JPEG");return;}
  ArrayList<String> order=new ArrayList<>(candidates);Collections.sort(order);
  line("候选"+order.size()+"张；选择排序最后1张校验（未保证按时间最新）");
  saveJpeg(order.get(order.size()-1));
 }
 private void shutterAndFetch(boolean changeMode) throws Exception {
  selectWifi();
  // Scope the state-changing test to the user's confirmed C110 endpoint.
  if(!"192.168.1.254".equals(ip))throw new IOException("拍照只允许已实测的192.168.1.254；请先连接C110的Wi-Fi");
  Result device=get("/?custom=1&cmd=3012");
  Integer deviceStatus=ProbeUtil.tagInt(device.text(),"Status");
  if(device.status!=200||device.body.length==0||(deviceStatus!=null&&deviceStatus!=0))
   throw new IOException("3012设备探测未通过；没有发送拍照");
  // Actual 3037 status -256 matches COMMAND_NOT_FOUND in Novatek docs.
  // The command is not supported by this camera's observed firmware: don't call it.
  try{
   Result sd=get("/?custom=1&cmd=3024");
   Integer sdStatus=ProbeUtil.tagInt(sd.text(),"Status");
   Integer sdValue=ProbeUtil.tagInt(sd.text(),"Value");
   line("SD卡3024：HTTP"+sd.status+"；Status="+sdStatus+"（"+ProbeUtil.statusName(sdStatus)+
     "）；Value="+(sdValue==null?"未提供":sdValue));
   if(sd.status==200&&sdStatus!=null&&sdStatus==0&&sdValue!=null&&sdValue==0)
    throw new IOException("SD卡状态明确显示未插卡，停止拍照");
  }catch(IOException e){if(e.getMessage()!=null&&e.getMessage().contains("未插卡"))throw e;
   line("SD卡查询不可用；不把查询失败当成相机禁止拍照");}
  try{
   Result supported=get("/?custom=1&cmd=3002");
   Integer sc=ProbeUtil.tagInt(supported.text(),"Status");
   line("能力3002：HTTP"+supported.status+"；Status="+sc+"；列出1001="+
    supported.text().contains("<Cmd>1001</Cmd>")+"；列出3001="+
    supported.text().contains("<Cmd>3001</Cmd>")+"；不记录完整回包");
  }catch(IOException e){line("能力3002只读查询失败，不擅自猜测能力");}
  Set<String> oldFiles=list(true);
  baseline=new LinkedHashSet<>(oldFiles);baselineIp=ip;
  line("快门前记录旧图："+oldFiles.size()+"张；不会将旧图判作新图");
  long totalStart=SystemClock.elapsedRealtime();
  if(changeMode){
   // Only after a separate explicit UI confirmation; never retry a mode change.
   Result switched=get("/?custom=1&cmd=3001&par=0");
   Integer switchStatus=ProbeUtil.tagInt(switched.text(),"Status");
   line("切照片模式3001/par=0：HTTP"+switched.status+"，"+switched.ms+"ms；Status="+
    switchStatus+"（"+ProbeUtil.statusName(switchStatus)+"）");
   if(switched.status!=200||switchStatus==null||switchStatus!=0){
    state("切照片模式未确认成功，不发送快门");
    return;
   }
   // Photo readiness is a read-only query; not all firmware provides it.
   try{
    Result free=get("/?custom=1&cmd=1003");
    Integer freeStatus=ProbeUtil.tagInt(free.text(),"Status");
    Integer freeValue=ProbeUtil.tagInt(free.text(),"Value");
    line("可拍张数1003：HTTP"+free.status+"，Status="+freeStatus+
      "（"+ProbeUtil.statusName(freeStatus)+"），Value="+freeValue);
    if(free.status==200&&freeStatus!=null&&freeStatus==0&&freeValue!=null&&freeValue<=0){
     state("相机报告可拍张数为0，停止拍照");
     return;
    }
   }catch(IOException e){line("1003只读查询不可用，仍遵守一次快门限制");}
  }
  long start=SystemClock.elapsedRealtime();
  // Exactly one shutter command per explicit confirmation. Never auto-retry.
  Result shot=get("/?custom=1&cmd=1001");
  Integer status=ProbeUtil.tagInt(shot.text(),"Status");
  line("拍照1001：HTTP"+shot.status+"，"+shot.ms+"ms；Status="+status+
   "（"+ProbeUtil.statusName(status)+"），不记录照片路径");
  if(shot.status!=200||status!=null&&status!=0){
   line("快门返回失败，未自动重试；本次不再浪费20秒轮询旧图");
   state("快门执行失败："+status+"（"+ProbeUtil.statusName(status)+"）；请复制日志");
   return;
  }
  state("1001未报告失败，等待新JPEG写盘…");
  int failures=0;boolean detected=false;
  while(SystemClock.elapsedRealtime()-start<20000){
   Set<String> files;
   try{files=list(false);failures=0;}
   catch(IOException e){
    if(++failures>=3)throw new IOException("三次连续读取列表失败，请检查相机Wi-Fi",e);
    line("列表暂时无法读取："+failures+"/3");
    Thread.sleep(450);continue;
   }
   Set<String> added=new LinkedHashSet<>(files);added.removeAll(oldFiles);
   if(!added.isEmpty()){
    if(!detected){detected=true;line("首次发现新增JPEG："+added.size()+"张，距快门"+(SystemClock.elapsedRealtime()-start)+"ms");}
    ArrayList<String> order=new ArrayList<>(added);Collections.sort(order);
    String candidate=order.get(order.size()-1);
    for(int attempt=1;attempt<=5;attempt++){
     try{
      saveJpeg(candidate);
      baseline=new LinkedHashSet<>(files);baselineIp=ip;
      line("快门至JPEG保存："+(SystemClock.elapsedRealtime()-start)+"ms；含模式切换总耗时："+(SystemClock.elapsedRealtime()-totalStart)+"ms");
      ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
      line("相机Wi-Fi仍可访问："+(cm.getNetworkCapabilities(wifi)!=null));
      return;
     }catch(IOException e){
      line("JPEG尚未完整可读："+attempt+"/5（"+e.getClass().getSimpleName()+"）");
      if(attempt==5)throw e;
      Thread.sleep(550);
     }
    }
   }
   Thread.sleep(450);
  }
  line("20秒无本次新增JPEG；HTTP200不能证明已经拍照");
  state("未确认新图，请复制诊断日志");
 }
 private void saveJpeg(String path) throws IOException {
  if(ProbeUtil.path(path,"/DCIM/",ip,false)==null)throw new IOException("不允许非DCIM照片路径");
  long start=SystemClock.elapsedRealtime();
  File temp=File.createTempFile("c110_temp_",".part",getCacheDir());
  HttpURLConnection conn=null;
  try{
   conn=(HttpURLConnection)wifi.openConnection(new URL("http",ip,80,path));
   conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(3500);conn.setReadTimeout(7000);
   conn.setUseCaches(false);conn.setRequestProperty("Accept-Encoding","identity");
   int http=conn.getResponseCode();if(http!=200)throw new IOException("JPEG HTTP"+http);
   long expected=conn.getContentLengthLong();
   if(expected>LIMIT_PHOTO)throw new IOException("JPEG超过40MB");
   int size=0,last=-1,prev=-1,firstCount=0;byte[] first=new byte[3];
   try(InputStream in=conn.getInputStream();OutputStream out=new FileOutputStream(temp)){
    byte[] buf=new byte[16384];int n;
    while((n=in.read(buf))!=-1){
     if(size+n>LIMIT_PHOTO)throw new IOException("下载超过40MB限制");
     for(int j=0;j<n;j++){int x=buf[j]&255;if(firstCount<3)first[firstCount++]=(byte)x;prev=last;last=x;}
     out.write(buf,0,n);size+=n;
    }
   }
   if(size<256||!ProbeUtil.jpegHeaderTail(first,(prev<<8)|last))throw new IOException("JPEG头尾无效/照片尚未写完");
   if(expected>=0&&expected!=size)throw new IOException("JPEG长度与响应头不一致");
   BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(temp.getAbsolutePath(),o);
   if(o.outWidth<=0||o.outHeight<=0)throw new IOException("JPEG宽高解析失败");
   long elapsed=SystemClock.elapsedRealtime()-start;
   saveToAlbum(temp);
   line("JPEG已完整校验："+size+"字节；"+o.outWidth+"×"+o.outHeight+"；用时"+elapsed+"ms");
   line("已存至系统相册 Pictures/BlueStar_C110_Test；不上传网络");
   state("取图成功："+o.outWidth+"×"+o.outHeight+"；请复制诊断");
  }finally{if(conn!=null)conn.disconnect();if(!temp.delete())temp.deleteOnExit();}
 }
 private void saveToAlbum(File temp) throws IOException {
  if(Build.VERSION.SDK_INT<29){
   File root=new File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),"BlueStar_C110_Test");
   if(!root.exists()&&!root.mkdirs())throw new IOException("不能创建相册目录");
   try(InputStream in=new FileInputStream(temp);OutputStream out=new FileOutputStream(new File(root,"C110_"+System.currentTimeMillis()+".jpg"))){
    byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
   }return;
  }
  ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,"C110_Test_"+System.currentTimeMillis()+".jpg");
  v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
  v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/BlueStar_C110_Test");
  v.put(MediaStore.Images.Media.IS_PENDING,1);
  Uri dest=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);
  if(dest==null)throw new IOException("系统图库创建失败");
  try{
   try(InputStream in=new FileInputStream(temp);OutputStream out=getContentResolver().openOutputStream(dest)){
    if(out==null)throw new IOException("图库文件不可写");
    byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
   }
   v.clear();v.put(MediaStore.Images.Media.IS_PENDING,0);
   getContentResolver().update(dest,v,null,null);
  }catch(Exception e){getContentResolver().delete(dest,null,null);throw new IOException("保存图库失败",e);}
 }
 @Override protected void onDestroy(){pool.shutdownNow();super.onDestroy();}
}
