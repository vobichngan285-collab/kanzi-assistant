package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Frameless 3x3 task launcher. Tap selects, swipe flips pages, and holding a
 * tile starts native Android drag-and-drop. Dragging only changes task IDs'
 * screen order, never the selected task, its prompt, its engine or camera state.
 */
final class TaskDesktopOverlay {
    interface Listener{
        void selected(TaskCatalog.Task task);
        void createTask();
    }
    private static final long HOLD_MS=480L;
    private final Activity activity;
    private final Dialog dialog;
    private final List<TaskCatalog.Task> items=new ArrayList<>();
    private final List<TaskCatalog.Task> availableTasks;
    private final String selected;
    private final Listener listener;
    private LinearLayout center;
    private GridLayout grid;
    private TextView pageLabel;
    private int page;
    private final int pages;
    private Runnable pendingHold;
    private View holdTarget;
    private boolean dragging;
    private int dragFrom=-1, highlighted=-1, arrowTarget=-1;

    static void show(Activity a,List<TaskCatalog.Task> tasks,String selectedId,Listener listener){
        new TaskDesktopOverlay(a,tasks,selectedId,listener).display();
    }

    private TaskDesktopOverlay(Activity a,List<TaskCatalog.Task> tasks,String selectedId,Listener callback){
        activity=a;selected=selectedId;listener=callback;
        availableTasks=new ArrayList<>(tasks);
        Map<String,TaskCatalog.Task> lookup=new HashMap<>();
        for(TaskCatalog.Task task:tasks)lookup.put(task.id,task);
        for(String id:TaskCatalog.displayOrder(a,tasks)){
            if(TaskOrder.CREATE.equals(id))items.add(null);
            else {
                TaskCatalog.Task task=lookup.get(id);
                if(task!=null)items.add(task);
            }
        }
        pages=Math.max(1,(items.size()+8)/9);
        dialog=new Dialog(a);
        dialog.setOnDismissListener(d->{cancelPendingHold();clearHighlight();});
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
    private int taskColor(TaskCatalog.Task task){
        if(task==null)return 0xFF386786;
        switch(task.id){
            case "ocr":return 0xFF245D92;
            case "menu":return 0xFF92522B;
            case "hospital":return 0xFF247F78;
            case "shop":return 0xFF504E9B;
            case "product":return 0xFF936530;
            default:
                int[] colors={0xFF64729F,0xFF37716D,0xFF6C547A};
                return colors[(task.id.hashCode()&0x7fffffff)%colors.length];
        }
    }

    private void display(){
        LinearLayout overlay=new LinearLayout(activity);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(dp(10),dp(12),dp(10),dp(12));
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

        grid=new GridLayout(activity);
        grid.setColumnCount(3);grid.setRowCount(3);grid.setUseDefaultMargins(false);
        grid.setOnDragListener((view,event)->onGridDrag(event));
        center.addView(grid);

        LinearLayout pager=new LinearLayout(activity);
        pager.setGravity(Gravity.CENTER);
        Button prev=new Button(activity);
        prev.setText("‹");prev.setAllCaps(false);
        prev.setContentDescription("上一页；拖动任务到这里可跨页");
        prev.setTextColor(Color.WHITE);prev.setTextSize(25);
        prev.setBackgroundTintList(ColorStateList.valueOf(0x88324D74));
        pager.addView(prev,new LinearLayout.LayoutParams(dp(58),dp(48)));
        pageLabel=label("",15,0xFFE0EEFF);
        pager.addView(pageLabel,new LinearLayout.LayoutParams(dp(96),dp(48)));
        Button next=new Button(activity);
        next.setText("›");next.setAllCaps(false);
        next.setContentDescription("下一页；拖动任务到这里可跨页");
        next.setTextColor(Color.WHITE);next.setTextSize(25);
        next.setBackgroundTintList(ColorStateList.valueOf(0x88324D74));
        pager.addView(next,new LinearLayout.LayoutParams(dp(58),dp(48)));
        prev.setOnClickListener(v->render(page-1));
        next.setOnClickListener(v->render(page+1));
        prev.setOnDragListener((view,event)->onPageArrowDrag(event,-1));
        next.setOnDragListener((view,event)->onPageArrowDrag(event,1));
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

    private void cancelPendingHold(){
        if(pendingHold!=null&&holdTarget!=null)holdTarget.removeCallbacks(pendingHold);
        pendingHold=null;holdTarget=null;
    }

    private void clearHighlight(){
        if(grid!=null&&highlighted>=0){
            int cell=highlighted-page*9;
            View target=(cell>=0&&cell<grid.getChildCount())?grid.getChildAt(cell):null;
            if(target!=null){
                target.setScaleX(1f);target.setScaleY(1f);
                target.setAlpha(1f);
            }
        }
        highlighted=-1;
    }

    private void highlight(int index){
        if(highlighted==index)return;
        clearHighlight();
        if(index<0||index>=items.size())return;
        int cell=index-page*9;
        if(cell<0||cell>=grid.getChildCount())return;
        View target=grid.getChildAt(cell);
        if(target==null)return;
        highlighted=index;
        target.setScaleX(1.10f);target.setScaleY(1.10f);
        target.setAlpha(.82f);
    }

    private int cellAt(float x,float y){
        if(x<0||y<0||x>grid.getWidth()||y>grid.getHeight())return -1;
        for(int i=0;i<grid.getChildCount();i++){
            View slot=grid.getChildAt(i);
            if(x>=slot.getLeft()&&x<slot.getRight()&&
               y>=slot.getTop()&&y<slot.getBottom()){
                return Math.min(page*9+i,items.size()-1);
            }
        }
        return -1;
    }

    private List<String> orderedIds(){
        List<String> ids=new ArrayList<>(items.size());
        for(TaskCatalog.Task task:items)
            ids.add(task==null?TaskOrder.CREATE:task.id);
        return ids;
    }

    private void moveAndSave(int from,int to){
        if(from<0||from>=items.size()||to<0||to>=items.size())return;
        if(from!=to){
            List<String> newOrder=TaskOrder.move(orderedIds(),from,to);
            Map<String,TaskCatalog.Task> map=new HashMap<>();
            for(TaskCatalog.Task task:items)if(task!=null)map.put(task.id,task);
            items.clear();
            for(String id:newOrder)items.add(TaskOrder.CREATE.equals(id)?null:map.get(id));
            TaskCatalog.saveDisplayOrder(activity,newOrder,availableTasks);
        }
        clearHighlight();
        render(page);
    }

    private boolean onGridDrag(DragEvent event){
        switch(event.getAction()){
            case DragEvent.ACTION_DRAG_STARTED:
                return dragging&&event.getLocalState() instanceof Integer;
            case DragEvent.ACTION_DRAG_ENTERED:
            case DragEvent.ACTION_DRAG_LOCATION:
                if(dragging)highlight(cellAt(event.getX(),event.getY()));
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                clearHighlight();
                return true;
            case DragEvent.ACTION_DROP:
                int target=cellAt(event.getX(),event.getY());
                if(dragging&&target>=0){moveAndSave(dragFrom,target);return true;}
                return false;
            case DragEvent.ACTION_DRAG_ENDED:
                clearHighlight();dragging=false;dragFrom=-1;
                return true;
            default:return true;
        }
    }

    private boolean onPageArrowDrag(DragEvent event,int direction){
        switch(event.getAction()){
            case DragEvent.ACTION_DRAG_STARTED:
                return dragging&&event.getLocalState() instanceof Integer;
            case DragEvent.ACTION_DRAG_ENTERED:
                clearHighlight();
                arrowTarget=-1;
                int nextPage=page+direction;
                if(nextPage<0||nextPage>=pages)return true;
                render(nextPage);
                // Releasing on the arrow itself moves into that adjacent page.
                arrowTarget=direction>0?Math.min(items.size()-1,page*9):
                    Math.min(items.size()-1,page*9+8);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                arrowTarget=-1;
                return true;
            case DragEvent.ACTION_DROP:
                if(!dragging||arrowTarget<0)return false;
                moveAndSave(dragFrom,arrowTarget);
                arrowTarget=-1;
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                clearHighlight();dragging=false;dragFrom=-1;arrowTarget=-1;
                return true;
            default:return true;
        }
    }

    private void attachTouch(LinearLayout slot,TextView icon,int index){
        slot.setOnTouchListener(new View.OnTouchListener(){
            float downX,downY;
            final boolean[] longStarted={false};
            @Override public boolean onTouch(View v,MotionEvent e){
                switch(e.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        downX=e.getX();downY=e.getY();longStarted[0]=false;
                        cancelPendingHold();
                        Runnable hold=()->{
                            if(!dialog.isShowing()||dragging)return;
                            dragFrom=index;
                            dragging=true;
                            ClipData data=ClipData.newPlainText("bluestar-task",String.valueOf(index));
                            boolean launched=v.startDragAndDrop(data,
                                new View.DragShadowBuilder(icon),Integer.valueOf(index),0);
                            if(launched){
                                longStarted[0]=true;
                                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            }else{dragging=false;dragFrom=-1;}
                        };
                        pendingHold=hold;holdTarget=v;
                        v.postDelayed(hold,HOLD_MS);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float distance=Math.abs(e.getX()-downX)+Math.abs(e.getY()-downY);
                        if(distance>dp(19))cancelPendingHold();
                        return true;
                    case MotionEvent.ACTION_UP:
                        cancelPendingHold();
                        if(longStarted[0]||dragging)return true;
                        float dx=e.getX()-downX,dy=e.getY()-downY;
                        if(Math.abs(dx)>dp(55)&&Math.abs(dx)>Math.abs(dy)){
                            render(page+(dx<0?1:-1));return true;
                        }
                        if(Math.abs(dx)<dp(16)&&Math.abs(dy)<dp(16))v.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        cancelPendingHold();
                        return true;
                    default:return true;
                }
            }
        });
    }

    private void render(int newPage){
        if(newPage<0||newPage>=pages)return;
        cancelPendingHold();
        clearHighlight();
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
            boolean active=!creation&&task.id.equals(selected);
            TextView icon=label(creation?"＋":task.icon,34,Color.WHITE);
            icon.setBackground(bg(taskColor(task),active?0xFFF8D46A:0));
            slot.addView(icon,new LinearLayout.LayoutParams(dp(69),dp(69)));
            TextView name=label(creation?"自定义任务":task.name,14,Color.WHITE);
            name.setMaxLines(2);name.setPadding(dp(2),dp(7),dp(2),0);
            slot.addView(name);
            slot.setContentDescription((creation?"新建自定义任务":task.name)+
                (active?"，当前任务":"")+"；短按选择，长按拖动调整顺序");
            slot.setFocusable(true);slot.setClickable(true);
            slot.setOnClickListener(v->{
                if(dragging)return;
                dialog.dismiss();
                if(creation)listener.createTask();
                else listener.selected(task);
            });
            attachTouch(slot,icon,idx);
        }
        if(pageLabel!=null)pageLabel.setText((page+1)+" / "+pages);
    }
}
