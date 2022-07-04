package com.cjx.airplayjavademo.player;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;


import com.cjx.airplayjavademo.model.PCMPacket;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AudioPlayer extends Thread {

    private static String TAG = "AudioPlayer";
    private AudioTrack mTrack;
    private int mChannel = AudioFormat.CHANNEL_OUT_STEREO;
    private int mSampleRate = 44100;
    private boolean isStopThread = false;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    BlockingQueue<PCMPacket> packets = new LinkedBlockingQueue<PCMPacket>(500);

    public AudioPlayer() {
        this.mTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate, mChannel, mAudioFormat,
                AudioTrack.getMinBufferSize(mSampleRate, mChannel, mAudioFormat), AudioTrack.MODE_STREAM);
        this.mTrack.play();
    }

    public void addPacker(PCMPacket pcmPacket) {
        try {
            packets.put(pcmPacket);
        } catch (InterruptedException e) {
            Log.e(TAG, "addPacker: ", e);
        }
    }

    @Override
    public void run() {
        super.run();
        while (!isStopThread) {
            try {
                doPlay(packets.take());
            } catch (InterruptedException e) {
                Log.e(TAG, "run: take error: ", e);
            }
        }
    }

    private void doPlay(PCMPacket pcmPacket) {
        if (mTrack != null) {
            try {
                mTrack.write(pcmPacket.data, 0, pcmPacket.data.length);
            } catch (Exception e) {
                Log.e(TAG, "doPlay: error", e);
            }
        }
    }

    public void stopPlay() {
        isStopThread = true;
        if (mTrack != null) {
            mTrack.flush();
            mTrack.stop();
            mTrack.release();
            packets.clear();
            mTrack = null;
        }
    }

}
