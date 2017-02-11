package com.martonbot.audiotrigger2;

import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;

public class AudioMonitor {

    static final private double EMA_FILTER = 0.6;
    private double mEMA = 0.0;

    private MediaRecorder mediaRecorder;

    public boolean startMonitoring() {

        boolean ret = true;

        if (mediaRecorder == null) {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            // TODO consider changing the output format
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            // TODO consider changing the encoder
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            // TODO consider setting a different bit rate and sampling rate
            mediaRecorder.setOutputFile("/dev/null");
            try {
                mediaRecorder.prepare();
            } catch (IOException e) {
                stopMonitoring();
                ret = false;
            }
            try {
                mediaRecorder.start();
            } catch (RuntimeException e) {
                stopMonitoring();
                ret = false;
            }
        }
        return ret;
    }

    public void stopMonitoring() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception e) {
                Log.e("com.martonbot", "Error while calling MediaRecorder.stop()", e);
            }
            try {
                mediaRecorder.reset();
            } catch (Exception e) {
                Log.e("com.martonbot", "Error while calling MediaRecorder.reset()", e);
            }
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    // old implementation using the logarithm
    /*public int getLogMaxAmplitude() {
        if (mediaRecorder != null) {
            int maxAmplitude = Math.max(mediaRecorder.getMaxAmplitude(), config.ampFloor);
            return (int) (config.logRatio * Math.log(maxAmplitude / ((double) config.ampFloor)));
        }
        return 0;
    }*/

    public double getAmplitudeEMA() {
        double amp = mediaRecorder.getMaxAmplitude()/2700.0;
        mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA;
        return Math.min(2* mEMA, 10);
    }

}
