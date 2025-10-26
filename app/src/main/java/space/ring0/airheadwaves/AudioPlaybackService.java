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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

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
    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;

    // Audio effects
    private BiquadFilter bassFilter;
    private BiquadFilter trebleFilter;

    // Stream state
    private int detectedSampleRate = 44100;
    private int detectedChannels = 2;
    private String connectedClientIP;
    private boolean hasAudioFocus = false;
    private float volumeBeforeDuck = 1.0f;

    // ViewModel for UI updates
    private MainViewModel viewModel;

    // Handler for Toast notifications on main thread
    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        viewModel = MainViewModel.Companion.getInstance(getApplication());
        viewModel.updateServiceRunning(true);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());
        setupAudioFocusListener();
        createNotificationChannel();
    }

    private void setupAudioFocusListener() {
        audioFocusChangeListener = focusChange -> {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    // Regained focus - restore playback and volume
                    if (audioTrack != null && !hasAudioFocus) {
                        Log.i(TAG, "Audio focus gained - restoring playback");
                        hasAudioFocus = true;
                        volumeBeforeDuck = 0;  // Clear ducking state
                        // Resume playback if paused
                        if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PAUSED) {
                            audioTrack.play();
                        }
                    }
                    break;

                case AudioManager.AUDIOFOCUS_LOSS:
                    // Lost focus permanently - pause playback
                    Log.i(TAG, "Audio focus lost permanently - pausing playback");
                    hasAudioFocus = false;
                    volumeBeforeDuck = 0;
                    if (audioTrack != null && audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack.pause();
                    }
                    break;

                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    // Lost focus temporarily - pause
                    Log.i(TAG, "Audio focus lost transiently - pausing playback");
                    hasAudioFocus = false;
                    volumeBeforeDuck = 0;
                    if (audioTrack != null && audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack.pause();
                    }
                    break;

                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    // Lost focus but can duck - reduce volume by 50%
                    Log.i(TAG, "Audio focus lost - ducking volume to 50%");
                    hasAudioFocus = false;
                    // Mark that we're ducking (volume reduction happens in applyAudioEffects)
                    volumeBeforeDuck = viewModel.getStreamVolume().getValue();
                    break;
            }
        };
    }

    private boolean requestAudioFocus() {
        if (audioManager == null) {
            return false;
        }

        int result = audioManager.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        );

        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        if (hasAudioFocus) {
            Log.i(TAG, "Audio focus granted");
        } else {
            Log.w(TAG, "Audio focus denied");
        }
        return hasAudioFocus;
    }

    private void abandonAudioFocus() {
        if (audioManager != null && audioFocusChangeListener != null) {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
            hasAudioFocus = false;
            Log.i(TAG, "Audio focus abandoned");
        }
    }

    private void setPreferredOutputDevice() {
        if (audioManager == null || audioTrack == null || profile == null) {
            return;
        }

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            return;
        }

        android.media.AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        android.media.AudioDeviceInfo preferredDevice = null;

        switch (profile.getOutputDevice()) {
            case SPEAKER:
                // Find built-in speaker
                for (android.media.AudioDeviceInfo device : devices) {
                    if (device.getType() == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        preferredDevice = device;
                        Log.i(TAG, "Routing to built-in speaker: " + device.getProductName());
                        break;
                    }
                }
                if (preferredDevice == null) {
                    Log.w(TAG, "Built-in speaker not found, using default routing");
                }
                break;

            case HEADPHONES:
                // Find wired headset/headphones first, then bluetooth
                for (android.media.AudioDeviceInfo device : devices) {
                    int type = device.getType();
                    if (type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET) {
                        preferredDevice = device;
                        Log.i(TAG, "Routing to wired headphones: " + device.getProductName());
                        break;
                    }
                }
                // Fallback to bluetooth if no wired headphones
                if (preferredDevice == null) {
                    for (android.media.AudioDeviceInfo device : devices) {
                        int type = device.getType();
                        if (type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                            preferredDevice = device;
                            Log.i(TAG, "Routing to bluetooth headphones: " + device.getProductName());
                            break;
                        }
                    }
                }
                if (preferredDevice == null) {
                    Log.w(TAG, "No headphones found, using default routing");
                }
                break;

            case AUTO:
            default:
                // Use default Android routing
                Log.i(TAG, "Using automatic device routing");
                audioTrack.setPreferredDevice(null);
                return;
        }

        if (preferredDevice != null) {
            boolean success = audioTrack.setPreferredDevice(preferredDevice);
            if (success) {
                Log.i(TAG, "Successfully set preferred output device");
            } else {
                Log.w(TAG, "Failed to set preferred output device");
            }
        }
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
            null,
            true,   // autoReconnect
            2000,   // reconnectDelayMs
            5       // maxReconnectAttempts
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
        int reconnectAttempts = 0;
        int maxAttempts = profile != null ? profile.getMaxReconnectAttempts() : 5;
        int reconnectDelay = profile != null ? profile.getReconnectDelayMs() : 2000;
        boolean autoReconnect = profile != null ? profile.getAutoReconnect() : true;

        try {
            int port = profile != null ? profile.getListenPort() : 8888;
            serverSocket = new ServerSocket(port);
            Log.i(TAG, "TCP Server listening on port " + port);

            while (serverRunning.get() && !serverSocket.isClosed()) {
                try {
                    // Accept incoming connection (blocking)
                    clientSocket = serverSocket.accept();
                    reconnectAttempts = 0;  // Reset attempts counter on successful connection

                    // Configure socket for low latency
                    clientSocket.setTcpNoDelay(true);  // Disable Nagle's algorithm
                    clientSocket.setReceiveBufferSize(8192);  // Small buffer for low latency

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

                        // Implement retry logic if auto-reconnect is enabled
                        if (autoReconnect && reconnectAttempts < maxAttempts) {
                            reconnectAttempts++;
                            String retryMsg = "Connection error. Retry " + reconnectAttempts + "/" + maxAttempts +
                                            " in " + (reconnectDelay / 1000) + "s...";
                            Log.i(TAG, retryMsg);
                            if (viewModel != null) {
                                viewModel.updateStats(retryMsg);
                            }

                            try {
                                Thread.sleep(reconnectDelay);
                            } catch (InterruptedException ie) {
                                Log.e(TAG, "Reconnect sleep interrupted", ie);
                                break;
                            }
                        } else if (reconnectAttempts >= maxAttempts) {
                            // Max retries exceeded
                            String errorMsg = "Max reconnect attempts (" + maxAttempts + ") exceeded. Stopping.";
                            Log.e(TAG, errorMsg);
                            if (viewModel != null) {
                                viewModel.updateStats(errorMsg);
                            }
                            showToast("Failed to connect after " + maxAttempts + " attempts. Receiver stopped.");
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Playback thread interrupted", e);
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "Failed to create server socket", e);
            handleServerError(e);
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
                handlePlaybackError(e);
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
            // Initialize MediaCodec for AAC decoding with low latency
            MediaFormat format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                detectedSampleRate,
                detectedChannels
            );

            // Create AAC codec-specific data (CSD) from ADTS header info
            // This tells the decoder the AAC profile and configuration
            // Format: 5 bits profile (2 = AAC-LC), 4 bits sample rate index, 4 bits channel config
            int aacProfile = 2;  // AAC-LC
            int freqIndex = getSampleRateIndex(detectedSampleRate);
            int channelConfig = detectedChannels;

            byte[] csd = new byte[2];
            csd[0] = (byte) (((aacProfile & 0x1F) << 3) | ((freqIndex & 0x0E) >> 1));
            csd[1] = (byte) (((freqIndex & 0x01) << 7) | ((channelConfig & 0x0F) << 3));

            ByteBuffer csdBuffer = ByteBuffer.wrap(csd);
            format.setByteBuffer("csd-0", csdBuffer);

            // Request low latency decoding (Android 9+)
            // This reduces internal decoder buffering
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
            }

            Log.i(TAG, "CSD-0 created: aacProfile=" + aacProfile + ", freqIndex=" + freqIndex +
                  ", channels=" + channelConfig + ", bytes=" + bytesToHex(csd));

            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            decoder.configure(format, null, null, 0);
            decoder.start();

            // Initialize AudioTrack for playback with configurable latency
            int minBufferSize = AudioTrack.getMinBufferSize(
                detectedSampleRate,
                detectedChannels == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            );

            // Calculate buffer size based on profile settings
            // BufferSize enum: ULTRA_LOW(0ms), LOW_LATENCY(75ms), BALANCED(150ms), SMOOTH(350ms)
            int targetBufferMs = 150;  // Default to BALANCED (150ms)
            if (profile != null) {
                targetBufferMs = profile.getBufferSize().getMilliseconds();
            }

            // Calculate buffer size in bytes from milliseconds
            // Formula: (sampleRate * channels * bytesPerSample * ms) / 1000
            int bytesPerSample = 2;  // 16-bit PCM = 2 bytes
            int calculatedBufferSize = (detectedSampleRate * detectedChannels * bytesPerSample * targetBufferMs) / 1000;

            // For ULTRA_LOW and LOW_LATENCY modes, use minimum buffer regardless of calculation
            // This matches GStreamer's aggressive low-latency approach
            int bufferSize;
            if (targetBufferMs <= 75) {
                bufferSize = minBufferSize;  // Use absolute minimum for lowest latency
            } else {
                bufferSize = Math.max(minBufferSize, calculatedBufferSize);
            }

            Log.i(TAG, "AudioTrack buffer: target=" + targetBufferMs + "ms, calculated=" +
                  calculatedBufferSize + " bytes, min=" + minBufferSize + " bytes, using=" + bufferSize + " bytes");

            // Configure audio attributes based on profile output device setting
            AudioAttributes.Builder audioAttributesBuilder = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);

            // Add routing flags based on output device preference
            if (profile != null) {
                switch (profile.getOutputDevice()) {
                    case SPEAKER:
                        // Force speaker output (don't route to headphones)
                        audioAttributesBuilder.setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED);
                        Log.i(TAG, "Output device: SPEAKER (forced)");
                        break;
                    case HEADPHONES:
                        // Prefer wired headset/headphones if available
                        Log.i(TAG, "Output device: HEADPHONES (preferred)");
                        break;
                    case AUTO:
                    default:
                        // Let Android decide based on what's connected
                        Log.i(TAG, "Output device: AUTO (system default)");
                        break;
                }
            }

            AudioAttributes audioAttributes = audioAttributesBuilder.build();

            AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(detectedSampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(detectedChannels == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO)
                .build();

            // Use low latency mode for ULTRA_LOW and LOW_LATENCY settings
            int performanceMode = (targetBufferMs <= 75)
                ? AudioTrack.PERFORMANCE_MODE_LOW_LATENCY
                : AudioTrack.PERFORMANCE_MODE_NONE;

            audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(performanceMode)
                .build();

            // Set preferred output device based on profile configuration
            if (profile != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                setPreferredOutputDevice();
            }

            // Request audio focus before starting playback
            if (!requestAudioFocus()) {
                Log.w(TAG, "Failed to gain audio focus, continuing anyway");
            }

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

        Log.d(TAG, "Received " + length + " bytes, buffer now has " + (frameBufferPos + length) + " bytes");

        System.arraycopy(data, 0, frameBuffer, frameBufferPos, length);
        frameBufferPos += length;

        // Process all complete ADTS frames in buffer
        int offset = 0;
        while (offset < frameBufferPos) {
            // Find ADTS sync word
            int syncPos = ADTSParser.findSyncWord(frameBuffer, offset);
            if (syncPos == -1) {
                // No sync word found, keep remaining data for next iteration
                Log.d(TAG, "No sync word found in buffer");
                break;
            }

            Log.d(TAG, "Found sync word at position " + syncPos);

            // Parse ADTS header
            ADTSParser.ADTSFrame frame = ADTSParser.parseHeader(frameBuffer, syncPos);
            if (frame == null || !frame.isValid) {
                // Invalid frame, skip to next byte
                Log.w(TAG, "Invalid ADTS frame at position " + syncPos);
                offset = syncPos + 1;
                continue;
            }

            Log.d(TAG, "Valid ADTS frame: " + frame.sampleRate + "Hz, " + frame.channels + "ch, " + frame.frameLength + " bytes");

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
            // Get input buffer from decoder (minimal timeout for lowest latency)
            int inputBufferIndex = decoder.dequeueInputBuffer(100);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                if (inputBuffer != null) {
                    inputBuffer.clear();

                    // Strip ADTS header and send only AAC payload
                    // MediaCodec expects raw AAC frames when configured without CSD
                    int payloadOffset = offset + 7;  // ADTS header is 7 bytes
                    int payloadLength = frame.getPayloadLength();

                    inputBuffer.put(data, payloadOffset, payloadLength);

                    // Queue input buffer for decoding
                    decoder.queueInputBuffer(inputBufferIndex, 0, payloadLength, 0, 0);

                    Log.v(TAG, "Queued AAC payload: " + payloadLength + " bytes");
                }
            } else if (inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.w(TAG, "Decoder input buffer not available");
            } else {
                Log.e(TAG, "Unexpected dequeueInputBuffer result: " + inputBufferIndex);
            }

            // Get decoded output
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 0);

            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferIndex);
                if (outputBuffer != null && bufferInfo.size > 0) {
                    // Get PCM data
                    byte[] pcmData = new byte[bufferInfo.size];
                    outputBuffer.get(pcmData);

                    Log.d(TAG, "Decoded PCM: " + bufferInfo.size + " bytes");

                    // Calculate audio level for visualization (before effects)
                    calculateAndBroadcastAudioLevel(pcmData, pcmData.length);

                    // Apply audio effects (bass/treble/volume)
                    applyAudioEffects(pcmData, pcmData.length);

                    // Play PCM data through AudioTrack
                    if (audioTrack != null) {
                        int written = audioTrack.write(pcmData, 0, pcmData.length);
                        Log.d(TAG, "AudioTrack wrote " + written + " bytes (requested " + pcmData.length + ")");
                    }
                } else {
                    Log.v(TAG, "Decoder output buffer empty or null (size=" + bufferInfo.size + ")");
                }

                decoder.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 0);
            }

            // Handle format changes
            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = decoder.getOutputFormat();
                Log.i(TAG, "Decoder output format changed: " + newFormat);
            } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // No output available yet - this is normal
            } else if (outputBufferIndex < 0 && outputBufferIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.w(TAG, "Unexpected dequeueOutputBuffer result: " + outputBufferIndex);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error decoding AAC frame", e);
        }
    }

    private void calculateAndBroadcastAudioLevel(byte[] pcmData, int length) {
        long sumOfSquares = 0;
        ByteBuffer buffer = ByteBuffer.wrap(pcmData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int numSamples = length / 2;

        for (int i = 0; i < numSamples; i++) {
            short sample = buffer.getShort(i * 2);
            sumOfSquares += (long) sample * sample;
        }

        double rms = Math.sqrt((double) sumOfSquares / numSamples);
        float normalizedRms = (float) (rms / 32767.0);

        if (viewModel != null) {
            viewModel.updateAudioLevel(normalizedRms);
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
            float effectiveVolume = 1.0f;
            if (profile != null) {
                effectiveVolume = profile.getVolume();
            }

            // Duck volume if we don't have audio focus
            if (!hasAudioFocus && volumeBeforeDuck > 0) {
                effectiveVolume *= 0.5f;  // Reduce to 50% when ducking
            }

            floatSample *= effectiveVolume;

            // Clamp to valid range
            floatSample = Math.max(-1.0f, Math.min(1.0f, floatSample));

            // Convert back to 16-bit
            short outputSample = (short) (floatSample * 32767.0f);

            // Write back to buffer
            buffer.putShort(i * 2, outputSample);
        }
    }

    private void cleanupAudioComponents() {
        // Abandon audio focus before stopping playback
        abandonAudioFocus();

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

    private int getSampleRateIndex(int sampleRate) {
        switch (sampleRate) {
            case 96000: return 0;
            case 88200: return 1;
            case 64000: return 2;
            case 48000: return 3;
            case 44100: return 4;
            case 32000: return 5;
            case 24000: return 6;
            case 22050: return 7;
            case 16000: return 8;
            case 12000: return 9;
            case 11025: return 10;
            case 8000: return 11;
            case 7350: return 12;
            default: return 4;  // Default to 44100Hz
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    /**
     * Show a Toast notification on the main thread
     */
    private void showToast(String message) {
        if (mainHandler != null) {
            mainHandler.post(() -> {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            });
        }
    }

    /**
     * Handle playback errors during streaming
     */
    private void handlePlaybackError(IOException e) {
        String errorMessage;
        String toastMessage;
        String exceptionMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        if (exceptionMessage.contains("connection reset") ||
            exceptionMessage.contains("broken pipe")) {
            // Transmitter disconnected
            errorMessage = "Transmitter disconnected";
            toastMessage = "Transmitter disconnected. Waiting for reconnection...";
            Log.i(TAG, "Transmitter disconnected, waiting for new connection");
        } else if (exceptionMessage.contains("timeout") ||
                   exceptionMessage.contains("timed out")) {
            // Stream timeout
            errorMessage = "Stream timeout";
            toastMessage = "Stream timed out. Try:\n" +
                          "• Check network stability\n" +
                          "• Ensure transmitter is still running";
            Log.e(TAG, "Stream timeout");
        } else {
            // Generic playback error
            errorMessage = "Playback error: " + (e.getMessage() != null ? e.getMessage() : "Unknown");
            toastMessage = "Playback error occurred. Waiting for reconnection...";
            Log.e(TAG, "Playback error: " + e.getMessage());
        }

        // Update UI status
        if (viewModel != null) {
            viewModel.updateStats(errorMessage);
        }

        // Show Toast notification for errors (not for normal disconnects)
        if (!exceptionMessage.contains("connection reset")) {
            showToast(toastMessage);
        }
    }

    /**
     * Handle server errors with specific detection and actionable messages
     */
    private void handleServerError(IOException e) {
        String errorMessage;
        String toastMessage;
        String exceptionMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        // Detect specific error types and provide actionable guidance
        if (exceptionMessage.contains("address already in use") ||
            exceptionMessage.contains("bind failed") ||
            e instanceof java.net.BindException) {
            // Port already in use
            int port = profile != null ? profile.getListenPort() : 8888;
            errorMessage = "Port " + port + " is already in use";
            toastMessage = "Port " + port + " is already in use. Try:\n" +
                          "• Stop other receiver apps\n" +
                          "• Change port in profile settings\n" +
                          "• Restart device if issue persists";
            Log.e(TAG, "Port already in use: " + port);
        } else if (exceptionMessage.contains("network is unreachable") ||
                   exceptionMessage.contains("no route to host")) {
            // Network connectivity issues
            errorMessage = "Network unreachable";
            toastMessage = "Network connection error. Try:\n" +
                          "• Check WiFi is enabled and connected\n" +
                          "• Verify you're on the same network as transmitter\n" +
                          "• Disable VPN if active";
            Log.e(TAG, "Network unreachable");
        } else if (exceptionMessage.contains("connection refused")) {
            // Connection refused (shouldn't happen on server side, but handle it)
            errorMessage = "Connection refused";
            toastMessage = "Connection was refused. Try:\n" +
                          "• Check firewall settings\n" +
                          "• Verify port is not blocked\n" +
                          "• Restart the app";
            Log.e(TAG, "Connection refused");
        } else if (exceptionMessage.contains("permission denied")) {
            // Permission issues
            errorMessage = "Permission denied";
            toastMessage = "Permission denied. Try:\n" +
                          "• Grant network permissions in Settings\n" +
                          "• Reinstall the app if needed";
            Log.e(TAG, "Permission denied");
        } else if (exceptionMessage.contains("timeout") ||
                   exceptionMessage.contains("timed out")) {
            // Timeout errors
            errorMessage = "Connection timeout";
            toastMessage = "Connection timed out. Try:\n" +
                          "• Check network stability\n" +
                          "• Move closer to WiFi router\n" +
                          "• Reduce network congestion";
            Log.e(TAG, "Connection timeout");
        } else {
            // Generic error
            errorMessage = "Error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
            toastMessage = "Receiver error: " + (e.getMessage() != null ? e.getMessage() : "Unknown") + "\n\n" +
                          "Try restarting the receiver or checking logs for details.";
            Log.e(TAG, "Generic server error: " + e.getMessage());
        }

        // Update UI status
        if (viewModel != null) {
            viewModel.updateStats(errorMessage);
        }

        // Show Toast notification
        showToast(toastMessage);
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
