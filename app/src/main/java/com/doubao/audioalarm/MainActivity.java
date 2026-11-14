package com.doubao.audioalarm;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.doubao.audioalarm.adapter.AlarmListAdapter;
import com.doubao.audioalarm.data.AlarmStore;
import com.doubao.audioalarm.model.Alarm;
import com.doubao.audioalarm.util.AlarmScheduler;
import com.doubao.audioalarm.util.Keeper;

/** 闹钟列表首页 */
public class MainActivity extends Activity implements AlarmListAdapter.Callback {

    private ListView listView;
    private View emptyView;
    private boolean overlayAsked;
    private TextView diagText;
    private AlarmListAdapter adapter;
    private Switch keeperSwitch;
    private TextView keeperStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.alarm_list);
        emptyView = findViewById(R.id.empty_view);
        adapter = new AlarmListAdapter(this, this);
        listView.setAdapter(adapter);

        findViewById(R.id.add_button).setOnClickListener(v -> {
            Intent i = new Intent(this, AlarmEditActivity.class);
            startActivity(i);
        });

        keeperSwitch = findViewById(R.id.keeper_switch);
        keeperStatus = findViewById(R.id.keeper_status);
        keeperSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Keeper.setEnabled(this, isChecked);
            updateKeeperUi();
            Toast.makeText(this, isChecked ? "已开启后台常驻" : "已关闭后台常驻", Toast.LENGTH_SHORT).show();
        });
        View keeperCard = findViewById(R.id.keeper_card);
        keeperCard.setOnClickListener(v -> requestBatteryWhitelist());
        findViewById(R.id.battery_hint).setOnClickListener(v -> requestBatteryWhitelist());
        findViewById(R.id.overlay_hint).setOnClickListener(v -> requestOverlayPermission());
        findViewById(R.id.test_btn).setOnClickListener(v -> startTestRing());
        diagText = findViewById(R.id.diag_text);
        diagText.setOnClickListener(v -> openNotificationSettings());

        checkPermissions();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 12 && grantResults.length > 0
                && grantResults[0] != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("通知权限是必需的")
                    .setMessage("系统要求开启通知，否则锁屏到点时无法弹出可划掉的响铃界面，也没有关闭按钮。")
                    .setNegativeButton("稍后", null)
                    .setPositiveButton("去开启", (d, w) -> openNotificationSettings())
                    .show();
        }
    }

    /** 5 秒后模拟一次响铃，方便验证锁屏弹页与顶部关闭按钮 */
    private void startTestRing() {
        AlarmStore store = AlarmStore.get(this);
        if (store.all().isEmpty()) {
            Toast.makeText(this, "先新建一个闹钟，再来测试", Toast.LENGTH_SHORT).show();
            return;
        }
        final Alarm alarm = store.all().get(0);
        Toast.makeText(this, "5 秒后开始响铃，可马上按电源键锁屏测试", Toast.LENGTH_LONG).show();
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            Intent svc = new Intent(this, AlarmService.class);
            svc.setAction(AlarmService.ACTION_START);
            svc.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id);
            svc.putExtra(AlarmScheduler.EXTRA_TIME_INDEX, 0);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc);
                } else {
                    startService(svc);
                }
            } catch (Exception ignored) {
            }
        }, 5000);
    }

    /** 申请「显示在其他应用上层」：决定锁屏响铃页与顶部关闭按钮是否可靠 */
    private void requestOverlayPermission() {
        if (AlarmOverlay.canDraw(this)) {
            Toast.makeText(this, "悬浮窗权限已开启", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, "请到系统设置 → 应用 → 显示在其他应用上层 中开启", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
        AlarmScheduler.rescheduleAll(this, AlarmStore.get(this).all());
        Keeper.start(this);
        updateKeeperUi();
        updateDiagnostics();
    }

    private void updateKeeperUi() {
        boolean enabled = Keeper.isEnabled(this);
        keeperSwitch.setOnCheckedChangeListener(null);
        keeperSwitch.setChecked(enabled);
        keeperSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Keeper.setEnabled(this, isChecked);
            updateKeeperUi();
            Toast.makeText(this, isChecked ? "已开启后台常驻" : "已关闭后台常驻", Toast.LENGTH_SHORT).show();
        });
        if (!enabled) {
            keeperStatus.setText("已关闭 · 闹钟可能被系统延迟");
            return;
        }
        String alive = KeeperService.running ? "守护服务运行中" : "守护服务启动中";
        String battery = Keeper.isIgnoringBatteryOptimizations(this) ? "已加入电池白名单" : "未加入电池白名单";
        keeperStatus.setText(alive + " · " + battery);
    }

    private void requestBatteryWhitelist() {
        if (Keeper.isIgnoringBatteryOptimizations(this)) {
            Toast.makeText(this, "已经在电池优化白名单里了", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(this, "请在系统设置中允许后台运行", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void reload() {
        adapter.setData(AlarmStore.get(this).all());
        emptyView.setVisibility(adapter.getCount() == 0 ? View.VISIBLE : View.GONE);
        listView.setVisibility(adapter.getCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 12);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !AlarmScheduler.canScheduleExact(this)) {
            boolean asked = getPreferences(MODE_PRIVATE).getBoolean("asked_exact", false);
            if (!asked) {
                getPreferences(MODE_PRIVATE).edit().putBoolean("asked_exact", true).apply();
                Toast.makeText(this, "请允许「闹钟和提醒」权限，否则闹钟可能不准时", Toast.LENGTH_LONG).show();
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception ignored) {
                }
            }
        }
        askOverlayOnce();
    }

    /** Android 14+ 是否允许本应用使用全屏通知（关掉后锁屏不会弹响铃页） */
    private boolean canUseFullScreenIntent() {
        if (Build.VERSION.SDK_INT < 34) return true;
        try {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            return nm != null && nm.canUseFullScreenIntent();
        } catch (Exception e) {
            return true;
        }
    }

    /** 诊断：上次响铃时全屏响铃页到底有没有被系统放行，以及通知是否正常 */
    private void updateDiagnostics() {
        boolean notifPerm = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPerm = checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        boolean channelOn = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            android.app.NotificationChannel ch = nm == null ? null
                    : nm.getNotificationChannel("alarm_ring_channel");
            if (ch != null && ch.getImportance() == android.app.NotificationManager.IMPORTANCE_NONE) {
                channelOn = false;
            }
        }
        android.content.SharedPreferences diag = getSharedPreferences("diag", MODE_PRIVATE);
        long trig = diag.getLong("last_trigger_at", 0L);
        long ui = diag.getLong("last_ui_at", 0L);
        String uiState;
        if (trig <= 0) {
            uiState = "还没响过，点右上角「测试响铃」验证";
        } else if (ui >= trig) {
            uiState = "响铃界面已弹出 ✓（上次 " + formatTime(trig) + "）";
        } else {
            uiState = "响铃界面被系统拦截 ✗（上次 " + formatTime(trig) + "），点此按提示开启权限";
        }
        StringBuilder sb = new StringBuilder("锁屏弹出检测：").append(uiState);
        if (!canUseFullScreenIntent()) {
            sb.append("\n「全屏通知」权限被系统关闭 → 锁屏不会弹响铃页，点此一键开启");
        } else if (!notifPerm) {
            sb.append("\n通知权限未开启 → 全屏响铃页无法弹出，点此开启");
        } else if (!channelOn) {
            sb.append("\n「闹钟响铃」通知通道被关闭 → 点此重新开启");
        } else if (!AlarmOverlay.canDraw(this)) {
            sb.append("\n悬浮窗权限未开启 → 顶部关闭条与后台弹页会失效，点此开启");
        } else if (trig > 0 && ui < trig) {
            sb.append("\n请到系统设置开启本应用的「锁屏显示 / 后台弹出界面」权限");
        }
        diagText.setText(sb.toString());
        diagText.setTextColor(getResources().getColor(
                (trig > 0 && ui < trig) || !notifPerm || !channelOn
                        ? R.color.accent : R.color.text3));
    }

    private String formatTime(long at) {
        return new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date(at));
    }

    private void openNotificationSettings() {
        // 1) Android 14+：全屏通知权限（锁屏弹页的关键）
        if (!canUseFullScreenIntent()) {
            try {
                Intent i = new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT",
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
                return;
            } catch (Exception ignored) {
            }
        }
        boolean notifPerm = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPerm = checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        // 2) 通知权限
        if (!notifPerm) {
            try {
                Intent i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(i);
                return;
            } catch (Exception ignored) {
            }
        }
        // 3) 悬浮窗权限
        if (!AlarmOverlay.canDraw(this)) {
            requestOverlayPermission();
            return;
        }
        // 4) 走到这里说明基础权限都给了，试试直接打开厂商的权限编辑页（小米/红米常见）
        try {
            Intent mi = new Intent("miui.intent.action.APP_PERM_EDITOR");
            mi.setClassName("com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity");
            mi.putExtra("extra_pkgname", getPackageName());
            startActivity(mi);
            return;
        } catch (Exception ignored) {
        }
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
            return;
        } catch (Exception ignored) {
        }
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
                Toast.makeText(this, "请到系统设置中开启通知权限", Toast.LENGTH_LONG).show();
            }
        }
    }

    /** 悬浮窗权限是锁屏弹页 + 顶部关闭按钮的关键，启动时引导一次 */
    private void askOverlayOnce() {
        if (overlayAsked) return;
        overlayAsked = true;
        if (AlarmOverlay.canDraw(this)) return;
        new android.app.AlertDialog.Builder(this)
                .setTitle("开启「显示在其他应用上层」")
                .setMessage("锁屏时弹出可划掉的闹钟界面、以及屏幕顶部的「关闭」悬浮按钮，都需要这个权限。\n\n"
                        + "开启后：锁屏到点自动亮屏弹闹铃页，上滑即可划掉；非锁屏时屏幕顶部也有一条带关闭按钮的提示。")
                .setNegativeButton("稍后", null)
                .setPositiveButton("去开启", (d, w) -> requestOverlayPermission())
                .show();
    }

    @Override
    public void onToggle(Alarm alarm, boolean enabled) {
        alarm.enabled = enabled;
        AlarmStore.get(this).upsert(alarm);
        AlarmScheduler.schedule(this, alarm);
        adapter.setData(AlarmStore.get(this).all());
        Toast.makeText(this, enabled ? "闹钟已开启" : "闹钟已关闭", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onOpen(Alarm alarm) {
        Intent i = new Intent(this, AlarmEditActivity.class);
        i.putExtra("edit_id", alarm.id);
        startActivity(i);
    }

    @Override
    public void onDelete(Alarm alarm) {
        AlarmScheduler.cancelAll(this, alarm);
        AlarmStore.get(this).delete(alarm.id);
        reload();
        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
    }
}
