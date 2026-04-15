package com.github.serezhka.jap2server;

import com.github.serezhka.jap2lib.rtsp.AudioStreamInfo;
import com.github.serezhka.jap2lib.rtsp.VideoStreamInfo;

public interface AirplayDataConsumer {

    void onVideo(byte[] video);

    void onVideoSize(int width, int height);

    void onVideoFormat(VideoStreamInfo videoStreamInfo);

    void onAudio(byte[] audio);

    void onAudioFormat(AudioStreamInfo audioInfo);
}
