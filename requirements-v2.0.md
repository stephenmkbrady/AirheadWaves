# AirheadWaves - Receive Mode Requirements

## Document Information
- **Version**: 2.0
- **Date**: 2025-10-18
- **Status**: Draft - Future Planning
- **Previous Version**: [requirements-v1.1.md](requirements-v1.1.md)

## 1. Overview

### 1.1 Purpose
This document defines the requirements for implementing bidirectional audio streaming in AirheadWaves, specifically adding **Receive Mode** to complement the existing **Transmit Mode**.

### 1.2 Current State
AirheadWaves currently supports **Transmit Mode only**:
- Captures internal Android device audio using MediaProjection API
- Encodes to AAC format with ADTS headers
- Streams over TCP to a configured server
- Supports multiple server profiles with audio settings (bitrate, sample rate, channels, bass, treble)

### 1.3 Scope
This document covers Phase 1 and Phase 2 of the receive mode implementation:
- **Phase 1**: Basic receive functionality with audio playback
- **Phase 2**: Enhanced receive features (audio effects, visualization, error handling)
- **Phase 3** (Future): Bidirectional mode - Out of scope for this document

## 2. Use Cases

### 2.1 Primary Use Case: Android-to-Android Audio Streaming

**Actors:**
- Source Device: Android phone/tablet with audio playback (Spotify, YouTube, etc.)
- Receiver Device: Android phone/tablet connected to speakers

**Scenario:**
1. User configures Receiver Device to listen on port 8888
2. User configures Source Device to transmit to Receiver Device's IP address
3. User starts receive mode on Receiver Device (opens TCP server on port 8888)
4. User starts transmit mode on Source Device (connects to Receiver Device)
5. Audio from Source Device plays through Receiver Device's speakers
6. User can adjust volume, bass, and treble on receiver independently

**Example:**
- Phone (192.168.1.100) playing Spotify → WiFi → Tablet (192.168.1.200:8888) connected to living room speakers

### 2.2 Secondary Use Cases

**Use Case 2.1: Wireless Speaker System**
- Android device as dedicated wireless speaker
- Receives from multiple source devices (one at a time initially)
- Replaces need for Chromecast/Bluetooth speaker

**Use Case 2.2: Multi-Room Audio**
- Multiple Android devices in different rooms
- All receiving from single source device
- Synchronized audio playback (future enhancement)

## 3. Functional Requirements

### 3.1 Receive Mode Core Functionality

#### 3.1.1 TCP Server Operation
**REQ-RCV-001**: The application SHALL act as a TCP server when in Receive Mode
- Opens a user-configured port (default: 8888)
- Accepts incoming connections from transmitter devices
- Handles connection lifecycle (connect, disconnect, reconnect)

**REQ-RCV-002**: The application SHALL support single concurrent connection
- Only one transmitter can connect at a time (Phase 1)
- Additional connection attempts are rejected while streaming

#### 3.1.2 Audio Reception and Decoding
**REQ-RCV-003**: The application SHALL receive AAC audio with ADTS headers
- Compatible with existing transmit mode format
- Supports AAC-LC profile

**REQ-RCV-004**: The application SHALL decode received audio using MediaCodec
- Decode AAC to PCM format
- Support sample rates: 22050 Hz, 44100 Hz, 48000 Hz
- Support channel configurations: Mono, Stereo

**REQ-RCV-005**: The application SHALL auto-detect stream parameters from ADTS headers
- Extract sample rate from ADTS header
- Extract channel configuration from ADTS header
- Gracefully handle parameter mismatches

#### 3.1.3 Audio Playback
**REQ-RCV-006**: The application SHALL play received audio using AudioTrack
- Route to appropriate output device
- Support real-time playback with minimal latency

**REQ-RCV-007**: The application SHALL support audio output routing
- **Priority 1**: Headphones (if plugged in)
- **Priority 2**: Speaker (if no headphones)
- **User Override**: Manual selection in settings

**REQ-RCV-008**: The application SHALL handle Android audio focus
- Request AUDIOFOCUS_GAIN for exclusive playback
- Support AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK for mixed playback
- User configurable: Exclusive vs. Mixed mode

### 3.2 Profile Management

#### 3.2.1 Profile Types
**REQ-PROF-001**: The application SHALL support two distinct profile types
- **Transmit Profiles**: Configuration for sending audio
- **Receive Profiles**: Configuration for receiving audio

**REQ-PROF-002**: Transmit and Receive profiles SHALL be managed separately
- Separate UI sections for each profile type
- Separate storage/serialization
- No profile can be both transmit and receive

#### 3.2.2 Transmit Profile Settings (Existing)
**REQ-PROF-003**: Transmit profiles SHALL include:
- Profile name (user-defined)
- Server IP address (destination)
- Server port (destination)
- Bitrate: 96, 128, 192, 256, 320 kbps
- Sample rate: 22050, 44100, 48000 Hz
- Channel configuration: Mono, Stereo
- Bass adjustment: -15dB to +15dB
- Treble adjustment: -15dB to +15dB

#### 3.2.3 Receive Profile Settings (New)
**REQ-PROF-004**: Receive profiles SHALL include:
- Profile name (user-defined)
- Listen port (default: 8888)
- Output device: Auto, Speaker, Headphones
- Buffer size: Low Latency (50-100ms), Balanced (100-200ms), Smooth (200-500ms)
- Bass adjustment: -15dB to +15dB (applied to received audio)
- Treble adjustment: -15dB to +15dB (applied to received audio)
- Volume control: 0-100%

**REQ-PROF-005**: Receive profiles SHALL NOT include:
- Bitrate (determined by transmitter)
- Sample rate (auto-detected from stream)
- Channel configuration (auto-detected from stream)

### 3.3 Mode Selection

**REQ-MODE-001**: The application SHALL support mode selection
- Transmit Mode
- Receive Mode
- Mode cannot be changed while streaming

**REQ-MODE-002**: Mode switching SHALL require stopping active stream
- User must stop current operation
- Select new mode
- Select appropriate profile
- Start streaming/receiving

**REQ-MODE-003**: The application SHALL remember last used mode
- Restore on app restart
- Persist in SharedPreferences

### 3.4 User Interface

#### 3.4.1 Mode Selector
**REQ-UI-001**: The application SHALL display mode selector on main screen
- Toggle/Tabs: [Transmit] [Receive]
- Visually indicate active mode
- Disable switching while streaming

#### 3.4.2 Profile Selection
**REQ-UI-002**: Profile dropdown SHALL show only profiles for current mode
- Transmit mode → Show transmit profiles only
- Receive mode → Show receive profiles only

#### 3.4.3 Status Display
**REQ-UI-003**: Status display SHALL show mode-specific information

**Transmit Mode Status:**
- Connection state: Connecting, Connected, Disconnected
- Server IP:Port
- Bitrate (actual)
- Stream statistics

**Receive Mode Status:**
- Connection state: Listening, Connected, Disconnected
- Listening port
- Connected client IP (if connected)
- Detected sample rate and channels
- Buffer health indicator

#### 3.4.4 Audio Controls
**REQ-UI-004**: Audio controls SHALL be available in both modes
- Volume slider (0-100%)
- Bass slider (-15dB to +15dB)
- Treble slider (-15dB to +15dB)
- Real-time adjustment without stopping stream

#### 3.4.5 Profile Management UI
**REQ-UI-005**: Profile settings screen SHALL separate transmit and receive profiles
- Two tabs/sections: "Transmit Profiles" and "Receive Profiles"
- Add/Edit/Delete operations for each type
- Profile editor shows only relevant fields per type

### 3.5 Audio Processing

#### 3.5.1 Receive Audio Effects
**REQ-AUDIO-001**: The application SHALL apply audio effects to received audio
- Bass boost/cut using Biquad low-shelf filter
- Treble boost/cut using Biquad high-shelf filter
- Volume scaling (0-100%)
- Real-time adjustment without interruption

**REQ-AUDIO-002**: Audio effects SHALL be applied in correct order
1. Decode AAC to PCM
2. Apply bass filter
3. Apply treble filter
4. Apply volume scaling
5. Write to AudioTrack

#### 3.5.2 Buffering
**REQ-AUDIO-003**: The application SHALL implement configurable buffering
- Low Latency: 50-100ms buffer (risk of dropouts on poor network)
- Balanced: 100-200ms buffer (recommended default)
- Smooth: 200-500ms buffer (prioritize smooth playback)

**REQ-AUDIO-004**: The application SHALL handle buffer underruns
- Detect underrun condition
- Insert silence padding
- Display warning to user
- Auto-recover when data available

### 3.6 Visualization

**REQ-VIS-001**: The application SHALL support audio visualization in receive mode
- Same background pulsing effect as transmit mode
- React to received audio levels
- Can be enabled/disabled in settings

### 3.7 Error Handling

**REQ-ERR-001**: The application SHALL handle network errors gracefully
- Connection refused (no transmitter connected)
- Connection lost (transmitter disconnected)
- Port already in use
- Network unreachable

**REQ-ERR-002**: The application SHALL provide user feedback for errors
- Toast notifications for temporary errors
- Status display shows error state
- Suggested actions (e.g., "Check network connection")

**REQ-ERR-003**: The application SHALL support automatic reconnection
- Configurable: Auto-reconnect on disconnect
- Retry delay: 2 seconds
- Maximum retries: 5 (then stop)

### 3.8 Permissions

**REQ-PERM-001**: Receive mode SHALL NOT require additional permissions
- Uses existing INTERNET permission
- Uses existing FOREGROUND_SERVICE permission
- No RECORD_AUDIO needed (receiving only)

## 4. Non-Functional Requirements

### 4.1 Performance

**REQ-PERF-001**: Audio latency SHALL be configurable
- Low Latency mode: Total latency < 150ms
- Balanced mode: Total latency < 300ms
- Smooth mode: Total latency < 600ms

**REQ-PERF-002**: The application SHALL handle continuous streaming
- Minimum continuous runtime: 24 hours
- No memory leaks
- Stable CPU usage

### 4.2 Compatibility

**REQ-COMPAT-001**: Receive mode SHALL work on Android 10+ (API 29+)
- Same minimum SDK as transmit mode

**REQ-COMPAT-002**: Receive mode SHALL be compatible with existing transmit mode
- Same AAC-LC codec
- Same ADTS framing
- Interoperable between AirheadWaves instances

### 4.3 Usability

**REQ-USE-001**: Mode switching SHALL be intuitive
- Clear visual distinction between modes
- Cannot accidentally switch while streaming
- Profile selection prevents wrong profile type

**REQ-USE-002**: Default settings SHALL provide good experience
- Default buffer: Balanced (100-200ms)
- Default output: Auto (headphones if present, else speaker)
- Default audio focus: Mixed (work alongside other apps)

### 4.4 Reliability

**REQ-REL-001**: The application SHALL maintain foreground service during receive
- Prevent Android from killing service
- Display persistent notification
- Same behavior as transmit mode

**REQ-REL-002**: The application SHALL handle app lifecycle events
- Continue streaming when backgrounded
- Pause on phone call (if audio focus lost)
- Resume after interruption

## 5. Architecture Overview

### 5.1 New Components

**AudioPlaybackService**
- Extends Service
- Implements TCP server
- Manages AAC decoder (MediaCodec)
- Manages audio playback (AudioTrack)
- Applies audio effects (BiquadFilter)
- Updates ViewModel with status

**Receive Profile Data Model**
```kotlin
data class ReceiveProfile(
    val id: String,
    val name: String,
    val listenPort: Int = 8888,
    val outputDevice: OutputDevice = OutputDevice.AUTO,
    val bufferSize: BufferSize = BufferSize.BALANCED,
    val bass: Float = 0f,
    val treble: Float = 0f,
    val volume: Float = 1.0f
)

enum class OutputDevice {
    AUTO,
    SPEAKER,
    HEADPHONES
}

enum class BufferSize(val milliseconds: Int) {
    LOW_LATENCY(75),
    BALANCED(150),
    SMOOTH(350)
}
```

### 5.2 Modified Components

**MainViewModel**
- Add receive profile management
- Add mode state (Transmit/Receive)
- Add receive-specific status fields

**MainActivity**
- Add mode selector UI
- Conditional profile dropdown (filter by mode)
- Launch AudioPlaybackService for receive mode

**Profile Settings Screen**
- Tabbed interface: Transmit | Receive
- Different editor forms per tab

## 6. Future Enhancements (Out of Scope)

### 6.1 Phase 3: Bidirectional Mode
- Simultaneous transmit and receive
- Full-duplex communication
- Echo cancellation
- Talk-back / intercom functionality

### 6.2 Advanced Features
- Multiple simultaneous receive streams with mixing
- Stream recording to file
- Scheduled streaming (start/stop at specific times)
- Multi-device synchronization
- Compression settings (beyond AAC)

## 7. Open Questions

### 7.1 Network Topology Confirmation
**QUESTION**: Confirm network architecture for Android-to-Android streaming:
- **Proposed**: Receiver opens TCP server port, Transmitter connects as client
- **Current Transmit**: Transmitter connects to external server
- **Concern**: Does this match user's network setup? (NAT/firewall considerations)

**ACTION REQUIRED**: User confirmation before implementation

### 7.2 Auto-Configuration vs. Manual
**QUESTION**: Should receive profiles store expected audio parameters?
- **Option A**: Store expected sample rate/channels, warn if mismatch detected
- **Option B**: Always auto-detect, don't store these in profile
- **Recommendation**: Option B (auto-detect only)

**ACTION REQUIRED**: User preference

### 7.3 Profile Linking
**QUESTION**: Should transmit and receive profiles be linked?
- **Example**: "Living Room Receiver" transmit profile automatically knows to connect to "Living Room Receiver" receive profile's settings
- **Complexity**: Adds profile relationship management
- **Benefit**: Easier configuration for typical use cases

**ACTION REQUIRED**: User feedback

## 8. Implementation Phases

### Phase 1: Basic Receive Mode (MVP)
**Deliverables:**
- AudioPlaybackService implementation
- Receive profile data model
- Mode selector UI
- Basic TCP server
- AAC decoding and playback
- Profile management for receive

**Estimated Effort**: 2-3 days development

### Phase 2: Enhanced Receive Mode
**Deliverables:**
- Audio effects (bass/treble/volume)
- Configurable buffering
- Output device selection
- Visualization support
- Error handling and reconnection
- Audio focus management

**Estimated Effort**: 2-3 days development

### Phase 3: Polish & Testing
**Deliverables:**
- End-to-end testing
- Documentation updates
- Performance optimization
- Bug fixes

**Estimated Effort**: 1-2 days

**Total Estimated Effort**: 5-8 days

## 9. Success Criteria

### 9.1 Phase 1 Acceptance Criteria
- [ ] User can create and save receive profiles
- [ ] User can select receive mode
- [ ] Application opens TCP server on configured port
- [ ] Application accepts connection from transmit device
- [ ] Application successfully decodes AAC audio
- [ ] Application plays audio through device speakers
- [ ] Mode switching works correctly (stop required)

### 9.2 Phase 2 Acceptance Criteria
- [ ] Bass/treble controls work on received audio
- [ ] Volume control works on received audio
- [ ] Buffer size selection affects latency/stability
- [ ] Output device routing works (auto/speaker/headphones)
- [ ] Visualization reacts to received audio
- [ ] Automatic reconnection works on disconnect
- [ ] Audio focus management works with other apps

### 9.3 Quality Criteria
- [ ] No audio dropouts on stable network
- [ ] Latency within configured range
- [ ] No memory leaks during 24hr streaming
- [ ] CPU usage < 15% average
- [ ] Battery drain < transmit mode (no encoding overhead)

## 10. Risks and Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Network firewall blocks incoming connections | High | Medium | Document port forwarding requirements, support UPnP in future |
| Buffer tuning difficult to get right | Medium | High | Provide three preset options, extensive testing |
| Audio synchronization issues | Medium | Medium | Use Android AudioTrack timestamp APIs |
| Incompatible ADTS variants | High | Low | Strictly follow AAC-LC ADTS spec from transmit |
| Android audio focus conflicts | Medium | Medium | Implement proper focus request/abandon lifecycle |

## 11. Appendix

### 11.1 Related Documents
- F-DROID-SUBMISSION.md
- README.md
- airheadwaves-server-setup.md

### 11.2 References
- Android AudioTrack API: https://developer.android.com/reference/android/media/AudioTrack
- AAC ADTS Format: ISO/IEC 13818-7
- Android Audio Focus: https://developer.android.com/guide/topics/media-apps/audio-focus

### 11.3 Revision History
| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-10-18 | Initial | Initial requirements draft |
