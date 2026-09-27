#!/usr/bin/env sh
set -eu

./gradlew :test-fixture:assembleDebug
adb install -r test-fixture/build/outputs/apk/debug/test-fixture-debug.apk

fixture_root="/sdcard/Download/device-health-file-fixture"
adb shell "mkdir -p '$fixture_root/photos' '$fixture_root/documents/nested'"
adb shell "rm -f '$fixture_root/large-video.mp4' '$fixture_root/old-document.pdf' '$fixture_root/mutable.txt' '$fixture_root/photos/sample-photo.jpg' '$fixture_root/documents/nested/notes.md'"
adb shell "dd if=/dev/zero of='$fixture_root/large-video.mp4' bs=1024 count=8192 >/dev/null 2>&1"
adb shell "dd if=/dev/zero of='$fixture_root/old-document.pdf' bs=1024 count=256 >/dev/null 2>&1"
adb shell "dd if=/dev/zero of='$fixture_root/photos/sample-photo.jpg' bs=1024 count=512 >/dev/null 2>&1"
adb shell "printf '%s\\n' 'This file is used to test revision changes.' > '$fixture_root/mutable.txt'"
adb shell "printf '%s\\n' 'Nested fixture document.' > '$fixture_root/documents/nested/notes.md'"
adb shell "touch -t 202401010000 '$fixture_root/old-document.pdf'"

printf '%s\n' "Installed app fixture and created files under $fixture_root"
