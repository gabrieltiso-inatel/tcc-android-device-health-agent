#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(dirname "$script_dir")
cd "$project_dir"

adb_bin=${ADB_BIN:-}
if [ -z "$adb_bin" ]; then
    adb_bin=$(command -v adb || true)
fi
if [ -z "$adb_bin" ]; then
    for sdk_dir in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        if [ -n "$sdk_dir" ] && [ -x "$sdk_dir/platform-tools/adb" ]; then
            adb_bin="$sdk_dir/platform-tools/adb"
            break
        fi
    done
fi
if [ -z "$adb_bin" ] || [ ! -x "$adb_bin" ]; then
    printf '%s\n' 'ADB not found. Set ADB_BIN to the platform-tools/adb executable or configure ANDROID_HOME.' >&2
    exit 1
fi

run_adb() {
    "$adb_bin" "$@"
}

if ! run_adb get-state >/dev/null; then
    printf '%s\n' 'Connect and authorize a device or start an emulator. With multiple devices, set ANDROID_SERIAL.' >&2
    exit 1
fi

./gradlew :test-fixture:assembleDebug
run_adb install -r test-fixture/build/outputs/apk/debug/test-fixture-debug.apk

run_adb shell sh <<'ANDROID_FILES'
set -eu
fixture_root=/sdcard/Download/device-health-file-fixture
mkdir -p "$fixture_root/photos" "$fixture_root/documents/nested"
dd if=/dev/zero of="$fixture_root/large-video.mp4" bs=1024 count=8192 >/dev/null 2>&1
dd if=/dev/zero of="$fixture_root/old-document.pdf" bs=1024 count=256 >/dev/null 2>&1
dd if=/dev/zero of="$fixture_root/photos/sample-photo.jpg" bs=1024 count=512 >/dev/null 2>&1
printf '%s\n' 'This file is used to test revision changes.' > "$fixture_root/mutable.txt"
printf '%s\n' 'Nested fixture document.' > "$fixture_root/documents/nested/notes.md"
touch -t 202401010000 "$fixture_root/old-document.pdf"
index=1
while [ "$index" -le 40 ]; do
    printf 'Disposable document %s\n' "$index" > "$fixture_root/documents/note-$index.txt"
    index=$((index + 1))
done
printf '%s' 'iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAIAAACQkWg2AAAAFklEQVR4nGNQjb9NEmIY1TCqYfhqAADkeF8QkT5t2gAAAABJRU5ErkJggg==' | base64 -d > "$fixture_root/photos/sample-media.png"
am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$fixture_root/photos/sample-media.png" >/dev/null
ANDROID_FILES

printf '%s\n' 'Installed the disposable app and recreated named test files in Download/device-health-file-fixture.'
printf '%s\n' 'Choose that folder in the agent, grant media access, then update apps and files.'
