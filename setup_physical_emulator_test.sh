#!/bin/bash

# Setup script for testing AirheadWaves with physical device + emulator
# Physical device = Transmitter
# Emulator = Receiver

export ANDROID_HOME=/home/user/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH

echo "=== AirheadWaves Physical + Emulator Test Setup ==="
echo ""

# Get host IP address
HOST_IP=$(ip route get 1 | awk '{print $7;exit}')
echo "Host Machine IP: $HOST_IP"
echo ""

# Check devices
echo "Checking connected devices..."
adb devices -l
echo ""

# Find physical device (not emulator)
PHYSICAL_DEVICE=$(adb devices | grep -v "emulator" | grep "device$" | awk '{print $1}')
EMULATOR_DEVICE=$(adb devices | grep "emulator" | awk '{print $1}')

if [ -z "$PHYSICAL_DEVICE" ]; then
    echo "ERROR: No physical device found!"
    echo "Please connect your physical Android device via USB"
    echo "Make sure USB debugging is enabled"
    exit 1
fi

if [ -z "$EMULATOR_DEVICE" ]; then
    echo "ERROR: No emulator found!"
    echo "Please start an emulator first"
    exit 1
fi

echo "Physical Device: $PHYSICAL_DEVICE"
echo "Emulator: $EMULATOR_DEVICE"
echo ""

# Set up port forwarding for emulator to act as receiver
echo "Setting up port forwarding..."
echo "  Forwarding host:8888 -> emulator:8888"
adb -s $EMULATOR_DEVICE reverse tcp:8888 tcp:8888
echo ""

echo "=== Testing Configuration ==="
echo ""
echo "EMULATOR (Receiver):"
echo "  1. Install app: adb -s $EMULATOR_DEVICE install -r app/build/outputs/apk/debug/app-debug.apk"
echo "  2. Switch to Receive mode"
echo "  3. Start receiving on port 8888"
echo "  4. Should show 'Listening on port 8888'"
echo ""
echo "PHYSICAL DEVICE (Transmitter):"
echo "  1. Install app: adb -s $PHYSICAL_DEVICE install -r app/build/outputs/apk/debug/app-debug.apk"
echo "  2. Switch to Transmit mode"
echo "  3. Create/edit transmit profile with:"
echo "     - IP Address: $HOST_IP"
echo "     - Port: 8888"
echo "  4. Start transmitting"
echo ""
echo "Alternative: Emulator as Transmitter, Physical as Receiver:"
echo "  - Physical device: Start receiver on port 8888"
echo "  - Find physical device IP (check device WiFi settings or tap info icon in app)"
echo "  - Emulator: Configure transmit to physical device IP:8888"
echo ""
echo "Port forwarding is active. Press Ctrl+C when done testing."
echo ""

# Keep script running
trap "echo 'Cleaning up...'; adb -s $EMULATOR_DEVICE reverse --remove tcp:8888; exit" INT TERM
echo "Waiting... (Ctrl+C to stop)"
while true; do
    sleep 1
done
