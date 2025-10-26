#!/bin/bash

export ANDROID_HOME=/home/user/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH

echo "=== Checking Device Logs ==="
echo ""

PHYSICAL_DEVICE="R58N23Q207L"
EMULATOR_DEVICE="emulator-5554"

echo "Physical Device Logs (AudioCaptureService):"
echo "-------------------------------------------"
adb -s $PHYSICAL_DEVICE logcat -d -s AudioCaptureService:V | tail -30
echo ""

echo "Emulator Logs (AudioPlaybackService):"
echo "-------------------------------------"
adb -s $EMULATOR_DEVICE logcat -d -s AudioPlaybackService:V | tail -30
echo ""

echo "Physical Device Logs (AudioPlaybackService):"
echo "--------------------------------------------"
adb -s $PHYSICAL_DEVICE logcat -d -s AudioPlaybackService:V | tail -30
echo ""
