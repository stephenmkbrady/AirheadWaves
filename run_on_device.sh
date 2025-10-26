#!/bin/bash

# Build, install, and run AirheadWaves on connected device/emulator

export JAVA_HOME=/opt/android-studio/jbr
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_HOME=/home/user/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH

echo "Building APK..."
./gradlew assembleDebug || { echo "Build failed"; exit 1; }

echo ""
echo "Installing on device..."
adb install -r app/build/outputs/apk/debug/app-debug.apk || { echo "Install failed"; exit 1; }

echo ""
echo "Launching app..."
adb shell am start -n space.ring0.airheadwaves/.MainActivity

echo ""
echo "App launched! Showing logcat (Ctrl+C to exit)..."
adb logcat -s "AirheadWaves:*" "AudioCaptureService:*" "AudioPlaybackService:*" "AndroidRuntime:E"
