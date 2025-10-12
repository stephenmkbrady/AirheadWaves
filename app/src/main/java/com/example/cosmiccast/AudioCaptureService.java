package com.example.cosmiccast;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AudioCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE";
    public static final String EXTRA_DATA = "EXTRA_DATA";
    public static final String ACTION_STATS = "com.example.cosmiccast.STATS";
    public static final String EXTRA_STATS = "STATS";
    public static final String ACTION_SET_VOLUME = "com.example.cosmiccast.SET_VOLUME";
    public static final String EXTRA_VOLUME = "VOLUME";
    private static final String TAG = "AudioCaptureService";
    private static final String CHANNEL_ID = "AudioCaptureServiceChannel";

    public static boolean isRunning = false;

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private AudioRecord audioRecord;
    private MediaCodec mediaCodec;
    private Thread captureThread;
    private String serverIp;
    private int serverPort;
    private int bitrate;
    private int sampleRate;
    private String channelConfig;
    private float streamVolume = 1.0f;

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_SET_VOLUME.equals(intent.getAction())) {
                streamVolume = intent.getFloatExtra(EXTRA_VOLUME, 1.0f);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        createNotificationChannel();
        LocalBroadcastManager.getInstance(this).registerReceiver(volumeReceiver, new IntentFilter(ACTION_SET_VOLUME));
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
        serverIp = intent.getStringExtra("SERVER_IP");
        serverPort = intent.getIntExtra("SERVER_PORT", 8888);
        bitrate = intent.getIntExtra("BITRATE", 128000);
        sampleRate = intent.getIntExtra("SAMPLE_RATE", 44100);
        channelConfig = intent.getStringExtra("CHANNEL_CONFIG");
        streamVolume = intent.getFloatExtra(EXTRA_VOLUME, 1.0f);

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
                .setSampleRate(sampleRate)
                .setChannelMask("Stereo".equals(channelConfig) ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO)
                .build();

        audioRecord = new AudioRecord.Builder()
                .setAudioFormat(audioFormat)
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
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, "Stereo".equals(channelConfig) ? 2 : 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            mediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            Log.e(TAG, "Error setting up MediaCodec", e);
        }
    }

    private void addAdtsHeader(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        int freqIdx = getFreqIndex(sampleRate);
        int chanCfg = "Stereo".equals(channelConfig) ? 2 : 1;

        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF1;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    private int getFreqIndex(int sampleRate) {
        switch (sampleRate) {
            case 22050:
                return 7;
            case 44100:
                return 4;
            case 48000:
                return 3;
            default:
                return 4;
        }
    }

    private void applyVolume(ByteBuffer buffer, int bytes) {
        float scaledVolume = streamVolume * streamVolume * streamVolume;

        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < bytes / 2; i++) {
            short sample = buffer.getShort(i * 2);
            sample = (short) (sample * scaledVolume);
            buffer.putShort(i * 2, sample);
        }
    }

    private void encodeAndStreamAudio() {
        try (Socket socket = new Socket(serverIp, serverPort)) {
            broadcastStats("Connected");
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            long lastStatTime = System.currentTimeMillis();
            long bytesSent = 0;

            while (!Thread.currentThread().isInterrupted()) {
                int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                    inputBuffer.clear();
                    int read = audioRecord.read(inputBuffer, 2 * 1024);
                    if (read > 0) {
                        applyVolume(inputBuffer, read);
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
                    bytesSent += outPacketSizeWithHeader;

                    if (System.currentTimeMillis() - lastStatTime > 1000) {
                        long bps = (bytesSent * 8) / ((System.currentTimeMillis() - lastStatTime) / 1000);
                        broadcastStats("Connected\n" + bps / 1000 + " kbps");
                        lastStatTime = System.currentTimeMillis();
                        bytesSent = 0;
                    }

                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error while streaming audio", e);
            broadcastStats("Error: " + e.getMessage());
        }
    }

    private void broadcastStats(String stats) {
        Intent intent = new Intent(ACTION_STATS);
        intent.putExtra(EXTRA_STATS, stats);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        LocalBroadcastManager.getInstance(this).unregisterReceiver(volumeReceiver);
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
