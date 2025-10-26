# AirheadWaves Testing Guide
## Physical Device + Emulator Setup

### Network Architecture

#### Android Emulator Networking Basics
The Android emulator uses a virtual router/firewall that isolates it from your development machine and the internet:
- **10.0.2.15**: The emulator's own IP (inside the virtual network)
- **10.0.2.2**: Special alias to your host machine (from emulator's perspective)
- External devices **cannot** directly reach 10.0.2.15

#### Testing Scenarios

### Scenario 1: Emulator (Transmit) → Physical Device (Receive) ✅ EASIER
This should work because:
- Physical device listens on its WiFi IP (e.g., 192.168.1.100:8888)
- Emulator can reach external IPs through the virtual router
- No special port forwarding needed

**Setup Steps:**
1. **Physical Device:**
   - Open AirheadWaves
   - Switch to **RECEIVE** mode
   - Tap "Start Receiving"
   - Note: Should show "Listening on port 8888"

2. **Find Physical Device IP:**
   - Tap the info icon (top-right) in AirheadWaves
   - Note the WiFi IP address (e.g., 192.168.1.100)
   - OR check Settings → WiFi → Your Network

3. **Emulator:**
   - Open AirheadWaves
   - Switch to **TRANSMIT** mode
   - Edit/create transmit profile:
     - Name: "To Physical"
     - IP Address: `<PHYSICAL_DEVICE_IP>` (from step 2)
     - Port: `8888`
   - Tap "Start Transmitting"

**Expected Result:** Should show "Connected to 1 destination(s)" and stream audio

**If No Audio:**
Check logs:
```bash
./check_logs.sh
```

Look for:
- "Connected to..." on emulator (transmit working)
- "Client connected from..." on physical device (receive working)
- Any decoder errors

---

### Scenario 2: Physical Device (Transmit) → Emulator (Receive) ⚠️ HARDER
This is more complex because external devices cannot directly reach the emulator's 10.0.2.15 IP.

#### Option A: Use adb forward (RECOMMENDED)

**IMPORTANT:** You need `adb forward`, NOT `adb reverse`!
- `adb reverse`: Emulator → Host (wrong direction for this scenario)
- `adb forward`: Host → Emulator (correct!)

**Setup Steps:**
1. **Start the setup script:**
   ```bash
   ./setup_physical_to_emulator.sh
   ```
   This sets up `adb forward tcp:8888 tcp:8888` and shows your host IP

2. **Emulator:**
   - Open AirheadWaves
   - Switch to **RECEIVE** mode
   - Tap "Start Receiving"
   - Should show "Listening on port 8888"

3. **Physical Device:**
   - Open AirheadWaves
   - Switch to **TRANSMIT** mode
   - Edit/create transmit profile:
     - Name: "To Emulator"
     - IP Address: `<HOST_IP>` (from script output, e.g., 192.168.178.24)
     - Port: `8888`
   - Tap "Start Transmitting"
   - Should show "Connected to 1 destination(s)"

4. **Emulator should show:**
   - "Connected: streaming from 127.0.0.1"

**Why This Works:**
- `adb forward tcp:8888 tcp:8888` forwards host:8888 → emulator:8888
- Physical device connects to host IP (192.168.178.x:8888)
- Host has port 8888 open (you configured this)
- adb forwards the connection to emulator
- Traffic flows: Physical → Host (WiFi) → Emulator (adb tunnel)

#### Option B: Use socat Relay (ADVANCED)

If Option A doesn't work, set up a TCP relay:

1. **Install socat:**
   ```bash
   sudo pacman -S socat  # Arch Linux
   # or sudo apt install socat  # Debian/Ubuntu
   ```

2. **Find your WiFi IP:**
   ```bash
   ip addr show | grep "inet.*wlan0"
   ```
   Example: 192.168.1.50

3. **Start socat relay:**
   ```bash
   # Terminal 1: Start relay
   socat TCP-LISTEN:8888,fork,bind=192.168.1.50 TCP:10.0.2.2:8888

   # Terminal 2: Start adb reverse
   export ANDROID_HOME=/home/user/Android/Sdk
   export PATH=$ANDROID_HOME/platform-tools:$PATH
   adb -s emulator-5554 reverse tcp:8888 tcp:8888
   ```

4. **Test:**
   - Emulator: Start Receiving
   - Physical: Transmit to 192.168.1.50:8888

**How It Works:**
```
Physical (WiFi) → Host socat (192.168.1.50:8888) → Host loopback (127.0.0.1:8888) → Emulator (10.0.2.15:8888)
```

---

### Troubleshooting

#### No Connection Established

**Check if receiver is listening:**
```bash
# For emulator
export ANDROID_HOME=/home/user/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH
adb -s emulator-5554 shell netstat -tlnp | grep 8888

# For physical device
adb -s R58N23Q207L shell netstat -tlnp | grep 8888
```

Should show:
```
tcp6  0  0 :::8888  :::*  LISTEN
```

**Check transmitter logs:**
```bash
adb -s <DEVICE> logcat -s AudioCaptureService:V
```

Look for:
- "Connected to X.X.X.X:8888" (success)
- "Failed to connect" (network issue)
- "Error: No destinations connected" (couldn't reach any)

**Check receiver logs:**
```bash
adb -s <DEVICE> logcat -s AudioPlaybackService:V
```

Look for:
- "Waiting for connection on port 8888" (listening)
- "Client connected from X.X.X.X" (connection established)
- "Stream parameters changed" (receiving data)

#### Connection Established But No Audio

**Possible causes:**

1. **Audio not playing on transmitter:**
   - Play music/video on transmitter device
   - Check audio level indicator in app

2. **Decoder issues:**
   - Check receiver logs for "Error decoding AAC frame"
   - May indicate sample rate mismatch

3. **Volume too low:**
   - Check receiver profile volume (default should be 1.0)
   - Check device volume

4. **Audio routing:**
   - Ensure device is not in silent/vibrate mode
   - Check if audio is routing to headphones/Bluetooth

**Check detailed logs:**
```bash
./check_logs.sh
```

#### Port Already in Use

If you see "Address already in use":
```bash
# Kill any process using port 8888
adb -s <DEVICE> shell "su -c 'kill -9 $(lsof -t -i:8888)'"

# Or restart the app
```

---

### Quick Reference

**Device IDs:**
- Physical: `R58N23Q207L`
- Emulator: `emulator-5554`

**Useful Commands:**
```bash
# Check connected devices
adb devices -l

# Check logs
./check_logs.sh

# Setup port forwarding (Physical → Emulator)
adb -s emulator-5554 forward tcp:8888 tcp:8888

# Setup port forwarding (Emulator → Host, for other scenarios)
adb -s emulator-5554 reverse tcp:8888 tcp:8888

# List active forwards
adb -s emulator-5554 forward --list
adb -s emulator-5554 reverse --list

# Remove forwards
adb -s emulator-5554 forward --remove tcp:8888
adb -s emulator-5554 forward --remove-all
adb -s emulator-5554 reverse --remove tcp:8888
adb -s emulator-5554 reverse --remove-all

# Check what's listening
adb -s emulator-5554 shell netstat -tlnp | grep 8888
adb -s R58N23Q207L shell netstat -tlnp | grep 8888

# Live log filtering
adb -s emulator-5554 logcat | grep -i airhead
adb -s R58N23Q207L logcat | grep -i airhead
```

---

### Success Indicators

**Transmitter:**
- Status shows "Connected to 1 destination(s)"
- Shows bitrate (e.g., "128 kbps")
- Audio level meter moves when audio plays

**Receiver:**
- Status shows "Connected: streaming from X.X.X.X"
- Audio plays through device speaker
- Audio level meter moves

---

### Current Known Limitations

1. **Physical → Emulator** requires socat relay (Android emulator NAT limitation)
2. **Multiple destinations** from physical device may have connection issues if destinations are unreachable
3. **Network changes** (WiFi reconnect) require restarting the stream
