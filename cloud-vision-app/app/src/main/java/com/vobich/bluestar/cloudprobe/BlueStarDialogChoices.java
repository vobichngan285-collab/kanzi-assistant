package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shared visual-only choice rows for BOTH Android platform AlertDialog and
 * AndroidX/AppCompat AlertDialog. Existing handlers, ordering, cancel/confirm,
 * single-choice semantics and Activity ownership remain unchanged.
 *
 * Other on-screen widgets use BlueStarFontScale and AppTheme directly.
 */
final class BlueStarDialogChoices {
    private BlueStarDialogChoices(){}

    static final class AppCompatBuilder extends androidx.appcompat.app.AlertDialog.Builder {
        AppCompatBuilder(Context context){super(context);}
        @Override public androidx.appcompat.app.AlertDialog.Builder setItems(
                CharSequence[] items,DialogInterface.OnClickListener listener){
            return super.setAdapter(new ChoiceRows(getContext(),items,-1),listener);
        }
        @Override public androidx.appcompat.app.AlertDialog.Builder setSingleChoiceItems(
                CharSequence[] items,int checked,DialogInterface.OnClickListener listener){
            return super.setSingleChoiceItems(
                new ChoiceRows(getContext(),items,checked),checked,listener);
        }
    }
    static final class PlatformBuilder extends android.app.AlertDialog.Builder {
        PlatformBuilder(Context context){super(context);}
        @Override public android.app.AlertDialog.Builder setItems(
                CharSequence[] items,DialogInterface.OnClickListener listener){
            return super.setAdapter(new ChoiceRows(getContext(),items,-1),listener);
        }
        @Override public android.app.AlertDialog.Builder setSingleChoiceItems(
                CharSequence[] items,int checked,DialogInterface.OnClickListener listener){
            return super.setSingleChoiceItems(
                new ChoiceRows(getContext(),items,checked),checked,listener);
        }
    }

    private static final class ChoiceRows extends BaseAdapter {
        private final Context context;
        private final CharSequence[] labels;
        private final int chosen;
        ChoiceRows(Context context,CharSequence[] items,int checked){
            this.context=context;
            labels=items==null?new CharSequence[0]:items.clone();
            chosen=checked;
        }
        @Override public int getCount(){return labels.length;}
        @Override public CharSequence getItem(int position){return labels[position];}
        @Override public long getItemId(int position){return position;}
        @Override public boolean hasStableIds(){return true;}

        @Override public View getView(int position,View convertView,ViewGroup parent){
            // Render rows independently. Small lists are typical, and this
            // avoids stale radio/check labels when Android recycles a row.
            String label=labels[position]==null?"":labels[position].toString();
            LinearLayout outer=new LinearLayout(context);
            outer.setPadding(dp(7),dp(3),dp(7),dp(3));
            outer.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout card=new LinearLayout(context);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setPadding(dp(16),0,dp(12),0);
            GradientDrawable shape=new GradientDrawable();
            shape.setColor(0xFF1B3C5D);
            shape.setCornerRadius(dp(13));
            shape.setStroke(dp(1),0xFF315B82);
            card.setBackground(shape);

            String glyph=icon(label);
            if(!glyph.isEmpty()){
                TextView emblem=text(glyph,23,0xFF81C5FF,false);
                emblem.setGravity(Gravity.CENTER);
                card.addView(emblem,new LinearLayout.LayoutParams(dp(39),-1));
            }
            TextView name=text(label,20,0xFFF8FCFF,false);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(name,new LinearLayout.LayoutParams(0,-1,1f));

            TextView arrow=text(chosen==position?"✓":"›",25,
                chosen==position?0xFF83D0FF:0xFFC6DBED,false);
            arrow.setGravity(Gravity.CENTER);
            card.addView(arrow,new LinearLayout.LayoutParams(dp(28),-1));
            outer.addView(card,new LinearLayout.LayoutParams(-1,dp(52)));
            outer.setContentDescription(label);
            outer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            return outer;
        }
        private int dp(float unit){
            return Math.round(unit*context.getResources().getDisplayMetrics().density);
        }
        private TextView text(String value,int sp,int color,boolean bold){
            TextView view=new TextView(context);
            view.setText(value);
            view.setTextColor(color);
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);
            view.setTypeface(Typeface.create("sans-serif",
                bold?Typeface.BOLD:Typeface.NORMAL));
            return view;
        }
        private String icon(String s){
            if(s.contains("文字识别")||s.contains("OCR"))return "▣";
            if(s.contains("视觉分析")||s.contains("AI追问"))return "▧";
            if(s.contains("语音")||s.contains("音色"))return "♩";
            if(s.contains("Wi-Fi")||s.contains("热点"))return "⌁";
            if(s.contains("指示灯")||s.contains("灯光"))return "☼";
            if(s.contains("声音")||s.contains("提示音"))return "♫";
            return "";
        }
    }
}
