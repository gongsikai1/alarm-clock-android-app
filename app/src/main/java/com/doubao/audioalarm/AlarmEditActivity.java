package com.doubao.audioalarm;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.doubao.audioalarm.data.AlarmStore;
import com.doubao.audioalarm.model.Alarm;
import com.doubao.audioalarm.util.AlarmScheduler;
import com.doubao.audioalarm.util.AudioScanner;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** 三步式闹钟编辑：1 选文件夹 → 2 选音频 → 3 选时间与选项 */
public class AlarmEditActivity extends Activity {

    private static final int REQ_FOLDER = 101;

    private static final String[] DURATION_LABELS = {"30 秒", "1 分钟", "2 分钟", "3 分钟", "5 分钟", "10 分钟"};
    private static final int[] DURATION_VALUES = {30, 60, 120, 180, 300, 600};

    private final Alarm draft = new Alarm();
    private boolean isEdit;
    private int step = 1;

    private View stepFolder, stepAudio, stepTime;
    private TextView stepLabel, tab1, tab2, tab3, folderName, folderPath, scanResult;
    private TextView btnPrev, btnNext, editTitle;
    private ListView audioList;
    private TextView audioCount, audioEmpty;
    private View timesContainer;

    private final List<AudioScanner.AudioItem> audioItems = new ArrayList<>();
    private AudioAdapter audioAdapter;
    private AudioScanner.AudioItem selectedAudio;

    private MediaPlayer preview;
    private String previewUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        stepFolder = findViewById(R.id.step_folder);
        stepAudio = findViewById(R.id.step_audio);
        stepTime = findViewById(R.id.step_time);
        stepLabel = findViewById(R.id.step_label);
        tab1 = findViewById(R.id.step_tab_1);
        tab2 = findViewById(R.id.step_tab_2);
        tab3 = findViewById(R.id.step_tab_3);
        folderName = findViewById(R.id.folder_name);
        folderPath = findViewById(R.id.folder_path);
        scanResult = findViewById(R.id.scan_result);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        editTitle = findViewById(R.id.edit_title);
        audioList = findViewById(R.id.audio_list);
        audioCount = findViewById(R.id.audio_count);
        audioEmpty = findViewById(R.id.audio_empty);
        timesContainer = findViewById(R.id.times_container);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.pick_folder_btn).setOnClickListener(v -> pickFolder());
        findViewById(R.id.add_time_btn).setOnClickListener(v -> addTime());

        audioAdapter = new AudioAdapter();
        audioList.setAdapter(audioAdapter);
        audioList.setOnItemClickListener((parent, view, position, id) -> {
            selectedAudio = audioItems.get(position);
            draft.audioUri = selectedAudio.uri;
            draft.audioName = selectedAudio.name;
            audioAdapter.notifyDataSetChanged();
        });

        long editId = getIntent().getLongExtra("edit_id", -1L);
        if (editId > 0) {
            Alarm src = AlarmStore.get(this).find(editId);
            if (src != null) {
                isEdit = true;
                copyInto(src, draft);
                editTitle.setText("编辑闹钟");
                if (!src.folderUri.isEmpty()) {
                    draft.folderUri = src.folderUri;
                    draft.folderName = src.folderName;
                    folderName.setText(src.folderName);
                    folderPath.setText(src.folderUri);
                    scanFolder(Uri.parse(src.folderUri), src.audioUri);
                }
            }
        } else {
            // 新闹钟：沿用上次选过的「主文件夹」
            String last = getSharedPreferences("ui_prefs", MODE_PRIVATE)
                    .getString("last_folder_uri", "");
            if (!last.isEmpty()) {
                draft.folderUri = last;
                draft.folderName = getSharedPreferences("ui_prefs", MODE_PRIVATE)
                        .getString("last_folder_name", "");
                folderName.setText(draft.folderName);
                folderPath.setText(last);
                scanFolder(Uri.parse(last), null);
            }
        }

        setupDurationSpinner();
        setupSwitches();
        renderTimes();

        btnPrev.setOnClickListener(v -> showStep(Math.max(1, step - 1)));
        btnNext.setOnClickListener(v -> {
            if (step < 3) {
                if (validateStep(step)) showStep(step + 1);
            } else {
                save();
            }
        });

        showStep(1);
    }

    private void copyInto(Alarm src, Alarm dst) {
        dst.id = src.id;
        dst.folderUri = src.folderUri;
        dst.folderName = src.folderName;
        dst.audioUri = src.audioUri;
        dst.audioName = src.audioName;
        dst.times.clear();
        dst.times.addAll(src.times);
        dst.daily = src.daily;
        dst.repeatIfNotDismissed = src.repeatIfNotDismissed;
        dst.durationSeconds = src.durationSeconds;
        dst.enabled = true;
    }

    private void setupDurationSpinner() {
        Spinner spinner = findViewById(R.id.duration_spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner, DURATION_LABELS);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinner.setAdapter(adapter);
        int index = 1;
        for (int i = 0; i < DURATION_VALUES.length; i++) {
            if (DURATION_VALUES[i] == draft.durationSeconds) index = i;
        }
        spinner.setSelection(index);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                draft.durationSeconds = DURATION_VALUES[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupSwitches() {
        Switch daily = findViewById(R.id.daily_switch);
        Switch rering = findViewById(R.id.rering_switch);
        daily.setChecked(draft.daily);
        rering.setChecked(draft.repeatIfNotDismissed);
        daily.setOnCheckedChangeListener((b, checked) -> draft.daily = checked);
        rering.setOnCheckedChangeListener((b, checked) -> draft.repeatIfNotDismissed = checked);
    }

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_FOLDER);
        } catch (Exception e) {
            Toast.makeText(this, "当前设备没有可用的文件选择器", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FOLDER && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            draft.folderUri = uri.toString();
            selectedAudio = null;
            draft.audioUri = "";
            draft.audioName = "";
            getSharedPreferences("ui_prefs", MODE_PRIVATE).edit()
                    .putString("last_folder_uri", uri.toString())
                    .putString("last_folder_name", AudioScanner.folderName(this, uri))
                    .apply();
            scanFolder(uri, null);
        }
    }

    private void scanFolder(Uri uri, final String preselectUri) {
        String name = AudioScanner.folderName(this, uri);
        draft.folderName = name;
        folderName.setText(name);
        folderPath.setText(uri.toString());
        scanResult.setText("正在扫描音频…");
        audioItems.clear();
        audioAdapter.notifyDataSetChanged();

        final android.content.Context appCtx = getApplicationContext();
        new Thread(() -> {
            final List<AudioScanner.AudioItem> result = AudioScanner.scan(appCtx, uri, 500);
            runOnUiThread(() -> {
                audioItems.clear();
                audioItems.addAll(result);
                selectedAudio = null;
                if (preselectUri != null) {
                    for (AudioScanner.AudioItem it : audioItems) {
                        if (preselectUri.equals(it.uri)) {
                            selectedAudio = it;
                            draft.audioUri = it.uri;
                            draft.audioName = it.name;
                            break;
                        }
                    }
                }
                audioAdapter.notifyDataSetChanged();
                scanResult.setText("已发现 " + audioItems.size() + " 个音频文件");
                audioCount.setText(audioItems.isEmpty()
                        ? "没有可用音频，换一个文件夹试试"
                        : "共 " + audioItems.size() + " 个音频 · 点右侧 ▶ 可试听");
                audioEmpty.setVisibility(audioItems.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    private void addTime() {
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);
        new TimePickerDialog(this, (view, h, m) -> {
            String t = String.format(Locale.US, "%02d:%02d", h, m);
            if (draft.times.contains(t)) {
                Toast.makeText(this, "这个时间已经添加过了", Toast.LENGTH_SHORT).show();
                return;
            }
            draft.times.add(t);
            sortTimes();
            renderTimes();
        }, hour, minute, true).show();
    }

    private void sortTimes() {
        java.util.Collections.sort(draft.times, (a, b) -> a.compareTo(b));
    }

    private void renderTimes() {
        android.widget.LinearLayout container = (android.widget.LinearLayout) timesContainer;
        container.removeAllViews();
        if (draft.times.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("还没有时间点，点下面的按钮添加");
            tv.setTextColor(getResources().getColor(R.color.text3));
            tv.setTextSize(13);
            tv.setPadding(4, 16, 4, 16);
            container.addView(tv);
            return;
        }
        for (final String time : new ArrayList<>(draft.times)) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_time, container, false);
            ((TextView) row.findViewById(R.id.time_text)).setText(time);
            row.findViewById(R.id.time_remove).setOnClickListener(v -> {
                draft.times.remove(time);
                renderTimes();
            });
            container.addView(row);
        }
    }

    private void showStep(int target) {
        step = target;
        stepFolder.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        stepAudio.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        stepTime.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
        stepLabel.setText(step + " / 3");
        styleTab(tab1, step >= 1 && step == 1);
        styleTab(tab2, step == 2);
        styleTab(tab3, step == 3);
        btnPrev.setVisibility(step == 1 ? View.INVISIBLE : View.VISIBLE);
        btnNext.setText(step == 3 ? "保存闹钟" : "下一步");
        if (step == 2) {
            audioCount.setText(audioItems.isEmpty()
                    ? "没有可用音频，换一个文件夹试试"
                    : "共 " + audioItems.size() + " 个音频 · 点右侧 ▶ 可试听");
            audioEmpty.setVisibility(audioItems.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (step == 3) renderTimes();
    }

    private void styleTab(TextView tab, boolean active) {
        tab.setBackgroundResource(active ? R.drawable.bg_row_selected : R.drawable.bg_card);
        tab.setTextColor(getResources().getColor(active ? R.color.accent : R.color.text2));
        tab.setTypeface(null, active ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private boolean validateStep(int s) {
        if (s == 1) {
            if (draft.folderUri == null || draft.folderUri.isEmpty()) {
                Toast.makeText(this, "请先选择一个音频文件夹", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (audioItems.isEmpty()) {
                Toast.makeText(this, "这个文件夹里没有音频文件，请换一个文件夹", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        }
        if (s == 2) {
            if (selectedAudio == null) {
                Toast.makeText(this, "请选择一个音频作为闹铃", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        }
        return true;
    }

    private void save() {
        if (!validateStep(1) || !validateStep(2)) {
            showStep(selectedAudio == null ? 2 : 1);
            return;
        }
        if (draft.times.isEmpty()) {
            Toast.makeText(this, "至少添加一个响铃时间", Toast.LENGTH_SHORT).show();
            return;
        }
        draft.enabled = true;
        if (!isEdit) draft.id = System.currentTimeMillis();

        AlarmStore store = AlarmStore.get(this);
        Alarm old = store.find(draft.id);
        if (old != null) AlarmScheduler.cancelAll(this, old);
        store.upsert(draft);
        AlarmScheduler.schedule(this, draft);

        if (AlarmScheduler.canScheduleExact(this)) {
            Toast.makeText(this, "已保存，闹钟会准时响起", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "已保存，但缺少「闹钟和提醒」权限，可能不准时", Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private void playPreview(AudioScanner.AudioItem item, TextView button) {
        if (preview != null && item.uri.equals(previewUri)) {
            stopPreview();
            button.setText("▶");
            return;
        }
        stopPreview();
        try {
            preview = new MediaPlayer();
            preview.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            preview.setDataSource(this, Uri.parse(item.uri));
            preview.prepare();
            preview.start();
            preview.setOnCompletionListener(mp -> {
                stopPreview();
                audioAdapter.notifyDataSetChanged();
            });
            previewUri = item.uri;
            button.setText("■");
        } catch (Exception e) {
            stopPreview();
            Toast.makeText(this, "这个音频无法播放", Toast.LENGTH_SHORT).show();
        }
        audioAdapter.notifyDataSetChanged();
    }

    private void stopPreview() {
        if (preview != null) {
            try {
                if (preview.isPlaying()) preview.stop();
            } catch (Exception ignored) {
            }
            try {
                preview.release();
            } catch (Exception ignored) {
            }
            preview = null;
        }
        previewUri = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPreview();
        if (audioAdapter != null) audioAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPreview();
    }

    /** 音频列表适配器 */
    private class AudioAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return audioItems.size();
        }

        @Override
        public Object getItem(int position) {
            return audioItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(AlarmEditActivity.this)
                        .inflate(R.layout.item_audio, parent, false);
            }
            final AudioScanner.AudioItem item = audioItems.get(position);
            TextView name = view.findViewById(R.id.audio_name);
            TextView size = view.findViewById(R.id.audio_size);
            final TextView play = view.findViewById(R.id.audio_play);

            name.setText(item.name);
            size.setText(formatSize(item.size));
            boolean selected = item == selectedAudio;
            view.setBackgroundResource(selected ? R.drawable.bg_row_selected : R.drawable.bg_row);
            name.setTextColor(getResources().getColor(selected ? R.color.accent : R.color.text));
            play.setText(item.uri.equals(previewUri) ? "■" : "▶");

            final View.OnClickListener playClick = v -> playPreview(item, play);
            play.setOnClickListener(playClick);
            view.setOnClickListener(v -> {
                selectedAudio = item;
                draft.audioUri = item.uri;
                draft.audioName = item.name;
                notifyDataSetChanged();
            });
            return view;
        }
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "音频文件";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
