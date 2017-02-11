package com.martonbot.audiotrigger2;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.animation.ScaleAnimation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends AudioActivity {

    private View ampBar;
    private TextView audioStatusText;
    private Switch enableAudioSwitch;
    private SeekBar thresholdSeekBar;
    private Spinner cooldownSpinner;
    private Spinner pollIntervalSpinner;

    private TextDropdownAdapter environmentAdapter;
    private IntDropdownAdapter cooldownAdapter;
    private IntDropdownAdapter pollIntervalAdapter;

    private Runnable pollTask;
    private Handler taskHandler;

    private ScaleAnimation scaleAnimation;
    private float currentScale = 1f;
    private long triggerTime;

    private boolean isAudioEnabled;
    private int threshold;
    private int cooldown;
    private int pollInterval;

    private CompoundButton.OnCheckedChangeListener onAudioStatusCheckedChangeListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sharedPreferences = getSharedPreferences(Preferences.SHARED_PREFS, MODE_PRIVATE);

        monitor = new AudioMonitor();
        taskHandler = new Handler();

        ampBar = findViewById(R.id.amp_bar);
        audioStatusText = (TextView) findViewById(R.id.audio_status_text);

        enableAudioSwitch = (Switch) findViewById(R.id.switch_enable_audio);
        thresholdSeekBar = (SeekBar) findViewById(R.id.threshold_seekbar);
        cooldownSpinner = (Spinner) findViewById(R.id.cooldown_spinner);
        pollIntervalSpinner = (Spinner) findViewById(R.id.poll_interval_spinner);

        cooldownAdapter = new IntDropdownAdapter();
        cooldownAdapter.add(500);
        cooldownAdapter.add(Preferences.DEFAULT_COOLDOWN);
        cooldownAdapter.add(2000);
        cooldownAdapter.add(5000);
        cooldownSpinner.setAdapter(cooldownAdapter);

        pollIntervalAdapter = new IntDropdownAdapter();
        pollIntervalAdapter.add(Preferences.DEFAULT_POLL_INTERVAL);
        pollIntervalAdapter.add(200);
        pollIntervalAdapter.add(500);
        pollIntervalSpinner.setAdapter(pollIntervalAdapter);

        enableAudioSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isAudioEnabled = isChecked;
                updateAudioStatusText();
                if (isAudioEnabled) {
                    startAudioMonitoring();
                    taskHandler.removeCallbacks(getPollTask()); // so we never have more than one running
                    taskHandler.post(getPollTask());
                } else {
                    stopAudioMonitoring();
                }
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(Preferences.PREF_AUDIO_ENABLED, isChecked);
                editor.apply();
            }
        });

        thresholdSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                threshold = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // nothing
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(Preferences.PREF_THRESHOLD, threshold);
                editor.apply();
            }

        });

        cooldownSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                cooldown = (int) cooldownSpinner.getSelectedItem();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(Preferences.PREF_COOLDOWN, cooldown);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });

        pollIntervalSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                pollInterval = (int) pollIntervalSpinner.getSelectedItem();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(Preferences.PREF_POLL_INTERVAL, pollInterval);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // shared preferences
        isAudioEnabled = sharedPreferences.getBoolean(Preferences.PREF_AUDIO_ENABLED, true);
        threshold = sharedPreferences.getInt(Preferences.PREF_THRESHOLD, Preferences.DEFAULT_THRESHOLD);
        cooldown = sharedPreferences.getInt(Preferences.PREF_COOLDOWN, Preferences.DEFAULT_COOLDOWN);
        pollInterval = sharedPreferences.getInt(Preferences.PREF_POLL_INTERVAL, Preferences.DEFAULT_POLL_INTERVAL);

        enableAudioSwitch.setChecked(isAudioEnabled);
        thresholdSeekBar.setProgress(threshold);
        cooldownSpinner.setSelection(cooldownAdapter.getPosition(cooldown));
        pollIntervalSpinner.setSelection(pollIntervalAdapter.getPosition(pollInterval));

        enableAudioSwitch.setOnCheckedChangeListener(getOnAudioStatusCheckedChangeListener());

        // start monitoring
        if (isAudioEnabled) {
            startAudioMonitoring();
        }

        taskHandler.removeCallbacks(getPollTask()); // so we never have more than one running
        taskHandler.postDelayed(getPollTask(), Constants.DELAY_AFTER_START);

    }

    @Override
    protected void onPause() {
        super.onPause();

        // stop monitoring
        stopAudioMonitoring();

        // cancel Handler tickTask callback
        taskHandler.removeCallbacks(getPollTask());

        if (scaleAnimation != null) {
            scaleAnimation.cancel();
        }

        enableAudioSwitch.setOnCheckedChangeListener(null);
    }

    private Runnable getPollTask() {
        if (pollTask == null) {
            pollTask = new Runnable() {
                @Override
                public void run() {

                    double emaAmp = monitor.getAmplitudeEMA();
                    int ampLog = (int) emaAmp;
                    //int ampLog = monitor.getLogMaxAmplitude();

                    float nextScale = ampLog / 10f;
                    animateBar(currentScale, nextScale);
                    currentScale = nextScale;

                    long time = SystemClock.elapsedRealtime();
                    boolean isTriggered = ampLog >= threshold && time >= triggerTime + cooldown;
                    if (isTriggered) {
                        triggerTime = time;
                    }

                    int colorId = time < triggerTime + cooldown ? R.color.accent : R.color.primary_dark;
                    ampBar.setBackgroundColor(ContextCompat.getColor(SettingsActivity.this, colorId));

                    if (isAudioEnabled && isAudioAvailable) {
                        taskHandler.postDelayed(this, pollInterval);
                    }
                }
            };
        }
        return pollTask;
    }

    private void updateAudioStatusText() {
        int textId;
        if (isAudioEnabled) {
            textId = R.string.audio_trigger_enabled;
        } else {
            textId = R.string.audio_trigger_disabled;
        }
        audioStatusText.setText(textId);
    }

    private void animateBar(float fromScale, float toScale) {
        scaleAnimation = new ScaleAnimation(fromScale, toScale, 1f, 1f);
        scaleAnimation.setDuration((long) (pollInterval * .75f)); //  to allow for slight inaccuracies so that he animation look seamless
        scaleAnimation.setFillEnabled(true);
        scaleAnimation.setFillAfter(true);
        ampBar.startAnimation(scaleAnimation);
    }

    private CompoundButton.OnCheckedChangeListener getOnAudioStatusCheckedChangeListener() {
        if (onAudioStatusCheckedChangeListener == null) {
            onAudioStatusCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    isAudioEnabled = isChecked;
                    updateAudioStatusText();
                    if (isAudioEnabled) {
                        startAudioMonitoring();
                        taskHandler.removeCallbacks(getPollTask()); // so we never have more than one running
                        taskHandler.post(getPollTask());
                    } else {
                        stopAudioMonitoring();
                    }
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean(Preferences.PREF_AUDIO_ENABLED, isChecked);
                    editor.apply();
                }
            };
        }
        return onAudioStatusCheckedChangeListener;
    }

    private class IntDropdownAdapter extends ArrayAdapter<Integer> {

        public IntDropdownAdapter() {
            super(SettingsActivity.this, R.layout.dropdown_item);
        }

    }

    private class TextDropdownAdapter extends ArrayAdapter<String> {

        public TextDropdownAdapter() {
            super(SettingsActivity.this, R.layout.dropdown_item);
        }

    }
}
