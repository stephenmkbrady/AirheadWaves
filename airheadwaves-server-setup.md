# AirheadWaves - Server Setup Guide

Complete guide for setting up a receiving server for AirheadWaves Android audio streaming application.

---

## About AirheadWaves (Android App)

AirheadWaves is a versatile Android application designed for real-time audio streaming from your device to a server on your local network. It captures internal audio playback, encodes it, and transmits it over TCP, making it a perfect solution for creating a whole-house audio system, streaming to a Raspberry Pi, or any other DIY audio project.

This project was proudly developed in collaboration with **Google's Gemini**.

### Key Features

- **Real-Time Audio Streaming:** Captures and streams internal device audio with low latency
- **Advanced Audio Controls:** Software-based volume, bass, and treble controls that apply in real-time
- **Multi-Profile System:** Create, save, and manage multiple server profiles with complete configurations
- **Dynamic Configuration:** 
  - **Bitrate:** 96kbps, 128kbps, 192kbps, 256kbps, 320kbps
  - **Sample Rate:** 22050Hz, 44100Hz, 48000Hz (48000Hz recommended)
  - **Channels:** Mono & Stereo (Default)
- **Audio-Reactive Visualization:** App background subtly pulses with audio volume
- **Theming:** Full-featured theme system with Light, Dark, and System Default options
- **Modern Android UI:** Built with Jetpack Compose featuring clean, animated interface
- **Robust State Management:** Handles backgrounding and window resizing

### Technical Architecture

- **UI Layer:** Built entirely with Jetpack Compose using single-activity architecture with NavController
- **Foreground Service:** Long-running `AudioCaptureService` ensures uninterrupted streaming when backgrounded
- **Audio Capture:** Uses `MediaProjection` API to capture internal audio from any source (Spotify, YouTube, podcast players, etc.)
- **Audio Processing:** Raw PCM audio encoded to AAC format using `MediaCodec` API
- **DSP Effects:** Software-based bass and treble controls using custom `BiquadFilter` class
- **Networking:** Encoded AAC audio wrapped in **ADTS (Audio Data Transport Stream) headers** streamed over TCP
- **Settings Persistence:** All settings saved to `SharedPreferences` using `kotlinx.serialization`

### Building from Source

If you want to build or modify the AirheadWaves app:

1. Clone the repository
2. Open the project in the latest version of Android Studio
3. The project uses Gradle and should sync automatically
4. Build and run on your physical Android device (Android 10 / API 29 or higher required)

### Future Enhancement Ideas for the App

- **Automatic Server Discovery (Avahi/Zeroconf):** Eliminate manual IP entry
- **More Audio Effects:** Multi-band equalizer, compressor, stereo widener
- **Quick Settings Tile:** One-tap streaming from Android Quick Settings
- **Wear OS Companion App:** Simple remote control for your stream

---

## Server Setup

This guide covers setting up a GStreamer-based receiving server to play audio streamed from AirheadWaves.

### Test Environment

This setup was tested and verified on:
- **Hardware:** Raspberry Pi Zero 2 W with BossDAC HAT
- **Operating System:** Raspberry Pi OS Lite (Debian-based)
- **Audio Output:** BossDAC I2S DAC

**Note:** This setup can be adapted for any Linux-based system with any audio output device. Simply adjust the ALSA device names to match your hardware.

### Audio System Compatibility

This guide uses **ALSA (Advanced Linux Sound Architecture)** with dmix for software mixing.

**Important:** If your system uses **PipeWire** or **PulseAudio** instead of ALSA, you will need alternative configurations:
- **PipeWire users:** Can use the PipeWire-ALSA compatibility layer or configure PipeWire directly
- **PulseAudio users:** Replace `alsasink` with `pulsesink` in GStreamer pipelines and skip the dmix configuration

This guide focuses on the ALSA setup. For PipeWire/PulseAudio configurations, consult their respective documentation.

## Prerequisites

### Install GStreamer with Required Plugins

```bash
# Debian/Ubuntu/Raspberry Pi OS
sudo apt update
sudo apt install gstreamer1.0-tools \
                 gstreamer1.0-plugins-base \
                 gstreamer1.0-plugins-good \
                 gstreamer1.0-libav \
                 gstreamer1.0-alsa
```

### Verify Installation

```bash
# Check GStreamer version
gst-launch-1.0 --version

# Verify required elements are available
gst-inspect-1.0 aacparse
gst-inspect-1.0 avdec_aac
```

## ALSA Configuration for Software Mixing

To allow multiple applications (like AirheadWaves and Snapcast) to share the audio device simultaneously, configure ALSA dmix:

### Find Your Audio Device

```bash
aplay -l
```

Example output:
```
card 0: BossDAC [BossDAC], device 0: BossDAC HiFi [BossDAC HiFi]
  Subdevices: 1/1
  Subdevice #0: subdevice #0
```

In this example, the card name is `BossDAC`.

### Create ALSA Configuration

```bash
sudo nano /etc/asound.conf
```

Add the following configuration (replace `BossDAC` with your card name):

```
pcm.!default {
    type asym
    playback.pcm "dmixer"
    capture.pcm "hw:BossDAC,0"
}

pcm.dmixer {
    type dmix
    ipc_key 1024
    ipc_perm 0666
    slave {
        pcm "hw:BossDAC,0"
        format S16_LE
        rate 48000
        channels 2
        period_size 1024
        buffer_size 8192
    }
}

ctl.!default {
    type hw
    card BossDAC
}
```

**Common card names to replace `BossDAC` with:**
- `card 0: ALSA` → use `ALSA`
- `card 0: Device` → use `Device`
- `card 1: USB` → use `USB`
- Or use generic: `hw:0,0` instead of `hw:BossDAC,0`

### Apply Configuration

```bash
sudo alsa force-reload
```

### Configuration Notes

- `rate 48000` - Match this to your AirheadWaves sample rate setting (48000Hz recommended, or 44100Hz)
- `ipc_perm 0666` - Allows multiple processes to access the mixer
- `period_size 1024` and `buffer_size 8192` - Optimized for low latency

## GStreamer Pipeline Configuration

### Recommended Pipeline (Production Ready)

This pipeline has been tested and provides stable, crackle-free playback:

```bash
gst-launch-1.0 -v tcpserversrc host=0.0.0.0 port=8888 do-timestamp=true ! \
  aacparse ! \
  avdec_aac ! \
  audioconvert ! \
  audioresample quality=10 ! \
  alsasink device=default \
    sync=false \
    provide-clock=false \
    buffer-time=100000 \
    latency-time=10000
```

### Pipeline Parameters Explained

- **`tcpserversrc host=0.0.0.0 port=8888`** - Listen on all network interfaces on port 8888
- **`do-timestamp=true`** - Add timestamps to incoming data for better synchronization
- **`aacparse`** - Parse AAC frames with ADTS headers
- **`avdec_aac`** - Decode AAC audio using high-quality libav decoder
- **`audioconvert`** - Convert audio format if needed
- **`audioresample quality=10`** - Resample audio with highest quality (0-10 scale)
- **`alsasink device=default`** - Output to ALSA default device (uses dmix)
- **`sync=false`** - Disable clock synchronization (critical for preventing crackling)
- **`provide-clock=false`** - Don't provide pipeline clock
- **`buffer-time=100000`** - 100ms buffer in microseconds
- **`latency-time=10000`** - 10ms latency target in microseconds

### Why avdec_aac Instead of faad?

The `avdec_aac` decoder (from libav) provides:
- Better compatibility with ADTS-wrapped AAC streams
- More reliable performance
- Fewer crackling issues
- No need for additional plugin installation

Some older guides recommend `faad`, but `avdec_aac` is the superior choice for this application.

### Alternative: Simple Test Pipeline

For quick testing without dmix configuration:

```bash
gst-launch-1.0 tcpserversrc host=0.0.0.0 port=8888 ! \
  aacparse ! \
  avdec_aac ! \
  audioconvert ! \
  autoaudiosink
```

**Warning:** This simple pipeline will exclusively lock the audio device and prevent other applications from using it.

## WiFi Optimization

Disabling WiFi power management is **critical** for preventing audio dropouts and crackling on WiFi connections.

### Disable WiFi Power Management

```bash
# Check current status
iwconfig wlan0 | grep "Power Management"

# Disable power management (temporary - until reboot)
sudo iwconfig wlan0 power off
```

### Make it Permanent

Add to `/etc/rc.local` before `exit 0`:

```bash
sudo nano /etc/rc.local
```

Add this line:
```bash
/sbin/iwconfig wlan0 power off
```

### Verify Status

```bash
iwconfig wlan0 | grep "Power Management"
```

Should show: `Power Management:off`

**Note:** The systemd service configuration shown below does not include the `ExecStartPre` WiFi management command. If you experience WiFi-related audio issues, either:
1. Add it manually to `/etc/rc.local` as shown above, or
2. Add this line to your systemd service file under `[Service]`:
   ```ini
   ExecStartPre=/sbin/iwconfig wlan0 power off
   ```

## Running as a Systemd Service

For automatic startup on boot and automatic restart on failure:

### Create Service File

```bash
sudo nano /etc/systemd/system/audio-stream.service
```

Add the following content:

```ini
[Unit]
Description=AirheadWaves Audio Streaming Receiver
After=network.target sound.target

[Service]
Type=simple
User=YOUR_USERNAME
Restart=always
RestartSec=2

ExecStart=/usr/bin/gst-launch-1.0 -v tcpserversrc host=0.0.0.0 port=8888 do-timestamp=true ! aacparse ! avdec_aac ! audioconvert ! audioresample quality=10 ! alsasink device=default sync=false provide-clock=false buffer-time=100000 latency-time=10000

[Install]
WantedBy=multi-user.target
```

**Important:** Replace `YOUR_USERNAME` with your actual username (e.g., `pi`, `user`, etc.)

**Note:** The `-v` flag enables verbose output which is useful for debugging. Remove it in production if you don't need detailed logs.

### Enable and Start Service

```bash
# Reload systemd configuration
sudo systemctl daemon-reload

# Enable service to start on boot
sudo systemctl enable audio-stream.service

# Start the service now
sudo systemctl start audio-stream.service

# Check service status
sudo systemctl status audio-stream.service
```

### Service Management Commands

```bash
# Stop the service
sudo systemctl stop audio-stream.service

# Restart the service
sudo systemctl restart audio-stream.service

# View live logs
journalctl -u audio-stream.service -f

# View recent logs
journalctl -u audio-stream.service -n 50

# Disable auto-start on boot
sudo systemctl disable audio-stream.service
```

## Connecting AirheadWaves to Your Server

### Finding Your Server IP Address

On the server:
```bash
hostname -I
```

Or check with:
```bash
ip addr show | grep "inet "
```

### AirheadWaves App Configuration

Configure the app with these recommended settings:

**Network Settings:**
- **Server IP:** Your server's IP address (from command above)
- **Port:** 8888

**Audio Settings (Recommended):**
- **Format:** AAC with ADTS headers (default - no configuration needed)
- **Sample Rate:** 48000 Hz (must match your `asound.conf` setting)
- **Bitrate:** 128-256 kbps (192kbps or 256kbps recommended for best quality)
- **Channels:** Stereo (default)

**Audio Controls:**
- **Volume:** Adjust in-app for independent control from Android system volume
- **Bass:** Software-based bass boost/cut
- **Treble:** Software-based treble boost/cut

### Using Multi-Profile System

For multiple rooms or servers:
1. Set up a server in each room following this guide
2. Use different ports (8888, 8889, 8890, etc.) on each server
3. Create separate profiles in AirheadWaves for each server
4. Switch between rooms by selecting different profiles in the app

## Firewall Configuration

If you have a firewall enabled, allow TCP port 8888:

### UFW (Uncomplicated Firewall)

```bash
sudo ufw allow 8888/tcp
sudo ufw reload
```

### iptables

```bash
sudo iptables -A INPUT -p tcp --dport 8888 -j ACCEPT
sudo iptables-save | sudo tee /etc/iptables/rules.v4
```

## Troubleshooting

### Server Not Listening

**Check if port is open:**
```bash
netstat -tln | grep 8888
```

Should show:
```
tcp        0      0 0.0.0.0:8888            0.0.0.0:*               LISTEN
```

**Port already in use:**
```bash
# Find what's using the port
sudo fuser -v 8888/tcp

# Kill the process
sudo fuser -k 8888/tcp
```

### Audio Quality Issues

**Crackling or popping audio:**
1. Verify WiFi power management is disabled: `iwconfig wlan0 | grep Power`
2. Increase buffer size: Change `buffer-time=100000` to `buffer-time=200000`
3. Check CPU usage: `top` or `htop` (should not be at 100%)
4. Ensure sample rates match between app and server
5. Verify you're using `avdec_aac`, not `faad`
6. Confirm `sync=false` and `provide-clock=false` are set in alsasink

**No audio output:**
1. Test audio device: `speaker-test -D default -c 2`
2. Check ALSA device: `aplay -l`
3. Verify GStreamer elements work: `gst-inspect-1.0 avdec_aac`
4. Run pipeline manually with `-v` flag for verbose output

**Audio cutting out:**
1. Check network connectivity: `ping YOUR_ANDROID_IP`
2. Monitor for packet loss: `netstat -s | grep -i retran`
3. Use wired ethernet if possible
4. Move closer to WiFi router or use 5GHz band
5. Lower bitrate in AirheadWaves app (try 128kbps)

### Device Busy Error

If you get "Device is being used by another application":

**Option 1: Stop other audio services**
```bash
sudo systemctl stop snapclient
sudo killall pulseaudio
```

**Option 2: Verify dmix is configured correctly**
```bash
# Check ALSA configuration
cat /etc/asound.conf

# Reload ALSA
sudo alsa force-reload
```

**Option 3: Use PulseAudio instead (requires different setup)**
```bash
# Install PulseAudio
sudo apt install pulseaudio

# Change alsasink to pulsesink in the pipeline
gst-launch-1.0 ... ! pulsesink
```

### Connection Issues

**App connects but no audio:**
1. Capture data to verify it's arriving:
   ```bash
   timeout 10 gst-launch-1.0 tcpserversrc host=0.0.0.0 port=8888 ! filesink location=test.aac
   file test.aac
   ```
2. Check if file contains data (should not be all zeros)
3. Verify app is actually playing audio, not just connected
4. Check that audio is not muted in AirheadWaves

**Pipeline crashes when app disconnects:**
- This is normal behavior
- Use the systemd service (with `Restart=always`) to automatically restart
- The service will wait for the next connection

### Performance Monitoring

**Check CPU usage while streaming:**
```bash
top
# Look for gst-launch-1.0 process
```

On Raspberry Pi Zero 2 W, CPU usage should be <20% at 256kbps.

**Monitor ALSA for xruns (buffer underruns):**
```bash
cat /proc/asound/card*/pcm*/sub*/status
```

**Check network statistics:**
```bash
# Monitor in real-time
watch -n 1 'netstat -s | grep -i retran'
```

## Advanced Configuration

### Adjusting Buffer Sizes

For unstable networks, increase buffering (adds latency):

```bash
gst-launch-1.0 -v tcpserversrc host=0.0.0.0 port=8888 do-timestamp=true ! \
  queue max-size-buffers=0 max-size-time=3000000000 max-size-bytes=0 ! \
  aacparse ! \
  avdec_aac ! \
  audioconvert ! \
  audioresample quality=10 ! \
  alsasink device=default \
    sync=false \
    provide-clock=false \
    buffer-time=200000 \
    latency-time=20000
```

This adds ~3 seconds of buffering for very poor network conditions.

### CPU Performance Mode

On Raspberry Pi, disable CPU frequency scaling for consistent performance:

```bash
# Check current governor
cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor

# Set to performance mode
echo performance | sudo tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor
```

### Sample Rate Matching

If audio sounds wrong (pitch/speed issues), ensure sample rates match:

**Check what rate the stream is using:**
```bash
gst-launch-1.0 -v tcpserversrc ... 2>&1 | grep -i rate
```

**Force a specific rate:**
```bash
... ! audioresample quality=10 ! 'audio/x-raw,rate=48000' ! alsasink ...
```

## Integration with Other Services

### Snapcast Compatibility

The dmix configuration allows both AirheadWaves and Snapcast to coexist. Configure Snapcast client:

```bash
sudo nano /etc/default/snapclient
```

Set:
```
SNAPCLIENT_OPTS="--player alsa:device=default"
```

Restart:
```bash
sudo systemctl restart snapclient
```

Both services can now run simultaneously.

## Performance Notes

**Test System (Raspberry Pi Zero 2 W with BossDAC):**
- **Latency:** Approximately 100-150ms total (network + buffering)
- **CPU Usage:** 10-20% at 256kbps on Pi Zero 2 W
- **Network Bandwidth:** 
  - 96kbps: ~12 KB/s
  - 128kbps: ~16 KB/s
  - 192kbps: ~24 KB/s
  - 256kbps: ~32 KB/s
  - 320kbps: ~40 KB/s
- **Quality:** Lossless AAC decoding with high-quality resampling
- **Compatibility:** Works with any audio source on Android (Spotify, YouTube, system audio, etc.)

**Note:** More powerful systems (Raspberry Pi 3/4/5) will have even lower CPU usage and can handle higher bitrates with ease.

## Additional Resources

- **GStreamer Documentation:** https://gstreamer.freedesktop.org/documentation/
- **ALSA dmix Guide:** https://www.alsa-project.org/wiki/Asoundrc#Software_mixing
- **AirheadWaves GitHub:** [Insert repository URL]
- **Android MediaProjection API:** https://developer.android.com/reference/android/media/projection/MediaProjection
- **Raspberry Pi OS:** https://www.raspberrypi.com/software/

## Credits

**Server Setup:**
- Tested and verified on Raspberry Pi Zero 2 W with Raspberry Pi OS Lite and BossDAC HAT
- **GStreamer** - Multimedia framework
- **ALSA** - Advanced Linux Sound Architecture

**AirheadWaves Android App:**
- Developed in collaboration with **Google's Gemini**
- Built with Jetpack Compose and modern Android development practices