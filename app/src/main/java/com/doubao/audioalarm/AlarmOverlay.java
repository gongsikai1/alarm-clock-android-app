package com.doubao.audioalarm;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.doubao.audioalarm.model.Alarm;
import com.doubao.audioalarm.util.AlarmScheduler;

/**
 * 屏幕顶部的悬浮关闭条：
 * 闹钟响铃时在状态栏下方显示“闹钟响铃中 + 关闭按钮”，通话/其他 App 界面也能点到。
 * 需要“显示在其他应用上层”权限；未授权时静默跳过（全屏响铃页仍可划掉）。
 */
public class AlarmOverlay {

    public static final String ACTION_FINISH_RING = "com.doubao.audioalarm.FINISH_RING";

    private static View view;
    private static WindowManager wm;

    public static boolean canDraw(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(ctx);
        }
        return true;
    }

    public static void show(Context context, Alarm alarm, int index) {
        final Context ctx = context.getApplicationContext();
        if (!canDraw(ctx)) return;
        hide(ctx);

        try {
            view = LayoutInflater.from(ctx).inflate(R.layout.overlay_banner, null);
            TextView title = view.findViewById(R.id.banner_title);
            String timeText = alarm == null ? "" : alarm.timesText();
            title.setText("⏰ 闹钟响铃中 · " + timeText);

            final long alarmId = alarm == null ? -1L : alarm.id;
            view.findViewById(R.id.banner_close).setOnClickListener(v -> dismiss(ctx, alarmId, index));

            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            // 让出系统状态栏（含刘海/挖孔）高度，避免被时间、电量等信息压住
            lp.y = statusBarHeight(ctx) + dp(ctx, 10);

            wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) wm.addView(view, lp);
        } catch (Exception e) {
            hide(ctx);
        }
    }

    public static void hide(Context context) {
        try {
            if (view != null && wm != null) wm.removeView(view);
        } catch (Exception ignored) {
        }
        view = null;
        wm = null;
    }

    public static boolean isShowing() {
        return view != null;
    }

    private static void dismiss(Context ctx, long alarmId, int index) {
        try {
            Intent stop = new Intent(ctx, AlarmService.class);
            stop.setAction(AlarmService.ACTION_STOP);
            stop.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);
            stop.putExtra(AlarmScheduler.EXTRA_TIME_INDEX, index);
            ctx.startService(stop);
        } catch (Exception ignored) {
        }
        AlarmScheduler.cancelReRing(ctx, alarmId, index);
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancelAll();
        } catch (Exception ignored) {
        }
        // 通知全屏响铃页一起关闭
        try {
            ctx.sendBroadcast(new Intent(ACTION_FINISH_RING).setPackage(ctx.getPackageName()));
        } catch (Exception ignored) {
        }
        hide(ctx);
    }

    private static int dp(Context ctx, float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }

    /** 读取系统状态栏高度（包含刘海/挖孔区域） */
    private static int statusBarHeight(Context ctx) {
        int resId = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            try {
                return ctx.getResources().getDimensionPixelSize(resId);
            } catch (Exception ignored) {
            }
        }
        return (int) dp(ctx, 28);
    }
}
