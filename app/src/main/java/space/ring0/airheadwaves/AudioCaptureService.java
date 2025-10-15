package space.ring0.airheadwaves;

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
    public static final String ACTION_STATS = "com.example.space.ring0.airheadwaves.STATS";
    public static final String EXTRA_STATS = "STATS";
    public static final String ACTION_SET_VOLUME = "com.example.space.ring0.airheadwaves.SET_VOLUME";
    public static final String EXTRA_VOLUME = "VOLUME";
    public static final String ACTION_AUDIO_LEVEL = "com.example.space.ring0.airheadwaves.AUDIO_LEVEL";
    public static final String EXTRA_AUDIO_LEVEL = "AUDIO_LEVEL";
    public static final String ACTION_UPDATE_TONE_CONTROLS = "com.example.space.ring0.airheadwaves.UPDATE_TONE_CONTROLS";

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
    private float bass = 0f;
    private float treble = 0f;

    private BiquadFilter bassFilter;
    private BiquadFilter trebleFilter;


    private final BroadcastReceiver settingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_SET_VOLUME.equals(intent.getAction())) {
                streamVolume = intent.getFloatExtra(EXTRA_VOLUME, 1.0f);
            } else if (ACTION_UPDATE_TONE_CONTROLS.equals(intent.getAction())) {
                bass = intent.getFloatExtra("BASS", 0f);
                treble = intent.getFloatExtra("TREBLE", 0f);
                updateFilters();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        createNotificationChannel();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SET_VOLUME);
        filter.addAction(ACTION_UPDATE_TONE_CONTROLS);
        LocalBroadcastManager.getInstance(this).registerReceiver(settingsReceiver, filter);
    }

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AirheadWaves")
                .setContentText("Streaming audio to your devices.")
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
        bass = intent.getFloatExtra("BASS", 0f);
        treble = intent.getFloatExtra("TREBLE", 0f);

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
        setupFilters();
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

    private void setupFilters() {
        bassFilter = new BiquadFilter(sampleRate);
        trebleFilter = new BiquadFilter(sampleRate);
        updateFilters();
    }

    private void updateFilters() {
        bassFilter.setLowShelf(bass, 200f);
        trebleFilter.setHighShelf(treble, 3000f);
    }

    private void addAdtsHeader(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        int freqIdx = getFreqIndex(sampleRate);
        int chanCfg = "Stereo".equals(channelConfig) ? 2 : 1;

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

    private void applyAudioEffects(ByteBuffer buffer, int bytes) {
        float scaledVolume = streamVolume * streamVolume * streamVolume;
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int numSamples = bytes / 2;

        for (int i = 0; i < numSamples; i++) {
            short pcmSample = buffer.getShort(i * 2);
            float sample = pcmSample / 32767f;

            sample = bassFilter.process(sample);
            sample = trebleFilter.process(sample);

            sample *= scaledVolume;
            
            sample = Math.max(-1.0f, Math.min(1.0f, sample));

            buffer.putShort(i * 2, (short) (sample * 32767f));
        }
    }

    private void calculateAndBroadcastAudioLevel(ByteBuffer buffer, int bytesRead) {
        long sumOfSquares = 0;
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int numSamples = bytesRead / 2;

        for (int i = 0; i < numSamples; i++) {
            short sample = buffer.getShort(i * 2);
            sumOfSquares += sample * sample;
        }

        double rms = Math.sqrt((double) sumOfSquares / numSamples);
        float normalizedRms = (float) (rms / 32767.0);

        Intent intent = new Intent(ACTION_AUDIO_LEVEL);
        intent.putExtra(EXTRA_AUDIO_LEVEL, normalizedRms);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
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
                        calculateAndBroadcastAudioLevel(inputBuffer, read);
                        applyAudioEffects(inputBuffer, read);
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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(settingsReceiver);
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
        broadcastStats("Not Connected");
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

class BiquadFilter {
    private float a1, a2, b0, b1, b2;
    private float x1, x2, y1, y2;
    private final int sampleRate;

    public BiquadFilter(int sampleRate) {
        this.sampleRate = sampleRate;
        b0 = 1.0f;
        b1 = 0.0f;
        b2 = 0.0f;
        a1 = 0.0f;
        a2 = 0.0f;
        x1 = 0.0f;
        x2 = 0.0f;
        y1 = 0.0f;
        y2 = 0.0f;
    }

    public void setLowShelf(float gainDb, float centerFreq) {
        float q = 0.707f;
        float A = (float) Math.pow(10, gainDb / 40.0);
        float w0 = (float) (2.0 * Math.PI * centerFreq / this.sampleRate);
        float cos_w0 = (float) Math.cos(w0);
        float alpha = (float) (Math.sin(w0) / (2.0f * q));

        float a0_ = (A + 1) + (A - 1) * cos_w0 + 2 * (float)Math.sqrt(A) * alpha;
        this.a1 = -2 * ((A - 1) + (A + 1) * cos_w0);
        this.a2 = (A + 1) + (A - 1) * cos_w0 - 2 * (float)Math.sqrt(A) * alpha;
        this.b0 = A * ((A + 1) - (A - 1) * cos_w0 + 2 * (float)Math.sqrt(A) * alpha);
        this.b1 = 2 * A * ((A - 1) - (A + 1) * cos_w0);
        this.b2 = A * ((A + 1) - (A - 1) * cos_w0 - 2 * (float)Math.sqrt(A) * alpha);

        this.a1 /= a0_;
        this.a2 /= a0_;
        this.b0 /= a0_;
        this.b1 /= a0_;
        this.b2 /= a0_;
    }

    public void setHighShelf(float gainDb, float centerFreq) {
        float q = 0.707f;
        float A = (float) Math.pow(10, gainDb / 40.0);
        float w0 = (float) (2.0 * Math.PI * centerFreq / this.sampleRate);
        float cos_w0 = (float) Math.cos(w0);
        float alpha = (float) (Math.sin(w0) / (2.0f * q));

        float a0_ = (A + 1) - (A - 1) * cos_w0 + 2 * (float)Math.sqrt(A) * alpha;
        this.a1 = 2 * ((A - 1) - (A + 1) * cos_w0);
        this.a2 = (A + 1) - (A - 1) * cos_w0 - 2 * (float)Math.sqrt(A) * alpha;
        this.b0 = A * ((A + 1) + (A - 1) * cos_w0 + 2 * (float)Math.sqrt(A) * alpha);
        this.b1 = -2 * A * ((A - 1) + (A + 1) * cos_w0);
        this.b2 = A * ((A + 1) + (A - 1) * cos_w0 - 2 * (float)Math.sqrt(A) * alpha);

        this.a1 /= a0_;
        this.a2 /= a0_;
        this.b0 /= a0_;
        this.b1 /= a0_;
        this.b2 /= a0_;
    }

    public float process(float in) {
        float out = b0 * in + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
        x2 = x1;
        x1 = in;
        y2 = y1;
        y1 = out;
        return out;
    }
}
