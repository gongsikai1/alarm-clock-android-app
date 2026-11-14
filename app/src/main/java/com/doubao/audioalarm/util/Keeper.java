package com.doubao.audioalarm.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;

import com.doubao.audioalarm.KeeperJobService;
import com.doubao.audioalarm.KeeperService;
import com.doubao.audioalarm.WatchdogReceiver;
import com.doubao.audioalarm.data.AlarmStore;

/**
 * 后台 7×24 常驻管理：前台守护服务 + 闹钟自愈（AlarmManager 看门狗 + JobScheduler 兜底）
 */
public class Keeper {

    private static final String PREFS = "keeper_prefs";
    private static final String KEY_ENABLED = "enabled";

    public static final String ACTION_WATCHDOG = "com.doubao.audioalarm.KEEPER_WATCHDOG";
    private static final int WATCHDOG_REQ = 7001;
    private static final int JOB_ID = 7002;

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (enabled) {
            start(ctx);
            scheduleWatchdog(ctx);
        } else {
            stop(ctx);
            cancelWatchdog(ctx);
        }
    }

    /** 启动守护服务（若用户开启常驻） */
    public static void start(Context ctx) {
        if (!isEnabled(ctx)) return;
        Intent i = new Intent(ctx, KeeperService.class);
        i.setAction(KeeperService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Exception e) {
            try {
                ctx.startService(i);
            } catch (Exception ignored) {
            }
        }
        scheduleWatchdog(ctx);
    }

    public static void stop(Context ctx) {
        Intent i = new Intent(ctx, KeeperService.class);
        i.setAction(KeeperService.ACTION_STOP);
        try {
            ctx.startService(i);
        } catch (Exception ignored) {
        }
        try {
            ctx.stopService(new Intent(ctx, KeeperService.class));
        } catch (Exception ignored) {
        }
    }

    /** 每 10 分钟唤醒一次：检查服务存活并把所有闹钟重新登记（防止被系统清空） */
    public static void scheduleWatchdog(Context ctx) {
        if (!isEnabled(ctx)) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent i = new Intent(ctx, WatchdogReceiver.class);
        i.setAction(ACTION_WATCHDOG);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, WATCHDOG_REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + 10 * 60 * 1000L,
                    10 * 60 * 1000L, pi);
        } catch (Exception ignored) {
        }

        // JobScheduler 兜底：即使进程被杀，系统也会定期把任务拉起
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                if (js != null && js.getPendingJob(JOB_ID) == null) {
                    JobInfo.Builder builder = new JobInfo.Builder(JOB_ID,
                            new ComponentName(ctx, KeeperJobService.class))
                            .setPeriodic(15 * 60 * 1000L)
                            .setPersisted(true)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
                    js.schedule(builder.build());
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static void cancelWatchdog(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(ctx, WatchdogReceiver.class);
            i.setAction(ACTION_WATCHDOG);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, WATCHDOG_REQ, i,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null && am != null) am.cancel(pi);
        } catch (Exception ignored) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                if (js != null) js.cancel(JOB_ID);
            } catch (Exception ignored) {
            }
        }
    }

    /** 进程被杀、任务被划掉时，2 秒后重新拉起守护服务 */
    public static void scheduleQuickRevive(Context ctx) {
        if (!isEnabled(ctx)) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(ctx, WatchdogReceiver.class);
        i.setAction(ACTION_WATCHDOG);
        i.putExtra("revive", true);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, WATCHDOG_REQ + 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long at = System.currentTimeMillis() + 2000L;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean isIgnoringBatteryOptimizations(Context ctx) {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    /** 自愈：重新登记全部闹钟 */
    public static void selfHeal(Context ctx) {
        AlarmScheduler.rescheduleAll(ctx, AlarmStore.get(ctx).all());
    }
}
