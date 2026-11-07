package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.util.List;

/** Full-screen Blue Star reading shelf, camera transient, and white-text reading UI. */
final class ReadingScreen {
    interface Listener {
        void capture(String bookId);
        void cancelCapture();
        void closeReading();
        void stopReading();
        void replay(String text);
        void togglePause();
        void exportBook(String bookId);
    }
    private static final int BG=0xFF091B32,PANEL=0xFF183553,ACCENT=0xFF286FCB,WHITE=Color.WHITE;
    private final Activity activity;
    private final FrameLayout host,screen;
    private final ReadingLibrary books;
    private final Listener listener;
    private String bookId="",phase="shelf",displayText="",status="";
    private ReadingLibrary.Page currentPage;
    private TextView textBody,statusLabel;
    private Button pauseButton,nextButton;
    private boolean paused,working;
    ReadingScreen(Activity activity,FrameLayout host,ReadingLibrary books,Listener listener){
        this.activity=activity;this.host=host;this.books=books;this.listener=listener;
        screen=new FrameLayout(activity);
        screen.setClickable(true);screen.setFocusable(true);
    }
    void open(){
        if(screen.getParent()==null)host.addView(screen,
            new FrameLayout.LayoutParams(-1,-1));
        showShelf();
    }
    boolean isOpen(){return screen.getParent()!=null;}
    void bringForward(){screen.bringToFront();}
    void dispose(){
        if(screen.getParent() instanceof ViewGroup)
            ((ViewGroup)screen.getParent()).removeView(screen);
        textBody=null;statusLabel=null;pauseButton=null;nextButton=null;
    }
    String bookId(){return bookId;}
    void showShelf(){
        phase="shelf";working=false;bookId="";
        listener.stopReading();render();
    }
    void openBook(String id){
        try{
            ReadingLibrary.Book book=books.get(id);
            bookId=book.id;currentPage=book.lastPage();
            displayText=currentPage==null?"":currentPage.text;
            status=currentPage==null?"尚未拍摄书页 · 摄像头已关闭":
                "第 "+currentPage.number+" 页 · 已保存到本机 · 摄像头已关闭";
            phase="page";paused=false;working=false;
            listener.stopReading();render();
        }catch(Exception e){message("打开书籍失败："+e.getMessage());}
    }
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private TextView label(String text,int sp,int color,boolean bold){
        TextView t=new TextView(activity);
        t.setText(text);t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);
        if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    private Button button(String text,int color,boolean prominent){
        Button b=new Button(activity);
        b.setText(text);b.setAllCaps(false);
        b.setTextColor(WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP,prominent?21:17);
        b.setTypeface(null,android.graphics.Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(bg(color,15));
        b.setMinHeight(0);b.setMinimumHeight(dp(prominent?66:50));
        b.setContentDescription(text);
        return b;
    }
    private void addButton(LinearLayout parent,Button b,int margin){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(margin);parent.addView(b,lp);
    }
    private void message(String s){
        android.widget.Toast.makeText(activity,s,android.widget.Toast.LENGTH_LONG).show();
    }
    private LinearLayout layout(int color){
        screen.removeAllViews();screen.setBackgroundColor(color);
        LinearLayout all=new LinearLayout(activity);
        all.setOrientation(LinearLayout.VERTICAL);
        all.setPadding(dp(14),dp(12),dp(14),dp(16));
        screen.addView(all,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("蓝星向导 · 阅读",21,WHITE,true);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(56),1f));
        Button close=button("返回",PANEL,false);
        top.addView(close,new LinearLayout.LayoutParams(dp(78),dp(50)));
        close.setOnClickListener(v->listener.closeReading());
        all.addView(top);
        return all;
    }
    private void render(){
        if("capturing".equals(phase)){renderCapturing();return;}
        LinearLayout all=layout(BG);
        ScrollView scroll=new ScrollView(activity);
        scroll.setFillViewport(true);
        all.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout content=new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4),dp(10),dp(4),dp(10));
        scroll.addView(content);
        if("shelf".equals(phase)){renderShelf(content);return;}
        renderPage(content,all);
    }
    private void renderShelf(LinearLayout content){
        content.addView(label("我的书架",26,WHITE,true));
        TextView hint=label("照片只用于当前云端识别，本机永久保存的是书页文字，不保存录音。",15,0xFFBCD8F4,false);
        hint.setPadding(0,dp(12),0,dp(12));content.addView(hint);
        Button newBook=button("＋ 新建一本书",ACCENT,true);
        addButton(content,newBook,8);
        newBook.setOnClickListener(v->createBook());
        try{
            List<ReadingLibrary.Book> all=books.list();
            if(all.isEmpty()){
                TextView empty=label("还没有书籍。点击上方按钮创建书籍并开始拍照朗读。",19,WHITE,false);
                empty.setPadding(dp(12),dp(26),dp(12),dp(26));
                content.addView(empty);return;
            }
            for(ReadingLibrary.Book b:all){
                Button item=button("📖 "+b.title+"\n已保存 "+b.pages.size()+" 页",PANEL,false);
                item.setContentDescription("打开"+b.title+"，已保存"+b.pages.size()+"页");
                item.setMinHeight(dp(85));addButton(content,item,11);
                item.setOnClickListener(v->openBook(b.id));
            }
        }catch(Exception e){message("读取书架失败："+e.getMessage());}
    }
    private void createBook(){
        EditText field=new EditText(activity);
        field.setTextColor(WHITE);field.setHint("例如：我的第一本书");
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout wrap=new LinearLayout(activity);
        wrap.setPadding(dp(22),dp(10),dp(22),0);wrap.addView(field);
        new AlertDialog.Builder(activity).setTitle("新建书籍")
            .setView(wrap).setNegativeButton("取消",null)
            .setPositiveButton("创建",(d,which)->{
                try{
                    ReadingLibrary.Book b=books.create(field.getText().toString());
                    openBook(b.id);
                }catch(Exception ex){message("创建失败："+ex.getMessage());}
            }).show();
    }
    private void renderPage(LinearLayout content,LinearLayout all){
        ReadingLibrary.Book book;
        try{book=books.get(bookId);}
        catch(Exception e){showShelf();message("无法打开书籍："+e.getMessage());return;}
        TextView name=label(book.title,24,WHITE,true);
        content.addView(name);
        statusLabel=label(status,15,0xFFBCD8F4,false);
        statusLabel.setPadding(0,dp(12),0,dp(12));
        content.addView(statusLabel);
        textBody=label(displayText.isEmpty()?"准备好后，点击下方按钮拍摄书页。":displayText,
            21,WHITE,false);
        textBody.setTextIsSelectable(true);
        textBody.setGravity(Gravity.TOP|Gravity.LEFT);
        textBody.setLineSpacing(dp(6),1f);
        textBody.setPadding(dp(14),dp(15),dp(14),dp(18));
        textBody.setBackground(bg(PANEL,16));
        content.addView(textBody,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout actions=new LinearLayout(activity);
        actions.setOrientation(LinearLayout.VERTICAL);
        all.addView(actions,new LinearLayout.LayoutParams(-1,-2));
        if("recognizing".equals(phase)){
            pauseButton=button(paused?"▶ 继续朗读":"Ⅱ 暂停朗读",PANEL,false);
            addButton(actions,pauseButton,4);
            pauseButton.setOnClickListener(v->{listener.togglePause();});
            nextButton=button("正在识别，请完成本页后拍下一张",ACCENT,true);
            nextButton.setEnabled(false);
            addButton(actions,nextButton,8);
        }else if("failed".equals(phase)){
            nextButton=button("重新拍照",ACCENT,true);
            addButton(actions,nextButton,8);
            nextButton.setOnClickListener(v->requestCapture());
        }else {
            if(currentPage!=null){
                pauseButton=button(paused?"▶ 继续朗读":"Ⅱ 暂停朗读",PANEL,false);
                addButton(actions,pauseButton,4);
                pauseButton.setOnClickListener(v->listener.togglePause());
                Button replay=button("↻ 重新朗读本页",PANEL,false);
                addButton(actions,replay,6);
                replay.setOnClickListener(v->listener.replay(displayText));
            }
            nextButton=button(currentPage==null?"拍照朗读":"下一张",ACCENT,true);
            addButton(actions,nextButton,8);
            nextButton.setOnClickListener(v->requestCapture());
        }
        LinearLayout secondary=new LinearLayout(activity);
        secondary.setGravity(Gravity.CENTER);actions.addView(secondary);
        Button shelf=button("书架",PANEL,false);
        LinearLayout.LayoutParams shelfLp=new LinearLayout.LayoutParams(0,dp(50),1f);
        shelfLp.setMargins(dp(2),dp(8),dp(2),0);secondary.addView(shelf,shelfLp);
        shelf.setOnClickListener(v->showShelf());
        Button chapters=button("章节 / 页码",PANEL,false);
        LinearLayout.LayoutParams chaptersLp=new LinearLayout.LayoutParams(0,dp(50),1f);
        chaptersLp.setMargins(dp(2),dp(8),dp(2),0);secondary.addView(chapters,chaptersLp);
        chapters.setOnClickListener(v->showContents());
        Button export=button("导出文字",PANEL,false);
        addButton(actions,export,7);export.setOnClickListener(v->listener.exportBook(bookId));
        Button delete=button("删除这本书",0xFF55304A,false);
        addButton(actions,delete,7);delete.setOnClickListener(v->confirmDelete());
    }
    private void showContents(){
        try{
            ReadingLibrary.Book b=books.get(bookId);
            if(b.pages.isEmpty()){message("还没有保存的书页");return;}
            String[] names=new String[b.pages.size()];
            for(int i=0;i<b.pages.size();i++){
                ReadingLibrary.Page p=b.pages.get(i);
                names[i]="第 "+p.number+" 页"+(p.chapter.isEmpty()?"":" · "+p.chapter);
            }
            new AlertDialog.Builder(activity).setTitle(b.title+" · 目录")
                .setItems(names,(d,position)->{
                    listener.stopReading();
                    currentPage=b.pages.get(position);
                    displayText=currentPage.text;phase="page";paused=false;working=false;
                    status="第 "+currentPage.number+" 页 · 本地保存 · 无需摄像头";
                    render();
                }).setNegativeButton("关闭",null).show();
        }catch(Exception e){message("无法显示目录："+e.getMessage());}
    }
    private void confirmDelete(){
        final String id=bookId;
        new AlertDialog.Builder(activity).setTitle("删除这本书？")
            .setMessage("将删除本机这本书的所有识别文字，无法撤销。")
            .setNegativeButton("取消",null)
            .setPositiveButton("确认删除",(d,w)->{
                try{listener.stopReading();books.delete(id);showShelf();}
                catch(Exception e){message("删除失败："+e.getMessage());}
            }).show();
    }
    private void requestCapture(){
        if(bookId.isEmpty()||working)return;
        listener.capture(bookId);
    }
    private void renderCapturing(){
        LinearLayout all=layout(0xB8091B32);
        TextView stage=label("正在唤醒摄像头\n自动获取下一张新照片…",24,WHITE,true);
        stage.setGravity(Gravity.CENTER);
        all.addView(stage,new LinearLayout.LayoutParams(-1,0,1f));
        Button cancel=button("取消拍照",PANEL,true);
        addButton(all,cancel,8);
        cancel.setOnClickListener(v->listener.cancelCapture());
    }
    void showCapturing(){
        phase="capturing";working=true;paused=false;displayText="";
        render();
    }
    void showRecognizing(){
        phase="recognizing";working=true;displayText="";
        status="正在识别并逐行朗读 · 摄像头已关闭";
        render();
    }
    void appendLine(String line){
        if(!"recognizing".equals(phase)||line==null||line.trim().isEmpty())return;
        displayText+=((displayText.isEmpty())?"":"\n")+line.trim();
        if(textBody!=null)textBody.setText(displayText);
    }
    void saved(ReadingLibrary.Page page){
        currentPage=page;displayText=page.text;phase="page";
        working=false;paused=false;
        status="第 "+page.number+" 页 · 已保存文字 · 摄像头已关闭";
        render();
    }
    void failure(String why){
        phase="failed";working=false;paused=false;
        status="本次识别未保存 · 摄像头已关闭";
        displayText=why==null?"本页识别失败，请重新拍照。":why;
        render();
    }
    void cancelled(){
        working=false;paused=false;openBook(bookId);
    }
    void setPaused(boolean value){
        paused=value;
        if(pauseButton!=null)pauseButton.setText(paused?"▶ 继续朗读":"Ⅱ 暂停朗读");
    }
}
