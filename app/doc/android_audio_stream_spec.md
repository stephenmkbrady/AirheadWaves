# Simple Android Audio Streaming App

## Overview
Lightweight Android application that captures system audio and streams it to a Raspberry Pi server over the local network.

## Architecture

### Android App Components

**MainActivity.java** (~100 LoC)
- Single button UI (Start/Stop)
- Handles MediaProjection permission request
- Starts/stops AudioCaptureService

**AudioCaptureService.java** (~350 LoC)
- Foreground service with notification
- MediaProjection + AudioPlaybackCapture for system audio capture
- MediaCodec AAC encoding
- TCP socket streaming to server
- Basic error handling and reconnection

**Config.java** (~50 LoC)
- Reads server configuration from SharedPreferences or properties file
- Default values: server_ip, server_port (8888), bitrate (128000), sample_rate (44100)

**AndroidManifest.xml** (~30 LoC)
- Permissions: RECORD_AUDIO, FOREGROUND_SERVICE, INTERNET
- Service and activity declarations

**Layout XML** (~20 LoC)
- Simple button-only interface

**Total: ~550 LoC**

### Key Android APIs Used
- `MediaProjection` + `AudioPlaybackCapture` (Android 10+) - System audio capture
- `MediaCodec` - AAC encoding
- `Socket` - TCP streaming
- `Service` + `NotificationChannel` - Foreground service

### What Audio Can Be Captured?

**AudioPlaybackCapture CAN capture:**
- ✅ Music apps (Spotify, YouTube Music, etc.)
- ✅ Video apps (YouTube, Netflix, etc.)
- ✅ Games
- ✅ Browser audio (Chrome, Firefox, etc.)
- ✅ Social media apps (TikTok, Instagram, etc.)
- ✅ Podcast apps
- ✅ Most app audio output

**AudioPlaybackCapture CANNOT capture:**
- ❌ Phone calls (privacy protection by Android)
- ❌ Apps that explicitly opt-out using `ALLOW_CAPTURE_BY_NONE` flag
- ❌ Some VoIP apps (Zoom, WhatsApp calls may opt-out)
- ❌ Some system sounds (notifications, ringtones - depending on implementation)
- ❌ Microphone input (would need separate permission/API)

**Important notes:**
- Captures playback audio only (what you hear from speakers)
- Requires `USAGE_MEDIA` and `USAGE_GAME` audio attributes
- Works with most consumer apps (streaming, gaming, social media)
- Some enterprise/security apps may block capture

### Audio Pipeline
```
System Audio → AudioPlaybackCapture → PCM Buffer → MediaCodec (AAC) → TCP Socket → Server
```

## Server Side

### Option 1: GStreamer (Low Latency)

**Install GStreamer:**
```bash
sudo apt install gstreamer1.0-tools gstreamer1.0-alsa gstreamer1.0-plugins-good
```

**Run receiver:**
```bash
gst-launch-1.0 tcpserversrc port=8888 ! \
  aacparse ! \
  avdec_aac ! \
  audioconvert ! \
  audioresample ! \
  alsasink device=plughw:BossDAC
```

**Systemd service (gstreamer-audio-receiver.service):**
```ini
[Unit]
Description=GStreamer Audio Receiver
After=network.target sound.target

[Service]
Type=simple
ExecStart=/usr/bin/gst-launch-1.0 tcpserversrc port=8888 ! aacparse ! avdec_aac ! audioconvert ! audioresample ! alsasink device=plughw:BossDAC
Restart=on-failure
User=user
Group=user

[Install]
WantedBy=multi-user.target
```

### Option 2: Python + FFmpeg (Simple, Flexible)

**Python server (audio_server.py):**
```python
#!/usr/bin/env python3
import socket
import subprocess
import sys

SERVER_PORT = 8888
ALSA_DEVICE = "plughw:BossDAC"

def start_server():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(('0.0.0.0', SERVER_PORT))
    server.listen(1)
    print(f"Listening on port {SERVER_PORT}...")
    
    while True:
        conn, addr = server.accept()
        print(f"Connection from {addr}")
        
        try:
            # Start FFmpeg to decode AAC and output to ALSA
            ffmpeg = subprocess.Popen([
                'ffmpeg',
                '-f', 'aac',
                '-i', 'pipe:0',
                '-f', 'alsa',
                ALSA_DEVICE
            ], stdin=subprocess.PIPE, stderr=subprocess.DEVNULL)
            
            # Stream data from socket to FFmpeg
            while True:
                data = conn.recv(4096)
                if not data:
                    break
                ffmpeg.stdin.write(data)
                
        except Exception as e:
            print(f"Error: {e}")
        finally:
            ffmpeg.terminate()
            conn.close()
            print("Connection closed")

if __name__ == '__main__':
    start_server()
```

**Systemd service (audio-streaming-server.service):**
```ini
[Unit]
Description=Audio Streaming Server
After=network.target sound.target

[Service]
Type=simple
ExecStart=/usr/bin/python3 /home/user/audio_server.py
Restart=on-failure
User=user
Group=user

[Install]
WantedBy=multi-user.target
```

## Configuration

### Android Config (SharedPreferences or config.properties)
```properties
server_ip=192.168.178.40
server_port=8888
bitrate=128000
sample_rate=44100
```

### Network Requirements
- Both devices on same WiFi network
- Server port 8888 accessible (firewall rules if needed)
- Low latency network for best audio sync

## Security
- Network-based (requires same WiFi)
- No authentication (trust local network)
- Could add simple token-based auth if needed

## Known Limitations
- Android 10+ required (AudioPlaybackCapture API)
- User must grant MediaProjection permission on each app start (Android privacy requirement)
- Audio latency ~100-300ms depending on buffer sizes and network
- Only one client can stream at a time (server accepts one connection)

## Development Effort
- Core functionality: 4-8 hours
- Polish + testing: 4-8 hours
- **Total: ~8-16 hours** for experienced Android developer

## Claude Code Success Probability
**High (85-90%)** - This is a straightforward project using standard Android APIs with clear requirements. Claude Sonnet 4.5 should generate working code with minimal intervention.

Potential issues requiring human intervention:
- MediaCodec configuration tuning for optimal quality/latency
- Buffer size optimization
- Edge case error handling
- UI polish (though minimal UI required)
