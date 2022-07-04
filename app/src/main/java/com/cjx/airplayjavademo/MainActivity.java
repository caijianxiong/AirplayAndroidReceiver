package com.cjx.airplayjavademo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.cjx.airplayjavademo.model.NALPacket;
import com.cjx.airplayjavademo.model.PCMPacket;
import com.cjx.airplayjavademo.player.AudioPlayer;
import com.cjx.airplayjavademo.player.VideoPlayer;
import com.github.serezhka.jap2lib.rtsp.AudioStreamInfo;
import com.github.serezhka.jap2lib.rtsp.VideoStreamInfo;
import com.github.serezhka.jap2server.AirPlayServer;
import com.github.serezhka.jap2server.AirplayDataConsumer;

import java.util.LinkedList;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private SurfaceView mSurfaceView;
    private AirPlayServer airPlayServer;
    private static String TAG = "MainActivity";

    private VideoPlayer mVideoPlayer;
    private AudioPlayer mAudioPlayer;
    private final LinkedList<NALPacket> mVideoCacheList = new LinkedList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSurfaceView = findViewById(R.id.surfaceView);
        mSurfaceView.getHolder().addCallback(this);
        mAudioPlayer = new AudioPlayer();
        mAudioPlayer.start();

        airPlayServer = new AirPlayServer("caicai", 7000, 49152, airplayDataConsumer);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    airPlayServer.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "start-server-thread").start();


    }

    @Override
    protected void onStop() {
        super.onStop();
        mAudioPlayer.stopPlay();
        mAudioPlayer = null;
        mVideoPlayer.stopVideoPlay();
        mVideoPlayer = null;
        airplayDataConsumer = null;
        airPlayServer.stop();
    }

    private AirplayDataConsumer airplayDataConsumer = new AirplayDataConsumer() {
        @Override
        public void onVideo(byte[] video) {
//            Logger.i(TAG, "rev video length :%d", video.length);
            NALPacket nalPacket=new NALPacket();
            nalPacket.nalData=video;
            if (mVideoPlayer != null) {
                while (!mVideoCacheList.isEmpty()) {
                    mVideoPlayer.addPacker(mVideoCacheList.removeFirst());
                }
                mVideoPlayer.addPacker(nalPacket);
            } else {
                mVideoCacheList.add(nalPacket);
            }

        }

        @Override
        public void onVideoFormat(VideoStreamInfo videoStreamInfo) {

        }

        @Override
        public void onAudio(byte[] audio) {
//            Logger.i(TAG, "rev audio length :%d", audio.length);
            PCMPacket pcmPacket=new PCMPacket();
            pcmPacket.data=audio;
            if (mAudioPlayer != null) {
                mAudioPlayer.addPacker(pcmPacket);
            }

        }

        @Override
        public void onAudioFormat(AudioStreamInfo audioInfo) {

        }
    };


    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        if (mVideoPlayer == null) {
            Log.i(TAG, "surfaceChanged: width:" + width + "---height" + height);
            mVideoPlayer = new VideoPlayer(holder.getSurface(), width, height);
            mVideoPlayer.start();
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

    }
}