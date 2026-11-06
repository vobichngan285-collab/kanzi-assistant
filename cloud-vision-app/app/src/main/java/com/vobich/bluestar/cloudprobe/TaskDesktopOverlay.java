package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Frameless launcher-like task chooser, shown only from the left dock button. */
final class TaskDesktopOverlay {
    interface Listener{
        void selected(TaskCatalog.Task task);
        void createTask();
    }
    private final Activity activity;
    private final Dialog dialog;
    private final List<TaskCatalog.Task> items;
    private final String selected;
    private final Listener listener;
    private LinearLayout center;
    private GridLayout grid;
    private TextView pageLabel;
    private int page;
    private final int pages;

    static void show(Activity a,List<TaskCatalog.Task> tasks,String selectedId,Listener listener){
        new TaskDesktopOverlay(a,tasks,selectedId,listener).display();
    }
    private TaskDesktopOverlay(Activity a,List<TaskCatalog.Task> tasks,String selectedId,Listener callback){
        activity=a;selected=selectedId;listener=callback;
        items=new ArrayList<>();
        for(TaskCatalog.Task t:tasks)if(!t.custom)items.add(t);
        // Always show creation entry in the first page, followed by saved custom tasks.
        items.add(null);
        for(TaskCatalog.Task t:tasks)if(t.custom)items.add(t);
        pages=Math.max(1,(items.size()+8)/9);
        dialog=new Dialog(a);
    }
    private int dp(int v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int stroke){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(18));
        if(stroke!=0)d.setStroke(dp(2),stroke);
        return d;
    }
    private TextView label(String str,int sp,int color){
        TextView t=new TextView(activity);t.setText(str);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);
        t.setTextColor(color);t.setGravity(Gravity.CENTER);
        return t;
    }
    private void display(){
        LinearLayout overlay=new LinearLayout(activity);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(dp(10),dp(12),dp(10),dp(12));
        // No enclosing box; each task icon is its own touch target.
        center=new LinearLayout(activity);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setPadding(dp(2),dp(8),dp(2),dp(8));
        LinearLayout top=new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("选择任务",23,Color.WHITE);
        title.setTypeface(null,android.graphics.Typeface.BOLD);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(58),1f));
        TextView close=label("×",32,Color.WHITE);
        close.setContentDescription("关闭任务选择");
        close.setBackground(bg(0x77203B62,0));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(50),dp(50));
        cp.leftMargin=dp(5);top.addView(close,cp);
        close.setOnClickListener(v->dialog.dismiss());
        center.addView(top);

        grid=new GridLayout(activity);grid.setColumnCount(3);
        grid.setRowCount(3);grid.setUseDefaultMargins(false);
        center.addView(grid);
        LinearLayout pager=new LinearLayout(activity);
        pager.setGravity(Gravity.CENTER);
        Button prev=new Button(activity);
        prev.setText("‹");prev.setAllCaps(false);
        prev.setTextColor(Color.WHITE);prev.setTextSize(25);
        prev.setBackgroundTintList(ColorStateList.valueOf(0x88324D74));
        pager.addView(prev,new LinearLayout.LayoutParams(dp(58),dp(48)));
        pageLabel=label("",15,0xFFE0EEFF);
        pager.addView(pageLabel,new LinearLayout.LayoutParams(dp(96),dp(48)));
        Button next=new Button(activity);
        next.setText("›");next.setAllCaps(false);
        next.setTextColor(Color.WHITE);next.setTextSize(25);
        next.setBackgroundTintList(ColorStateList.valueOf(0x88324D74));
        pager.addView(next,new LinearLayout.LayoutParams(dp(58),dp(48)));
        prev.setOnClickListener(v->render(page-1));
        next.setOnClickListener(v->render(page+1));
        center.addView(pager);
        pager.setVisibility(pages>1?View.VISIBLE:View.GONE);
        overlay.addView(center,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(overlay);
        Window window=dialog.getWindow();
        if(window!=null){
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params=window.getAttributes();
            params.width=WindowManager.LayoutParams.MATCH_PARENT;
            params.height=WindowManager.LayoutParams.MATCH_PARENT;
            params.gravity=Gravity.CENTER;
            params.dimAmount=.45f;
            window.setAttributes(params);
        }
        render(0);
        dialog.show();
        if(dialog.getWindow()!=null)
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void render(int newPage){
        if(newPage<0||newPage>=pages)return;
        page=newPage;grid.removeAllViews();
        int begin=page*9;
        int screenWidth=activity.getResources().getDisplayMetrics().widthPixels;
        int cell=Math.min(dp(118),Math.max(dp(94),(screenWidth-dp(36))/3));
        for(int j=0;j<9;j++){
            int idx=begin+j;
            LinearLayout slot=new LinearLayout(activity);
            slot.setOrientation(LinearLayout.VERTICAL);
            slot.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams layout=new GridLayout.LayoutParams(
                GridLayout.spec(j/3),GridLayout.spec(j%3));
            layout.width=cell;layout.height=dp(122);
            grid.addView(slot,layout);
            if(idx>=items.size())continue;
            final TaskCatalog.Task task=items.get(idx);
            boolean creation=task==null;
            int[] colors={0xFF245D92,0xFF92522B,0xFF247F78,0xFF504E9B,0xFF936530,0xFF386786,0xFF64729F,0xFF37716D,0xFF6C547A};
            int color=colors[idx%colors.length];
            boolean active=!creation&&task.id.equals(selected);
            TextView icon=label(creation?"＋":task.icon,34,Color.WHITE);
            icon.setBackground(bg(color,active?0xFFF8D46A:0));
            slot.addView(icon,new LinearLayout.LayoutParams(dp(69),dp(69)));
            TextView name=label(creation?"自定义任务":task.name,14,Color.WHITE);
            name.setMaxLines(2);name.setPadding(dp(2),dp(7),dp(2),0);
            slot.addView(name);
            slot.setContentDescription((creation?"新建自定义任务":task.name)+(active?"，当前任务":""));
            slot.setFocusable(true);slot.setClickable(true);
            slot.setOnClickListener(v->{
                dialog.dismiss();
                if(creation)listener.createTask();
                else listener.selected(task);
            });
            // Allow horizontal swipe on the icon target without firing click.
            slot.setOnTouchListener(new View.OnTouchListener(){
                float downX,downY;
                @Override public boolean onTouch(View v,MotionEvent e){
                    switch(e.getActionMasked()){
                        case MotionEvent.ACTION_DOWN:
                            downX=e.getX();downY=e.getY();return true;
                        case MotionEvent.ACTION_UP:
                            float dx=e.getX()-downX,dy=e.getY()-downY;
                            if(Math.abs(dx)>dp(55)&&Math.abs(dx)>Math.abs(dy)){
                                render(page+(dx<0?1:-1));return true;
                            }
                            if(Math.abs(dx)<dp(16)&&Math.abs(dy)<dp(16))v.performClick();
                            return true;
                        case MotionEvent.ACTION_CANCEL:return true;
                        default:return true;
                    }
                }
            });
        }
        if(pageLabel!=null)pageLabel.setText((page+1)+" / "+pages);
    }
}
