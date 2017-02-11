package com.martonbot.audiotrigger2;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

public class AudioActivity extends AppCompatActivity {

    protected AudioMonitor monitor;
    protected boolean isAudioAvailable = true;
    protected SharedPreferences sharedPreferences;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case Constants.MY_PERMISSIONS_REQUEST_RECORD_AUDIO:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startAudioMonitoring();
                }
                else {
                    // disable audio trigger
                    stopAudioMonitoring();
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean(Preferences.PREF_AUDIO_ENABLED, false);
                    editor.apply();
                    Toast.makeText(AudioActivity.this, "Well just use your fingers then", Toast.LENGTH_SHORT).show();
                }
        }
    }

    protected void stopAudioMonitoring() {
        monitor.stopMonitoring();
        isAudioAvailable = true;
    }

    protected void startAudioMonitoring() {

        // check for permission to record audio
        int permissionCheck = ContextCompat.checkSelfPermission(AudioActivity.this, Manifest.permission.RECORD_AUDIO);
        if (permissionCheck == PackageManager.PERMISSION_DENIED) {
            // ask for permission
            ActivityCompat.requestPermissions(AudioActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, Constants.MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
        }
        else {
            // permission granted
            isAudioAvailable = monitor.startMonitoring();
            if (!isAudioAvailable) {
                Toast.makeText(AudioActivity.this, "Audio monitoring is not available", Toast.LENGTH_SHORT).show();
            }
        }
    }

}
