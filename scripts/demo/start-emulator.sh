#!/bin/bash
# Starts the alarme37 AVD headless with host GPU rendering. Without DISPLAY the emulator falls back to software rendering (about 5 fps recordings).
set -e
export DISPLAY=${DISPLAY:-:1}
nohup "${ANDROID_HOME:-/opt/android-sdk}/emulator/emulator" -avd "${AVD:-alarme37}" -no-window -no-audio -no-snapshot-save -no-boot-anim -gpu host > "${WORK:-/tmp/alarme-demo}.emulator.log" 2>&1 &
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 2; done
adb root >/dev/null
sleep 2
adb wait-for-device
adb shell dumpsys SurfaceFlinger | grep -E "^GLES"
