package com.github.serezhka.jap2server.internal.handler.mirroring;

import com.github.serezhka.jap2lib.AirPlay;
import com.github.serezhka.jap2server.AirplayDataConsumer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MirroringHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger log = LoggerFactory.getLogger(MirroringHandler.class);

    private final ByteBuf headerBuf = ByteBufAllocator.DEFAULT.ioBuffer(128, 128);
    private final AirPlay airPlay;
    private final AirplayDataConsumer dataConsumer;

    private MirroringHeader header;
    private ByteBuf payload;

    public MirroringHandler(AirPlay airPlay, AirplayDataConsumer dataConsumer) {
        this.airPlay = airPlay;
        this.dataConsumer = dataConsumer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        while (msg.isReadable()) {

            if (header == null) {
                msg.readBytes(headerBuf, Math.min(headerBuf.writableBytes(), msg.readableBytes()));
                if (headerBuf.writableBytes() == 0) {
                    header = new MirroringHeader(headerBuf);
                    headerBuf.clear();
                }
            }

            if (header != null && msg.readableBytes() > 0) {

                if (payload == null || payload.writableBytes() == 0) {
                    payload = ctx.alloc().directBuffer(header.getPayloadSize(), header.getPayloadSize());
                }

                msg.readBytes(payload, Math.min(payload.writableBytes(), msg.readableBytes()));

                if (payload.writableBytes() == 0) {

                    byte[] payloadBytes = new byte[header.getPayloadSize()];
                    payload.readBytes(payloadBytes);

                    try {
                        if (header.getPayloadType() == 0) {
                            airPlay.decryptVideo(payloadBytes);
                            processVideo(payloadBytes);
                        } else if (header.getPayloadType() == 1) {
                            processSPSPPS(payload);
                        } else {
                            log.debug("Unhandled payload type: {}", header.getPayloadType());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    payload.release();
                    payload = null;
                    header = null;
                }
            }
        }
    }

    private void processVideo(byte[] payload) {

        // TODO One nalu per packet?
        int nalu_size = 0;
        while (nalu_size < payload.length) {
            int nc_len = (payload[nalu_size + 3] & 0xFF) | ((payload[nalu_size + 2] & 0xFF) << 8) | ((payload[nalu_size + 1] & 0xFF) << 16) | ((payload[nalu_size] & 0xFF) << 24);
            log.debug("payload len: {}, nc_len: {}, nalu_type: {}", payload.length, nc_len, payload[4] & 0x1f);
            if (nc_len > 0) {
                payload[nalu_size] = 0;
                payload[nalu_size + 1] = 0;
                payload[nalu_size + 2] = 0;
                payload[nalu_size + 3] = 1;
                nalu_size += nc_len + 4;
            }
            if (payload.length - nc_len > 4) {
                log.error("Decrypt error!");
                return;
            }
        }

        dataConsumer.onVideo(payload);
    }

    private void processSPSPPS(ByteBuf payload) {
        payload.readerIndex(6);

        short spsLen = (short) payload.readUnsignedShort();
        byte[] sequenceParameterSet = new byte[spsLen];
        payload.readBytes(sequenceParameterSet);

        payload.skipBytes(1); // pps count

        short ppsLen = (short) payload.readUnsignedShort();
        byte[] pictureParameterSet = new byte[ppsLen];
        payload.readBytes(pictureParameterSet);

        int spsPpsLen = spsLen + ppsLen + 8;
        log.info("SPS PPS length: {}", spsPpsLen);
        byte[] spsPps = new byte[spsPpsLen];
        spsPps[0] = 0;
        spsPps[1] = 0;
        spsPps[2] = 0;
        spsPps[3] = 1;
        System.arraycopy(sequenceParameterSet, 0, spsPps, 4, spsLen);
        spsPps[spsLen + 4] = 0;
        spsPps[spsLen + 5] = 0;
        spsPps[spsLen + 6] = 0;
        spsPps[spsLen + 7] = 1;
        System.arraycopy(pictureParameterSet, 0, spsPps, 8 + spsLen, ppsLen);

        dataConsumer.onVideo(spsPps);
    }
}
