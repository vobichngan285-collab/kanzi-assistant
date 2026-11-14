package com.vobichngan.bluestar.oemautoimport;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;

/**
 * UI-only auto-import pilot, intentionally restricted to the original app.
 * No direct BLE, Wi-Fi, HTTP, media/file access, overlay, screen capture,
 * cloud/Internet permission, or vendor delete/finish command.
 */
public final class OemAutoImportService extends AccessibilityService {
    private static final String PREF = "oem_autoimport_preferences";
    private static final long STABLE_UI_MS = 950L;
    private static final long OBSERVATION_MAX_AGE_MS = 15_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable checkAgain = this::inspectOemWindow;
    private boolean observedAbsentInOemAlbum;
    private boolean clickedCurrentCandidate;
    private long firstCandidateSeenAt;
    private boolean serviceEnabledLastCheck;
    private boolean oneShotConsumed;
    private long observedArmSequence = -1L;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        observedAbsentInOemAlbum = false;
        clickedCurrentCandidate = false;
        firstCandidateSeenAt = 0;
        serviceEnabledLastCheck = false;
        oneShotConsumed = false;
        observedArmSequence = -1L;
        state("无障碍服务已开启。先到原厂相册观察无待导入状态，再新拍一张。");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !AutoImportPolicy.isOemPackage(event.getPackageName())) return;
        inspectOemWindow();
    }

    @Override public void onInterrupt() {
        handler.removeCallbacks(checkAgain);
        state("系统中断了自动导入监听");
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(checkAgain);
        super.onDestroy();
    }

    private void state(String s) {
        getSharedPreferences(PREF, MODE_PRIVATE).edit()
                .putString(MainActivity.LAST_STATE,s).apply();
    }

    private static final class Snapshot {
        int pendingCount = -1;
        boolean albumCue;
        AccessibilityNodeInfo importButton;
    }

    private Snapshot observe(AccessibilityNodeInfo root) {
        Snapshot s = new Snapshot();
        ArrayDeque<AccessibilityNodeInfo> todo = new ArrayDeque<>();
        todo.add(root);
        int visited = 0;
        while (!todo.isEmpty() && visited++ < 240) {
            AccessibilityNodeInfo node = todo.removeFirst();
            CharSequence text = node.getText();
            if (text != null) {
                int pending = AutoImportPolicy.pendingCount(text);
                if (pending >= 0) s.pendingCount = pending;
                if ("相册".equals(text.toString().trim())) s.albumCue = true;
                if (s.importButton == null && AutoImportPolicy.isExactImportButton(text)
                        && node.isVisibleToUser()) {
                    s.importButton = findClickableAncestor(node);
                }
            }
            CharSequence description = node.getContentDescription();
            if (description != null) {
                int pending = AutoImportPolicy.pendingCount(description);
                if (pending >= 0) s.pendingCount = pending;
                if ("相册".equals(description.toString().trim())) s.albumCue = true;
                if (s.importButton == null
                        && AutoImportPolicy.isExactImportButton(description)
                        && node.isVisibleToUser()) {
                    s.importButton = findClickableAncestor(node);
                }
            }
            for (int i=0;i<node.getChildCount();i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) todo.addLast(child);
            }
        }
        return s;
    }

    private AccessibilityNodeInfo findClickableAncestor(AccessibilityNodeInfo node) {
        for (int i=0;i<4 && node!=null;i++,node=node.getParent()) {
            if (node.isClickable() && node.isEnabled() && node.isVisibleToUser())
                return node;
        }
        return null;
    }

    private void inspectOemWindow() {
        boolean enabled = getSharedPreferences(PREF, MODE_PRIVATE)
                .getBoolean(MainActivity.ENABLED,false);
        if (!enabled) {
            handler.removeCallbacks(checkAgain);
            if (serviceEnabledLastCheck) state("开关关闭，自动导入已暂停");
            serviceEnabledLastCheck = false;
            observedAbsentInOemAlbum = false;
            clickedCurrentCandidate = false;
            firstCandidateSeenAt = 0L;
            return;
        }
        serviceEnabledLastCheck = true;
        long arm = getSharedPreferences(PREF, MODE_PRIVATE)
                .getLong("arm_sequence", 0L);
        if (arm != observedArmSequence) {
            observedArmSequence = arm;
            oneShotConsumed = false;
            observedAbsentInOemAlbum = false;
            clickedCurrentCandidate = false;
            firstCandidateSeenAt = 0L;
            state("本次单张导入已重新准备；先观察原厂相册空候选");
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !AutoImportPolicy.isOemPackage(root.getPackageName())) {
            return;
        }
        if (oneShotConsumed) return; // No second auto click until opt-in toggled again.
        Snapshot s = observe(root);
        if (s.pendingCount < 0) {
            firstCandidateSeenAt = 0L;
            clickedCurrentCandidate = false;
            // Don't arm on OEM home or on unrelated screens with no import UI.
            // The import control in the album or the visible album title is
            // evidence the user is actually looking at the OEM media page.
            if (s.albumCue || s.importButton != null) {
                observedAbsentInOemAlbum = true;
                state("已在原厂相册观察到空候选；等待新出现的1个文件");
            }
            return;
        }
        if (s.pendingCount > 1) {
            state("原厂提示多个待导入文件；为防批量旧图，本试验不会自动点击");
            firstCandidateSeenAt = 0L;
            return;
        }
        if (!observedAbsentInOemAlbum) {
            state("原厂相册已有待导入文件，尚未观察过空相册；请先手动清理旧候选");
            return;
        }
        if (clickedCurrentCandidate) return;
        if (s.importButton == null) {
            state("发现1个文件，但没有找到可点击的「导入」控件；不乱点");
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (firstCandidateSeenAt == 0L) {
            firstCandidateSeenAt = now;
            state("新发现1个待导入文件，等待原厂界面稳定后检查按钮");
            handler.removeCallbacks(checkAgain);
            handler.postDelayed(checkAgain, STABLE_UI_MS);
            return;
        }
        if (now - firstCandidateSeenAt < STABLE_UI_MS) {
            handler.removeCallbacks(checkAgain);
            handler.postDelayed(checkAgain, STABLE_UI_MS);
            return;
        }
        if (now - firstCandidateSeenAt > OBSERVATION_MAX_AGE_MS) {
            state("待导入候选出现时间过长；不点击旧状态，请先手动确认");
            return;
        }
        if (!AutoImportPolicy.allowAutoClick(true,true,
                observedAbsentInOemAlbum,s.pendingCount,true,false,
                clickedCurrentCandidate)) return;
        // Arm is consumed BEFORE the action: prevents duplicate import if
        // Android emits multiple fast content-changed notifications.
        observedAbsentInOemAlbum = false;
        clickedCurrentCandidate = true;
        oneShotConsumed = true;
        handler.removeCallbacks(checkAgain);
        boolean clicked = s.importButton.performAction(
                AccessibilityNodeInfo.ACTION_CLICK);
        state(clicked
                ? "已在原厂相册点击一次「导入」，等待原厂下载（未确认完成）；本轮不会再次点击"
                : "尝试点击失败；本轮不会再次点击，请手动检查");
    }
}
