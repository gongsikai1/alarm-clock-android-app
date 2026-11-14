package com.doubao.audioalarm;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import com.doubao.audioalarm.data.AlarmStore;
import com.doubao.audioalarm.model.Alarm;
import com.doubao.audioalarm.util.AlarmScheduler;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 响铃界面：显示时间与音频名，上滑（或任意方向划动）即可划掉闹钟 */
public class AlarmActivity extends Activity {

    private View panel;
    private float downY, downX;
    private boolean dismissing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 必须在 setContentView 前设置，才能让响铃页直接覆盖锁屏并点亮屏幕
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        // 记录“响铃界面成功拉起”，供首页诊断判断是否被系统拦截
        getSharedPreferences("diag", MODE_PRIVATE).edit()
                .putLong("last_ui_at", System.currentTimeMillis())
                .putString("last_ui_error", "")
                .apply();

        setContentView(R.layout.activity_alarm);

        panel = findViewById(R.id.ring_panel);
        refreshFromIntent();
        panel.setOnTouchListener(new SwipeDismissListener());
        // 整屏都可划：锁屏时手指可能落在任意位置
        findViewById(android.R.id.content).setOnTouchListener(new SwipeDismissListener());
        View stopBtn = findViewById(R.id.ring_stop);
        stopBtn.setOnClickListener(v -> dismiss());
        findViewById(R.id.ring_top_close).setOnClickListener(v -> dismiss());
    }

    private final BroadcastReceiver finishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            dismiss();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(AlarmOverlay.ACTION_FINISH_RING);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(finishReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(finishReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(finishReceiver);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // 再次响铃（如 10 分钟补响）时刷新界面并恢复可见
        dismissing = false;
        panel.animate().cancel();
        panel.setAlpha(1f);
        panel.setTranslationY(0f);
        refreshFromIntent();
    }

    private void refreshFromIntent() {
        TextView timeView = findViewById(R.id.ring_time);
        TextView audioView = findViewById(R.id.ring_audio);
        TextView metaView = findViewById(R.id.ring_meta);
        TextView nowView = findViewById(R.id.ring_now);
        long id = getIntent().getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L);
        Alarm a = AlarmStore.get(this).find(id);
        if (a != null) {
            timeView.setText(a.timesText());
            audioView.setText(a.audioName);
            StringBuilder meta = new StringBuilder();
            meta.append(a.daily ? "每日" : "仅一次");
            meta.append(" · 响铃 ").append(durationText(a.durationSeconds));
            if (a.repeatIfNotDismissed) meta.append(" · 未关闭将 10 分钟后再响");
            metaView.setText(meta.toString());
        } else {
            timeView.setText("闹钟");
            audioView.setText("");
            metaView.setText("");
        }
        nowView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
    }

    private String durationText(int seconds) {
        if (seconds % 60 == 0) return (seconds / 60) + " 分钟";
        return seconds + " 秒";
    }

    private void dismiss() {
        if (dismissing) return;
        dismissing = true;
        long id = getIntent().getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L);
        int idx = getIntent().getIntExtra(AlarmScheduler.EXTRA_TIME_INDEX, 0);
        Intent stop = new Intent(this, AlarmService.class);
        stop.setAction(AlarmService.ACTION_STOP);
        stop.putExtra(AlarmScheduler.EXTRA_ALARM_ID, id);
        stop.putExtra(AlarmScheduler.EXTRA_TIME_INDEX, idx);
        try {
            startService(stop);
        } catch (Exception ignored) {
        }
        AlarmScheduler.cancelReRing(this, id, idx);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancelAll();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
        }
    }

    private class SwipeDismissListener implements View.OnTouchListener {
        private VelocityTracker tracker;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = event.getRawY();
                    downX = event.getRawX();
                    panel.animate().cancel();
                    if (tracker == null) tracker = VelocityTracker.obtain();
                    tracker.clear();
                    tracker.addMovement(event);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (tracker != null) tracker.addMovement(event);
                    float dy = event.getRawY() - downY;
                    float dx = event.getRawX() - downX;
                    float dist = (float) Math.sqrt(dy * dy + dx * dx);
                    float alpha = Math.max(0.35f, 1f - dist / dp(520));
                    panel.setAlpha(alpha);
                    panel.setTranslationY(dy * 0.55f);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    float dy = event.getRawY() - downY;
                    float dx = event.getRawX() - downX;
                    float dist = (float) Math.sqrt(dy * dy + dx * dx);
                    float velocity = 0f;
                    if (tracker != null) {
                        tracker.addMovement(event);
                        tracker.computeCurrentVelocity(1000);
                        float vy = tracker.getYVelocity();
                        float vx = tracker.getXVelocity();
                        velocity = (float) Math.sqrt(vy * vy + vx * vx);
                        tracker.recycle();
                        tracker = null;
                    }
                    if (dist > dp(64) || velocity > dp(420)) {
                        panel.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        panel.animate().alpha(0f).translationY(dy + dp(80))
                                .setDuration(170).setInterpolator(new DecelerateInterpolator())
                                .withEndAction(AlarmActivity.this::dismiss).start();
                    } else {
                        panel.animate().alpha(1f).translationY(0f).setDuration(160).start();
                    }
                    return true;
                }
            }
            return false;
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    public void onBackPressed() {
        // 需划掉或点“关闭”才停止，避免误触
    }
}
