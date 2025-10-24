#!/bin/bash

# Local build script for AirheadWaves
# Sets up Java environment and builds the app

export JAVA_HOME=/opt/android-studio/jbr
export PATH=$JAVA_HOME/bin:$PATH

echo "Using Java: $(java -version 2>&1 | head -1)"
echo "Building APK..."

./gradlew assembleDebug "$@"
