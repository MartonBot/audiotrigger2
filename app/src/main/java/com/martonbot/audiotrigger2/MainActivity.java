package com.martonbot.audiotrigger2;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.TextView;

public class MainActivity extends AudioActivity {

    private static final long TICK_DELAY = 75;
    private static final String ZERO_ZERO = "00";

    private boolean isAudioEnabled;

    private int threshold;
    private int cooldown;
    private int pollInterval;

    private View settingsButton;
    private View resetButton;
    private TextView minutesText;
    private TextView secondsText;
    private TextView hundredthsText;
    private View ampDisc;
    private View chronoView;
    private View mainBackground;

    private Runnable pollTask;
    private Runnable tickTask;
    private Handler taskHandler;


    private ScaleAnimation anim;
    private float currentScale = 1f;
    private float newScale = 1f;
    boolean currentlyTriggered = false;

    private boolean isChronometerRunning = false;
    private long elapsedTime = 0;
    private long chronoBase;
    private long triggerTime;

    private int hundredths;
    private int seconds;
    private int minutes;

    private int hd;
    private int sd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences(Preferences.SHARED_PREFS, MODE_PRIVATE);

        monitor = new AudioMonitor();
        taskHandler = new Handler();

        resetButton = findViewById(R.id.reset_button);
        minutesText = (TextView) findViewById(R.id.minutes_text);
        secondsText = (TextView) findViewById(R.id.seconds_text);
        hundredthsText = (TextView) findViewById(R.id.hundredths_text);
        ampDisc = findViewById(R.id.amp_disc);
        mainBackground = findViewById(R.id.main_background);
        settingsButton = findViewById(R.id.settings_button);
        chronoView = findViewById(R.id.chrono_view);

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reset();
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent settingsActivity = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(settingsActivity);
            }
        });

        chronoView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleChronometer();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // shared preferences
        isAudioEnabled = sharedPreferences.getBoolean(Preferences.PREF_AUDIO_ENABLED, true);
        threshold = sharedPreferences.getInt(Preferences.PREF_THRESHOLD, Preferences.DEFAULT_THRESHOLD);
        cooldown = sharedPreferences.getInt(Preferences.PREF_COOLDOWN, Preferences.DEFAULT_COOLDOWN);
        pollInterval = sharedPreferences.getInt(Preferences.PREF_POLL_INTERVAL, Preferences.DEFAULT_POLL_INTERVAL);

        updateResetButton();

        // start monitoring
        if (isAudioEnabled) {
            startAudioMonitoring();
        }

        taskHandler.postDelayed(getPollTask(), Constants.DELAY_AFTER_START);
        taskHandler.post(getTickTask());

    }

    @Override
    protected void onPause() {
        super.onPause();

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // stop monitoring
        stopAudioMonitoring();

        // cancel Handler tickTask callback
        taskHandler.removeCallbacks(getTickTask());
        taskHandler.removeCallbacks(getPollTask());

        if (anim != null) {
            anim.cancel();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(State.IS_RUNNING, isChronometerRunning);
        outState.putLong(State.ELAPSED_TIME, elapsedTime);
        outState.putLong(State.CHRONO_BASE, chronoBase);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        isChronometerRunning = savedInstanceState.getBoolean(State.IS_RUNNING);
        elapsedTime = savedInstanceState.getLong(State.ELAPSED_TIME);
        chronoBase = savedInstanceState.getLong(State.CHRONO_BASE);
    }

    private void startChronometer() {
        chronoBase = SystemClock.elapsedRealtime() - elapsedTime;
        isChronometerRunning = true;
        taskHandler.postDelayed(getTickTask(), TICK_DELAY);
        updateResetButton();
    }

    private void stopChronometer() {
        elapsedTime = SystemClock.elapsedRealtime() - chronoBase;
        taskHandler.removeCallbacks(getTickTask());
        updateChronoFields(elapsedTime, true); // to make sure the chrono displays the latest version of recorded elapsed time
        isChronometerRunning = false;
        updateResetButton();
    }

    private void toggleChronometer() {
        if (isChronometerRunning) {
            stopChronometer();
        } else {
            startChronometer();
        }
    }

    private void reset() {
        elapsedTime = 0;
        updateResetButton();
        hundredthsText.setText(ZERO_ZERO);
        secondsText.setText(ZERO_ZERO);
        minutesText.setText(ZERO_ZERO);
    }

    private void updateResetButton() {
        if (elapsedTime > 0 && !isChronometerRunning) {
            resetButton.setVisibility(View.VISIBLE);
        } else {
            resetButton.setVisibility(View.GONE);
        }
    }

    private Runnable getPollTask() {
        if (pollTask == null) {
            pollTask = new Runnable() {
                @Override
                public void run() {

                    double emaAmp = monitor.getAmplitudeEMA();

                    int ampLog = (int) emaAmp;
                    //int ampLog = monitor.getLogMaxAmplitude();
                    float ratio = ampLog / (float) threshold;
                    newScale = Math.min(1, (2 + ratio) / 3);

                    anim = new ScaleAnimation(currentScale, newScale, currentScale, newScale, Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5f);
                    anim.setDuration((long) (pollInterval * .75f));
                    anim.setFillEnabled(true);
                    anim.setFillAfter(true);
                    ampDisc.startAnimation(anim);

                    currentScale = (2 + ratio) / 3;

                    long time = SystemClock.elapsedRealtime();
                    if (ampLog >= threshold && time - triggerTime > cooldown) {
                        triggerTime = time;
                        toggleChronometer();
                    }

                    boolean triggered = time < triggerTime + cooldown;
                    int colorId = triggered ? R.color.accent : R.color.primary_dark;

                    if (triggered != currentlyTriggered) {
                        int drawableId = triggered ? R.drawable.accent_circle: R.drawable.dark_circle;
                        Drawable drawable = ContextCompat.getDrawable(MainActivity.this, drawableId);
                        ampDisc.setBackground(drawable);
                    }
                    currentlyTriggered = triggered;

                    mainBackground.setBackgroundColor(ContextCompat.getColor(MainActivity.this, colorId));

                    if (isAudioEnabled && isAudioAvailable) {
                        taskHandler.postDelayed(this, pollInterval);
                    }
                }
            };
        }
        return pollTask;
    }

    private Runnable getTickTask() {
        if (tickTask == null) {
            tickTask = new Runnable() {

                @Override
                public void run() {

                    updateChronoFields(elapsedTime, false);

                    if (isChronometerRunning) {
                        elapsedTime = SystemClock.elapsedRealtime() - chronoBase;
                        taskHandler.postDelayed(this, TICK_DELAY);
                    }

                }
            };
        }
        return tickTask;
    }

    private String format(int n) {
        return String.format("%02d", n);
    }

    private void updateChronoFields(long elapsed, boolean forceUpdate) {
        hundredths = (int) (elapsed) / Constants.TEN;
        hd = hundredths % Constants.ONE_HUNDRED;
        hundredthsText.setText(format(hd));

        if (forceUpdate || hd <= 20) { // savin' CPU
            seconds = hundredths / Constants.ONE_HUNDRED;
            sd = seconds % Constants.SIXTY;
            secondsText.setText(format(sd));

            if (forceUpdate || sd <= 1) { // savin' CPU
                minutes = seconds / Constants.SIXTY;
                minutesText.setText(format(minutes % Constants.SIXTY));
            }
        }
    }



}
