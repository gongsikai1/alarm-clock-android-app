package com.doubao.audioalarm.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.doubao.audioalarm.AlarmReceiver;
import com.doubao.audioalarm.MainActivity;
import com.doubao.audioalarm.model.Alarm;

import java.util.Calendar;
import java.util.List;

/** 负责把 Alarm 写入系统 AlarmManager */
public class AlarmScheduler {

    public static final String ACTION_TRIGGER = "com.doubao.audioalarm.TRIGGER";
    public static final String EXTRA_ALARM_ID = "alarm_id";
    public static final String EXTRA_TIME_INDEX = "time_index";
    public static final String EXTRA_RE_RING = "re_ring";

    /** 未划掉时再次响铃的间隔（毫秒） */
    public static final long RE_RING_DELAY_MS = 10 * 60 * 1000L;

    private static int requestCode(long alarmId, int timeIndex, boolean reRing) {
        int base = (int) (alarmId % 90000L);
        return base * 20 + (Math.max(0, Math.min(9, timeIndex)) * 2) + (reRing ? 1 : 0);
    }

    private static PendingIntent pendingIntent(Context ctx, long alarmId, int index,
                                               boolean reRing, int flags) {
        Intent i = new Intent(ctx, AlarmReceiver.class);
        i.setAction(ACTION_TRIGGER);
        i.putExtra(EXTRA_ALARM_ID, alarmId);
        i.putExtra(EXTRA_TIME_INDEX, index);
        i.putExtra(EXTRA_RE_RING, reRing);
        return PendingIntent.getBroadcast(ctx, requestCode(alarmId, index, reRing), i,
                flags | PendingIntent.FLAG_IMMUTABLE);
    }

    public static boolean canScheduleExact(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    private static void setExact(Context ctx, long triggerAt, PendingIntent pi) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (canScheduleExact(ctx)) {
            // 用 setAlarmClock：系统按“闹钟”对待，触发时可豁免后台启动界面的限制，
            // 锁屏闹铃页因此更容易弹出；同时状态栏会显示闹钟图标。
            try {
                PendingIntent show = PendingIntent.getActivity(ctx, 999,
                        new Intent(ctx, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(triggerAt, show);
                am.setAlarmClock(info, pi);
                return;
            } catch (Exception ignored) {
            }
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                return;
            } catch (SecurityException e) {
                // 权限被收回时退化为非精确闹钟
            }
        }
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } catch (Exception ignored) {
        }
    }

    /** 计算某个 "HH:mm" 的下一次触发时间戳（今天或明天） */
    public static long nextOccurrence(String hhmm, long now) {
        int hour = 8, minute = 0;
        try {
            String[] parts = hhmm.split(":");
            hour = Integer.parseInt(parts[0]);
            minute = Integer.parseInt(parts[1]);
        } catch (Exception ignored) {
        }
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= now + 2000L) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    /** 重新登记该闹钟的全部触发点 */
    public static void schedule(Context ctx, Alarm alarm) {
        cancelAll(ctx, alarm);
        if (alarm == null || !alarm.enabled || alarm.times.isEmpty()) return;

        long now = System.currentTimeMillis();
        List<String> times = alarm.times;

        if (alarm.daily) {
            for (int i = 0; i < times.size(); i++) {
                long at = nextOccurrence(times.get(i), now);
                setExact(ctx, at, pendingIntent(ctx, alarm.id, i, false,
                        PendingIntent.FLAG_UPDATE_CURRENT));
            }
        } else {
            int bestIndex = -1;
            long bestAt = Long.MAX_VALUE;
            for (int i = 0; i < times.size(); i++) {
                long at = nextOccurrence(times.get(i), now);
                if (at < bestAt) {
                    bestAt = at;
                    bestIndex = i;
                }
            }
            if (bestIndex >= 0) {
                setExact(ctx, bestAt, pendingIntent(ctx, alarm.id, bestIndex, false,
                        PendingIntent.FLAG_UPDATE_CURRENT));
            }
        }
    }

    /** 只重新登记某一个时间点（每日重复触发后使用） */
    public static void scheduleIndex(Context ctx, Alarm alarm, int index) {
        if (!alarm.enabled || index < 0 || index >= alarm.times.size()) return;
        long at = nextOccurrence(alarm.times.get(index), System.currentTimeMillis());
        setExact(ctx, at, pendingIntent(ctx, alarm.id, index, false,
                PendingIntent.FLAG_UPDATE_CURRENT));
    }

    public static void scheduleReRing(Context ctx, long alarmId, int index) {
        long at = System.currentTimeMillis() + RE_RING_DELAY_MS;
        setExact(ctx, at, pendingIntent(ctx, alarmId, index, true,
                PendingIntent.FLAG_UPDATE_CURRENT));
    }

    public static void cancelReRing(Context ctx, long alarmId, int index) {
        PendingIntent pi = pendingIntent(ctx, alarmId, index, true, PendingIntent.FLAG_NO_CREATE);
        if (pi != null) {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pi);
            pi.cancel();
        }
    }

    public static void cancelAll(Context ctx, Alarm alarm) {
        if (alarm == null) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        for (int i = 0; i < Math.max(1, alarm.times.size()); i++) {
            for (boolean reRing : new boolean[]{false, true}) {
                PendingIntent pi = pendingIntent(ctx, alarm.id, i, reRing,
                        PendingIntent.FLAG_NO_CREATE);
                if (pi != null) {
                    if (am != null) am.cancel(pi);
                    pi.cancel();
                }
            }
        }
    }

    public static void rescheduleAll(Context ctx, List<Alarm> alarms) {
        for (Alarm a : alarms) schedule(ctx, a);
    }
}
