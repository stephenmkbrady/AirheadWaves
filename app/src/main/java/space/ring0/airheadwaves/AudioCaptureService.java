package space.ring0.airheadwaves;

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
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AudioCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE";
    public static final String EXTRA_DATA = "EXTRA_DATA";
    public static final String EXTRA_VOLUME = "VOLUME";

    private static final String TAG = "AudioCaptureService";
    private static final String CHANNEL_ID = "AudioCaptureServiceChannel";

    public static boolean isRunning = false;

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private AudioRecord audioRecord;
    private MediaCodec mediaCodec;
    private Thread captureThread;
    private space.ring0.airheadwaves.models.TransmitProfile profile;
    private int bitrate;
    private int sampleRate;
    private String channelConfig;

    private BiquadFilter bassFilter;
    private BiquadFilter trebleFilter;

    private MainViewModel viewModel;
    private float lastBass = 0f;
    private float lastTreble = 0f;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        viewModel = MainViewModel.Companion.getInstance(getApplication());
        viewModel.updateServiceRunning(true);
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        createNotificationChannel();
    }

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AirheadWaves")
                .setContentText("Streaming audio to your devices.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
        Intent data = intent.getParcelableExtra(EXTRA_DATA);

        // Deserialize TransmitProfile from JSON
        String profileJson = intent.getStringExtra("PROFILE_JSON");
        if (profileJson != null) {
            try {
                profile = ProfileSerializer.deserializeTransmitProfile(profileJson);
                bitrate = profile.getBitrate();
                sampleRate = profile.getSampleRate();
                channelConfig = profile.getChannelConfig();
            } catch (Exception e) {
                Log.e(TAG, "Failed to deserialize profile", e);
                stopSelf();
                return START_NOT_STICKY;
            }
        } else {
            Log.e(TAG, "No profile provided");
            stopSelf();
            return START_NOT_STICKY;
        }

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

        // Initialize filters with profile settings or 0dB (no effect)
        float bass = profile != null ? profile.getBass() : 0f;
        float treble = profile != null ? profile.getTreble() : 0f;
        bassFilter.setLowShelf(bass, 200f);
        trebleFilter.setHighShelf(treble, 3000f);
        lastBass = bass;
        lastTreble = treble;
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
        // Read current volume from ViewModel for real-time updates
        float currentVolume = viewModel.getStreamVolume().getValue();
        float scaledVolume = currentVolume * currentVolume * currentVolume;

        // Read current bass/treble from TransmitProfile for real-time updates
        if (profile != null) {
            float currentBass = profile.getBass();
            float currentTreble = profile.getTreble();

            // Update filters if bass or treble changed
            if (currentBass != lastBass || currentTreble != lastTreble) {
                bassFilter.setLowShelf(currentBass, 200f);
                trebleFilter.setHighShelf(currentTreble, 3000f);
                lastBass = currentBass;
                lastTreble = currentTreble;
            }
        }

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

        viewModel.updateAudioLevel(normalizedRms);
    }

    private void encodeAndStreamAudio() {
        // Create sockets for all destinations
        java.util.List<space.ring0.airheadwaves.models.Destination> destinations = profile.getDestinations();
        java.util.List<Socket> sockets = new java.util.ArrayList<>();
        java.util.List<String> connected = new java.util.ArrayList<>();

        // Connect to all destinations
        for (space.ring0.airheadwaves.models.Destination dest : destinations) {
            try {
                Socket socket = new Socket(dest.getIpAddress(), dest.getPort());
                sockets.add(socket);
                connected.add(dest.getIpAddress() + ":" + dest.getPort());
                Log.i(TAG, "Connected to " + dest.getIpAddress() + ":" + dest.getPort());
            } catch (IOException e) {
                Log.e(TAG, "Failed to connect to " + dest.getIpAddress() + ":" + dest.getPort(), e);
            }
        }

        if (sockets.isEmpty()) {
            viewModel.updateStats("Error: No destinations connected");
            return;
        }

        viewModel.updateStats("Connected to " + sockets.size() + " destination(s)");

        try {
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

                    // Send to all connected destinations
                    for (int i = sockets.size() - 1; i >= 0; i--) {
                        Socket socket = sockets.get(i);
                        try {
                            socket.getOutputStream().write(outData);
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to send to " + connected.get(i) + ", disconnecting", e);
                            try {
                                socket.close();
                            } catch (IOException ex) {
                                // Ignore
                            }
                            sockets.remove(i);
                            connected.remove(i);
                        }
                    }

                    // Check if all destinations disconnected
                    if (sockets.isEmpty()) {
                        Log.e(TAG, "All destinations disconnected");
                        viewModel.updateStats("Error: All destinations disconnected");
                        return;
                    }

                    bytesSent += outPacketSizeWithHeader;

                    if (System.currentTimeMillis() - lastStatTime > 1000) {
                        long bps = (bytesSent * 8) / ((System.currentTimeMillis() - lastStatTime) / 1000);
                        viewModel.updateStats("Connected to " + sockets.size() + " destination(s)\n" + bps / 1000 + " kbps");
                        lastStatTime = System.currentTimeMillis();
                        bytesSent = 0;
                    }

                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while streaming audio", e);
            viewModel.updateStats("Error: " + e.getMessage());
        } finally {
            // Close all sockets
            for (Socket socket : sockets) {
                try {
                    socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing socket", e);
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;

        // Interrupt and wait for capture thread to finish
        if (captureThread != null) {
            captureThread.interrupt();
            try {
                captureThread.join(1000); // Wait up to 1 second for thread to finish
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for capture thread", e);
            }
        }

        // Now safely release resources in order
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error stopping audioRecord", e);
            }
            audioRecord = null;
        }

        if (mediaCodec != null) {
            try {
                mediaCodec.stop();
                mediaCodec.release();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error stopping mediaCodec", e);
            }
            mediaCodec = null;
        }

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        viewModel.updateStats("Not Connected");
        viewModel.updateAudioLevel(0.0f);
        viewModel.updateServiceRunning(false);
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
