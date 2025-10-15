# AirheadWaves - Android Audio Streamer

AirheadWaves is a versatile Android application designed for real-time audio streaming from your device to a server on your local network. It captures internal audio playback, encodes it, and transmits it over TCP, making it a perfect solution for creating a whole-house audio system, streaming to a Raspberry Pi, or any other DIY audio project. Turn any Linux device into a WiFi speaker for your Android phone.

## Features

*   **Real-Time Audio Streaming:** Captures and streams internal device audio with low latency
*   **Advanced Audio Controls:** Fine-tune your audio with software-based volume, bass, and treble controls that apply in real-time
*   **Multi-Profile System:** Create, save, and manage multiple server profiles, each with its own complete set of configurations
*   **Dynamic Configuration:** Easily switch between common audio settings:
    *   **Bitrate:** 96kbps, 128kbps, 192kbps, 256kbps, 320kbps
    *   **Sample Rate:** 22050Hz, 44100Hz, 48000Hz
    *   **Channels:** Mono & Stereo (Default)
*   **Audio-Reactive Visualization:** The app background subtly pulses with the volume of the audio, providing fun and engaging visual feedback
*   **Theming:** Full-featured theme system with persistent Light, Dark, and System Default options
*   **Modern Android UI:** Built with Jetpack Compose, featuring a clean, animated, and user-friendly interface
*   **Robust State Management:** The app correctly handles its state across window resizing and backgrounding

## Quick Start

### Android App Setup

1. Install AirheadWaves on your Android device (Android 10 / API 29 or higher)
2. Grant the required permissions (Media Projection for audio capture)
3. Configure a server profile:
    - Enter your server's IP address
    - Set port (default: 8888)
    - Choose audio settings (48000Hz sample rate recommended)
4. Start streaming!

### Server Setup

AirheadWaves streams AAC audio with ADTS headers over TCP. You'll need to set up a receiving server to play the audio.

**📖 For detailed server setup instructions, see [airheadwaves-server-setup.md](airheadwaves-server-setup.md)**

The server setup guide includes:
- Complete GStreamer installation and configuration
- ALSA dmix setup for multi-application audio sharing
- Systemd service for automatic startup
- WiFi optimization for crackle-free playback
- Troubleshooting and advanced configuration
- Tested on Raspberry Pi Zero 2 W with BossDAC, adaptable to any Linux system

**Quick Server Test** (not for production use):
```bash
# Install GStreamer first (see full guide for details)
gst-launch-1.0 tcpserversrc host=0.0.0.0 port=8888 ! \
  aacparse ! avdec_aac ! audioconvert ! autoaudiosink
```

## Architecture

The app is built using modern Android development practices and follows a simple but effective architecture:

*   **UI Layer (Jetpack Compose):** The entire user interface is built with Jetpack Compose, using a single-activity architecture with multiple composable screens managed by a `NavController`
*   **Foreground Service (`AudioCaptureService`):** A long-running foreground service handles all the audio processing and network streaming. This ensures the stream continues uninterrupted even when the app is in the background
*   **Audio Capture (`MediaProjection`):** The `MediaProjection` API is used to capture the device's internal audio playback. This allows the app to stream audio from any source, including apps like Spotify, YouTube, or podcast players
*   **Audio Processing:**
    *   **Encoding:** Raw PCM audio is encoded into the **AAC** format using the `MediaCodec` API
    *   **DSP Effects:** Software-based **Bass and Treble** controls are implemented using a custom `BiquadFilter` class to directly manipulate the raw audio samples before encoding
*   **Networking:** The encoded AAC audio, wrapped in **ADTS (Audio Data Transport Stream) headers**, is streamed over a standard TCP socket
*   **Settings Persistence:** All user settings, including server profiles, volume levels, and theme choices, are saved to `SharedPreferences` using `kotlinx.serialization` for profile management

## Building from Source

1.  Clone this repository
2.  Open the project in the latest version of Android Studio
3.  The project uses Gradle and should sync automatically
4.  Build and run on your physical Android device (Android 10 / API 29 or higher)

## Use Cases

- **Whole-House Audio:** Stream music throughout your home to multiple Raspberry Pi receivers
- **Legacy Speaker Upgrade:** Add wireless streaming to non-smart speakers
- **Multi-Room Control:** Use the multi-profile system to quickly switch between rooms
- **Low-Latency Streaming:** Perfect for streaming podcasts, audiobooks, or music with minimal delay
- **DIY Audio Projects:** Build custom audio systems with complete control over the audio pipeline

## Requirements

**Android Device:**
- Android 10 (API 29) or higher
- MediaProjection capability (standard on most devices)

**Server:**
- Any Linux-based system (Raspberry Pi, desktop, server)
- Network connectivity to Android device
- Audio output device

See [airheadwaves-server-setup.md](airheadwaves-server-setup.md) for complete server requirements and setup.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

MIT License

## Acknowledgments

The Android application was generated with the assistance of **Google's Gemini**.

## Support

For server setup issues, troubleshooting, and advanced configuration, please refer to [airheadwaves-server-setup.md](airheadwaves-server-setup.md).

For Android app issues, please open an issue on this repository.