package com.doubao.audioalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.doubao.audioalarm.data.AlarmStore;
import com.doubao.audioalarm.model.Alarm;
import com.doubao.audioalarm.util.AlarmScheduler;

/** 闹钟到点：启动前台铃声服务，并安排下一次触发 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L);
        int index = intent.getIntExtra(AlarmScheduler.EXTRA_TIME_INDEX, 0);
        boolean reRing = intent.getBooleanExtra(AlarmScheduler.EXTRA_RE_RING, false);

        AlarmStore store = AlarmStore.get(context);
        Alarm alarm = store.find(id);
        if (alarm == null || alarm.audioUri == null || alarm.audioUri.isEmpty()) return;

        // 一次性闹钟触发后自动失效；每日闹钟登记第二天的同一时间点
        if (!reRing) {
            if (alarm.daily) {
                AlarmScheduler.scheduleIndex(context, alarm, index);
            } else {
                alarm.enabled = false;
                store.upsert(alarm);
            }
        }

        // 先在广播里直接拉起响铃页（精确闹钟触发的广播有临时后台启动豁免）
        try {
            Intent ring = new Intent(context, AlarmActivity.class);
            ring.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            ring.putExtra(AlarmScheduler.EXTRA_ALARM_ID, id);
            ring.putExtra(AlarmScheduler.EXTRA_TIME_INDEX, index);
            context.startActivity(ring);
        } catch (Exception ignored) {
        }

        Intent svc = new Intent(context, AlarmService.class);
        svc.setAction(AlarmService.ACTION_START);
        svc.putExtra(AlarmScheduler.EXTRA_ALARM_ID, id);
        svc.putExtra(AlarmScheduler.EXTRA_TIME_INDEX, index);
        svc.putExtra(AlarmScheduler.EXTRA_RE_RING, reRing);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc);
        } else {
            context.startService(svc);
        }
    }
}
