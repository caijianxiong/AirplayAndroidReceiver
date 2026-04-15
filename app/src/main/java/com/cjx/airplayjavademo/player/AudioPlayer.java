package com.cjx.airplayjavademo.player;

import static android.media.MediaCodec.MetricsConstants.MIME_TYPE;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaFormat;
import android.util.Log;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

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

    private static final String MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC;
    private MediaCodec mDecoder;

    public AudioPlayer() {
        this.mTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate, mChannel, mAudioFormat,
                AudioTrack.getMinBufferSize(mSampleRate, mChannel, mAudioFormat), AudioTrack.MODE_STREAM);
        this.mTrack.play();
        initDecoder();
    }

    private void initDecoder() {
        try {
            MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE, 44100, 2);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectELD);

            // --- 关键修改：添加 CSD-0 ---
            // 这是 AAC_ELD (Profile 39), 44100Hz, Stereo 的通用配置
            // 这里的字节数组需要根据具体的 fmtp config 生成，以下是一个常用值
            byte[] csd0 = new byte[]{(byte) 0xF8, (byte) 0xE8, (byte) 0x50, (byte) 0x00};
            // 或者更简单的 2 字节版本（取决于具体参数）: {(byte) 0xF8, (byte) 0x02}
            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0));

            mDecoder = MediaCodec.createDecoderByType(MIME_TYPE);
            mDecoder.configure(format, null, null, 0);
            mDecoder.start();
        } catch (Exception e) {
            Log.e(TAG, "Decoder init failed", e);
        }
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
                PCMPacket packet = packets.take();

                // 监控：如果队列快满了，说明处理太慢，可以考虑清理部分旧数据
                if (packets.size() > 400) {
                    Log.w(TAG, "Queue near full, clearing: " + packets.size());
                    packets.clear();
                    continue;
                }

                doPlay(packet);
            } catch (InterruptedException e) {
                Log.e(TAG, "run: take error: ", e);
            }
        }
    }

    private void doPlay(PCMPacket pcmPacket) {

        if (mDecoder != null && mTrack != null) {
//            Log.d(TAG, "doPlay: ");
            decodeAndPlay(pcmPacket.data);
        } else {
            Log.d(TAG, "doPlay mDecoder:" + mDecoder + "--mTrack:" + mTrack);
        }

    }

    private void decodeAndPlay(byte[] data) {
        // 1. 将数据塞入解码器输入缓冲区
        int inputIndex = mDecoder.dequeueInputBuffer(10000);
        if (inputIndex >= 0) {
            ByteBuffer buffer = mDecoder.getInputBuffer(inputIndex);
            buffer.clear();
            buffer.put(data);
            mDecoder.queueInputBuffer(inputIndex, 0, data.length, 0, 0);
        }

        // 2. 从输出缓冲区拿 PCM 数据并播放
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outputIndex = mDecoder.dequeueOutputBuffer(info, 10000);
        if (outputIndex < 0) {
            Log.d(TAG, "decodeAndPlay outputIndex: " + outputIndex);
        }
        while (outputIndex >= 0) {
            ByteBuffer buffer = mDecoder.getOutputBuffer(outputIndex);
            byte[] pcm = new byte[info.size];
            buffer.get(pcm);
            buffer.clear();

//            Log.d(TAG, "decodeAndPlay: " + pcm.length);
            mTrack.write(pcm, 0, pcm.length); // 这里才是真正的 PCM 播放

            mDecoder.releaseOutputBuffer(outputIndex, false);
            outputIndex = mDecoder.dequeueOutputBuffer(info, 0);
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

        if (mDecoder != null) {
            mDecoder.release();
        }
    }

}
