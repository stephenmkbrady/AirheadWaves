#!/bin/bash

export ANDROID_HOME=/home/user/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH

PHYSICAL_DEVICE="R58N23Q207L"

echo "=== Checking for errors in AudioCaptureService ==="
adb -s $PHYSICAL_DEVICE logcat -d | grep -i "audiocapture\|error\|exception" | tail -50

echo ""
echo "=== Checking MediaCodec status ==="
adb -s $PHYSICAL_DEVICE logcat -d -s MediaCodec:V | tail -20
