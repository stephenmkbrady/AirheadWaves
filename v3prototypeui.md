# AirheadWaves v3.0 Prototype UI - Node Graph Editor

## Vision
Transmitter with node graph editor interface for configuring transmitter and receiver nodes. Transmitter becomes a CNC (Command & Control) node that pushes configuration to receivers.

---

## 1. Node Graph UI Framework

**Question:** How should the node graph interface work?

**Canvas-based drag-and-drop?**
Response: yes

**Draggable, zoomable, pannable nodes?**
Response: yes

**Visual feedback (animated connections, color-coded status, data flow)?**
Response: animated connections, color-coded status, data flow

**Android UI approach preference? (Custom Canvas, Jetpack Compose Canvas, library?)**
Response: Jetpack Compose Canvas I guess, I don't know what would be best for API compatibility 

---

## 2. CNC (Command & Control) Architecture

**Question:** How does the transmitter control receivers?

**Does transmitter send configuration to receivers over the network?**
Response: Yes, over LAN network via MQTT pub/sub. QR code pairing with randomly generated API key for security.

**When does config get pushed? (On connection? On change? Periodic sync?)**
Response: Real-time push on change via MQTT. Periodic heartbeat status updates from receivers every 2s.

**What protocol? (JSON over TCP? Separate control channel?)**
Response: MQTT over TLS (port 8883) for control channel. Separate audio stream via TCP (port 8888+).

**Do receivers become "headless" (only accept remote config)?**
Response: Receivers should be essentially headless when in remote config mode, but it should be possible to disable/enable this mode and have it work like it does in v2.0 of the app

**Can receivers override with local config?**
Response: only when remote config mode is disabled.

**What happens if receiver loses connection to CNC transmitter? (Keep last config? Revert to local? Stop?)**
Response: It should obviously try to reconnect to CNC transmitter until remote config mode is disabled or a paired transmitter connects to the receiver in remote config mode

---

## 3. Node Configuration

**Question:** What configuration lives on each node type?

**Transmitter Node Config:**
Response: the same as currently on transmitter node config

**Can you have multiple transmitter nodes in one graph?**
Response: yes, as part of this prototype I'd like to be able to have multiple transmitters to single receiver.

**Receiver Node Config (per node):**
Response: the same as currently on receiver node config

**Each receiver node = one physical receiver device?**
Response: yes

**Can you duplicate/clone receiver nodes easily?**
Response: yes

**Should there be node templates/presets?**
Response: yes, single transmitter to single receiver

---

## 4. Connections & Data Flow

**Question:** What do connection lines represent and show?

**Connection lines show which receivers get audio from which transmitter?**
Response: direction and if their status

**Should connections show status? (connected=green, disconnected=red, streaming=animated)**
Response: yes

**Can one receiver connect to multiple transmitters (audio mixing)?**
Response: yes

**Should connections show real-time stats? (bitrate, latency, packet loss)**
Response: this should be possible

**What flows through connections? (Just audio? Audio + config? Audio + config + control?)**
Response: audio only, I want to keep audio and config/control separate 

---

## 5. Node Discovery & Auto-Population

**Question:** How are receiver nodes created in the graph?

**Manual creation? (Click "Add Receiver", enter IP address)**
Response: yes, "Add" -> "Node" and then configure it as receiver or transmitter

**Auto-discovery? (Scan network via mDNS, auto-create nodes)**
Response: in a later phase

**Hybrid? (Show discovered receivers in sidebar, drag into graph)**
Response: in a later phase it should be possible to discover receivers/transmitters aka remote config devices.

**If physical receiver comes online, auto-appear in graph?**
Response: in the sidebar remote config devices should appear to be dragged onto the graph editor, in a later phase

**If you delete receiver node, disconnect from physical receiver immediately?**
Response: yes, deleting a remote config device will disconnect the device from the audio stream. 

---

## 6. Backwards Compatibility

**Question:** Do v2.0 receivers work with v3.0 CNC transmitter?

**Can "dumb" v2.0 receivers (local profiles only) still work?**
Response: yes, it should be backward compatible

**Does this require new receiver code to accept remote config?**
Response: yes

**Should v3.0 support both modes? (CNC mode + legacy local profile mode)**
Response: yes

---

## 7. Persistence & State

**Question:** How is graph state saved and applied?

**Is entire node graph saved as a profile? (e.g., "Home Theater Setup")**
Response: yes

**Can you have multiple graph configurations and switch between them?**
Response: yes

**Where does graph data save? (New GraphProfile model? Part of TransmitProfile?)**
Response: new GraphProfile model

**When you edit receiver node settings, does it immediately update physical receiver?**
Response: no

**Or do you need to "Apply"/"Deploy" changes?**
Response: yes

**Can you preview changes before pushing to receivers?**
Response: before apply is pressed it should be possible to look at the receiver and transmitter nodes to check their configurations

---

## 8. Scope for Prototype

**Question:** What features are must-have vs nice-to-have for the prototype?

### Must-Have Features
Response (list): multi transmitter to single receiver and everything specified above except for auto-discover of nodes


### Nice-to-Have Features
Response (list): remote config device autodiscovery as this will be handled when tackling more complex network topologies


### Explicitly Out of Scope
Response (list): everything not mentioned in this document


---

## 9. Additional Considerations

**Any other requirements, constraints, or ideas?**
Response: Try to keep changes to a minimal, removed code that is made unused.


---

## 10. Technical Clarifications

**Question:** Audio mixing behavior and technical implementation details.

### Audio Mixing Behavior
**When multiple transmitters connect to one receiver, how should audio be mixed?**

Options:
- A) Simple Mix: Add PCM samples together (may clip if loud)
- B) Normalized Mix: Average PCM samples (volume = 1/N transmitters)
- C) Weighted Mix: Each transmitter has a volume slider in receiver node config
- D) Ducking/Priority: One transmitter is "primary", others duck to 50% when primary is active

Response:
- Normalized Mix: Average PCM samples (volume = 1/N transmitters)
- Weighted Mix: Each transmitter has a volume slider in receiver node config
- when one slider is increased the others should decrease proportionally for a normalized mix

### Port Allocation for Multiple Transmitters
**If you have 2 transmitter nodes on the SAME physical device, how do they send audio?**

Options:
- A) Different Ports: Transmitter 1 on port 8888, Transmitter 2 on port 8889, etc.
- B) Different Audio Sources: Each transmitter captures different audio (Spotify vs YouTube)
- C) Same Audio, Different Processing: Both send same captured audio but with different effects

Response:
I don't want this, I want 1 transmitter node per 1 physical device. I'd like to be able to configure remote config devices as either transmitters or receivers.

### SSL/TLS Certificate Management
**For the MQTT over TLS control channel:**

Options:
- A) Self-signed certificates (generated per device, user must trust)
- B) No certificate validation initially (just encrypted, not authenticated)
- C) Certificate pinning (QR code includes certificate fingerprint)

Response:
- No certificate validation initially (just encrypted, not authenticated)

### Graph Persistence Format
**Should the GraphProfile save:**

Options:
- A) Just the config: Node positions, connections, settings (physical devices must be re-paired)
- B) Config + pairing data: Includes API keys, receiver IDs (can reconnect automatically)
- C) Config + runtime state: Includes connection status (reloads exactly as it was)

Response:
- B

### Node Deletion Behavior
**When you delete a receiver node from graph:**

Options:
- A) Immediately disconnect from audio stream AND unpair from CNC
- B) Just disconnect audio but stay paired (can re-add node easily)
- C) Provide both "Unpair" and "Disconnect" options

Response:
- C

---

## Summary

**Architecture Pattern:**
MQTT Pub/Sub for CNC control channel with QR code pairing and API key authentication.

**MQTT Topic Structure:**
```
airheadwaves/
  ├─ {receiver-id}/config      # Transmitter → Receiver: Configuration updates
  ├─ {receiver-id}/command     # Transmitter → Receiver: Control commands (start/stop/reconnect)
  └─ {receiver-id}/status      # Receiver → Transmitter: Heartbeat and connection state
```

**QR Code Pairing:**
```json
{
  "protocol": "mqtt",
  "broker_ip": "192.168.1.100",
  "broker_port": 8883,
  "api_key": "uuid-generated-key",
  "receiver_id": "living-room-speaker",
  "pairing_expiry": 1730000000
}
```

**Key Technologies:**
- Jetpack Compose Canvas for node graph UI
- Eclipse Paho MQTT Android client
- Mosquitto MQTT broker (embedded on transmitter device)
- MQTT over TLS for secure control channel
- AAC audio streaming over TCP (existing v2.0 implementation)

**Implementation Phases:**

### Phase 1: Data Models & MQTT Foundation (2-3 days)
- Create GraphProfile data model (nodes, connections, positions)
- Create Node data model (transmitter/receiver type, config, position)
- Create Connection data model (source, target, status)
- Add MQTT broker dependency (Eclipse Paho Android)
- Implement basic MQTT client service
- Add "Remote Config Mode" toggle to receiver
- Implement QR code generation/scanning

### Phase 2: Node Graph Canvas UI (3-4 days)
- Build Jetpack Compose Canvas for graph editor
- Implement pan, zoom, drag gestures
- Create visual node components (transmitter/receiver boxes)
- Implement connection line drawing (Bezier curves)
- Add node selection and property panel
- Implement "Add Node" button and node creation
- Add color-coded connection status (green/red/yellow)

### Phase 3: CNC Control Channel (2-3 days)
- Implement MQTT publish for config updates
- Implement MQTT subscribe for status updates
- Create config push on "Apply" button
- Implement heartbeat status from receivers (2s interval)
- Add pairing flow (QR code → MQTT handshake)
- Implement weighted audio mixing logic on receiver
- Add per-transmitter volume sliders in receiver node

### Phase 4: Graph Persistence & UI Polish (1-2 days)
- Implement GraphProfile save/load
- Add graph profile selector dropdown
- Implement node duplication/clone
- Add node templates (single transmitter → single receiver)
- Add "Unpair" vs "Disconnect" context menu
- Connection animation (data flow visualization)
- Real-time stats overlay on connections (optional)

### Phase 5: Integration & Testing (2-3 days)
- Test multi-transmitter to single receiver scenario
- Test pairing flow end-to-end
- Test config push and receiver update
- Test graph save/load with pairing data
- Fix bugs, optimize performance
- Update README with v3 prototype instructions

**Estimated Effort:** 10-15 days for complete prototype

**Dependencies to Add:**
- `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5` (MQTT client)
- `org.eclipse.paho:org.eclipse.paho.android.service:1.1.1` (Android MQTT service)
- `com.google.zxing:core:3.5.1` (QR code generation/scanning)
- Mosquitto MQTT broker (external dependency, runs on transmitter device)

