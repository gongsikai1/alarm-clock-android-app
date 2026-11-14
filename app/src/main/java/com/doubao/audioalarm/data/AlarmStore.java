package com.doubao.audioalarm.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.doubao.audioalarm.model.Alarm;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 闹钟本地持久化（SharedPreferences + JSON） */
public class AlarmStore {

    private static final String PREFS = "audio_alarm_store";
    private static final String KEY_LIST = "alarms";

    private static AlarmStore instance;

    private final SharedPreferences sp;
    private final List<Alarm> cache = new ArrayList<>();

    private AlarmStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized AlarmStore get(Context context) {
        if (instance == null) instance = new AlarmStore(context);
        return instance;
    }

    private void load() {
        cache.clear();
        String raw = sp.getString(KEY_LIST, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                cache.add(Alarm.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
    }

    private void persist() {
        JSONArray arr = new JSONArray();
        for (Alarm a : cache) {
            try {
                arr.put(a.toJson());
            } catch (Exception ignored) {
            }
        }
        sp.edit().putString(KEY_LIST, arr.toString()).apply();
    }

    public List<Alarm> all() {
        return Collections.unmodifiableList(cache);
    }

    public Alarm find(long id) {
        for (Alarm a : cache) if (a.id == id) return a;
        return null;
    }

    public void upsert(Alarm alarm) {
        Alarm old = find(alarm.id);
        if (old == null) {
            cache.add(alarm);
        } else {
            int idx = cache.indexOf(old);
            cache.set(idx, alarm);
        }
        persist();
    }

    public void delete(long id) {
        Alarm old = find(id);
        if (old != null) {
            cache.remove(old);
            persist();
        }
    }
}
