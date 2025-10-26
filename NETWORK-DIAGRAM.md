# AirheadWaves Network Architecture

## Understanding adb forward vs adb reverse

### `adb forward` - Host → Emulator
```
┌─────────────────┐         ┌──────────────┐         ┌─────────────────┐
│ Physical Device │         │ Host Machine │         │    Emulator     │
│  (Transmitter)  │ ─WiFi─> │ 192.168.x.x  │ ─adb──> │   10.0.2.15     │
│                 │         │   :8888      │ tunnel  │     :8888       │
│                 │         │              │         │   (Receiver)    │
└─────────────────┘         └──────────────┘         └─────────────────┘

Command: adb forward tcp:8888 tcp:8888
Means: "Forward connections TO host:8888 INTO emulator:8888"
```

### `adb reverse` - Emulator → Host
```
┌─────────────────┐         ┌──────────────┐         ┌─────────────────┐
│    Emulator     │         │ Host Machine │         │ Physical Device │
│  (Transmitter)  │ <─adb── │ 192.168.x.x  │ <─WiFi─ │   (Receiver)    │
│   10.0.2.15     │ tunnel  │   :8888      │         │     :8888       │
│     :8888       │         │              │         │                 │
└─────────────────┘         └──────────────┘         └─────────────────┘

Command: adb reverse tcp:8888 tcp:8888
Means: "Forward connections FROM emulator:8888 OUT TO host:8888"
```

## Two Working Test Scenarios

### Scenario 1: Emulator → Physical (EASY) ✅
```
┌─────────────────────────────────────────────────────────────┐
│                                                               │
│  ┌─────────────────┐                  ┌─────────────────┐   │
│  │   Emulator      │                  │    Physical     │   │
│  │ (Transmitter)   │ ────WiFi────────>│   (Receiver)    │   │
│  │                 │  Direct connect  │   192.168.x.y   │   │
│  │  10.0.2.2 →     │  to physical IP  │     :8888       │   │
│  │  192.168.x.y    │                  │                 │   │
│  └─────────────────┘                  └─────────────────┘   │
│                                                               │
│  Emulator uses special 10.0.2.2 to reach host network        │
│  Then routes to physical device via host's WiFi              │
│                                                               │
└─────────────────────────────────────────────────────────────┘

No special setup needed - just use physical device's real IP!
```

### Scenario 2: Physical → Emulator (HARDER) ⚠️
```
┌────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ┌─────────────┐      ┌──────────────┐      ┌─────────────┐   │
│  │  Physical   │      │     Host     │      │  Emulator   │   │
│  │(Transmitter)│──WiFi→│   Machine   │─adb──│ (Receiver)  │   │
│  │             │      │ 192.168.x.x  │tunnel│  10.0.2.15  │   │
│  │ Connect to: │      │              │      │             │   │
│  │192.168.x.x  │      │ Port 8888    │  →   │ Port 8888   │   │
│  │   :8888     │      │   OPEN       │      │             │   │
│  └─────────────┘      └──────────────┘      └─────────────┘   │
│                                                                  │
│  Requires: adb forward tcp:8888 tcp:8888                        │
│  Requires: Host firewall allows port 8888                       │
│                                                                  │
└────────────────────────────────────────────────────────────────┘

Setup script: ./setup_physical_to_emulator.sh
```

## Port Forwarding Rules Summary

| Scenario | Command | Direction | Purpose |
|----------|---------|-----------|---------|
| Physical → Emulator | `adb forward tcp:8888 tcp:8888` | Host → Emulator | Forward incoming connections on host to emulator |
| Emulator → Physical | None needed | Direct | Emulator can reach external IPs via 10.0.2.2 |
| Emulator → Host Service | `adb reverse tcp:8888 tcp:8888` | Emulator → Host | Forward outgoing connections from emulator to host |

## Firewall Requirements

### For Physical → Emulator:
- **Host machine**: Port 8888 TCP must be open
  ```bash
  sudo firewall-cmd --add-port=8888/tcp --permanent
  sudo firewall-cmd --reload
  # OR
  sudo iptables -A INPUT -p tcp --dport 8888 -j ACCEPT
  ```

### For Emulator → Physical:
- **Physical device**: No firewall config needed (Android has no built-in firewall)
- **Host machine**: No specific rules needed (outgoing traffic allowed by default)

## Troubleshooting

### "Error: No destinations connected"
This means the transmitter cannot reach the receiver.

**Check:**
1. Is receiver actually listening?
   ```bash
   adb -s <DEVICE> shell netstat -tlnp | grep 8888
   ```
   Should show: `tcp6  0  0 :::8888  :::*  LISTEN`

2. Is firewall blocking? (if host is receiver)
   ```bash
   sudo ss -tlnp | grep 8888
   ```

3. Is port forwarding active? (if emulator is receiver)
   ```bash
   adb -s emulator-5554 forward --list
   ```
   Should show: `emulator-5554 tcp:8888 tcp:8888`

4. Can you reach the receiver with telnet?
   ```bash
   telnet <RECEIVER_IP> 8888
   # Should connect successfully
   ```

### "Connected" but no audio
This means connection works but streaming has issues.

**Check logs:**
```bash
./check_logs.sh
```

Look for:
- Transmitter: "Connected to 1 destination(s)", "XXX kbps"
- Receiver: "Stream parameters changed: 44100Hz, 2ch"
- Receiver: "Error decoding AAC frame" (bad sign)

## Your Current Working Setup

Based on your tests:

✅ **Working:** Emulator (TX) → Physical (RX)
- Emulator shows: "Connected to 1 destination(s), 128 kbps"
- Physical shows: "Connected: streaming from 192.168.178.24"
- Audio plays: [needs testing]

❌ **Not Working:** Physical (TX) → Emulator (RX)
- Physical shows: "Error: No destinations connected"
- Reason: Was using `adb reverse` (wrong direction)
- Solution: Use `adb forward` with `./setup_physical_to_emulator.sh`
