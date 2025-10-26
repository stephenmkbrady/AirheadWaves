# AirheadWaves - Android Audio Streamer

AirheadWaves is a versatile Android application designed for **bidirectional real-time audio streaming** on your local network. It can both transmit and receive audio, making it perfect for creating a whole-house audio system, wireless speaker networks, or any DIY audio project. Stream from your Android device to receivers, or turn your Android device into a WiFi speaker that receives audio from other devices.

## Features

### Transmit Mode (Send Audio)
*   **Real-Time Audio Capture:** Captures internal device audio using MediaProjection
*   **Multi-Receiver Broadcast:** Send audio to multiple receivers simultaneously
*   **Advanced Audio Controls:** Software-based bass and treble controls applied before transmission
*   **Dynamic Configuration:**
    *   **Bitrate:** 96kbps, 128kbps, 192kbps, 256kbps, 320kbps
    *   **Sample Rate:** 22050Hz, 44100Hz, 48000Hz
    *   **Channels:** Mono & Stereo (Default)
*   **Multi-Profile System:** Save multiple transmit profiles with different receiver configurations

### Receive Mode (WiFi Speaker)
*   **TCP Server:** Opens configurable port to accept incoming audio streams
*   **Multiple Transmitter Support:** Accept audio from multiple sources with mixing (Phase 2)
*   **Access Control:** IP-based whitelist for allowed transmitters
*   **Independent Audio Processing:** Apply bass, treble, and volume adjustments to received audio
*   **Auto-Detection:** Automatically detects stream parameters (sample rate, channels) from ADTS headers
*   **Configurable Buffering:** Choose latency vs. stability (Low Latency, Balanced, Smooth)
*   **Output Routing:** Auto-select or manually choose speaker/headphones

### Common Features
*   **Audio-Reactive Visualization:** Background pulses with audio volume in both modes
*   **Theming:** Light, Dark, and System Default theme support
*   **Modern Android UI:** Built with Jetpack Compose
*   **Profile Management:** Separate transmit and receive profile configurations
*   **Robust State Management:** Handles backgrounding and window resizing correctly

## Quick Start

### Option 1: Transmit Mode (Android → Receiver)

**Android Device (Transmitter):**
1. Install AirheadWaves on your Android device (Android 10 / API 29 or higher)
2. Grant Media Projection permission for audio capture
3. Select "Transmit Mode"
4. Create a transmit profile:
    - Enter receiver IP address(es) and port(s)
    - Choose audio settings (48000Hz sample rate recommended)
    - Configure bass/treble if desired
5. Start streaming!

**Receiver Options:**
- **Another Android device:** Use AirheadWaves in Receive Mode (see below)
- **Linux server:** See [airheadwaves-server-setup.md](airheadwaves-server-setup.md) for GStreamer setup
- **Quick test:** `gst-launch-1.0 tcpserversrc host=0.0.0.0 port=8888 ! aacparse ! avdec_aac ! audioconvert ! autoaudiosink`

### Option 2: Receive Mode (Android as WiFi Speaker)

**Android Device (Receiver):**
1. Install AirheadWaves on your Android device
2. Select "Receive Mode"
3. Create a receive profile:
    - Set listen port (default: 8888)
    - Configure access control (allow all or whitelist specific IPs)
    - Choose buffer size (Balanced recommended)
    - Configure bass/treble/volume if desired
4. Start receiving - the device will listen on the configured port
5. Note your device's IP address (shown in status)

**Transmitter Setup:**
- **Another Android device:** Use AirheadWaves in Transmit Mode, connect to receiver's IP:Port
- **Linux/Desktop:** Stream AAC with ADTS headers to the receiver's IP and port

### Android-to-Android Streaming

The easiest setup is Android-to-Android:
1. **Device A (Receiver):** Start Receive Mode on port 8888 (note its IP, e.g., 192.168.1.100)
2. **Device B (Transmitter):** Start Transmit Mode, connect to 192.168.1.100:8888
3. Audio from Device B now plays through Device A's speakers!

### Server Setup (External Receivers)

For non-Android receivers (Raspberry Pi, Linux servers, etc.), see [airheadwaves-server-setup.md](airheadwaves-server-setup.md) which includes:
- Complete GStreamer installation and configuration
- ALSA dmix setup for multi-application audio sharing
- Systemd service for automatic startup
- WiFi optimization for crackle-free playback
- Tested on Raspberry Pi Zero 2 W with BossDAC

## Architecture

The app uses modern Android development practices with bidirectional audio streaming:

### Core Components

*   **UI Layer (Jetpack Compose):** Single-activity architecture with multiple composable screens
*   **Mode Selection:** Toggle between Transmit and Receive modes
*   **Profile Management:** Separate profile systems for transmit and receive configurations

### Transmit Mode Architecture

*   **Foreground Service (`AudioCaptureService`):** Long-running service for audio capture and transmission
*   **Audio Capture (`MediaProjection`):** Captures internal device audio from any source (Spotify, YouTube, etc.)
*   **Audio Processing:**
    *   **DSP Effects:** Bass/treble controls using `BiquadFilter` before encoding
    *   **Encoding:** PCM → AAC using `MediaCodec`
    *   **ADTS Framing:** AAC wrapped in ADTS headers
*   **Networking:** TCP client connections to multiple receivers simultaneously

### Receive Mode Architecture

*   **Foreground Service (`AudioPlaybackService`):** Long-running service for receiving and playback
*   **TCP Server:** Accepts incoming connections on configured port
*   **Audio Processing:**
    *   **Decoding:** AAC (ADTS) → PCM using `MediaCodec`
    *   **Auto-Detection:** Extract sample rate and channels from ADTS headers
    *   **DSP Effects:** Bass/treble/volume controls using `BiquadFilter` after decoding
*   **Playback (`AudioTrack`):** Routes to speakers or headphones
*   **Access Control:** IP-based whitelist for allowed transmitters
*   **Buffering:** Configurable buffer sizes (Low Latency, Balanced, Smooth)

### Common Architecture

*   **Settings Persistence:** SharedPreferences with `kotlinx.serialization` for profile management
*   **Audio Visualization:** Real-time amplitude analysis for background pulsing effect
*   **Foreground Services:** Persistent notifications prevent Android from killing streams

## Building from Source

1.  Clone this repository
2.  Open the project in the latest version of Android Studio
3.  The project uses Gradle and should sync automatically
4.  Build and run on your physical Android device (Android 10 / API 29 or higher)

## Use Cases

### Transmit Mode Use Cases
- **Whole-House Audio:** Broadcast music from your phone to multiple receivers throughout your home
- **Multi-Room Streaming:** Send to several Raspberry Pi or Android devices simultaneously
- **Legacy Speaker Upgrade:** Turn any device with AirheadWaves into a wireless speaker for your phone
- **Party Mode:** Stream music from your phone to multiple Android tablets connected to speakers

### Receive Mode Use Cases
- **WiFi Speaker System:** Turn old Android tablets/phones into WiFi speakers
- **Bedroom Audio:** Place Android device with speakers in bedroom, stream from anywhere in house
- **Kitchen Radio:** Dedicated Android receiver device for kitchen audio
- **Portable Speaker:** Use Android tablet as portable receiver with independent volume/EQ

### Combined Use Cases
- **Multi-Source System:** Have multiple transmitters (phones) that can stream to the same receiver
- **Flexible Room Configuration:** Quickly switch which rooms receive audio using profiles
- **Android-Only Network:** No need for Raspberry Pi or Linux servers - pure Android ecosystem
- **Mixed Network:** Combine Android receivers with traditional Linux/Pi receivers

## Requirements

**Android Device:**
- Android 10 (API 29) or higher
- **For Transmit Mode:** MediaProjection capability (standard on most devices)
- **For Receive Mode:** No special permissions required (uses standard networking)

**Network:**
- LAN connectivity between devices (WiFi or Ethernet)
- Firewall configured to allow TCP connections on configured ports (default: 8888)

**External Receivers (Optional):**
- Any Linux-based system (Raspberry Pi, desktop, server)
- GStreamer or compatible AAC decoder
- See [airheadwaves-server-setup.md](airheadwaves-server-setup.md) for setup

**Future (v3.0):**
- Internet streaming with NAT traversal (STUN/TURN)
- See [requirements-v3.0.md](requirements-backlog.md) for planned features

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

MIT License

## Acknowledgments

The Android application was generated with the assistance of **Google's Gemini**.

## Support

For server setup issues, troubleshooting, and advanced configuration, please refer to [airheadwaves-server-setup.md](airheadwaves-server-setup.md).

For Android app issues, please open an issue on this repository.