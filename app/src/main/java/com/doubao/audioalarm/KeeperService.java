package com.doubao.audioalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.doubao.audioalarm.util.Keeper;

/**
 * 后台常驻守护服务：常驻通知 + 重生机制，让进程 7×24 保持存活以便准时响铃。
 */
public class KeeperService extends Service {

    public static final String ACTION_START = "com.doubao.audioalarm.KEEPER_START";
    public static final String ACTION_STOP = "com.doubao.audioalarm.KEEPER_STOP";

    private static final String CHANNEL_ID = "keeper_channel";
    private static final int NOTIF_ID = 1002;

    /** 供看门狗判断服务是否还活着（同进程内有效） */
    public static volatile boolean running = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            running = false;
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!Keeper.isEnabled(this)) {
            running = false;
            stopSelf();
            return START_NOT_STICKY;
        }

        createChannel();
        startForeground(NOTIF_ID, buildNotification());
        running = true;

        // 每次唤醒都自愈：重新登记所有闹钟，避免被系统或厂商清理后失效
        Keeper.selfHeal(this);
        Keeper.scheduleWatchdog(this);
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // 用户从最近任务划掉 App 时，2 秒后拉起（若常驻仍开启）
        running = false;
        if (Keeper.isEnabled(this)) {
            Keeper.scheduleQuickRevive(this);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        running = false;
        if (Keeper.isEnabled(this)) {
            Keeper.scheduleQuickRevive(this);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "后台常驻",
                NotificationManager.IMPORTANCE_MIN);
        ch.setDescription("保持闹钟后台常驻，到点准时响铃");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 3, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("音频闹钟后台常驻中")
                .setContentText("闹钟会准时响铃，可随时在首页关闭常驻")
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MIN)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE);
        return b.build();
    }
}
