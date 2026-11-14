package com.doubao.audioalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.doubao.audioalarm.model.Alarm;
import com.doubao.audioalarm.data.AlarmStore;
import com.doubao.audioalarm.util.AlarmScheduler;

/** 响铃前台服务：播放所选音频、震动、控制时长、安排再次响铃 */
public class AlarmService extends Service {

    public static final String ACTION_START = "com.doubao.audioalarm.START_RING";
    public static final String ACTION_STOP = "com.doubao.audioalarm.STOP_RING";
    public static final String ACTION_DISMISS = "com.doubao.audioalarm.DISMISS";

    private static final String CHANNEL_ID = "alarm_ring_channel";
    private static final int NOTIF_ID = 1001;

    /** 当前正在响的闹钟 id，供界面查询 */
    public static long ringingAlarmId = -1L;
    public static int ringingIndex = 0;

    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private PowerManager.WakeLock screenWakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Alarm alarm;
    private int index;
    private boolean reRing;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action) || ACTION_DISMISS.equals(action)) {
            long id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L);
            int idx = intent.getIntExtra(AlarmScheduler.EXTRA_TIME_INDEX, 0);
            if (id > 0) AlarmScheduler.cancelReRing(this, id, idx);
            stopEverything(true);
            return START_NOT_STICKY;
        }

        if (intent == null) return START_NOT_STICKY;

        long id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L);
        index = intent.getIntExtra(AlarmScheduler.EXTRA_TIME_INDEX, 0);
        reRing = intent.getBooleanExtra(AlarmScheduler.EXTRA_RE_RING, false);

        alarm = AlarmStore.get(this).find(id);
        if (alarm == null) {
            stopEverything(true);
            return START_NOT_STICKY;
        }

        ringingAlarmId = alarm.id;
        ringingIndex = index;

        getSharedPreferences("diag", MODE_PRIVATE).edit()
                .putLong("last_trigger_at", System.currentTimeMillis())
                .putString("last_trigger_times", alarm.timesText())
                .apply();

        createChannel();
        startForeground(NOTIF_ID, buildNotification());
        // 每次都重发一次全屏通知，避免通道/权限被系统降级后不弹锁屏
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, buildNotification());
        } catch (Exception ignored) {
        }

        // 锁屏场景：点亮屏幕并主动拉起可划掉的响铃界面（全屏通知被拦截时兜底）
        wakeScreenUp();
        launchRingActivity();

        // 屏幕顶部悬浮关闭条（非锁屏/任意界面可用，需要悬浮窗权限）
        AlarmOverlay.show(this, alarm, index);

        acquireWakeLock(alarm.durationSeconds);
        startSound();
        startVibration();

        // 响铃时长结束后停止声音（未划掉则保留重新响铃的预约）
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::finishRingCycle, Math.max(10, alarm.durationSeconds) * 1000L);

        // 未划掉则 10 分钟后再响一次（只在首次响铃时安排，避免无限循环）
        if (alarm.repeatIfNotDismissed && !reRing) {
            AlarmScheduler.scheduleReRing(this, alarm.id, index);
        }
        return START_STICKY;
    }

    /** 唤醒屏幕（锁屏/息屏时闹铃界面要能直接亮起来） */
    @SuppressWarnings("deprecation")
    private void wakeScreenUp() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            if (screenWakeLock == null) {
                screenWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP
                        | PowerManager.ON_AFTER_RELEASE, "AudioAlarm:screen");
                screenWakeLock.setReferenceCounted(false);
            }
            if (!screenWakeLock.isHeld()) screenWakeLock.acquire(30 * 1000L);
        } catch (Exception ignored) {
        }
    }

    /** 直接拉起响铃界面：即便系统没有弹全屏通知，也能在锁屏上看到并可划掉 */
    private void launchRingActivity() {
        Intent i = new Intent(this, AlarmActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        i.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm == null ? -1L : alarm.id);
        i.putExtra(AlarmScheduler.EXTRA_TIME_INDEX, index);

        // 方案一：直接 startActivity（持有悬浮窗权限或闹钟豁免时可用）
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }

        // 方案二：用 PendingIntent.send() 由系统代发；Android 14+ 显式声明允许后台启动界面
        try {
            PendingIntent pi = PendingIntent.getActivity(this, 88, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (Build.VERSION.SDK_INT >= 34) {
                android.app.ActivityOptions opts = android.app.ActivityOptions.makeBasic();
                opts.setPendingIntentBackgroundActivityStartMode(
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                pi.send(this, 0, null, null, null, null, opts.toBundle());
            } else {
                pi.send();
            }
        } catch (Exception ignored) {
        }

        // 方案三：重复 post 一条带 fullScreenIntent 的高优先级通知
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && alarm != null) nm.notify(NOTIF_ID + 1, buildFullScreenNotification());
        } catch (Exception ignored) {
        }
    }

    private Notification buildFullScreenNotification() {
        return buildNotificationInternal("闹钟响铃中 · " + alarm.timesText(),
                alarm.audioName + " · 上滑可划掉");
    }

    private void startSound() {
        stopSound();
        Uri uri;
        try {
            uri = Uri.parse(alarm.audioUri);
        } catch (Exception e) {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            stopSound();
            try {
                player = MediaPlayer.create(this,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
                if (player != null) {
                    player.setLooping(true);
                    player.start();
                }
            } catch (Exception ignored) {
            }
        }
        pushAlarmVolume();
    }

    private void pushAlarmVolume() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setStreamVolume(AudioManager.STREAM_ALARM,
                        am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void startVibration() {
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            long[] pattern = {0, 600, 400, 600, 400};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void acquireWakeLock(int seconds) {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AudioAlarm:ring");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire((seconds + 120L) * 1000L);
        } catch (Exception ignored) {
        }
    }

    /** 时长结束：停止声音与震动 */
    private void finishRingCycle() {
        stopSound();
        stopVibration();
        releaseWakeLock();
        ringingAlarmId = -1L;

        AlarmOverlay.hide(this);
        if (alarm != null && alarm.repeatIfNotDismissed && !reRing) {
            // 保留通知，提示 10 分钟后再次响铃，用户仍可点击进入关闭
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, buildPendingNotification());
            stopForeground(Service.STOP_FOREGROUND_DETACH);
            stopSelf();
        } else {
            stopEverything(false);
            stopSelf();
        }
    }

    private void stopEverything(boolean userDismissed) {
        handler.removeCallbacksAndMessages(null);
        AlarmOverlay.hide(this);
        stopSound();
        stopVibration();
        releaseWakeLock();
        ringingAlarmId = -1L;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIF_ID);
            nm.cancel(NOTIF_ID + 1);
        }
        stopSelf();
    }

    private void stopSound() {
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
            } catch (Exception ignored) {
            }
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    private void stopVibration() {
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception ignored) {
            }
            vibrator = null;
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null) {
            try {
                if (wakeLock.isHeld()) wakeLock.release();
            } catch (Exception ignored) {
            }
            wakeLock = null;
        }
        if (screenWakeLock != null) {
            try {
                if (screenWakeLock.isHeld()) screenWakeLock.release();
            } catch (Exception ignored) {
            }
            screenWakeLock = null;
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "闹钟响铃",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("闹钟响铃提醒");
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setBypassDnd(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        return buildNotificationInternal("闹钟响铃中 · " + alarm.timesText(),
                alarm.audioName + " · 上滑可划掉");
    }

    private Notification buildPendingNotification() {
        return buildNotificationInternal("闹钟未关闭",
                "10 分钟后会再次响铃，点击进入关闭");
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotificationInternal(String title, String text) {
        Intent open = new Intent(this, AlarmActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm == null ? -1L : alarm.id);
        open.putExtra(AlarmScheduler.EXTRA_TIME_INDEX, index);
        PendingIntent fullScreen = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent dismiss = new Intent(this, AlarmService.class);
        dismiss.setAction(ACTION_DISMISS);
        dismiss.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm == null ? -1L : alarm.id);
        dismiss.putExtra(AlarmScheduler.EXTRA_TIME_INDEX, index);
        PendingIntent dismissPi = PendingIntent.getService(this, 2, dismiss,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setFullScreenIntent(fullScreen, true)
                .setContentIntent(fullScreen)
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "关闭闹钟", dismissPi).build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            b.setVisibility(Notification.VISIBILITY_PUBLIC);
        }
        return b.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopSound();
        stopVibration();
        releaseWakeLock();
        super.onDestroy();
    }
}
