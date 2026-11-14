package com.doubao.audioalarm;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.doubao.audioalarm.util.Keeper;

/** 定时看门狗：确保守护服务存活 + 闹钟登记有效 */
public class WatchdogReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Keeper.isEnabled(context)) return;
        if (!KeeperService.running) {
            Keeper.start(context);
        } else {
            Keeper.selfHeal(context);
        }
        Keeper.scheduleWatchdog(context);
    }
}
