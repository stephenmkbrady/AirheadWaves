# CosmicCast - Audio Streaming Application Requirements

## Document Information
- **Version**: 1.1
- **Date**: 2025-10-22
- **Status**: Active - Reflects Current Implementation
- **Next Version**: [requirements-v2.0.md](requirements-v2.0.md) - Planned receive mode features

## Changes from v1.0
- Updated to reflect actual implemented state of the application
- This version documents the transmit-only implementation
- Receive mode features moved to v2.0 (future)

---

## 1.1.1. Overview

### 1.1.1.1 Purpose
CosmicCast (formerly AirheadWaves) is an Android application that captures internal device audio and streams it over TCP to a remote server. It enables users to listen to their Android device's audio output (Spotify, YouTube, podcasts, games, etc.) on remote speakers or devices.

### 1.1.1.2 Current State (v1.1)
CosmicCast currently supports **Transmit Mode only**:
- Captures internal Android device audio using MediaProjection API
- Encodes to AAC format with ADTS headers
- Streams over TCP to a configured server
- Supports multiple server profiles with audio settings
- Real-time audio effects (bass, treble, volume)
- Audio-reactive visualization
- Modern Jetpack Compose UI

### 1.1.1.3 Scope
This document describes the **current implementation** of CosmicCast v1.1, which is a transmit-only audio streaming application.

**In Scope:**
- Audio capture and transmission
- Profile management
- Audio effects processing
- User interface and settings
- Background service operation

**Out of Scope (Future - v2.0):**
- Receive mode (playing audio from remote sources)
- Bidirectional audio streaming
- Multi-device broadcasting

---

## 2. Use Cases

### 2.1 Primary Use Case: Stream to Remote Speaker System

**Actors:**
- Source Device: Android phone/tablet with CosmicCast installed
- Receiver: Remote server/device (Raspberry Pi, computer, etc.) running audio receiver software

**Scenario:**
1. User configures a profile with server IP address and port
2. User selects desired audio quality settings (bitrate, sample rate, channels)
3. User grants MediaProjection permission
4. User starts streaming
5. Audio from all apps (Spotify, YouTube, etc.) streams to remote device
6. User can adjust volume, bass, and treble in real-time
7. User stops streaming when done

**Example:**
- Phone playing Spotify → WiFi → Raspberry Pi (192.168.1.100:8888) → Living room speakers

### 2.2 Secondary Use Cases

**Use Case 2.1: Wireless Audio Distribution**
- Stream device audio to home stereo system
- Replace need for physical aux cable or Bluetooth (higher quality)
- Control audio from source device

**Use Case 2.2: Multi-Location Streaming**
- Switch between different receiver profiles (bedroom, living room, office)
- Save different audio settings per location
- Quick profile switching without reconfiguration

---

## 3. Functional Requirements

### 3.1 Audio Capture

#### 3.1.1 Internal Audio Capture
**REQ-CAP-001**: The application SHALL capture internal device audio
- Uses Android MediaProjection API
- Captures USAGE_MEDIA and USAGE_GAME audio
- No microphone input
- Requires user permission grant via system dialog

**REQ-CAP-002**: The application SHALL support configurable audio formats
- Sample rates: 22050 Hz, 44100 Hz, 48000 Hz
- Channel configurations: Mono, Stereo
- PCM 16-bit input format

### 3.2 Audio Processing

#### 3.2.1 Audio Effects
**REQ-PROC-001**: The application SHALL apply real-time audio effects
- Bass control: -15dB to +15dB using biquad low-shelf filter (150 Hz)
- Treble control: -15dB to +15dB using biquad high-shelf filter (4000 Hz)
- Volume control: 0-100% with cubic scaling
- Effects applied before encoding

**REQ-PROC-002**: Audio effects SHALL be adjustable without stopping the stream
- Real-time parameter updates
- No audio interruption or glitches
- Settings persisted per profile

#### 3.2.2 Audio Level Detection
**REQ-PROC-003**: The application SHALL calculate audio levels for visualization
- RMS-based amplitude detection
- Updates for UI animation (background color pulsing)
- Minimal CPU overhead

### 3.3 Audio Encoding

**REQ-ENC-001**: The application SHALL encode audio to AAC format
- AAC-LC (Low Complexity) profile
- Uses Android MediaCodec API
- Configurable bitrates: 96, 128, 192, 256, 320 kbps

**REQ-ENC-002**: The application SHALL add ADTS headers to AAC frames
- Standard ADTS framing for stream transport
- Compatible with common audio receivers
- Proper sync word and header construction

### 3.4 Network Streaming

**REQ-NET-001**: The application SHALL stream audio over TCP
- Client mode (connects to configured server)
- User-configurable destination IP and port
- Maintains persistent connection during streaming

**REQ-NET-002**: The application SHALL monitor streaming statistics
- Real-time bitrate calculation and display (kbps)
- Connection status indicators
- Updates to UI via ViewModel

### 3.5 Profile Management

#### 3.5.1 Profile Storage
**REQ-PROF-001**: The application SHALL support multiple streaming profiles
- User-defined profile names
- Each profile stores:
  - Profile name
  - Server IP address
  - Server port
  - Bitrate (96-320 kbps)
  - Sample rate (22050-48000 Hz)
  - Channel configuration (Mono/Stereo)
  - Bass adjustment (-15 to +15 dB)
  - Treble adjustment (-15 to +15 dB)

**REQ-PROF-002**: Profiles SHALL be persisted across app restarts
- Stored in SharedPreferences using JSON serialization
- Profile selection remembered
- All settings preserved

#### 3.5.2 Profile Operations
**REQ-PROF-003**: Users SHALL be able to manage profiles
- Add new profiles via FAB button
- Edit existing profile settings
- Delete profiles (with confirmation)
- Select active profile from dropdown

### 3.6 Background Operation

**REQ-SVC-001**: The application SHALL run as a foreground service
- AudioCaptureService maintains streaming in background
- Persistent notification displayed while streaming
- Survives app backgrounding and screen off
- Proper resource cleanup on stop

**REQ-SVC-002**: The application SHALL request required permissions
- RECORD_AUDIO
- FOREGROUND_SERVICE
- FOREGROUND_SERVICE_MEDIA_PROJECTION
- INTERNET
- POST_NOTIFICATIONS

### 3.7 User Interface

#### 3.7.1 Main Screen
**REQ-UI-001**: The main screen SHALL display streaming controls and status
- Service status indicator (streaming/stopped)
- Profile selection dropdown
- Start/Stop button
- Real-time bitrate display
- Volume slider (0-100%)
- Settings navigation button

**REQ-UI-002**: The main screen SHALL show audio-reactive visualization
- Background color animation based on audio level
- Color transitions between primary and background colors
- Can be enabled/disabled in settings
- Smooth 100ms transitions

#### 3.7.2 Settings Screens
**REQ-UI-003**: The application SHALL provide settings management
- **App Settings Screen**:
  - Theme selection: Light, Dark, System Default
  - Visualization toggle
- **Profile Settings Screen**:
  - List of all profiles
  - Expandable profile editor cards
  - Add/delete profile operations
  - Real-time dB value display for bass/treble

#### 3.7.3 UI Framework
**REQ-UI-004**: The UI SHALL be built with Jetpack Compose
- Single Activity architecture
- Navigation via NavController
- Material Design 3 components
- Reactive state management via ViewModel

### 3.8 State Management

**REQ-STATE-001**: Application state SHALL be managed via MainViewModel
- Centralized state holder (singleton pattern)
- Kotlin StateFlow for reactive UI updates
- State includes:
  - Service running status
  - Streaming statistics
  - Volume level
  - Audio level
  - Profile list and selection
  - Theme and visualization settings

---

## 4. Non-Functional Requirements

### 4.1 Performance

**REQ-PERF-001**: Audio processing SHALL have minimal latency
- Real-time encoding and streaming
- Effects processing without noticeable delay
- Smooth audio playback on receiver

**REQ-PERF-002**: The application SHALL maintain stable performance
- No memory leaks during extended streaming sessions
- Efficient CPU usage
- Proper thread management for encoding

### 4.2 Compatibility

**REQ-COMPAT-001**: The application SHALL support Android 10+
- Minimum SDK: API 29 (Android 10)
- Target SDK: API 36
- Compile SDK: API 36

**REQ-COMPAT-002**: The application SHALL be F-Droid compliant
- No proprietary dependencies
- No Google Play Services
- Reproducible builds enabled
- Open source libraries only

### 4.3 Usability

**REQ-USE-001**: The application SHALL provide intuitive controls
- Clear status indicators
- Simple start/stop operation
- Easy profile switching
- Real-time feedback for adjustments

**REQ-USE-002**: Settings SHALL persist across sessions
- Last used profile remembered
- Volume level saved
- Theme preference saved
- All profile configurations preserved

### 4.4 Reliability

**REQ-REL-001**: The application SHALL handle errors gracefully
- Network connection failures
- Permission denial handling
- Encoder initialization failures
- User-friendly error messages

**REQ-REL-002**: The application SHALL properly manage resources
- Clean service shutdown
- Socket closure on stop
- MediaCodec release
- AudioRecord cleanup

---

## 5. Architecture Overview

### 5.1 Components

**MainActivity**
- Single Activity with Jetpack Compose UI
- Navigation host
- Permission request handler
- Service lifecycle management

**MainViewModel**
- Centralized state management
- Profile CRUD operations
- Settings persistence
- Service status tracking

**AudioCaptureService**
- Foreground Service
- MediaProjection setup
- Audio capture loop (AudioRecord)
- Effect processing (BiquadFilter × 2)
- AAC encoding (MediaCodec)
- TCP streaming (Socket)
- Audio level calculation

**BiquadFilter**
- Digital signal processing class
- Low-shelf filter (bass)
- High-shelf filter (treble)
- Configurable gain and frequency

### 5.2 Data Flow

```
User Interaction → MainViewModel → AudioCaptureService
                                          ↓
                    MediaProjection → AudioRecord (PCM)
                                          ↓
                    BiquadFilter (Bass) → BiquadFilter (Treble)
                                          ↓
                    Volume Scaling → MediaCodec (AAC Encoding)
                                          ↓
                    ADTS Framing → TCP Socket → Remote Server
                                          ↓
                    Audio Level → ViewModel → UI Visualization
```

### 5.3 Technology Stack

- **Language**: Kotlin 2.0.21
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM with Single Activity
- **State Management**: Kotlin StateFlow
- **Serialization**: kotlinx.serialization
- **Audio APIs**: MediaProjection, AudioRecord, MediaCodec
- **Network**: Java Socket (TCP)
- **Build System**: Gradle with Kotlin DSL

---

## 6. Known Limitations

1. **Transmit-only**: Cannot receive audio from other devices
2. **Single connection**: Streams to one server at a time
3. **No recording**: Cannot save streams to local files
4. **No reconnection**: Manual restart required on network failure
5. **No compression presets**: Manual bitrate selection only
6. **No microphone input**: Internal audio capture only
7. **No Bluetooth streaming**: Network streaming only

---

## 7. Testing Requirements

### 7.1 Functional Testing
- [ ] Audio capture starts successfully
- [ ] TCP connection establishes to server
- [ ] Audio streams with all bitrate/sample rate combinations
- [ ] Bass/treble effects audibly change output
- [ ] Volume control works correctly
- [ ] Profile add/edit/delete operations work
- [ ] Profile switching loads correct settings
- [ ] Service continues during background operation
- [ ] Service stops cleanly with resource cleanup

### 7.2 Compatibility Testing
- [ ] Works on Android 10, 11, 12, 13, 14+
- [ ] Works on various device manufacturers
- [ ] Builds reproducibly for F-Droid
- [ ] No crashes on permission denial

### 7.3 Performance Testing
- [ ] No audio dropouts during stable network
- [ ] Memory usage remains stable over 1+ hour streaming
- [ ] CPU usage acceptable during encoding
- [ ] Battery drain reasonable for streaming workload

---

## 8. Future Enhancements (v2.0+)

See [requirements-v2.0.md](requirements-v2.0.md) for planned features:
- Receive mode (act as audio player for remote streams)
- Bidirectional audio streaming
- Multiple profile types (transmit/receive)
- Enhanced error handling and reconnection
- Multi-device broadcasting

---

## 9. Appendix

### 9.1 Related Documents
- [requirements-v2.0.md](requirements-v2.0.md) - Future receive mode requirements
- [F-DROID-SUBMISSION.md](F-DROID-SUBMISSION.md) - F-Droid submission checklist
- [instructions.md](instructions.md) - Implementation instructions
- README.md - Project overview

### 9.2 Revision History
| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.1 | 2025-10-22 | System | Current implementation state documentation |

---

**Status**: This document reflects the **actual implemented state** of CosmicCast v1.1 as of 2025-10-22.
