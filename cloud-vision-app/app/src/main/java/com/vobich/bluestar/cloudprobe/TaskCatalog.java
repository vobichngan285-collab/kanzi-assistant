package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Fixed task routes: plain reading -> cloud OCR, all named scenarios -> cloud VL. */
final class TaskCatalog {
    static final String OCR = "ocr";
    static final String VISION = "vision";
    private static final String PREFS = "cloud_vision_tasks";
    private static final String CUSTOM = "custom_tasks";
    private static final String SELECTED = "selected_task_id";

    static final class Task {
        final String id, name, instruction, engine;
        final boolean custom;
        Task(String id, String name, String instruction, String engine, boolean custom) {
            this.id = id; this.name = name; this.instruction = instruction;
            this.engine = engine; this.custom = custom;
        }
        @Override public String toString() { return name; }
    }

    static List<Task> all(Context context) {
        List<Task> out = new ArrayList<>();
        out.add(new Task("ocr", "原样朗读文字",
            "按自然阅读顺序识别照片中清楚可见的文字。保留数字、价格、专名。不猜字、不补字，不解释。", OCR, false));
        out.add(new Task("menu", "餐厅菜单",
            "提取菜单中清楚的菜名和价格，简单归类荤素、主食、汤品。只有画面明确标注招牌、特色、推荐时才这么称呼；不编造价格。", VISION, false));
        out.add(new Task("mall", "逛商场",
            "优先读清楚的店铺名称、主营类别、招牌和促销信息。明确区分看到的字与推测，不猜测品牌特色。", VISION, false));
        out.add(new Task("hospital", "寻找科室",
            "优先读清楚的科室名称、楼层、诊室和方向标志。看不清方向要说明不确定，不提供医疗诊断或推测路线。", VISION, false));
        out.add(new Task("scene", "场景识别",
            "简洁描述照片中可以直接看到的重要物体、文字和环境。不要猜测看不清的事物，也不要断言安全的行走路线。", VISION, false));
        try {
            JSONArray arr = new JSONArray(prefs(context).getString(CUSTOM, "[]"));
            for (int i = 0; i < Math.min(arr.length(), 80); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id", "");
                String name = o.optString("name", "");
                String instruction = o.optString("instruction", "");
                if (!id.isEmpty() && !name.isEmpty() && !instruction.isEmpty()) {
                    out.add(new Task(id, name, instruction, VISION, true));
                }
            }
        } catch (JSONException ignored) { }
        return out;
    }

    static String selectedId(Context c) { return prefs(c).getString(SELECTED, OCR); }
    static void select(Context c, String id) { prefs(c).edit().putString(SELECTED, id).apply(); }

    static Task upsert(Context c, String existingId, String name, String instruction) {
        String cleanName = name.trim();
        String cleanInstruction = instruction.trim();
        if (cleanName.isEmpty() || cleanName.length() > 32 ||
            cleanInstruction.isEmpty() || cleanInstruction.length() > 1500) {
            throw new IllegalArgumentException("名称须为1–32字；任务说明须为1–1500字");
        }
        String id = (existingId == null || existingId.isEmpty()) ?
            "user-" + UUID.randomUUID() : existingId;
        JSONArray next = new JSONArray();
        for (Task t : all(c)) {
            if (!t.custom || t.id.equals(id)) continue;
            JSONObject o = new JSONObject();
            try {
                o.put("id", t.id); o.put("name", t.name); o.put("instruction", t.instruction);
                next.put(o);
            } catch (JSONException ignored) { }
        }
        JSONObject item = new JSONObject();
        try {
            item.put("id", id); item.put("name", cleanName); item.put("instruction", cleanInstruction);
            next.put(item);
        } catch (JSONException ignored) { }
        prefs(c).edit().putString(CUSTOM, next.toString()).apply();
        return new Task(id, cleanName, cleanInstruction, VISION, true);
    }

    static void remove(Context c, String id) {
        JSONArray next = new JSONArray();
        for (Task t : all(c)) {
            if (!t.custom || t.id.equals(id)) continue;
            JSONObject item = new JSONObject();
            try {
                item.put("id", t.id); item.put("name", t.name);
                item.put("instruction", t.instruction); next.put(item);
            } catch (JSONException ignored) { }
        }
        prefs(c).edit().putString(CUSTOM, next.toString()).putString(SELECTED, OCR).apply();
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
}
