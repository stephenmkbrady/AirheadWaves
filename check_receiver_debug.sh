#!/bin/bash

export ANDROID_HOME=/home/user/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH

# Get all physical devices (not emulator)
for device in $(adb devices | grep -v "List\|emulator" | grep "device" | awk '{print $1}'); do
    echo "=== Device $device ==="
    PID=$(adb -s $device shell "ps | grep airheadwaves" | awk '{print $2}')

    if [ -n "$PID" ]; then
        echo "Process PID: $PID"
        echo ""
        echo "AudioPlaybackService logs:"
        adb -s $device logcat --pid=$PID -d 2>/dev/null | grep "AudioPlaybackService" | tail -50
    fi
    echo ""
done
