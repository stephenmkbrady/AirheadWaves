#!/bin/bash

# Test script for AirheadWaves bidirectional streaming
# Runs two emulators and sets up networking between them

export ANDROID_HOME=/home/user/Android/Sdk
export PATH=$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH

AVD_NAME="Medium_Phone_API_36.1"

echo "Starting two emulator instances..."
echo ""

# Start emulator 1 (receiver)
echo "Starting Emulator 1 (Receiver) on port 5554..."
emulator -avd $AVD_NAME -port 5554 -no-snapshot-load &
EMULATOR1_PID=$!

sleep 5

# Start emulator 2 (transmitter)
echo "Starting Emulator 2 (Transmitter) on port 5556..."
emulator -avd $AVD_NAME -port 5556 -no-snapshot-load &
EMULATOR2_PID=$!

echo ""
echo "Waiting for emulators to boot..."
adb -s emulator-5554 wait-for-device
adb -s emulator-5556 wait-for-device

echo ""
echo "Emulators ready!"
echo ""
echo "=== Network Setup ==="
echo "Emulator 1 (emulator-5554): Receiver"
echo "  - Use IP: 10.0.2.2 (host machine)"
echo "  - Listen on port: 8888"
echo ""
echo "Emulator 2 (emulator-5556): Transmitter"
echo "  - Connect to: 10.0.2.2:8888 (will reach emulator-5554 via port forwarding)"
echo ""
echo "Setting up port forwarding..."
# Forward host port 8888 to emulator-5554's port 8888
adb -s emulator-5554 reverse tcp:8888 tcp:8888

echo ""
echo "=== Testing Instructions ==="
echo "1. On Emulator 1 (left): Start Receive mode, listen on port 8888"
echo "2. On Emulator 2 (right): Configure transmit profile:"
echo "   - IP: 10.0.2.2"
echo "   - Port: 8888"
echo "3. Start transmitting on Emulator 2"
echo ""
echo "Press Ctrl+C to stop both emulators"
echo ""

# Wait for interrupt
trap "kill $EMULATOR1_PID $EMULATOR2_PID 2>/dev/null; exit" INT TERM
wait
