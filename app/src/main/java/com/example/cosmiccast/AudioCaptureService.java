
package com.example.cosmiccast;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;

public class AudioCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE";
    public static final String EXTRA_DATA = "EXTRA_DATA";
    private static final String TAG = "AudioCaptureService";
    private static final String CHANNEL_ID = "AudioCaptureServiceChannel";

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private AudioRecord audioRecord;
    private MediaCodec mediaCodec;
    private Thread captureThread;

    @Override
    public void onCreate() {
        super.onCreate();
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        createNotificationChannel();
    }

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("CosmicCast")
                .setContentText("Streaming audio to your Raspberry Pi.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, notification);
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
        Intent data = intent.getParcelableExtra(EXTRA_DATA);

        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);

        startAudioCapture();

        return START_NOT_STICKY;
    }

    @SuppressLint("MissingPermission")
    private void startAudioCapture() {
        AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build();

        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(Config.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();

        audioRecord = new AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(2 * 1024)
                .setAudioPlaybackCaptureConfig(config)
                .build();

        setupMediaCodec();
        mediaCodec.start();
        audioRecord.startRecording();

        captureThread = new Thread(this::encodeAndStreamAudio);
        captureThread.start();
    }

    private void setupMediaCodec() {
        try {
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, Config.SAMPLE_RATE);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, Config.BITRATE);
            mediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            Log.e(TAG, "Error setting up MediaCodec", e);
        }
    }

    private void addAdtsHeader(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        int freqIdx = 4;  //44.1KHz
        int chanCfg = 1;  //Mono

        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF1;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    private void encodeAndStreamAudio() {
        try (Socket socket = new Socket(Config.SERVER_IP, Config.SERVER_PORT)) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            while (!Thread.currentThread().isInterrupted()) {
                int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                    inputBuffer.clear();
                    int read = audioRecord.read(inputBuffer, 2 * 1024);
                    if (read > 0) {
                        mediaCodec.queueInputBuffer(inputBufferIndex, 0, read, 0, 0);
                    }
                }

                int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                while (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                    int outPacketSize = bufferInfo.size;
                    int outPacketSizeWithHeader = outPacketSize + 7;
                    byte[] outData = new byte[outPacketSizeWithHeader];

                    addAdtsHeader(outData, outPacketSizeWithHeader);

                    outputBuffer.get(outData, 7, outPacketSize);

                    socket.getOutputStream().write(outData);

                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error while streaming audio", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (captureThread != null) {
            captureThread.interrupt();
        }
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
        }
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Audio Capture Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
