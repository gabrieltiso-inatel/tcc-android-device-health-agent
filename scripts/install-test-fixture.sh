#!/usr/bin/env sh
set -eu

./gradlew :test-fixture:assembleDebug
adb install -r test-fixture/build/outputs/apk/debug/test-fixture-debug.apk
