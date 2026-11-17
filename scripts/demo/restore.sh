#!/bin/bash
# Restores the emulator after a capture session
"$(dirname "$0")/status-bar.sh" exit
adb shell settings put global auto_time 1
adb shell settings put system accelerometer_rotation 1
adb shell cmd uimode night no
adb unroot >/dev/null
