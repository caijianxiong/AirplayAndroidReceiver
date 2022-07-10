package com.github.serezhka.jap2lib;

import android.util.Log;

import com.dd.plist.BinaryPropertyListParser;
import com.dd.plist.BinaryPropertyListWriter;
import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListFormatException;
import com.github.serezhka.jap2lib.rtsp.AudioStreamInfo;
import com.github.serezhka.jap2lib.rtsp.MediaStreamInfo;
import com.github.serezhka.jap2lib.rtsp.VideoStreamInfo;

import net.i2p.crypto.eddsa.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

class RTSP {

    private static final Logger log = LoggerFactory.getLogger(RTSP.class);
    private static String TAG = "RTSP";

    private String streamConnectionID;
    private byte[] encryptedAESKey;
    private byte[] eiv;

    MediaStreamInfo getMediaStreamInfo(InputStream rtspSetupPayload) throws Exception {
        NSDictionary rtspSetup = (NSDictionary) BinaryPropertyListParser.parse(rtspSetupPayload);
        if (rtspSetup.containsKey("streams")) {
            // assume one stream info per RTSP SETUP request
            HashMap stream = (HashMap) ((Object[]) rtspSetup.get("streams").toJavaObject())[0];
            int type = (int) stream.get("type");
            switch (type) {

                // video
                case 110:
                    Log.i(TAG, "video plist: " + rtspSetup.toXMLPropertyList());

                    if (stream.containsKey("streamConnectionID")) {
                        streamConnectionID = Long.toUnsignedString((long) stream.get("streamConnectionID"));
                    }
                    return new VideoStreamInfo(streamConnectionID);

                // audio
                case 96:
                    Log.i(TAG, "audio plist: " + rtspSetup.toXMLPropertyList());

                    AudioStreamInfo.AudioStreamInfoBuilder builder = new AudioStreamInfo.AudioStreamInfoBuilder();
                    if (stream.containsKey("ct")) {
                        int compressionType = (int) stream.get("ct");
                        builder.compressionType(AudioStreamInfo.CompressionType.fromCode(compressionType));
                    }
                    if (stream.containsKey("audioFormat")) {
                        long audioFormatCode = (int) stream.get("audioFormat"); // FIXME int or long ?!
                        builder.audioFormat(AudioStreamInfo.AudioFormat.fromCode(audioFormatCode));
                    }
                    if (stream.containsKey("spf")) {
                        int samplesPerFrame = (int) stream.get("spf");
                        builder.samplesPerFrame(samplesPerFrame);
                    }
                    return builder.build();

                default:
                    log.error("Unknown stream type: {}", type);
            }
        } else {
            Log.w(TAG, "getMediaStreamInfo: other type");
        }
        return null;
    }

    int getSetParameterVolume(InputStream in) throws IOException, PropertyListFormatException {
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader
                (in, Charset.forName(StandardCharsets.UTF_8.name())))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }
        String data = textBuilder.toString().trim();
        // ios 音量 -30----》0由小变大
        int volume = -15;
        try {
            volume = (int) Float.parseFloat(data.split(":")[1]);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.i(TAG, "getSetParameterVolume: " + volume);
        return volume;
    }

    void setup(InputStream in) throws Exception {
        NSDictionary request = (NSDictionary) BinaryPropertyListParser.parse(in);

        if (request.containsKey("ekey")) {
            encryptedAESKey = (byte[]) request.get("ekey").toJavaObject();
            log.info("Encrypted AES key: " + Utils.bytesToHex(encryptedAESKey));
        }

        if (request.containsKey("eiv")) {
            eiv = (byte[]) request.get("eiv").toJavaObject();
            log.info("AES eiv: " + Utils.bytesToHex(eiv));
        }
    }

    void setupVideo(OutputStream out, int videoDataPort, int videoEventPort, int videoTimingPort) throws IOException {
        NSArray streams = new NSArray(1);
        NSDictionary dataStream = new NSDictionary();
        dataStream.put("dataPort", videoDataPort);
        dataStream.put("type", 110);
        streams.setValue(0, dataStream);

        NSDictionary response = new NSDictionary();
        response.put("streams", streams);
        response.put("eventPort", videoEventPort);
        response.put("timingPort", videoTimingPort);
        BinaryPropertyListWriter.write(out, response);
    }

    void setupAudio(OutputStream out, int audioDataPort, int audioControlPort) throws IOException {
        NSArray streams = new NSArray(1);
        NSDictionary dataStream = new NSDictionary();
        dataStream.put("dataPort", audioDataPort);
        dataStream.put("type", 96);
        dataStream.put("controlPort", audioControlPort);
        streams.setValue(0, dataStream);

        NSDictionary response = new NSDictionary();
        response.put("streams", streams);
        BinaryPropertyListWriter.write(out, response);
    }

    String getStreamConnectionID() {
        return streamConnectionID;
    }

    byte[] getEncryptedAESKey() {
        return encryptedAESKey;
    }

    byte[] getEiv() {
        return eiv;
    }

    public void printPlist(String rtspMethod, InputStream inputStream) {

        try {
            NSDictionary plist = (NSDictionary) BinaryPropertyListParser.parse(inputStream);
            Log.i(TAG, rtspMethod + " plist 01: " + plist.toXMLPropertyList());
        } catch (Exception e) {
            StringBuilder textBuilder = new StringBuilder();
            try (Reader reader = new BufferedReader(new InputStreamReader
                    (inputStream, Charset.forName(StandardCharsets.UTF_8.name())))) {
                int c = 0;
                while ((c = reader.read()) != -1) {
                    textBuilder.append((char) c);
                }
                Log.i(TAG, rtspMethod + " plist 02: " + textBuilder.toString());
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
//            Log.e(TAG, rtspMethod+ "--printPlist: ", e);

        }
    }

}
