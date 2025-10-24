package space.ring0.airheadwaves;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import space.ring0.airheadwaves.models.ReceiveProfile;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AudioPlaybackService - Receive Mode Service
 *
 * Implements TCP server for receiving AAC audio streams.
 * Decodes AAC to PCM and plays through AudioTrack.
 *
 * Phase 1: Single connection support
 * Phase 2: Multiple connections with mixing
 */
public class AudioPlaybackService extends Service {
    private static final String TAG = "AudioPlaybackService";
    private static final String CHANNEL_ID = "AudioPlaybackChannel";
    private static final int NOTIFICATION_ID = 2;

    public static boolean isRunning = false;

    // Service state
    private final AtomicBoolean serverRunning = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private Thread serverThread;
    private Thread playbackThread;

    // Audio components
    private MediaCodec decoder;
    private AudioTrack audioTrack;
    private ReceiveProfile profile;

    // Audio effects
    private BiquadFilter bassFilter;
    private BiquadFilter trebleFilter;

    // Stream state
    private int detectedSampleRate = 44100;
    private int detectedChannels = 2;
    private String connectedClientIP;

    // ViewModel for UI updates
    private MainViewModel viewModel;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        viewModel = MainViewModel.Companion.getInstance(getApplication());
        viewModel.updateServiceRunning(true);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if ("START".equals(action)) {
            // Get profile from intent (serialized as JSON)
            String profileJson = intent.getStringExtra("PROFILE_JSON");
            if (profileJson != null) {
                try {
                    ReceiveProfile profile = ProfileSerializer.deserializeReceiveProfile(profileJson);
                    startReceiving(profile);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse profile", e);
                    stopSelf();
                }
            } else {
                // No profile provided, use default
                startReceiving(createDefaultProfile());
            }
        } else if ("STOP".equals(action)) {
            stopReceiving();
        }

        return START_STICKY;
    }

    private ReceiveProfile createDefaultProfile() {
        // Create a default profile as fallback
        return new ReceiveProfile(
            java.util.UUID.randomUUID().toString(),
            "Default Receiver",
            8888,
            space.ring0.airheadwaves.models.OutputDevice.AUTO,
            space.ring0.airheadwaves.models.BufferSize.BALANCED,
            0f,
            0f,
            1.0f,
            true,
            java.util.Collections.emptyList(),
            true,
            null,
            null
        );
    }

    private void startReceiving(ReceiveProfile profile) {
        if (serverRunning.getAndSet(true)) {
            return;  // Already running
        }

        this.profile = profile;
        startForeground(NOTIFICATION_ID, createNotification("Starting receiver..."));

        // Start TCP server thread
        serverThread = new Thread(this::runTCPServer);
        serverThread.start();

        String listeningMessage = "Listening on port " + (profile != null ? profile.getListenPort() : 8888);
        updateNotification(listeningMessage);
        if (viewModel != null) {
            viewModel.updateStats(listeningMessage);
        }
    }

    private void runTCPServer() {
        try {
            int port = profile != null ? profile.getListenPort() : 8888;
            serverSocket = new ServerSocket(port);
            Log.i(TAG, "TCP Server listening on port " + port);

            while (serverRunning.get() && !serverSocket.isClosed()) {
                try {
                    // Accept incoming connection (blocking)
                    clientSocket = serverSocket.accept();
                    connectedClientIP = clientSocket.getInetAddress().getHostAddress();

                    Log.i(TAG, "Client connected: " + connectedClientIP);
                    String connectedMessage = "Connected: streaming from " + connectedClientIP;
                    updateNotification(connectedMessage);
                    if (viewModel != null) {
                        viewModel.updateStats(connectedMessage);
                    }

                    // Check access control
                    if (!isClientAllowed(connectedClientIP)) {
                        Log.w(TAG, "Client not allowed: " + connectedClientIP);
                        clientSocket.close();
                        continue;
                    }

                    // Start playback thread
                    playbackThread = new Thread(this::runPlayback);
                    playbackThread.start();

                    // Wait for playback to finish
                    playbackThread.join();

                } catch (IOException e) {
                    if (serverRunning.get()) {
                        Log.e(TAG, "Error accepting connection", e);
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Playback thread interrupted", e);
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "Failed to create server socket", e);
            String errorMessage = "Error: " + e.getMessage();
            if (viewModel != null) {
                viewModel.updateStats(errorMessage);
            }
        } finally {
            cleanup();
        }
    }

    private boolean isClientAllowed(String clientIP) {
        if (profile == null) {
            return true;
        }

        // Check if unknown transmitters are allowed
        if (profile.getAllowUnknownTransmitters()) {
            return true;
        }

        // Check whitelist
        for (String allowedIP : profile.getAllowedTransmitterIPs()) {
            if (allowedIP.equals(clientIP)) {
                return true;
            }
        }

        return false;
    }

    private void runPlayback() {
        try {
            InputStream inputStream = clientSocket.getInputStream();

            // Initialize decoder and audio track
            initializeAudioComponents();

            // Read and decode AAC frames
            byte[] buffer = new byte[8192];
            while (serverRunning.get() && !clientSocket.isClosed()) {
                int bytesRead = inputStream.read(buffer);
                if (bytesRead == -1) {
                    break;  // End of stream
                }

                // TODO: Parse ADTS headers and decode AAC
                // This is a placeholder for Phase 1
                processAudioData(buffer, bytesRead);
            }

        } catch (IOException e) {
            if (serverRunning.get()) {
                Log.e(TAG, "Playback error", e);
            }
        } finally {
            cleanupAudioComponents();
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing client socket", e);
            }

            String listeningMessage = "Listening on port " + (profile != null ? profile.getListenPort() : 8888);
            updateNotification(listeningMessage);
            if (viewModel != null) {
                viewModel.updateStats(listeningMessage);
            }
        }
    }

    private void initializeAudioComponents() {
        try {
            // Initialize MediaCodec for AAC decoding
            MediaFormat format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                detectedSampleRate,
                detectedChannels
            );

            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            decoder.configure(format, null, null, 0);
            decoder.start();

            // Initialize AudioTrack for playback
            int bufferSize = AudioTrack.getMinBufferSize(
                detectedSampleRate,
                detectedChannels == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            );

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

            AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(detectedSampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(detectedChannels == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO)
                .build();

            audioTrack = new AudioTrack(
                audioAttributes,
                audioFormat,
                bufferSize * 2,  // Double buffer for smoothness
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            );

            audioTrack.play();

            // Initialize audio effects filters
            initializeFilters();

            Log.i(TAG, "Audio components initialized: " + detectedSampleRate + "Hz, " + detectedChannels + " channels");

        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize audio components", e);
        }
    }

    private void initializeFilters() {
        bassFilter = new BiquadFilter(detectedSampleRate);
        trebleFilter = new BiquadFilter(detectedSampleRate);

        // Apply current profile settings
        if (profile != null) {
            bassFilter.setLowShelf(profile.getBass(), 200.0f);  // Bass at 200Hz
            trebleFilter.setHighShelf(profile.getTreble(), 3000.0f);  // Treble at 3000Hz
        } else {
            // Default: no effect (0dB)
            bassFilter.setLowShelf(0.0f, 200.0f);
            trebleFilter.setHighShelf(0.0f, 3000.0f);
        }
    }

    // Buffer for incomplete ADTS frames
    private byte[] frameBuffer = new byte[8192 * 2];
    private int frameBufferPos = 0;

    private void processAudioData(byte[] data, int length) {
        // Copy received data to frame buffer
        if (frameBufferPos + length > frameBuffer.length) {
            // Buffer overflow, reset
            Log.w(TAG, "Frame buffer overflow, resetting");
            frameBufferPos = 0;
        }

        System.arraycopy(data, 0, frameBuffer, frameBufferPos, length);
        frameBufferPos += length;

        // Process all complete ADTS frames in buffer
        int offset = 0;
        while (offset < frameBufferPos) {
            // Find ADTS sync word
            int syncPos = ADTSParser.findSyncWord(frameBuffer, offset);
            if (syncPos == -1) {
                // No sync word found, keep remaining data for next iteration
                break;
            }

            // Parse ADTS header
            ADTSParser.ADTSFrame frame = ADTSParser.parseHeader(frameBuffer, syncPos);
            if (frame == null || !frame.isValid) {
                // Invalid frame, skip to next byte
                offset = syncPos + 1;
                continue;
            }

            // Check if we have complete frame
            if (!ADTSParser.hasCompleteFrame(frameBuffer, syncPos)) {
                // Incomplete frame, wait for more data
                break;
            }

            // Auto-detect stream parameters on first frame
            if (detectedSampleRate != frame.sampleRate || detectedChannels != frame.channels) {
                Log.i(TAG, "Stream parameters changed: " + frame.sampleRate + "Hz, " + frame.channels + "ch");
                detectedSampleRate = frame.sampleRate;
                detectedChannels = frame.channels;

                // Reinitialize audio components with detected parameters
                cleanupAudioComponents();
                initializeAudioComponents();
            }

            // Decode AAC frame
            decodeAACFrame(frameBuffer, syncPos, frame);

            // Move to next frame
            offset = syncPos + frame.frameLength;
        }

        // Shift remaining data to beginning of buffer
        if (offset > 0 && offset < frameBufferPos) {
            System.arraycopy(frameBuffer, offset, frameBuffer, 0, frameBufferPos - offset);
            frameBufferPos -= offset;
        } else if (offset >= frameBufferPos) {
            frameBufferPos = 0;
        }
    }

    private void decodeAACFrame(byte[] data, int offset, ADTSParser.ADTSFrame frame) {
        if (decoder == null) {
            return;
        }

        try {
            // Get input buffer from decoder
            int inputBufferIndex = decoder.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                if (inputBuffer != null) {
                    inputBuffer.clear();

                    // Copy AAC payload (without ADTS header) to input buffer
                    int payloadOffset = offset + 7;  // ADTS header is 7 bytes
                    int payloadLength = frame.getPayloadLength();

                    inputBuffer.put(data, payloadOffset, payloadLength);

                    // Queue input buffer for decoding
                    decoder.queueInputBuffer(inputBufferIndex, 0, payloadLength, 0, 0);
                }
            }

            // Get decoded output
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000);

            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferIndex);
                if (outputBuffer != null && bufferInfo.size > 0) {
                    // Get PCM data
                    byte[] pcmData = new byte[bufferInfo.size];
                    outputBuffer.get(pcmData);

                    // Apply audio effects (bass/treble/volume)
                    applyAudioEffects(pcmData, pcmData.length);

                    // Play PCM data through AudioTrack
                    if (audioTrack != null) {
                        audioTrack.write(pcmData, 0, pcmData.length);
                    }
                }

                decoder.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 0);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error decoding AAC frame", e);
        }
    }

    private void applyAudioEffects(byte[] pcmData, int length) {
        if (bassFilter == null || trebleFilter == null) {
            return;
        }

        // Convert byte array to samples (16-bit PCM)
        ByteBuffer buffer = ByteBuffer.wrap(pcmData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        int numSamples = length / 2;  // 16-bit = 2 bytes per sample

        for (int i = 0; i < numSamples; i++) {
            // Read 16-bit sample
            short sample = buffer.getShort(i * 2);

            // Convert to float (-1.0 to 1.0)
            float floatSample = sample / 32768.0f;

            // Apply bass filter
            floatSample = bassFilter.process(floatSample);

            // Apply treble filter
            floatSample = trebleFilter.process(floatSample);

            // Apply volume
            if (profile != null) {
                floatSample *= profile.getVolume();
            }

            // Clamp to valid range
            floatSample = Math.max(-1.0f, Math.min(1.0f, floatSample));

            // Convert back to 16-bit
            short outputSample = (short) (floatSample * 32767.0f);

            // Write back to buffer
            buffer.putShort(i * 2, outputSample);
        }
    }

    private void cleanupAudioComponents() {
        if (decoder != null) {
            try {
                decoder.stop();
                decoder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping decoder", e);
            }
            decoder = null;
        }

        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping audio track", e);
            }
            audioTrack = null;
        }
    }

    private void stopReceiving() {
        serverRunning.set(false);

        try {
            if (clientSocket != null) {
                clientSocket.close();
            }
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing sockets", e);
        }

        cleanup();
        stopForeground(true);
        stopSelf();
    }

    private void cleanup() {
        cleanupAudioComponents();

        if (serverThread != null) {
            serverThread.interrupt();
        }
        if (playbackThread != null) {
            playbackThread.interrupt();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Audio Playback Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Receives and plays audio streams");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AirheadWaves Receiver")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)  // TODO: Use app icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String contentText) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(contentText));
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopReceiving();
        isRunning = false;

        // Update ViewModel to reflect service stopped
        if (viewModel != null) {
            viewModel.updateStats("Not Connected");
            viewModel.updateAudioLevel(0.0f);
            viewModel.updateServiceRunning(false);
        }

        super.onDestroy();
    }
}
