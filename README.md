# 音频闹钟 · Audio Alarm

[中文](#中文文档) | [English](#english)

一个轻量的安卓闹钟 App：用你指定的文件夹里的任意音频当闹铃，支持多个响铃时间、每日重复、响铃时长、未划掉补响，并针对锁屏与后台常驻做了专门优化。

An Android alarm clock that plays any audio from a folder you pick, with multiple ring times, daily repeat, configurable ring duration, re-ring when not dismissed, plus dedicated work for lock screen and 24/7 background residency.

---

## 下载安装 · Download

- **最新安装包 v1.4**（debug 签名，约 102 KB）：
  https://github.com/gongsikai1/alarm-clock-android-app/raw/main/apk/alarm-clock-v1.4.apk
- 安装：手机浏览器打开链接下载 → 系统提示时允许「安装未知来源应用」→ 点击安装
- 兼容：Android 7.0（API 24）及以上

---

## 中文文档

### 功能概览

| 能力 | 说明 |
|---|---|
| 选择音频文件夹 | 通过系统文件选择器（SAF）选一个目录作为「主文件夹」，自动递归扫描子目录内的音频，权限长期保留 |
| 选择音频 | 列表展示文件名 + 大小，可试听（▶），点选即设为闹铃 |
| 多个响铃时间 | 一个闹钟可添加多个时间点（HH:mm），自动排序、可单独删除 |
| 每日重复 | 开关控制，默认开启；关闭后只响一次，响完自动失效 |
| 未划掉再响一次 | 开关控制，默认开启：没从屏幕划掉闹钟时，10 分钟后再响一次 |
| 响铃时长 | 30 秒 / 1 分钟（默认）/ 2 / 3 / 5 / 10 分钟，到时自动停止播放 |
| 划掉闹钟 | 全屏响铃页任意方向滑动即可关闭，另有「关闭闹钟」按钮兜底 |
| 顶部关闭条 | 响铃时在状态栏下方悬浮一条「闹钟响铃中 + 关闭」浮窗，任意界面可一键停铃 |
| 编辑 / 删除 | 列表中点击进入三步编辑（文件夹 → 音频 → 时间），全部回填；长按删除 |
| 后台常驻 | 前台守护服务 + 开机自启 + 任务被划掉后自动复活 + 10 分钟看门狗 + JobScheduler 双兜底 |
| 准点保障 | 使用 `setAlarmClock()` 登记系统级闹钟，精确到秒并可唤醒设备 |
| 自检诊断 | 首页显示「锁屏弹出检测」，指出权限缺失并一键跳转对应设置页 |

### 创建闹钟流程

1. **选文件夹**：点「选择文件夹」→ 系统文件选择器选目录；下次新建闹钟会自动带出上次的文件夹
2. **选音频**：在扫描结果里点选一首，可先试听
3. **选时间**：添加一个或多个时间点，设置「每日重复 / 未划掉再响 / 响铃时长」→ 保存

### 权限说明

| 权限 | 用途 |
|---|---|
| `POST_NOTIFICATIONS` | 响铃通知与全屏通知（Android 13+） |
| `USE_FULL_SCREEN_INTENT` | 锁屏时弹出响铃页（Android 14+ 需在系统中单独允许「全屏通知」） |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | 精确闹钟，避免延迟 |
| `SYSTEM_ALERT_WINDOW` | 屏幕顶部悬浮关闭条 |
| `FOREGROUND_SERVICE` + `MEDIA_PLAYBACK` / `SPECIAL_USE` | 响铃与后台常驻服务 |
| `RECEIVE_BOOT_COMPLETED` | 重启后恢复所有闹钟 |
| `WAKE_LOCK` / `VIBRATE` | 响铃时保持唤醒与震动 |

### 锁屏不弹响铃页？按顺序检查

1. 系统「通知」中允许本应用通知，且「闹钟响铃」通知通道为开启
2. Android 14+：系统设置里允许本应用使用「**全屏通知**」
3. 允许本应用「**显示在其他应用上层**」（悬浮窗）
4. 国产 ROM（小米/红米、OPPO、vivo、荣耀等）还需在系统权限里开启「**锁屏显示**」或「**后台弹出界面**」
5. 首页的「锁屏弹出检测」会直接给出缺哪一项，点击即跳转

### 构建

环境要求：

- JDK 17
- Android SDK：`compileSdk 34`、`build-tools 34.0.0`、`minSdk 24`
- Gradle 8.7（项目使用 AGP 8.5.2）

命令行构建 Debug 包：

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

或用 Android Studio 直接打开工程目录运行。

### 目录结构

```
AudioAlarm/
├── app/
│   └── src/main/
│       ├── java/com/doubao/audioalarm/
│       │   ├── MainActivity.java          # 首页：闹钟列表、后台常驻、诊断
│       │   ├── AlarmEditActivity.java     # 三步式新建/编辑（文件夹 → 音频 → 时间）
│       │   ├── AlarmActivity.java         # 锁屏/全屏响铃页，支持滑动划掉
│       │   ├── AlarmService.java          # 响铃前台服务：播放、震动、时长、补响
│       │   ├── AlarmReceiver.java         # 闹钟到点广播
│       │   ├── KeeperService.java         # 后台常驻守护服务
│       │   ├── KeeperJobService.java      # JobScheduler 兜底拉活
│       │   ├── WatchdogReceiver.java      # 10 分钟看门狗，自愈闹钟登记
│       │   ├── AlarmOverlay.java          # 屏幕顶部悬浮关闭条
│       │   ├── data/AlarmStore.java       # SharedPreferences + JSON 持久化
│       │   ├── model/Alarm.java           # 闹钟数据模型
│       │   ├── util/AlarmScheduler.java   # AlarmManager 登记与计算
│       │   ├── util/AudioScanner.java     # SAF 递归扫描音频
│       │   └── util/Keeper.java           # 常驻与自愈策略
│       └── res/                           # 深色主题布局与图标
├── build.gradle
└── settings.gradle
```

### 版本记录

- **v1.0**：基础功能（选文件夹 / 选音频 / 多时间 / 每日 / 补响 / 时长 / 划掉 / 编辑）+ 后台常驻
- **v1.1**：锁屏亮屏、全屏页覆盖锁屏、滑动判定强化、顶部提示
- **v1.2**：`setAlarmClock` 系统级闹钟、悬浮窗关闭条、多重后台启动兜底、权限引导
- **v1.3**：悬浮条避开状态栏（刘海/挖孔自适应）
- **v1.4**：全屏通知权限检测与跳转、Android 14 后台启动显式放行、首页锁屏诊断

### 说明

- 仓库内的 APK 为 debug 签名，仅供侧载安装测试
- 项目不依赖 AndroidX，使用原生框架 API，安装包体积小

---

## English

### Features

| Feature | Description |
|---|---|
| Pick an audio folder | Choose one folder via SAF as the "main folder"; audio files in sub-folders are scanned recursively, permission is persisted |
| Pick audio | List shows name + size, tap ▶ to preview, tap to select |
| Multiple ring times | Add several HH:mm times per alarm, auto sorted, individually removable |
| Daily repeat | Switch, on by default; when off the alarm fires once and auto-disables |
| Re-ring when not dismissed | Switch, on by default: rings again 10 minutes later if you didn't swipe it away |
| Ring duration | 30s / 1 min (default) / 2 / 3 / 5 / 10 min, playback stops automatically |
| Swipe to dismiss | Any-direction swipe on the full-screen alarm page, plus a fallback "Stop" button |
| Top floating close bar | A floating bar below the status bar with a **Close** button, available over any app |
| Edit / delete | Tap a row to edit the same 3-step flow; long-press to delete |
| 24/7 background | Foreground keeper service, boot auto-start, revive after task removal, 10-min watchdog + JobScheduler |
| On-time scheduling | Uses `setAlarmClock()` for system-level exact alarms that wake the device |
| Diagnostics | Home screen reports whether the lock-screen page was blocked and jumps to the right settings page |

### 3-step creation

1. **Folder**: "Choose folder" → system picker; the last folder is remembered for new alarms
2. **Audio**: pick one from the scanned list (preview available)
3. **Times**: add one or more times, set daily repeat / re-ring / ring duration → save

### Permissions

| Permission | Purpose |
|---|---|
| `POST_NOTIFICATIONS` | Ring notification and full-screen intent (Android 13+) |
| `USE_FULL_SCREEN_INTENT` | Show the alarm page on the lock screen (Android 14+ requires allow "Full-screen notifications") |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Exact alarms without delays |
| `SYSTEM_ALERT_WINDOW` | Top floating close bar |
| `FOREGROUND_SERVICE` (+ `MEDIA_PLAYBACK` / `SPECIAL_USE`) | Ringing and keeper services |
| `RECEIVE_BOOT_COMPLETED` | Restore alarms after reboot |
| `WAKE_LOCK` / `VIBRATE` | Keep awake and vibrate while ringing |

### Lock screen page doesn't appear?

1. Allow notifications for this app and keep the "Alarm ring" channel enabled
2. Android 14+: allow **Full-screen notifications** for this app
3. Allow **Display over other apps**
4. Some OEM ROMs (Xiaomi, OPPO, vivo, Honor) additionally require **Show on lock screen** / **Background pop-up**
5. The home-screen "Lock-screen check" line tells you exactly which one is missing; tap it to jump

### Build

Requirements: JDK 17, Android SDK with `compileSdk 34` / `build-tools 34.0.0` / `minSdk 24`, Gradle 8.7 with AGP 8.5.2.

```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Or open the project folder in Android Studio.

### Notes

- The APK in this repo is debug-signed and intended for sideload testing
- No AndroidX dependency; pure framework APIs, hence a very small APK

### License

MIT
