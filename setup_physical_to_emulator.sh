#!/bin/bash

# Setup script for Physical Device (Transmitter) → Emulator (Receiver)
# This is the tricky direction that requires proper port forwarding

export ANDROID_HOME=/home/user/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH

echo "=== Physical Device → Emulator Setup ==="
echo ""

PHYSICAL_DEVICE="R58N23Q207L"
EMULATOR_DEVICE="emulator-5554"

# Get host IP
HOST_IP=$(ip route get 1 | awk '{print $7;exit}')
echo "Host Machine IP: $HOST_IP"
echo ""

# Check devices
echo "Checking devices..."
if ! adb -s $PHYSICAL_DEVICE get-state >/dev/null 2>&1; then
    echo "ERROR: Physical device not found!"
    exit 1
fi

if ! adb -s $EMULATOR_DEVICE get-state >/dev/null 2>&1; then
    echo "ERROR: Emulator not found!"
    exit 1
fi

echo "✓ Physical device: $PHYSICAL_DEVICE"
echo "✓ Emulator: $EMULATOR_DEVICE"
echo ""

# Setup port forwarding
# This forwards: host:8888 → emulator:8888
echo "Setting up port forwarding..."
echo "  host:8888 → emulator:8888"

# Remove any existing forwards
adb -s $EMULATOR_DEVICE forward --remove tcp:8888 2>/dev/null

# Add the forward
adb -s $EMULATOR_DEVICE forward tcp:8888 tcp:8888

if [ $? -eq 0 ]; then
    echo "✓ Port forwarding active"
else
    echo "✗ Port forwarding failed"
    exit 1
fi

echo ""
echo "Checking active forwards:"
adb -s $EMULATOR_DEVICE forward --list
echo ""

echo "=== Testing Instructions ==="
echo ""
echo "EMULATOR (Receiver):"
echo "  1. Open AirheadWaves"
echo "  2. Switch to RECEIVE mode"
echo "  3. Tap 'Start Receiving'"
echo "  4. Should show: 'Listening on port 8888'"
echo ""
echo "PHYSICAL DEVICE (Transmitter):"
echo "  1. Open AirheadWaves"
echo "  2. Switch to TRANSMIT mode"
echo "  3. Edit/create transmit profile:"
echo "     - IP Address: $HOST_IP"
echo "     - Port: 8888"
echo "  4. Tap 'Start Transmitting'"
echo "  5. Should show: 'Connected to 1 destination(s)' with bitrate"
echo ""
echo "EMULATOR should show:"
echo "  'Connected: streaming from 127.0.0.1'"
echo ""
echo ""
echo "How this works:"
echo "  Physical device → Host ($HOST_IP:8888) → Emulator (localhost:8888)"
echo ""
echo "To stop:"
echo "  Press Ctrl+C or run: adb -s $EMULATOR_DEVICE forward --remove tcp:8888"
echo ""

# Keep script running
trap "echo ''; echo 'Cleaning up...'; adb -s $EMULATOR_DEVICE forward --remove tcp:8888; exit" INT TERM
echo "Port forwarding active. Press Ctrl+C to stop."
while true; do
    sleep 1
done
