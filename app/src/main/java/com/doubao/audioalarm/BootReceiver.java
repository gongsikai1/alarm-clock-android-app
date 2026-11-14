package com.doubao.audioalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.doubao.audioalarm.data.AlarmStore;
import com.doubao.audioalarm.util.AlarmScheduler;
import com.doubao.audioalarm.util.Keeper;

/** 开机 / 应用更新后恢复所有闹钟，并拉起后台常驻服务 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            AlarmScheduler.rescheduleAll(context, AlarmStore.get(context).all());
            Keeper.start(context);
        } catch (Exception ignored) {
        }
    }
}
