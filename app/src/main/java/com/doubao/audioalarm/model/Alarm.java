package com.doubao.audioalarm.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个闹钟：绑定一个音频文件（来自用户选定的文件夹），可含多个时间点，
 * 可选每日重复、未划掉则 10 分钟后再响、响铃时长。
 */
public class Alarm {
    public long id = System.currentTimeMillis();
    public String folderUri = "";
    public String folderName = "";
    public String audioUri = "";
    public String audioName = "";
    /** "HH:mm" 列表，可多个 */
    public List<String> times = new ArrayList<>();
    /** 是否每天重复 */
    public boolean daily = true;
    /** 未从屏幕划掉时，10 分钟后再响一次 */
    public boolean repeatIfNotDismissed = true;
    /** 响铃时长（秒） */
    public int durationSeconds = 60;
    /** 总开关 */
    public boolean enabled = true;

    public String timesText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times.size(); i++) {
            if (i > 0) sb.append("  ");
            sb.append(times.get(i));
        }
        return sb.toString();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("folderUri", folderUri);
        o.put("folderName", folderName);
        o.put("audioUri", audioUri);
        o.put("audioName", audioName);
        JSONArray arr = new JSONArray();
        for (String t : times) arr.put(t);
        o.put("times", arr);
        o.put("daily", daily);
        o.put("repeatIfNotDismissed", repeatIfNotDismissed);
        o.put("durationSeconds", durationSeconds);
        o.put("enabled", enabled);
        return o;
    }

    public static Alarm fromJson(JSONObject o) {
        Alarm a = new Alarm();
        a.id = o.optLong("id", System.currentTimeMillis());
        a.folderUri = o.optString("folderUri", "");
        a.folderName = o.optString("folderName", "");
        a.audioUri = o.optString("audioUri", "");
        a.audioName = o.optString("audioName", "");
        JSONArray arr = o.optJSONArray("times");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) a.times.add(arr.optString(i));
        }
        a.daily = o.optBoolean("daily", true);
        a.repeatIfNotDismissed = o.optBoolean("repeatIfNotDismissed", true);
        a.durationSeconds = o.optInt("durationSeconds", 60);
        a.enabled = o.optBoolean("enabled", true);
        return a;
    }
}
