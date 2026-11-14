package com.vobichngan.bluestar.oemautoimport;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Small independent proof-of-concept. Does not replace, alter or re-sign the
 * installed original com.starglasses.app.
 */
public final class MainActivity extends Activity {
    private static final String PREF = "oem_autoimport_preferences";
    public static final String ENABLED = "user_opted_in";
    public static final String LAST_STATE = "last_state";
    private TextView state;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(28, 30, 28, 36);
        scroll.addView(main);

        TextView title = new TextView(this);
        title.setText("原厂 M02S 自动导入验证 · v0.1.0");
        title.setTextSize(21); title.setTextColor(Color.BLACK);
        main.addView(title);

        TextView explanation = new TextView(this);
        explanation.setText("这不是改造后的原厂APK，而是独立的最小验证器。"
                + "仅在原厂相册处于手机前台，并且先见到无待导入、"
                + "之后新出现“发现1个文件”和“导入”按钮时，"
                + "通过无障碍执行一次“导入”点击。"
                + "所有蓝牙、Wi-Fi连接和原图下载仍由原厂App处理。"
                + "\n\n不承诺熄屏、后台或旧版本所有界面可用。"
                + "不读取照片或网络信息，不删除文件，不上传云端。"
                + "但是原厂自身导入完成后的清理行为仍由原厂决定，"
                + "本助手无法保证原厂不会删除眼镜中的已导入媒体。");
        explanation.setTextSize(16); explanation.setPadding(0,18,0,16);
        main.addView(explanation);

        Switch enabled = new Switch(this);
        enabled.setText("允许自动点击原厂相册中的「导入」（仅1个新文件）");
        enabled.setTextSize(16);
        enabled.setChecked(getSharedPreferences(PREF, MODE_PRIVATE).getBoolean(ENABLED, false));
        enabled.setOnCheckedChangeListener((CompoundButton b, boolean checked) -> {
            long arm = getSharedPreferences(PREF, MODE_PRIVATE).getLong("arm_sequence",0L);
            getSharedPreferences(PREF, MODE_PRIVATE).edit()
                    .putBoolean(ENABLED,checked)
                    .putLong("arm_sequence", checked ? arm+1L : arm).apply();
            state.setText(checked
                    ? "实验开关已开启；仍须在系统无障碍设置中开启对应服务。"
                    : "已暂停；不会再执行自动导入点击。");
        });
        main.addView(enabled);

        Button permission = new Button(this);
        permission.setText("前往系统无障碍设置 → 开启「蓝星·仅原厂相册导入」");
        permission.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        main.addView(permission);

        Button original = new Button(this);
        original.setText("打开原厂眼镜App");
        original.setOnClickListener(v -> {
            Intent intent = getPackageManager().getLaunchIntentForPackage(
                    AutoImportPolicy.OEM_PACKAGE);
            if (intent != null) startActivity(intent);
            else state.setText("没有找到原厂 com.starglasses.app；请手动打开原厂App");
        });
        main.addView(original);

        Button refresh = new Button(this);
        refresh.setText("查看匿名状态");
        refresh.setOnClickListener(v -> refreshState());
        main.addView(refresh);

        state = new TextView(this);
        state.setTextSize(16); state.setPadding(0,16,0,16);
        main.addView(state);

        TextView guide = new TextView(this);
        guide.setText("一次测试顺序：\n"
                + "① 原厂App先手动处理旧的待导入文件。\n"
                + "② 打开原厂相册，确认当前没有待导入。\n"
                + "③ 返回本程序打开开关和无障碍服务，再回到原厂相册。\n"
                + "④ 佩戴眼镜拍一张新照片；相册出现「发现1个文件」与「导入」按钮时，等待自动点击。\n"
                + "⑤ 检查高清照片是否真正导入成功；失败时只提交匿名状态。本测试每次打开开关最多自动点一次，下张需关掉开关再打开。\n\n"
                + "不支持删除眼镜文件、自动处理多个旧文件或操作别的App。"
                + "需要停用时关闭上方开关或系统无障碍服务。");
        guide.setTextSize(16);
        main.addView(guide);

        setContentView(scroll);
        refreshState();
    }

    @Override protected void onResume() {
        super.onResume();
        if (state != null) refreshState();
    }

    private void refreshState() {
        state.setText("状态：" + getSharedPreferences(PREF, MODE_PRIVATE)
                .getString(LAST_STATE, "尚未收到原厂相册界面事件"));
    }
}
