package com.github.serezhka.jap2server.internal.handler.audio;

import com.github.serezhka.jap2lib.AirPlay;
import com.github.serezhka.jap2server.AirplayDataConsumer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class AudioHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger log = LoggerFactory.getLogger(AudioHandler.class);

    private final AirPlay airPlay;
    private final AirplayDataConsumer dataConsumer;

    private final AudioPacket[] buffer = new AudioPacket[512];

    private int prevSeqNum;
    private int packetsInBuffer;

    public AudioHandler(AirPlay airPlay, AirplayDataConsumer dataConsumer) {
        this.airPlay = airPlay;
        this.dataConsumer = dataConsumer;
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = new AudioPacket();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        ByteBuf content = msg.content();

        byte[] headerBytes = new byte[12];
        content.readBytes(headerBytes);

        int flag = headerBytes[0] & 0xFF;
        int type = headerBytes[1] & 0x7F;

        int curSeqNo = ((headerBytes[2] & 0xFF) << 8) | (headerBytes[3] & 0xFF);

        long timestamp = (headerBytes[7] & 0xFF) | ((headerBytes[6] & 0xFF) << 8) |
                ((headerBytes[5] & 0xFF) << 16) | ((headerBytes[4] & 0xFF) << 24);

        long ssrc = (headerBytes[11] & 0xFF) | ((headerBytes[6] & 0xFF) << 8) |
                ((headerBytes[9] & 0xFF) << 16) | ((headerBytes[8] & 0xFF) << 24);

        // 1. 处理序列号翻转和重同步
        if (prevSeqNum != 0) {
            int diff = (short) (curSeqNo - prevSeqNum);

            if (diff <= 0) return; // 忽略过期包

            // 阈值改进：如果差值太大（超过512）或积压太多（超过128），强制重同步
            if (diff > buffer.length || packetsInBuffer > 128) {
                log.warn("Force sync: packetsInBuffer={}, diff={}, resetting to {}", packetsInBuffer, diff, curSeqNo);

                // 如果是积压导致的，为了让 dequeue 能跑通，我们将 prevSeqNum 设为当前包的前一个
                prevSeqNum = (curSeqNo - 1) & 0xFFFF;

                // 如果是跨度太大导致的，清空旧数据
                if (diff > buffer.length) {
                    resetBuffer();
                    prevSeqNum = 0;
                }
            }
        }

        log.debug("Got audio packet. flag: {}, type: {}, prevSeqNum: {}, curSecNum: {}, audio packets in buffer: {}",
                flag, type, prevSeqNum, curSeqNo, packetsInBuffer);

        // 2. 将数据存入缓冲区
        AudioPacket audioPacket = buffer[curSeqNo % buffer.length];
        if (audioPacket.isAvailable()) {
            // 如果缓冲区该位置已有未消费的数据（被覆盖），计数器减一
            packetsInBuffer--;
        }

        audioPacket
                .flag(flag)
                .type(type)
                .sequenceNumber(curSeqNo)
                .timestamp(timestamp)
                .ssrc(ssrc)
                .available(true)
                .encodedAudioSize(content.readableBytes())
                .encodedAudio(packet -> content.readBytes(packet, 0, content.readableBytes()));
        packetsInBuffer++;

        // 3. 驱动消费循环
        // 如果是第一个包，从当前包开始消费；否则尝试从期望的下一个包开始
        int nextSeqToDequeue = (prevSeqNum == 0) ? curSeqNo : ((prevSeqNum + 1) & 0xFFFF);
        int maxLoop = 512; // 防止死循环
        while (dequeue(nextSeqToDequeue) && maxLoop-- > 0) {
            nextSeqToDequeue = (prevSeqNum + 1) & 0xFFFF;
        }
    }

    private void resetBuffer() {
        for (AudioPacket packet : buffer) {
            packet.available(false);
        }
        packetsInBuffer = 0;
    }

    private boolean dequeue(int seqNo) throws Exception {
        if (prevSeqNum == 0 || (short) (seqNo - prevSeqNum) == 1) {
            AudioPacket audioPacket = buffer[seqNo % buffer.length];

            if (audioPacket.isAvailable() && audioPacket.getSequenceNumber() == (seqNo & 0xFFFF)) {

                int size = audioPacket.getEncodedAudioSize();
                byte[] dataToDecrypt = new byte[size];
                // 拷贝一份，避免原地修改带来的竞争或残留问题
                System.arraycopy(audioPacket.getEncodedAudio(), 0, dataToDecrypt, 0, size);

                // 执行解密
                airPlay.decryptAudio(dataToDecrypt, size);

                // 关键：将解密后的数据交给消费者
                dataConsumer.onAudio(dataToDecrypt);

                audioPacket.available(false);
                prevSeqNum = seqNo & 0xFFFF;
                packetsInBuffer--;
                return true;
            }
        }
        return false;
    }
}