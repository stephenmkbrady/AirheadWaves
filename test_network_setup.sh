#!/bin/bash

# Enhanced network testing script for AirheadWaves
# Tests bidirectional audio streaming between physical device and emulator

export ANDROID_HOME=/home/user/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH

echo "=== AirheadWaves Network Diagnostics ===="
echo ""

# Get host IP
HOST_IP=$(ip route get 1 2>/dev/null | awk '{print $7;exit}')
echo "Host Machine IP: $HOST_IP"
echo ""

# Check devices
echo "Connected Devices:"
adb devices -l
echo ""

PHYSICAL_DEVICE=$(adb devices | grep -v "emulator" | grep "device$" | awk '{print $1}')
EMULATOR_DEVICE=$(adb devices | grep "emulator" | awk '{print $1}')

if [ -z "$PHYSICAL_DEVICE" ]; then
    echo "ERROR: No physical device found!"
    exit 1
fi

if [ -z "$EMULATOR_DEVICE" ]; then
    echo "ERROR: No emulator found!"
    exit 1
fi

echo "Physical Device: $PHYSICAL_DEVICE"
echo "Emulator: $EMULATOR_DEVICE"
echo ""

# Get physical device IP
echo "Getting physical device IP address..."
PHYSICAL_IP=$(adb -s $PHYSICAL_DEVICE shell ip addr show wlan0 2>/dev/null | grep "inet " | awk '{print $2}' | cut -d/ -f1)
if [ -z "$PHYSICAL_IP" ]; then
    echo "WARNING: Could not detect physical device WiFi IP"
    PHYSICAL_IP="<Check device WiFi settings>"
else
    echo "Physical Device IP: $PHYSICAL_IP"
fi
echo ""

# Setup port forwarding for both scenarios
echo "=== Setting up port forwarding ==="
echo ""

# Scenario 1: Physical → Emulator
# Forward host:8888 to emulator:8888
echo "1. Physical → Emulator setup:"
echo "   Setting up: Physical:any → Host:8888 → Emulator:8888"
adb -s $EMULATOR_DEVICE reverse tcp:8888 tcp:8888
if [ $? -eq 0 ]; then
    echo "   ✓ Port forwarding active"
else
    echo "   ✗ Port forwarding failed"
fi
echo ""

# Check if host can reach emulator (we need socat or nc for proper forwarding)
if command -v socat &> /dev/null; then
    echo "   socat detected - can set up host listening socket"
    HAS_SOCAT=1
else
    echo "   NOTE: socat not installed. Physical device must connect directly to emulator IP."
    HAS_SOCAT=0
fi
echo ""

# Get emulator IP
EMULATOR_IP=$(adb -s $EMULATOR_DEVICE shell ip addr show eth0 2>/dev/null | grep "inet " | awk '{print $2}' | cut -d/ -f1)
if [ -z "$EMULATOR_IP" ]; then
    echo "   Emulator IP: 10.0.2.15 (standard emulator IP)"
    EMULATOR_IP="10.0.2.15"
else
    echo "   Emulator IP: $EMULATOR_IP"
fi
echo ""

# Scenario 2: Emulator → Physical
# Emulator can reach host via 10.0.2.2, and host can reach physical via WiFi
echo "2. Emulator → Physical setup:"
echo "   Emulator needs to connect directly to physical device IP"
echo "   Physical IP: $PHYSICAL_IP"
echo ""

echo "=== Testing Instructions ==="
echo ""
echo "TEST 1: Physical Device (Transmitter) → Emulator (Receiver)"
echo "-------------------------------------------------------"
echo "EMULATOR:"
echo "  1. Open AirheadWaves"
echo "  2. Switch to RECEIVE mode"
echo "  3. Select a receive profile (or use default)"
echo "  4. Tap 'Start Receiving'"
echo "  5. Should show 'Listening on port 8888'"
echo ""
echo "PHYSICAL DEVICE:"
echo "  1. Open AirheadWaves"
echo "  2. Switch to TRANSMIT mode"
echo "  3. Edit transmit profile:"
echo "     - IP Address: $HOST_IP"
echo "     - Port: 8888"
echo "  4. Tap 'Start Transmitting'"
echo ""
if [ $HAS_SOCAT -eq 0 ]; then
    echo "ALTERNATIVE (if above doesn't work):"
    echo "  - Try IP: $EMULATOR_IP instead of $HOST_IP"
    echo "  - May require emulator networking changes"
    echo ""
fi
echo ""

echo "TEST 2: Emulator (Transmitter) → Physical Device (Receiver)"
echo "-------------------------------------------------------"
echo "PHYSICAL DEVICE:"
echo "  1. Open AirheadWaves"
echo "  2. Switch to RECEIVE mode"
echo "  3. Tap 'Start Receiving'"
echo "  4. Note the listening port (should be 8888)"
echo ""
echo "EMULATOR:"
echo "  1. Open AirheadWaves"
echo "  2. Switch to TRANSMIT mode"
echo "  3. Edit transmit profile:"
echo "     - IP Address: $PHYSICAL_IP"
echo "     - Port: 8888"
echo "  4. Tap 'Start Transmitting'"
echo ""
echo ""

echo "=== Debugging Commands ==="
echo ""
echo "Check emulator reverse forwarding:"
echo "  adb -s $EMULATOR_DEVICE reverse --list"
echo ""
echo "Check what's listening on physical device:"
echo "  adb -s $PHYSICAL_DEVICE shell netstat -tlnp | grep 8888"
echo ""
echo "Check what's listening on emulator:"
echo "  adb -s $EMULATOR_DEVICE shell netstat -tlnp | grep 8888"
echo ""
echo "View emulator logs:"
echo "  adb -s $EMULATOR_DEVICE logcat | grep -i airhead"
echo ""
echo "View physical device logs:"
echo "  adb -s $PHYSICAL_DEVICE logcat | grep -i airhead"
echo ""

echo "Port forwarding is active. Press Ctrl+C when done testing."
echo ""

# Keep script running
trap "echo 'Cleaning up...'; adb -s $EMULATOR_DEVICE reverse --remove-all; exit" INT TERM
while true; do
    sleep 1
done
