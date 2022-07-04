package com.cjx.airplayjavademo.player;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;


import com.cjx.airplayjavademo.model.NALPacket;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class VideoPlayer {
    private static final String TAG = "VideoPlayer";
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private final int mVideoWidth = 540;
    private final int mVideoHeight = 960;
    private final MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private MediaCodec mDecoder = null;
    private final Surface mSurface;
    private BlockingQueue<NALPacket> packets = new LinkedBlockingQueue<>(500);
    private final HandlerThread mDecodeThread = new HandlerThread("VideoDecoder");

    private final MediaCodec.Callback mDecoderCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            try {
                NALPacket packet = packets.take();
                codec.getInputBuffer(index).put(packet.nalData);
                mDecoder.queueInputBuffer(index, 0, packet.nalData.length, packet.pts, 0);
            } catch (InterruptedException e) {
                throw new IllegalStateException("Interrupted when is waiting");
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
            try {
                codec.releaseOutputBuffer(index, true);
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            Log.e(TAG, "Decode error", e);
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {

        }
    };

    public VideoPlayer(Surface surface, int width, int heigth) {
//        this.mVideoWidth=width;
//        this.mVideoHeight=heigth;
        mSurface = surface;
    }

    public void initDecoder() {
        mDecodeThread.start();
        try {
            // 解码分辨率
            Log.i(TAG, "initDecoder: mVideoWidth=" + mVideoWidth + "---mVideoHeight=" + mVideoHeight);
            mDecoder = MediaCodec.createDecoderByType(MIME_TYPE);
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mVideoWidth, mVideoHeight);
            mDecoder.configure(format, mSurface, null, 0);
            mDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            mDecoder.setCallback(mDecoderCallback, new Handler(mDecodeThread.getLooper()));
            mDecoder.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addPacker(NALPacket nalPacket) {
        try {
            packets.put(nalPacket);
        } catch (InterruptedException e) {
            // 队列满了
            Log.e(TAG, "run: put error:", e);
        }
    }

    public void start() {
        initDecoder();
    }

    public void stopVideoPlay() {
        try {
            mDecoder.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            mDecoder.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
        mDecodeThread.quit();
        packets.clear();
    }

    private void doDecode(NALPacket nalPacket) throws IllegalStateException {
        final long timeoutUsec = 10000;
//        Log.i(TAG, "doDecode: start");
        if (nalPacket.nalData == null) {
            Log.w(TAG, "doDecode: data is null return");
            return;
        }
        //获取MediaCodec的输入流
        ByteBuffer[] decoderInputBuffers = mDecoder.getInputBuffers();
        int inputBufIndex = -10000;
        try {
            inputBufIndex = mDecoder.dequeueInputBuffer(timeoutUsec);//设置解码等待时间，0为不等待，-1为一直等待，其余为时间单位
        } catch (Exception e) {
            Log.e(TAG, "dequeueInputBuffer error", e);
        }
        if (inputBufIndex >= 0) {
            ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
            inputBuf.put(nalPacket.nalData);
            // 输入流入队列
            mDecoder.queueInputBuffer(inputBufIndex, 0, nalPacket.nalData.length, nalPacket.pts, 0);
        } else {
            Log.d(TAG, "dequeueInputBuffer failed");
        }
        decode(timeoutUsec);
//        workHandler.post(new Runnable() {
//            @Override
//            public void run() {
//                decode(TIMEOUT_USEC);
//            }
//        });
//        Log.i(TAG, "doDecode: end");
    }

    @SuppressLint("WrongConstant")
    private void decode(long timeoutUsec) {
        int outputBufferIndex = -10000;
        try {
            outputBufferIndex = mDecoder.dequeueOutputBuffer(mBufferInfo, timeoutUsec);
        } catch (Exception e) {
            Log.e(TAG, "doDecode: dequeueOutputBuffer error:" + e.getMessage());
        }
        if (outputBufferIndex >= 0) {
            mDecoder.releaseOutputBuffer(outputBufferIndex, true);
//            try {
//                Thread.sleep(50);
//            } catch (InterruptedException ie) {
//                ie.printStackTrace();
//            }
        } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
//            try {
//                Thread.sleep(10);
//            } catch (InterruptedException ie) {
//                ie.printStackTrace();
//            }
        } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            // not important for us, since we're using Surface
        }
    }
}
