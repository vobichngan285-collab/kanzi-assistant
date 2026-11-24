package com.vobich.bluestar.cloudprobe;

import android.content.Context;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Separate opaque task screen. Home never appears beneath a translucent dialog.
 * Only task tiles belong here: NO reading shortcut, camera panel, settings, footer
 * navigation, permanent pencil buttons, delete bars or diagnostic copy. */
public final class TaskDesktopActivity extends Activity {
    private static final int BACKGROUND=0xFF091B32;
    private static final int FOREGROUND=0xFFF3F8FF;
    private static final int MUTED=0xFFB9DCF8;
    private static final int COLUMNS=4;
    private static final int PAGE_SIZE=12;
    private static final long LONG_PRESS_MS=480L;

    private final List<TaskCatalog.Task> tiles=new ArrayList<>();
    private List<TaskCatalog.Task> catalog=new ArrayList<>();
    private GridLayout grid;
    private LinearLayout pager;
    private TextView pageIndicator;
    private FrameLayout screen;
    private int page=0,pages=1;
    private boolean dragging;
    private int dragSource=-1,highlighted=-1;
    private Runnable pendingHold;
    private View holdOwner;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(BlueStarFontScale.wrap(base));
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(0);
        screen=new FrameLayout(this);
        screen.setBackgroundColor(BACKGROUND);
        screen.setOnApplyWindowInsetsListener((v,insets)->{
            int top=insets.getSystemWindowInsetTop();
            int bottom=insets.getSystemWindowInsetBottom();
            if(v.getPaddingTop()!=top||v.getPaddingBottom()!=bottom)
                v.setPadding(0,top,0,bottom);
            return insets;
        });
        setContentView(screen);
        screen.requestApplyInsets();

        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(11),dp(11),dp(11),dp(8));
        screen.addView(content,new FrameLayout.LayoutParams(-1,-1));

        FrameLayout header=new FrameLayout(this);
        content.addView(header,new LinearLayout.LayoutParams(-1,dp(71)));
        TextView back=text("‹ 返回",20,FOREGROUND);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(dp(4),0,dp(8),0);
        back.setContentDescription("返回上一页");
        back.setClickable(true);back.setFocusable(true);
        back.setOnClickListener(v->finish());
        FrameLayout.LayoutParams backLp=new FrameLayout.LayoutParams(dp(108),-1,Gravity.LEFT);
        header.addView(back,backLp);
        TextView heading=text("朗读任务",25,FOREGROUND);
        heading.setTypeface(null,Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        // Title centered on the actual screen, not the remaining space after back.
        header.addView(heading,new FrameLayout.LayoutParams(-2,-1,Gravity.CENTER));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0,dp(28),0,dp(12));
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));

        grid=new GridLayout(this);
        grid.setColumnCount(COLUMNS);
        grid.setRowCount(PAGE_SIZE/COLUMNS);
        grid.setUseDefaultMargins(false);
        grid.setOnDragListener((v,event)->onGridDrag(event));
        body.addView(grid,new LinearLayout.LayoutParams(-1,-2));

        pager=new LinearLayout(this);
        pager.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pagerLp=new LinearLayout.LayoutParams(-1,dp(58));
        pagerLp.topMargin=dp(7);
        body.addView(pager,pagerLp);
        TextView previous=text("‹",28,MUTED);
        previous.setGravity(Gravity.CENTER);
        previous.setClickable(true);previous.setFocusable(true);
        previous.setContentDescription("上一页任务");
        previous.setOnClickListener(v->render(page-1));
        previous.setOnDragListener((v,e)->onPagerDrag(e,-1));
        pager.addView(previous,new LinearLayout.LayoutParams(dp(62),dp(50)));
        pageIndicator=text("",14,MUTED);
        pageIndicator.setGravity(Gravity.CENTER);
        pager.addView(pageIndicator,new LinearLayout.LayoutParams(dp(70),dp(50)));
        TextView next=text("›",28,MUTED);
        next.setGravity(Gravity.CENTER);
        next.setClickable(true);next.setFocusable(true);
        next.setContentDescription("下一页任务");
        next.setOnClickListener(v->render(page+1));
        next.setOnDragListener((v,e)->onPagerDrag(e,1));
        pager.addView(next,new LinearLayout.LayoutParams(dp(62),dp(50)));

        refresh();
    }

    private int dp(int px){
        return Math.round(px*getResources().getDisplayMetrics().density);
    }
    private TextView text(String label,int sp,int color){
        TextView t=new TextView(this);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }
    private GradientDrawable rounded(int color,boolean selected){
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(17));
        if(selected)bg.setStroke(dp(3),0xFFFFCF65);
        return bg;
    }
    private int tileColor(TaskCatalog.Task task){
        if(task==null)return 0xFF355D86;
        switch(task.id){
            case "ocr":return 0xFF1168CF;
            case "menu":return 0xFFB65D25;
            case "hospital":return 0xFF21A57E;
            case "shop":return 0xFF6752BB;
            case "product":return 0xFFB27834;
            default:
                int[] colors={0xFF316AA3,0xFFCE538A,0xFF377D8E,0xFF645DAC};
                return colors[(task.id.hashCode()&Integer.MAX_VALUE)%colors.length];
        }
    }
    private void refresh(){
        catalog=TaskCatalog.all(this);
        Map<String,TaskCatalog.Task> lookup=new HashMap<>();
        for(TaskCatalog.Task task:catalog)lookup.put(task.id,task);
        tiles.clear();
        for(String id:TaskCatalog.displayOrder(this,catalog)){
            if(TaskOrder.CREATE.equals(id))tiles.add(null);
            else if(lookup.containsKey(id))tiles.add(lookup.get(id));
        }
        pages=Math.max(1,(tiles.size()+PAGE_SIZE-1)/PAGE_SIZE);
        render(Math.min(page,pages-1));
    }
    private void render(int newPage){
        if(newPage<0||newPage>=pages)return;
        cancelHold();
        clearHighlight();
        page=newPage;
        grid.removeAllViews();
        // Equal-size four-column desktop cells, with no underlying Home layout.
        int screenWidth=getResources().getDisplayMetrics().widthPixels;
        int cellWidth=(screenWidth-dp(22))/COLUMNS;
        int iconSize=Math.min(dp(74),cellWidth-dp(16));
        for(int offset=0;offset<PAGE_SIZE;offset++){
            int index=page*PAGE_SIZE+offset;
            LinearLayout slot=new LinearLayout(this);
            slot.setOrientation(LinearLayout.VERTICAL);
            slot.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(
                GridLayout.spec(offset/COLUMNS),GridLayout.spec(offset%COLUMNS));
            lp.width=cellWidth;
            lp.height=dp(133);
            grid.addView(slot,lp);
            if(index>=tiles.size())continue;
            TaskCatalog.Task task=tiles.get(index);
            boolean creation=task==null;
            boolean selected=!creation&&task.id.equals(TaskCatalog.selectedId(this));
            TextView icon=text(creation?"＋":task.icon,36,Color.WHITE);
            icon.setBackground(rounded(tileColor(task),selected));
            icon.setContentDescription(creation?"添加任务":task.name);
            LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(iconSize,iconSize);
            iconLp.topMargin=dp(6);
            slot.addView(icon,iconLp);
            TextView label=text(creation?"添加任务":task.name,14,FOREGROUND);
            label.setTypeface(null,Typeface.BOLD);
            label.setMaxLines(2);
            label.setPadding(dp(1),dp(7),dp(1),0);
            slot.addView(label,new LinearLayout.LayoutParams(-1,dp(44)));
            slot.setContentDescription((creation?"添加任务":task.name)+
                (selected?"，当前任务":"")+
                "；点击选择，长按打开编辑删除选项，长按拖动可调整位置");
            slot.setFocusable(true);slot.setClickable(true);
            // Expose a real Android accessibility long-click action to screen readers.
            // Touch users still get the held-then-drag gesture below.
            slot.setOnLongClickListener(v->{
                if(creation)editTask(null);
                else showTaskActions(task);
                return true;
            });
            slot.setOnClickListener(v->{
                if(dragging)return;
                if(creation)editTask(null);
                else{
                    TaskCatalog.select(this,task.id);
                    Toast.makeText(this,"当前任务："+task.name,Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
            attachTouch(slot,icon,index,task);
        }
        pager.setVisibility(pages>1?View.VISIBLE:View.GONE);
        pageIndicator.setText((page+1)+" / "+pages);
    }
    private void cancelHold(){
        if(pendingHold!=null&&holdOwner!=null)holdOwner.removeCallbacks(pendingHold);
        pendingHold=null;holdOwner=null;
    }
    private void clearHighlight(){
        if(highlighted>=0&&grid!=null){
            int offset=highlighted-page*PAGE_SIZE;
            if(offset>=0&&offset<grid.getChildCount()){
                View child=grid.getChildAt(offset);
                child.setAlpha(1f);
                child.setScaleX(1f);child.setScaleY(1f);
            }
        }
        highlighted=-1;
    }
    private void highlight(int index){
        if(index==highlighted)return;
        clearHighlight();
        int offset=index-page*PAGE_SIZE;
        if(offset<0||offset>=grid.getChildCount())return;
        highlighted=index;
        View child=grid.getChildAt(offset);
        child.setAlpha(.72f);
        child.setScaleX(1.04f);
        child.setScaleY(1.04f);
    }
    private int targetAt(float x,float y){
        for(int i=0;i<grid.getChildCount();i++){
            View child=grid.getChildAt(i);
            if(x>=child.getLeft()&&x<child.getRight()&&
               y>=child.getTop()&&y<child.getBottom()){
                int index=page*PAGE_SIZE+i;
                return Math.min(index,tiles.size()-1);
            }
        }
        return -1;
    }
    private List<String> orderedIds(){
        List<String> ids=new ArrayList<>();
        for(TaskCatalog.Task task:tiles)
            ids.add(task==null?TaskOrder.CREATE:task.id);
        return ids;
    }
    private void moveAndSave(int from,int to){
        if(from<0||to<0||from>=tiles.size()||to>=tiles.size())return;
        if(from!=to){
            List<String> desired=TaskOrder.move(orderedIds(),from,to);
            TaskCatalog.saveDisplayOrder(this,desired,catalog);
        }
        refresh();
    }
    private boolean onGridDrag(DragEvent e){
        switch(e.getAction()){
            case DragEvent.ACTION_DRAG_STARTED:
                return dragging&&e.getLocalState() instanceof Integer;
            case DragEvent.ACTION_DRAG_LOCATION:
                if(dragging)highlight(targetAt(e.getX(),e.getY()));
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                clearHighlight();
                return true;
            case DragEvent.ACTION_DROP:
                int target=targetAt(e.getX(),e.getY());
                int source=dragSource;
                dragging=false;dragSource=-1;
                clearHighlight();
                if(source>=0&&target>=0){moveAndSave(source,target);return true;}
                return false;
            case DragEvent.ACTION_DRAG_ENDED:
                dragging=false;dragSource=-1;clearHighlight();
                return true;
            default:return true;
        }
    }
    private boolean onPagerDrag(DragEvent e,int direction){
        switch(e.getAction()){
            case DragEvent.ACTION_DRAG_STARTED:
                return dragging;
            case DragEvent.ACTION_DRAG_ENTERED:
                if(dragging)render(page+direction);
                return true;
            case DragEvent.ACTION_DROP:
                int target=Math.min(tiles.size()-1,
                    page*PAGE_SIZE+(direction<0?PAGE_SIZE-1:0));
                int source=dragSource;
                dragging=false;dragSource=-1;
                if(source>=0){moveAndSave(source,target);return true;}
                return false;
            default:return true;
        }
    }
    private void attachTouch(LinearLayout slot,TextView icon,int index,
                             TaskCatalog.Task task){
        slot.setOnTouchListener(new View.OnTouchListener(){
            float firstX,firstY;
            boolean held,dragStarted;
            @Override public boolean onTouch(View view,MotionEvent e){
                switch(e.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        firstX=e.getX();firstY=e.getY();
                        held=false;dragStarted=false;
                        cancelHold();
                        Runnable hold=()->{
                            if(isFinishing()||isDestroyed())return;
                            held=true;
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            icon.setScaleX(1.06f);icon.setScaleY(1.06f);
                        };
                        pendingHold=hold;holdOwner=view;
                        view.postDelayed(hold,LONG_PRESS_MS);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx=e.getX()-firstX,dy=e.getY()-firstY;
                        if(!held&&Math.abs(dx)+Math.abs(dy)>dp(20))cancelHold();
                        if(held&&!dragStarted&&Math.abs(dx)+Math.abs(dy)>dp(14)){
                            dragging=true;dragSource=index;
                            ClipData data=ClipData.newPlainText("task-id",
                                task==null?TaskOrder.CREATE:task.id);
                            dragStarted=view.startDragAndDrop(data,
                                new View.DragShadowBuilder(icon),Integer.valueOf(index),0);
                            if(!dragStarted){dragging=false;dragSource=-1;}
                            icon.setScaleX(1f);icon.setScaleY(1f);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        cancelHold();
                        icon.setScaleX(1f);icon.setScaleY(1f);
                        if(dragStarted||dragging)return true;
                        if(held){
                            if(task==null)editTask(null);
                            else showTaskActions(task);
                            return true;
                        }
                        float totalX=e.getX()-firstX,totalY=e.getY()-firstY;
                        if(Math.abs(totalX)>dp(55)&&Math.abs(totalX)>Math.abs(totalY)){
                            render(page+(totalX<0?1:-1));return true;
                        }
                        if(Math.abs(totalX)<dp(16)&&Math.abs(totalY)<dp(16))
                            view.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        cancelHold();
                        icon.setScaleX(1f);icon.setScaleY(1f);
                        return true;
                    default:return true;
                }
            }
        });
    }
    private void showTaskActions(TaskCatalog.Task task){
        String[] actions=task.custom?
            new String[]{"编辑任务","删除任务"}:
            new String[]{"编辑任务要求","隐藏这个任务"};
        new BlueStarDialogChoices.PlatformBuilder(this).setTitle(task.name)
            .setItems(actions,(dialog,choice)->{
                if(choice==0)editTask(task);
                else confirmRemoval(task);
            }).setNegativeButton("取消",null).show();
    }
    private void confirmRemoval(TaskCatalog.Task task){
        new BlueStarDialogChoices.PlatformBuilder(this)
            .setTitle(task.custom?"删除任务":"隐藏任务")
            .setMessage(task.custom?"确定删除「"+task.name+"」？":
                "将「"+task.name+"」从此页面隐藏，可在设置中的任务管理恢复。")
            .setNegativeButton("取消",null)
            .setPositiveButton(task.custom?"删除":"隐藏",(dialog,which)->{
                if(task.custom)TaskCatalog.remove(this,task.id);
                else if(!TaskCatalog.hidePreset(this,task.id)){
                    Toast.makeText(this,"至少保留一个任务",Toast.LENGTH_SHORT).show();
                    return;
                }
                refresh();
            }).show();
    }
    private void editTask(TaskCatalog.Task original){
        boolean existing=original!=null;
        boolean preset=existing&&!original.custom;
        EditText name=new EditText(this);
        name.setHint("任务名称");
        name.setSingleLine(true);
        name.setTextColor(FOREGROUND);
        name.setText(existing?original.name:"");
        if(preset)name.setEnabled(false);
        EditText prompt=new EditText(this);
        prompt.setHint("识别与分析要求");
        prompt.setTextColor(FOREGROUND);
        prompt.setMinLines(3);
        prompt.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        prompt.setText(existing?original.instruction:"");
        String[] chosen={existing?original.icon:"✦"};
        Button icon=new Button(this);
        icon.setAllCaps(false);icon.setText("图标："+chosen[0]);
        icon.setVisibility(preset?View.GONE:View.VISIBLE);
        icon.setOnClickListener(v->{
            String[] icons=TaskCatalog.iconPalette();
            new BlueStarDialogChoices.PlatformBuilder(this).setTitle("选择图标")
                .setItems(icons,(dialog,index)->{
                    chosen[0]=icons[index];icon.setText("图标："+chosen[0]);
                }).show();
        });
        LinearLayout form=new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20),dp(8),dp(20),dp(8));
        form.addView(name);form.addView(prompt);form.addView(icon);
        AlertDialog dialog=new BlueStarDialogChoices.PlatformBuilder(this)
            .setTitle(existing?"编辑任务":"添加任务")
            .setView(form).setNegativeButton("取消",null)
            .setPositiveButton("保存",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(button->{
                try{
                    if(preset)TaskCatalog.updatePreset(this,original.id,
                        prompt.getText().toString());
                    else TaskCatalog.upsert(this,existing?original.id:null,
                        name.getText().toString(),prompt.getText().toString(),chosen[0]);
                    dialog.dismiss();refresh();
                }catch(IllegalArgumentException error){
                    prompt.setError(error.getMessage());
                }
            }));
        dialog.show();
    }
    @Override protected void onDestroy(){
        cancelHold();
        super.onDestroy();
    }
}
