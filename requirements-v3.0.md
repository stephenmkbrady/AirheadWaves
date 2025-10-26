# AirheadWaves v3.0 Requirements

## Overview
Version 3.0 focuses on advanced audio routing capabilities, multi-device support, and enhanced streaming features.

---

## Phase 1: Advanced Multi-Device Audio Output

### REQ-MULTI-001: Multi-Device Selection UI
**Priority**: High
**Description**: Allow users to select multiple audio output devices simultaneously in receive profiles.

**Requirements**:
- Display all available audio output devices in receive profile settings
- Support multi-select checkbox interface for device selection
- Show device details:
  - Device name/product name
  - Device type (speaker, wired headphones, bluetooth, USB, etc.)
  - Connection state
- Real-time device list updates when devices connect/disconnect
- Minimum Android API: 13 (Tiramisu) for advanced routing
  - Fallback behavior for Android 9-12: single device only with warning message

**UI Components**:
- "Output Devices" section in receive profile editor
- Scrollable list with checkboxes for each available device
- Device icon indicators (speaker, headphones, bluetooth, etc.)
- "Select All" / "Deselect All" quick actions
- Warning when no devices selected (must select at least one)

**Data Model Changes**:
```kotlin
// Profiles.kt
@Serializable
data class ReceiveProfile(
    // ... existing fields ...
    val outputDevices: List<String> = listOf("AUTO"),  // Device IDs or "AUTO"
    val enableMultiDevice: Boolean = false
)
```

### REQ-MULTI-002: Simultaneous Multi-Device Playback
**Priority**: High
**Description**: Play decoded audio stream to multiple devices simultaneously with proper synchronization.

**Requirements**:
- Create separate AudioTrack instance for each selected device
- Route each AudioTrack to its designated output device
- Duplicate decoded PCM data to all active AudioTrack instances
- Implement ring buffer for decoded audio to prevent blocking
- Handle device-specific buffer sizes and latency characteristics

**Synchronization**:
- Align playback start time across all devices
- Monitor playback position of each AudioTrack
- Detect and log synchronization drift (warning if >50ms difference)
- Use timestamp-based synchronization with `AudioTrack.write()` timestamps

**Performance Considerations**:
- Efficient PCM data duplication (avoid unnecessary copies)
- Thread-safe buffer access for concurrent writes
- Monitor CPU usage and warn if exceeds threshold (>40% sustained)

### REQ-MULTI-003: Per-Device Audio Configuration
**Priority**: Medium
**Description**: Allow independent audio settings per output device.

**Requirements**:
- Per-device volume control (0.0 - 2.0)
- Per-device bass/treble equalization
- Per-device latency compensation (0-500ms)
- Visual indicator showing which device is currently playing

**UI Components**:
- Expandable settings per device in receive profile editor
- Mini-sliders for volume/bass/treble per device
- Latency offset slider with millisecond display
- "Copy settings from..." dropdown to duplicate settings across devices

**Data Model Changes**:
```kotlin
@Serializable
data class DeviceAudioSettings(
    val deviceId: String,
    val volume: Float = 1.0f,
    val bass: Float = 0f,
    val treble: Float = 0f,
    val latencyOffsetMs: Int = 0,
    val enabled: Boolean = true
)

@Serializable
data class ReceiveProfile(
    // ... existing fields ...
    val deviceSettings: Map<String, DeviceAudioSettings> = emptyMap()
)
```

### REQ-MULTI-004: Device-Specific Error Handling
**Priority**: High
**Description**: Handle errors independently for each device without stopping entire stream.

**Requirements**:
- Continue streaming to functioning devices if one device fails
- Show Toast notification when device fails: "Device [name] disconnected, continuing on remaining devices"
- Log per-device errors with device identification
- Automatically remove failed device from active playback list
- Attempt to re-add device if it reconnects (with auto-reconnect enabled)
- Stop entire stream only if ALL devices fail

**Error Scenarios**:
- Device disconnected (bluetooth out of range, headphones unplugged)
- AudioTrack write failure
- Device buffer underrun
- Permission denied for specific device

**Status Display**:
- Show active device count in status (e.g., "Streaming to 2/3 devices")
- List failed devices in error message
- Visual indicator (red/yellow/green) for each device state

### REQ-MULTI-005: Device Capability Detection
**Priority**: Medium
**Description**: Detect and respect device-specific audio capabilities.

**Requirements**:
- Query each device's supported sample rates
- Query each device's supported channel configurations
- Query each device's supported encodings
- Automatically adjust per-device configuration to match capabilities
- Warn user if selected device doesn't support stream parameters
- Fallback to resampling if device requires different sample rate

**Device Compatibility Matrix**:
- Show supported formats for each device in UI (optional info dialog)
- Highlight incompatible devices in red with explanation
- Suggest alternative configurations for incompatible devices

### REQ-MULTI-006: Multi-Device Performance Monitoring
**Priority**: Low
**Description**: Monitor and display performance metrics for multi-device playback.

**Requirements**:
- Track per-device buffer utilization
- Track per-device underrun count
- Track synchronization drift between devices
- Display performance stats in debug overlay (optional)
- Log performance warnings when issues detected

**Metrics Displayed**:
- CPU usage percentage
- Memory usage for audio buffers
- Per-device latency measurements
- Per-device buffer health (green/yellow/red indicator)

---

## Phase 2: Stream Encryption and Security

### Current Security Vulnerability
**CRITICAL**: As of v2.0, all audio is transmitted over **unencrypted TCP sockets**. Anyone on the same network can:
- Intercept and decode audio streams using Wireshark or similar tools
- Man-in-the-middle attack to modify or inject audio
- Impersonate receivers or transmitters
- Capture sensitive audio conversations

The only protection is optional IP whitelisting, which:
- Does NOT encrypt data
- Can be bypassed via IP spoofing
- Provides NO eavesdropping protection

### REQ-SEC-001: TLS/SSL Encryption
**Priority**: Critical
**Description**: Implement industry-standard TLS encryption for all audio streams.

**Requirements**:
- Migrate from plain TCP to TLS/SSL sockets
- Use TLS 1.3 (minimum TLS 1.2 for compatibility)
- Support both server certificate validation and self-signed certificates
- Certificate pinning option for advanced users
- Automatic certificate generation for receivers
- Certificate trust UI for first-time connections

**Implementation**:
```java
// Use SSLSocket instead of Socket
SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
SSLSocket socket = (SSLSocket) factory.createSocket(host, port);

// Use SSLServerSocket instead of ServerSocket
SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(port);
```

**Certificate Management**:
- Auto-generate self-signed certificates for receivers on first launch
- Store certificates in Android Keystore
- Export certificate option for manual trust
- QR code certificate sharing between devices
- Certificate expiration warnings (warn 30 days before expiry)
- Certificate renewal automation

**UI Components**:
- "Encryption" section in both transmit and receive profiles
- Toggle for "Require TLS Encryption" (default: ON for v3.0)
- Certificate trust prompt on first connection with fingerprint display
- "View Certificate" button showing details (issuer, expiry, fingerprint)
- "Trust All Certificates" option (discouraged, show security warning)

**Data Model Changes**:
```kotlin
@Serializable
data class TransmitProfile(
    // ... existing fields ...
    val requireEncryption: Boolean = true,
    val allowSelfSignedCerts: Boolean = true,
    val trustedCertificates: List<String> = emptyList()  // Certificate fingerprints
)

@Serializable
data class ReceiveProfile(
    // ... existing fields ...
    val requireEncryption: Boolean = true,
    val certificateId: String? = null  // Reference to keystore certificate
)
```

**Performance Impact**:
- Target: <5ms additional latency for TLS handshake (one-time per connection)
- Target: <2% CPU overhead for encryption/decryption
- Monitor and log if encryption overhead exceeds targets

### REQ-SEC-002: Authentication and Authorization
**Priority**: High
**Description**: Verify identity of transmitters and receivers before allowing connections.

**Requirements**:
- Password/passphrase authentication for transmitter-receiver pairing
- Support for pre-shared keys (PSK) as alternative to certificates
- Optional two-factor authentication (TOTP)
- Device pairing workflow similar to Bluetooth
- Persistent device trust list

**Authentication Methods**:

1. **Password-Based Authentication**:
   - Receiver sets passphrase in profile settings
   - Transmitter must provide matching passphrase
   - Passwords hashed with PBKDF2 or Argon2
   - Minimum 8 characters, recommend 12+
   - Password strength indicator in UI

2. **Pre-Shared Key (PSK)**:
   - Generate random 256-bit key
   - Share via QR code or NFC
   - Store securely in Android Keystore
   - Display key in hex format for manual entry

3. **Device Pairing**:
   - Receiver shows pairing code (6 digits) on screen
   - Transmitter enters pairing code within 60 seconds
   - Successful pairing saves device ID to trust list
   - Option to unpair devices from settings

**UI Components**:
- "Security" tab in profile settings
- Authentication method dropdown (Password / PSK / Pairing Code)
- Password field with show/hide toggle
- "Generate PSK" button with QR code display
- "Paired Devices" list with unpair buttons
- "Require Authentication" toggle (default: ON)

**Data Model Changes**:
```kotlin
@Serializable
enum class AuthMethod {
    NONE,
    PASSWORD,
    PRE_SHARED_KEY,
    PAIRING_CODE
}

@Serializable
data class ReceiveProfile(
    // ... existing fields ...
    val authMethod: AuthMethod = AuthMethod.PASSWORD,
    val passwordHash: String? = null,  // Never store plaintext
    val pskId: String? = null,  // Reference to keystore PSK
    val trustedDeviceIds: List<String> = emptyList()
)

@Serializable
data class TransmitProfile(
    // ... existing fields ...
    val authMethod: AuthMethod = AuthMethod.PASSWORD,
    val password: String? = null,  // Cleared after successful auth
    val pskId: String? = null
)
```

### REQ-SEC-003: End-to-End Encryption (Optional)
**Priority**: Medium
**Description**: Encrypt audio data before transmission for maximum security.

**Requirements**:
- AES-256-GCM encryption for audio payloads
- Separate encryption keys per stream session
- Key exchange via Diffie-Hellman (ECDH)
- Perfect forward secrecy (new keys per session)
- No key material stored after session ends

**Use Cases**:
- Public WiFi streaming
- Enterprise/corporate networks with monitoring
- Privacy-sensitive applications
- Multi-hop routing (future feature)

**Implementation Notes**:
- Encrypt PCM data before AAC encoding, OR
- Encrypt AAC frames after encoding (more efficient)
- Include authentication tag to detect tampering
- Padding for fixed-size packets to prevent traffic analysis

**Performance Considerations**:
- Target: <3% CPU overhead for encryption
- Target: <5ms latency impact
- May require hardware crypto acceleration
- Make this feature optional (OFF by default)

**UI Components**:
- "Advanced Security" section (collapsed by default)
- "Enable End-to-End Encryption" toggle
- Warning: "May increase latency by 5-10ms"
- Encryption status indicator during streaming

### REQ-SEC-004: Security Indicators and Warnings
**Priority**: Medium
**Description**: Clearly communicate security status to users.

**Requirements**:
- Lock icon indicator when encryption active
- Warning icon when encryption disabled
- "Insecure Connection" banner for unencrypted streams
- Security status in notification
- Log security events (auth failures, cert issues)

**Visual Indicators**:
- 🔒 Green lock icon = Encrypted + Authenticated
- 🔓 Yellow lock icon = Encrypted, self-signed cert
- ⚠️ Orange warning = Encryption disabled
- ❌ Red X = Authentication failed

**Security Warnings**:
- "This stream is not encrypted. Anyone on your network can intercept the audio."
- "Certificate verification failed. This connection may not be secure."
- "Authentication disabled. Any device can connect to this receiver."
- "Using self-signed certificate. Verify fingerprint: [ABC123...]"

**Notifications**:
- Show lock icon in streaming notification
- Update notification text to include security status
- Example: "🔒 Streaming audio securely to 192.168.1.100"

### REQ-SEC-005: Security Settings and Policies
**Priority**: Low
**Description**: Advanced security configuration for power users.

**Requirements**:
- Minimum TLS version setting (1.2 or 1.3)
- Cipher suite selection (default: strong ciphers only)
- Certificate validation mode (strict / permissive / disabled)
- Authentication timeout configuration
- Failed authentication lockout (3 strikes = 5 minute ban)
- Security audit log export

**Security Policies**:
- "Strict Mode": Requires TLS 1.3, auth, and certificate validation
- "Balanced Mode": Requires TLS 1.2+, auth, allows self-signed certs (default)
- "Permissive Mode": Optional encryption, optional auth (for testing only)
- "Insecure Mode": No encryption or auth (show big warning, require confirmation)

**UI Components**:
- "Advanced Security" settings screen
- Security policy presets dropdown
- Individual setting toggles for customization
- "Restore Defaults" button
- Warning dialog when selecting insecure options

**Data Model Changes**:
```kotlin
@Serializable
enum class SecurityPolicy {
    STRICT,
    BALANCED,
    PERMISSIVE,
    INSECURE
}

@Serializable
data class SecuritySettings(
    val policy: SecurityPolicy = SecurityPolicy.BALANCED,
    val minTlsVersion: String = "1.2",
    val allowedCipherSuites: List<String> = emptyList(),  // Empty = use defaults
    val strictCertValidation: Boolean = false,
    val authTimeoutSeconds: Int = 30,
    val enableFailedAuthLockout: Boolean = true,
    val lockoutAttempts: Int = 3,
    val lockoutDurationMinutes: Int = 5
)
```

### REQ-SEC-006: Secure Key Storage
**Priority**: High
**Description**: Store cryptographic keys and sensitive data securely.

**Requirements**:
- Use Android Keystore System for all keys
- Hardware-backed encryption when available
- Biometric authentication to access keys (optional)
- Never store plaintext passwords or keys in SharedPreferences
- Secure deletion of keys when profiles deleted
- Key backup and restore with password protection

**Android Keystore Integration**:
```java
KeyGenerator keyGenerator = KeyGenerator.getInstance(
    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
    "airheadwaves_key_" + profileId,
    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setUserAuthenticationRequired(false)  // Or true for biometric
    .build();

keyGenerator.init(keyGenParameterSpec);
keyGenerator.generateKey();
```

**Biometric Authentication** (Optional):
- Require fingerprint/face unlock to start streaming
- Require biometric to view/edit security settings
- Fallback to device PIN/password
- UI: "Unlock to stream" prompt with biometric icon

---

## Phase 3: Enhanced Streaming Features (TBD)

### Placeholder for Future Requirements
- Multi-transmitter support (mixing multiple sources)
- Advanced codec support (Opus, FLAC)
- Network optimization (adaptive bitrate)
- Stream recording/playback

---

## Technical Constraints

### Android API Requirements
- **Minimum API**: 29 (Android 10) for basic multi-device
- **Recommended API**: 33 (Android 13) for advanced routing features
- Graceful degradation for older Android versions

### Performance Targets
- Maximum CPU overhead: +15% vs single device
- Maximum memory overhead: +20MB per additional device
- Maximum latency increase: +10ms for multi-device coordination
- Synchronization drift tolerance: <50ms between devices

### Battery Impact
- Target: <10% additional battery drain for 2-device playback
- Warning if battery drain exceeds 15% additional

---

## Testing Requirements

### Multi-Device Scenarios
1. Speaker + Wired Headphones simultaneous playback
2. Speaker + Bluetooth A2DP simultaneous playback
3. Multiple Bluetooth devices (if supported by Android)
4. USB audio + built-in speaker
5. Device disconnect during playback (unplug headphones)
6. Device reconnect during playback (plug headphones back in)
7. All devices disconnect (verify stream stops gracefully)

### Performance Testing
1. Measure CPU usage with 1, 2, 3+ devices
2. Measure memory usage with multiple devices
3. Verify synchronization drift stays <50ms
4. Test sustained playback (1+ hour) for stability
5. Test rapid device switching (connect/disconnect)

### Compatibility Testing
- Test on Android 10, 11, 12, 13, 14+
- Test with different device types (speaker, headphones, bluetooth, USB)
- Test with devices having different buffer sizes
- Test with devices having different sample rate support

### Security Testing

#### Encryption Testing
1. **TLS Handshake**: Verify successful TLS connection establishment
2. **Certificate Validation**: Test with valid, self-signed, and invalid certificates
3. **Cipher Suite Support**: Verify strong ciphers are used (AES-256, etc.)
4. **TLS Version**: Ensure TLS 1.2+ only, reject TLS 1.0/1.1
5. **Performance Impact**: Measure encryption overhead (target <2% CPU)
6. **Certificate Expiry**: Test behavior with expired certificates

#### Authentication Testing
1. **Password Auth**: Test correct/incorrect passwords
2. **Password Hashing**: Verify passwords never stored in plaintext
3. **PSK Generation**: Test key generation and QR code sharing
4. **Pairing Code**: Test pairing workflow and timeout
5. **Failed Auth Lockout**: Verify lockout after 3 failed attempts
6. **Device Trust List**: Test adding/removing trusted devices

#### Security Attack Scenarios
1. **Man-in-the-Middle**: Verify MITM attacks fail with certificate validation
2. **Replay Attack**: Ensure old auth tokens cannot be reused
3. **Eavesdropping**: Confirm encrypted traffic cannot be decoded with Wireshark
4. **Brute Force**: Test rate limiting on authentication attempts
5. **Certificate Spoofing**: Verify certificate pinning prevents spoofing
6. **Downgrade Attack**: Ensure client cannot be forced to use weak ciphers

#### Penetration Testing (Recommended)
- Hire security professional to audit implementation
- Use automated security scanning tools (OWASP ZAP, etc.)
- Test against OWASP Mobile Top 10 vulnerabilities
- Verify compliance with Android security best practices

#### Security Audit Checklist
- [ ] No plaintext passwords in logs
- [ ] No sensitive data in SharedPreferences
- [ ] All keys stored in Android Keystore
- [ ] TLS certificate validation enabled by default
- [ ] Strong cipher suites enforced
- [ ] Authentication enabled by default for v3.0
- [ ] Security warnings shown when encryption disabled
- [ ] Failed authentication attempts logged
- [ ] Certificate fingerprints displayed for user verification
- [ ] Secure random number generation for keys (SecureRandom)

---

## Future Considerations (Post-v3.0)

- **Zone-based audio**: Different audio streams to different device groups
- **Spatial audio**: Position-based audio mixing for multi-room setups
- **Device profiles**: Save preferred device combinations as presets
- **Smart routing**: AI-based device selection based on use case
- **Multi-source mixing**: Combine multiple transmitters into single output

---

## Notes

- Multi-device support requires significant testing on diverse hardware
- Battery impact needs real-world measurement and optimization
- Synchronization may be challenging with high-latency Bluetooth devices
- Consider making multi-device an "Advanced" or "Experimental" feature initially
