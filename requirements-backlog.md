# AirheadWaves - Advanced Networking Requirements

## Document Information
- **Version**: 3.0
- **Date**: 2025-10-24
- **Status**: Future Planning
- **Previous Version**: [requirements-v2.0.md](requirements-v2.0.md)

## 1. Overview

### 1.1 Purpose
This document defines requirements for advanced networking features in AirheadWaves, focusing on complex network scenarios beyond LAN-based streaming.

### 1.2 Scope
Version 3.0 addresses networking challenges that are out of scope for v2.0:
- NAT traversal (streaming across networks/internet)
- Firewall traversal techniques
- Automatic network configuration (UPnP)
- Secure streaming (encryption)
- Dynamic DNS integration
- Quality of Service (QoS) management

### 1.3 Background
Version 2.0 assumes:
- All devices on same LAN
- User-configurable firewalls
- Manual IP address configuration
- Unencrypted TCP streams

Version 3.0 removes these limitations for enterprise and internet-based deployments.

## 2. Use Cases

### 2.1 Internet-Based Streaming
**Scenario**: User wants to stream from home device to office device over internet
- Source: Home Android device (behind home router NAT)
- Destination: Office Android device (behind corporate firewall)
- Challenge: Neither device has public IP, both behind NAT

### 2.2 Mobile Network Streaming
**Scenario**: User on cellular data wants to receive stream from home WiFi device
- Source: Home device on WiFi (192.168.x.x)
- Destination: Mobile device on 4G/5G (carrier NAT)
- Challenge: Carrier-grade NAT, dynamic IP changes

### 2.3 Public Streaming / Broadcasting
**Scenario**: User wants to broadcast audio to multiple listeners worldwide
- Source: Single transmitter device
- Destinations: Hundreds of receivers (unknown IPs)
- Challenge: Scalability, bandwidth, discovery

### 2.4 Secure Enterprise Streaming
**Scenario**: Corporate deployment with security requirements
- Requirement: Encrypted transmission (TLS)
- Requirement: Authentication/authorization
- Requirement: Audit logging

## 3. Functional Requirements

### 3.1 NAT Traversal

**REQ-NAT-001**: The application SHALL support NAT hole punching
- Use STUN (Session Traversal Utilities for NAT) for discovery
- Establish peer-to-peer connections through NAT
- Fallback to relay when P2P impossible

**REQ-NAT-002**: The application SHALL support TURN relay servers
- Use TURN (Traversal Using Relays around NAT) when direct connection fails
- Configurable TURN server addresses
- Support for authenticated TURN servers

**REQ-NAT-003**: The application SHALL support ICE (Interactive Connectivity Establishment)
- Automatic candidate gathering (host, server reflexive, relayed)
- Connectivity checks and candidate pair selection
- Dynamic adaptation to network changes

### 3.2 Automatic Configuration

**REQ-AUTO-001**: The application SHALL support UPnP/NAT-PMP
- Automatic port forwarding on compatible routers
- Discovery of external IP address
- Cleanup (remove port mappings on exit)

**REQ-AUTO-002**: The application SHALL support mDNS/Bonjour
- Automatic discovery of receivers on local network
- Advertise receiver availability
- Service browsing UI (show discovered receivers)

**REQ-AUTO-003**: The application SHALL support dynamic DNS
- Integration with DynDNS, No-IP, etc.
- Automatic IP update on change
- Custom domain name support

### 3.3 Security

**REQ-SEC-001**: The application SHALL support TLS encryption
- TLS 1.3 for all network traffic
- Certificate validation
- Support for self-signed certificates (with user confirmation)

**REQ-SEC-002**: The application SHALL support authentication
- Pre-shared key (PSK) authentication
- Public key authentication
- Integration with Android Keystore

**REQ-SEC-003**: The application SHALL support authorization
- Token-based access control
- Per-receiver permission management
- Revocation support

### 3.4 Quality of Service

**REQ-QOS-001**: The application SHALL monitor network quality
- Measure latency, jitter, packet loss
- Display network statistics to user
- Adaptive bitrate based on conditions

**REQ-QOS-002**: The application SHALL support adaptive streaming
- Dynamic bitrate adjustment (64-320 kbps)
- Sample rate downgrade on poor connections
- Automatic reconnection with lower quality

**REQ-QOS-003**: The application SHALL implement congestion control
- Detect network congestion
- Reduce transmission rate appropriately
- Fair bandwidth sharing with other traffic

### 3.5 Scalability

**REQ-SCALE-001**: The application SHALL support relay servers
- Dedicated server mode (Android device or separate server)
- One-to-many broadcasting through relay
- Reduce transmitter bandwidth requirements

**REQ-SCALE-002**: The application SHALL support multicast (IP multicast)
- Use multicast for efficient broadcasting on capable networks
- Automatic fallback to unicast when unsupported
- IGMP management

## 4. Network Topology Options

### 4.1 Peer-to-Peer (Current v2.0)
```
[Transmitter] --direct TCP--> [Receiver]
```
- Simple, low latency
- Limited to LAN or manual port forwarding

### 4.2 NAT Traversal (v3.0)
```
[Transmitter] <--STUN--> [STUN Server]
       |
       +--hole punched UDP--> [Receiver]
```
- Works across NAT
- Requires STUN/TURN infrastructure

### 4.3 Relay Server (v3.0)
```
[Transmitter] --> [Relay Server] --> [Receiver 1]
                                 --> [Receiver 2]
                                 --> [Receiver N]
```
- Scalable broadcasting
- Higher latency, requires server infrastructure

### 4.4 Hybrid (v3.0+)
```
[Transmitter] --P2P--> [Receiver 1] (same LAN)
             |
             +--Relay--> [Receiver 2] (internet)
```
- Optimal for mixed scenarios
- Complexity in connection management

## 5. Implementation Considerations

### 5.1 Protocol Changes
- **v2.0**: Raw TCP with ADTS-AAC
- **v3.0**: WebRTC, SRTP, or custom protocol over UDP

**Trade-offs**:
- TCP: Reliable, but high latency on poor networks
- UDP: Low latency, but requires application-level reliability
- WebRTC: Industry standard, but heavy library dependency

### 5.2 Server Infrastructure
Options for STUN/TURN/Relay servers:
1. **Public servers**: Use existing free/paid services
2. **Self-hosted**: User deploys on VPS/cloud
3. **Peer-hosted**: Android device acts as server
4. **Hybrid**: Fallback chain (P2P → Self-hosted → Public)

### 5.3 Library Dependencies
Potential libraries for v3.0:
- **libwebrtc**: Full WebRTC stack (large, ~10MB)
- **libjingle**: Subset of WebRTC
- **libnice**: ICE/STUN/TURN only (lightweight)
- **Custom**: Implement STUN/TURN from scratch

## 6. Open Questions

### 6.1 Protocol Selection
**QUESTION**: Should v3.0 use WebRTC or custom UDP protocol?
- **WebRTC Pros**: Industry standard, built-in NAT traversal, browser compatibility
- **WebRTC Cons**: Large library, complex API, overkill for audio-only
- **Custom Pros**: Lightweight, full control, simpler
- **Custom Cons**: More development, testing, standards compliance

### 6.2 Server Hosting Model
**QUESTION**: How should users access STUN/TURN/Relay servers?
- **Option A**: Bundle with app (list of public servers)
- **Option B**: User provides own server URLs
- **Option C**: Offer cloud service (subscription model)
- **Option D**: Hybrid approach

### 6.3 Backward Compatibility
**QUESTION**: Should v3.0 maintain v2.0 simple TCP mode?
- **Keep both**: More complexity, but easier migration
- **Deprecate**: Force upgrade, simpler codebase

## 7. Success Criteria

- [ ] Stream successfully from device behind NAT to internet receiver
- [ ] Automatic receiver discovery on LAN without manual IP entry
- [ ] TLS encryption with negligible latency impact
- [ ] Adaptive bitrate maintains quality on variable networks
- [ ] Relay server handles 10+ simultaneous receivers

## 8. Risks and Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| WebRTC library bloat increases APK size | Medium | High | Consider lightweight alternatives, modular build |
| TURN server costs for users | High | Medium | Offer P2P by default, relay as premium/self-hosted |
| NAT traversal success rate < 100% | High | Medium | Clear fallback instructions, support page |
| TLS encryption CPU overhead on old devices | Medium | Medium | Make encryption optional, adaptive |
| UDP firewall blocking | High | Low | TCP fallback mode |

## 9. Phasing

### Phase 3A: NAT Traversal (Estimated: 2-3 weeks)
- STUN/TURN support
- ICE implementation
- Fallback to relay

### Phase 3B: Auto-Configuration (Estimated: 1-2 weeks)
- UPnP port forwarding
- mDNS discovery
- Dynamic DNS integration

### Phase 3C: Security (Estimated: 1-2 weeks)
- TLS encryption
- Authentication/authorization
- Secure key storage

### Phase 3D: QoS and Scalability (Estimated: 2-3 weeks)
- Network quality monitoring
- Adaptive streaming
- Relay server implementation

**Total Estimated Effort**: 6-10 weeks

## 10. References

- RFC 8445: ICE (Interactive Connectivity Establishment)
- RFC 8489: STUN (Session Traversal Utilities for NAT)
- RFC 8656: TURN (Traversal Using Relays around NAT)
- RFC 6886: NAT-PMP (NAT Port Mapping Protocol)
- RFC 6762: mDNS (Multicast DNS)
- WebRTC Standards: https://www.w3.org/TR/webrtc/

## 11. Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-10-24 | Initial | Initial v3.0 requirements draft |
