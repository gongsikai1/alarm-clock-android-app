package com.doubao.audioalarm.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Switch;
import android.widget.TextView;

import com.doubao.audioalarm.R;
import com.doubao.audioalarm.model.Alarm;

import java.util.ArrayList;
import java.util.List;

public class AlarmListAdapter extends BaseAdapter {

    public interface Callback {
        void onToggle(Alarm alarm, boolean enabled);
        void onOpen(Alarm alarm);
        void onDelete(Alarm alarm);
    }

    private final Context ctx;
    private final Callback callback;
    private final List<Alarm> data = new ArrayList<>();

    public AlarmListAdapter(Context ctx, Callback callback) {
        this.ctx = ctx;
        this.callback = callback;
    }

    public void setData(List<Alarm> alarms) {
        data.clear();
        data.addAll(alarms);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Object getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return data.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null) {
            convertView = LayoutInflater.from(ctx).inflate(R.layout.item_alarm, parent, false);
            h = new Holder();
            h.time = convertView.findViewById(R.id.item_time);
            h.audio = convertView.findViewById(R.id.item_audio);
            h.meta = convertView.findViewById(R.id.item_meta);
            h.switchOn = convertView.findViewById(R.id.item_switch);
            convertView.setTag(h);
        } else {
            h = (Holder) convertView.getTag();
        }

        final Alarm alarm = data.get(position);
        h.time.setText(alarm.timesText());
        h.audio.setText(alarm.audioName);
        StringBuilder meta = new StringBuilder(alarm.daily ? "每日" : "仅一次");
        meta.append(" · ").append(alarm.durationSeconds % 60 == 0
                ? (alarm.durationSeconds / 60) + " 分钟" : alarm.durationSeconds + " 秒");
        if (alarm.repeatIfNotDismissed) meta.append(" · 未关闭再响");
        h.meta.setText(meta.toString());

        h.time.setAlpha(alarm.enabled ? 1f : 0.45f);
        h.audio.setAlpha(alarm.enabled ? 1f : 0.45f);
        h.meta.setAlpha(alarm.enabled ? 1f : 0.45f);

        h.switchOn.setOnCheckedChangeListener(null);
        h.switchOn.setChecked(alarm.enabled);
        h.switchOn.setOnCheckedChangeListener((buttonView, isChecked) -> callback.onToggle(alarm, isChecked));

        convertView.setOnClickListener(v -> callback.onOpen(alarm));
        convertView.setOnLongClickListener(v -> {
            new android.app.AlertDialog.Builder(ctx)
                    .setTitle("删除闹钟？")
                    .setMessage(alarm.timesText() + " · " + alarm.audioName)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (d, w) -> callback.onDelete(alarm))
                    .show();
            return true;
        });
        return convertView;
    }

    private static class Holder {
        TextView time;
        TextView audio;
        TextView meta;
        Switch switchOn;
    }
}
