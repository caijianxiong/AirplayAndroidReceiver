package com.cjx.airplayjavademo.player;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import com.cjx.airplayjavademo.model.NALPacket;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class VideoPlayer {
    private static final String TAG = "VideoPlayer";
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;

    // 移除 hardcode 宽高，使用构造函数传入的值
    private int mVideoWidth;
    private int mVideoHeight;

    private MediaCodec mDecoder = null;
    private final Surface mSurface;
    private final BlockingQueue<NALPacket> packets = new LinkedBlockingQueue<>(500);
    private HandlerThread mDecodeThread;

    public VideoPlayer(Surface surface, int width, int height) {
        this.mSurface = surface;
        this.mVideoWidth = width > 0 ? width : 1920;
        this.mVideoHeight = height > 0 ? height : 1080;
    }

    private final MediaCodec.Callback mDecoderCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            try {
                // 改进：使用 poll 代替 take，严禁在回调中阻塞！
                NALPacket packet = packets.poll();
                if (packet != null) {
                    java.nio.ByteBuffer inputBuffer = codec.getInputBuffer(index);
                    if (inputBuffer != null) {
                        inputBuffer.clear();
                        inputBuffer.put(packet.nalData);
                        codec.queueInputBuffer(index, 0, packet.nalData.length, packet.pts, 0);
                    }
                } else {
                    // 如果暂时没数据，队列空，不处理，解码器会在下次有空位时再次触发回调
                }
            } catch (Exception e) {
                Log.e(TAG, "onInputBufferAvailable error", e);
            }
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
            try {
                codec.releaseOutputBuffer(index, true);
            } catch (Exception e) {
                Log.e(TAG, "onOutputBufferAvailable error", e);
            }
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            Log.e(TAG, "MediaCodec onError", e);
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            Log.i(TAG, "onOutputFormatChanged: " + format);
        }
    };

    public void start() {
        // 每次启动都创建新的线程
        mDecodeThread = new HandlerThread("VideoDecoder");
        mDecodeThread.start();

        try {
            mDecoder = MediaCodec.createDecoderByType(MIME_TYPE);

            // 改进：对于 H.264，如果宽度不确定，可以先设一个小一点的或者常用的 720p，
            // 实际解码时解码器会根据 SPS 自动调整。
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mVideoWidth, mVideoHeight);

            // 某些 RK 芯片需要显式设置这些参数防止 CORRUPTED
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024);

            mDecoder.setCallback(mDecoderCallback, new Handler(mDecodeThread.getLooper()));

            if (mSurface != null && mSurface.isValid()) {
                mDecoder.configure(format, mSurface, null, 0);
                mDecoder.start();
                Log.i(TAG, "Video Decoder started");
            }
        } catch (Exception e) {
            Log.e(TAG, "initDecoder failed", e);
        }
    }

    public void addPacker(NALPacket nalPacket) {
        packets.offer(nalPacket); // 使用 offer 防止阻塞主线程
    }

    public void stopVideoPlay() {
        try {
            if (mDecoder != null) {
                mDecoder.stop();
                mDecoder.release();
                mDecoder = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping decoder", e);
        } finally {
            if (mDecodeThread != null) {
                mDecodeThread.quitSafely();
                mDecodeThread = null;
            }
            packets.clear();
        }
    }

    // 移除了 doDecode 和 decode 同步方法，避免模式冲突
}