#!/bin/bash

export ANDROID_HOME=/home/user/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH

TRANSMITTER="R58N23Q207L"

# Get current PID
PID=$(adb -s $TRANSMITTER shell "ps | grep airheadwaves" | awk '{print $2}')

echo "=== AudioCaptureService Logs (PID: $PID) ==="
adb -s $TRANSMITTER logcat --pid=$PID -d 2>/dev/null | grep -E "AudioCaptureService|First audio|Encoded AAC" | tail -50
