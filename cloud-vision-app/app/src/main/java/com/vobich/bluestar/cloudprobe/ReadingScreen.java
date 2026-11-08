package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.util.List;

/** Full-screen Blue Star reading shelf, camera transient, and white-text reading UI. */
final class ReadingScreen {
    interface Listener {
        void enterPreview(String bookId);
        void takePhoto(String bookId);
        void cancelCapture();
        void closeReading();
        void stopReading();
        void replay(String text);
        void togglePause();
        void exportBook(String bookId);
        String lastBookId();
        void selectedBook(String bookId);
    }
    private static final int BG=0xFF091B32,PANEL=0xFF183553,ACCENT=0xFF286FCB,
        MARK=0xFF952D40,WHITE=Color.WHITE;
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
        showEntry();
    }
    boolean isOpen(){return screen.getParent()!=null;}
    void bringForward(){screen.bringToFront();}
    void dispose(){
        if(screen.getParent() instanceof ViewGroup)
            ((ViewGroup)screen.getParent()).removeView(screen);
        textBody=null;statusLabel=null;pauseButton=null;nextButton=null;
    }
    String bookId(){return bookId;}
    void showEntry(){
        listener.stopReading();
        phase="entry";working=false;paused=false;bookId="";
        currentPage=null;displayText="";render();
    }
    /** Continue means open page index, never activate camera or OCR. */
    private void continueBook(){
        try{
            List<ReadingLibrary.Book> all=books.list();
            if(all.isEmpty()){createBook();return;}
            String previous=listener.lastBookId();
            ReadingLibrary.Book choice=null;
            for(ReadingLibrary.Book b:all)
                if(b.id.equals(previous)){choice=b;break;}
            if(choice==null)choice=all.get(0);
            showBookPages(choice.id);
        }catch(Exception e){message("打开阅读记录失败："+e.getMessage());}
    }
    void showShelf(){
        phase="shelf";working=false;bookId="";
        listener.stopReading();render();
    }
    void openBook(String id){
        try{
            ReadingLibrary.Book book=books.get(id);
            if(book.lastPage()==null){showBookPages(id);return;}
            showSavedPage(id,book.lastPage().number);
        }catch(Exception e){message("打开书籍失败："+e.getMessage());}
    }
    /** Ordered by successful OCR captures, never by guessed chapters. */
    void showBookPages(String id){
        try{
            ReadingLibrary.Book book=books.get(id);
            listener.stopReading();
            listener.selectedBook(id);
            bookId=id;currentPage=null;displayText="";
            phase="pages";paused=false;working=false;render();
        }catch(Exception e){message("打开书页失败："+e.getMessage());}
    }
    private void showSavedPage(String id,int number){
        try{
            ReadingLibrary.Book book=books.get(id);
            for(ReadingLibrary.Page page:book.pages){
                if(page.number!=number)continue;
                listener.stopReading();
                listener.selectedBook(id);
                bookId=id;currentPage=page;displayText=page.text;
                phase="page";paused=false;working=false;
                status=page.displayName()+" · 已保存";
                render();return;
            }
            message("指定书页已被删除");showBookPages(id);
        }catch(Exception e){message("打开书页失败："+e.getMessage());}
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
        Button close=button("‹",PANEL,false);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP,30);
        close.setContentDescription("返回蓝星向导主界面");
        top.addView(close,new LinearLayout.LayoutParams(dp(54),dp(52)));
        close.setOnClickListener(v->{
            if("entry".equals(phase))listener.closeReading();
            else if("page".equals(phase)||"failed".equals(phase)||
                    "recognizing".equals(phase))showBookPages(bookId);
            else showEntry();
        });
        TextView title=label("蓝星向导 · 阅读",20,WHITE,true);
        title.setPadding(dp(10),0,0,0);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(56),1f));
        if("page".equals(phase)||"recognizing".equals(phase)||
            "failed".equals(phase)){
            Button menu=button("☰",PANEL,false);
            menu.setTextSize(TypedValue.COMPLEX_UNIT_SP,24);
            menu.setContentDescription("更多阅读功能：书架、书页列表、导出、删除");
            top.addView(menu,new LinearLayout.LayoutParams(dp(54),dp(52)));
            menu.setOnClickListener(this::showPageMenu);
        }
        all.addView(top);
        return all;
    }
    /** Ordinary Android anchored menu; only reading controls stay visible. */
    private void showPageMenu(View anchor){
        if(bookId.isEmpty())return;
        PopupMenu popup=new PopupMenu(activity,anchor);
        popup.getMenu().add(0,1,0,"书架");
        popup.getMenu().add(0,2,1,"书页列表");
        popup.getMenu().add(0,3,2,"导出文字");
        popup.getMenu().add(0,4,3,"删除这本书");
        popup.setOnMenuItemClickListener(item->{
            switch(item.getItemId()){
                case 1: showShelf();return true;
                case 2: showContents();return true;
                case 3: listener.exportBook(bookId);return true;
                case 4: confirmDelete();return true;
                default:return false;
            }
        });
        popup.show();
    }
    private void render(){
        if("preview".equals(phase)){renderPreview();return;}
        if("capturing".equals(phase)){renderCapturing();return;}
        LinearLayout all=layout(BG);
        ScrollView scroll=new ScrollView(activity);
        scroll.setFillViewport(true);
        all.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout content=new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4),dp(10),dp(4),dp(10));
        scroll.addView(content);
        if("entry".equals(phase)){renderEntry(content);return;}
        if("shelf".equals(phase)){renderShelf(content);return;}
        if("pages".equals(phase)){renderPages(content,all);return;}
        renderPage(content,all);
    }
    /** The entrance intentionally contains no subtitle or explanatory copy. */
    private void renderEntry(LinearLayout content){
        LinearLayout choices=new LinearLayout(activity);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(-1,dp(208));
        row.topMargin=dp(36);
        content.addView(choices,row);
        Button again=button("▣\\n继续阅读".replace("\\\\n","\\n"),ACCENT,true);
        again.setTextSize(TypedValue.COMPLEX_UNIT_SP,23);
        again.setContentDescription("继续阅读：进入上次书籍的书页列表");
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,1f);
        left.rightMargin=dp(6);choices.addView(again,left);
        again.setOnClickListener(v->continueBook());
        Button fresh=button("▤\\n新阅读".replace("\\\\n","\\n"),PANEL,true);
        fresh.setTextSize(TypedValue.COMPLEX_UNIT_SP,23);
        fresh.setContentDescription("新阅读：新建书籍后手动拍摄");
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-1,1f);
        right.leftMargin=dp(6);choices.addView(fresh,right);
        fresh.setOnClickListener(v->createBook());
    }
    /** Persistent page list: exactly one row for each successful OCR photograph. */
    private void renderPages(LinearLayout content,LinearLayout all){
        ReadingLibrary.Book book;
        try{book=books.get(bookId);}
        catch(Exception e){showShelf();message("读取书页失败："+e.getMessage());return;}
        LinearLayout heading=new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label(book.title,24,WHITE,true);
        heading.addView(title,new LinearLayout.LayoutParams(0,dp(62),1f));
        Button shelf=button("书架",PANEL,false);
        heading.addView(shelf,new LinearLayout.LayoutParams(dp(72),dp(50)));
        shelf.setOnClickListener(v->showShelf());
        content.addView(heading);
        if(book.pages.isEmpty()){
            TextView empty=label("尚无书页",19,WHITE,false);
            empty.setPadding(dp(12),dp(16),0,0);
            content.addView(empty);
        }
        for(ReadingLibrary.Page page:book.pages){
            Button item=button((page.marked?"★  ":"")+page.displayName(),
                page.marked?MARK:PANEL,false);
            item.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            item.setPadding(dp(20),0,dp(12),0);
            item.setMinimumHeight(dp(68));
            item.setContentDescription(page.displayName()+
                (page.marked?"，已标记":"")+"，点击打开原文，长按管理");
            addButton(content,item,7);
            item.setOnClickListener(v->showSavedPage(book.id,page.number));
            item.setOnLongClickListener(v->{
                openPageActions(book.id,page.number);
                return true;
            });
        }
        Button continueButton=button("继续阅读",ACCENT,true);
        continueButton.setContentDescription("继续阅读，打开实时摄像头预览，再点击拍照");
        LinearLayout.LayoutParams foot=new LinearLayout.LayoutParams(-1,dp(72));
        foot.topMargin=dp(12);all.addView(continueButton,foot);
        continueButton.setOnClickListener(v->listener.enterPreview(book.id));
    }
    /** Android dialog anchored to the BOTTOM; no gratuitous fourth option. */
    private void openPageActions(String id,int number){
        try{
            ReadingLibrary.Book b=books.get(id);
            ReadingLibrary.Page chosen=null;
            for(ReadingLibrary.Page p:b.pages)
                if(p.number==number){chosen=p;break;}
            if(chosen==null){message("书页已删除");showBookPages(id);return;}
            final ReadingLibrary.Page page=chosen;
            Dialog dialog=new Dialog(activity);
            LinearLayout sheet=new LinearLayout(activity);
            sheet.setOrientation(LinearLayout.VERTICAL);
            sheet.setPadding(dp(14),dp(12),dp(14),dp(20));
            sheet.setBackground(bg(BG,18));
            Button mark=button(page.marked?"取消标记":"标记",PANEL,false);
            addButton(sheet,mark,4);
            mark.setOnClickListener(v->{
                dialog.dismiss();
                try{books.toggleMark(id,page.number);showBookPages(id);}
                catch(Exception e){message("标记失败："+e.getMessage());}
            });
            Button rename=button("重命名",PANEL,false);
            addButton(sheet,rename,8);
            rename.setOnClickListener(v->{
                dialog.dismiss();renamePage(id,page);
            });
            Button delete=button("删除",MARK,false);
            addButton(sheet,delete,8);
            delete.setOnClickListener(v->{
                dialog.dismiss();confirmDeletePage(id,page);
            });
            dialog.setContentView(sheet);
            dialog.setCanceledOnTouchOutside(true);
            Window window=dialog.getWindow();
            if(window!=null){
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams params=window.getAttributes();
                params.width=WindowManager.LayoutParams.MATCH_PARENT;
                params.height=WindowManager.LayoutParams.WRAP_CONTENT;
                params.gravity=Gravity.BOTTOM;
                params.dimAmount=0.45f;
                window.setAttributes(params);
            }
            dialog.show();
            if(window!=null)window.setLayout(-1,-2);
        }catch(Exception e){message("打开书页菜单失败："+e.getMessage());}
    }
    private void renamePage(String id,ReadingLibrary.Page page){
        EditText input=new EditText(activity);
        input.setText(page.displayName());
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        input.setTextColor(WHITE);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout wrap=new LinearLayout(activity);
        wrap.setPadding(dp(20),dp(8),dp(20),0);
        wrap.addView(input);
        new AlertDialog.Builder(activity).setTitle("重命名书页")
            .setView(wrap).setNegativeButton("取消",null)
            .setPositiveButton("保存",(d,w)->{
                try{
                    books.renamePage(id,page.number,input.getText().toString());
                    showBookPages(id);
                }catch(Exception e){message("重命名失败："+e.getMessage());}
            }).show();
    }
    private void confirmDeletePage(String id,ReadingLibrary.Page page){
        new AlertDialog.Builder(activity).setTitle("删除 "+page.displayName()+"？")
            .setMessage("只删除这一页的文字，无法撤销。")
            .setNegativeButton("取消",null)
            .setPositiveButton("确认删除",(d,w)->{
                try{books.deletePage(id,page.number);showBookPages(id);}
                catch(Exception e){message("删除书页失败："+e.getMessage());}
            }).show();
    }
    private void renderShelf(LinearLayout content){
        content.addView(label("我的书架",26,WHITE,true));
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
                item.setOnClickListener(v->showBookPages(b.id));
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
                    bookId=b.id;
                    listener.selectedBook(b.id);
                    listener.enterPreview(b.id);
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

        // RC24: only three bottom buttons, fixed order left to right.
        // Reading, chapter management, export and deletion live in the top menu.
        LinearLayout actions=new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        all.addView(actions,new LinearLayout.LayoutParams(-1,-2));

        nextButton=button("下一张",ACCENT,false);
        nextButton.setContentDescription("下一张，先打开实时摄像头预览，点击屏幕后再拍照");
        nextButton.setEnabled(!working);
        nextButton.setOnClickListener(v->requestCapture());
        LinearLayout.LayoutParams nextLp=new LinearLayout.LayoutParams(0,dp(68),1f);
        nextLp.setMargins(dp(2),dp(8),dp(3),0);
        actions.addView(nextButton,nextLp);

        pauseButton=button(paused?"继续":"暂停",PANEL,false);
        pauseButton.setContentDescription(paused?"继续朗读":"暂停朗读");
        boolean canControlSpeech="recognizing".equals(phase)||
            ("page".equals(phase)&&currentPage!=null);
        pauseButton.setEnabled(canControlSpeech);
        pauseButton.setOnClickListener(v->listener.togglePause());
        LinearLayout.LayoutParams pauseLp=new LinearLayout.LayoutParams(0,dp(68),1f);
        pauseLp.setMargins(dp(3),dp(8),dp(3),0);
        actions.addView(pauseButton,pauseLp);

        Button restart=button("重新开始",PANEL,false);
        restart.setContentDescription("从头重新朗读当前完整书页");
        // Do not restart partial OCR while new C-mode lines are still arriving.
        restart.setEnabled("page".equals(phase)&&currentPage!=null);
        restart.setOnClickListener(v->listener.replay(displayText));
        LinearLayout.LayoutParams restartLp=new LinearLayout.LayoutParams(0,dp(68),1f);
        restartLp.setMargins(dp(3),dp(8),dp(2),0);
        actions.addView(restart,restartLp);
    }

    private void showContents(){showBookPages(bookId);}
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
        listener.enterPreview(bookId);
    }
    /** Transparent surface exposes the real live camera preview behind this screen.
     * The preview is never a still image or a cached frame. ONLY the user tap takes a photo.
     */
    private void renderPreview(){
        screen.removeAllViews();
        screen.setBackgroundColor(Color.TRANSPARENT);
        TextView tap=label("",21,WHITE,false);
        tap.setContentDescription("实时摄像头预览。对准书页后双击拍照");
        tap.setClickable(true);tap.setFocusable(true);
        tap.setOnClickListener(v->listener.takePhoto(bookId));
        screen.addView(tap,new FrameLayout.LayoutParams(-1,-1));
        TextView banner=label("阅读 · 对准书页后点击屏幕拍照",19,WHITE,true);
        banner.setGravity(Gravity.CENTER);
        banner.setBackground(bg(0xED091B32,12));
        FrameLayout.LayoutParams top=new FrameLayout.LayoutParams(-1,dp(64),Gravity.TOP);
        top.setMargins(dp(10),dp(8),dp(10),0);screen.addView(banner,top);
        LinearLayout bottom=new LinearLayout(activity);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(14),dp(5),dp(14),dp(14));
        bottom.setBackground(bg(0xED091B32,16));
        Button shutter=button("拍照 · 点击屏幕也可以",ACCENT,true);
        addButton(bottom,shutter,2);
        shutter.setOnClickListener(v->listener.takePhoto(bookId));
        Button back=button("返回书页 / 取消拍照",PANEL,false);
        addButton(bottom,back,8);
        back.setOnClickListener(v->listener.cancelCapture());
        FrameLayout.LayoutParams footer=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        screen.addView(bottom,footer);
    }
    void showPreview(){
        phase="preview";working=false;paused=false;
        status="实时预览 · 请对准书页并点击屏幕拍照";
        render();
    }
    boolean isPreview(){return "preview".equals(phase);}
    private void renderCapturing(){
        LinearLayout all=layout(0xB8091B32);
        TextView stage=label("已点击拍照\n正在获取当前画面，完成后会震动并关闭摄像头…",24,WHITE,true);
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
        status=page.displayName()+" · 已保存";
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
        if(pauseButton!=null){
            pauseButton.setText(paused?"继续":"暂停");
            pauseButton.setContentDescription(paused?"继续朗读":"暂停朗读");
        }
    }
}
