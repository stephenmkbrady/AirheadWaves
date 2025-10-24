#!/bin/bash

export ANDROID_HOME=/home/user/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "Error: APK not found at $APK_PATH"
    echo "Run ./build_local.sh first"
    exit 1
fi

echo "Installing to physical device..."
adb -s R58N23Q207L install -r "$APK_PATH"

echo ""
echo "Installing to emulator..."
adb -s emulator-5554 install -r "$APK_PATH"

echo ""
echo "Done! APK installed on both devices."
