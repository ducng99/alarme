#!/bin/bash
# Shared helpers for the demo capture scripts
PKG=dev.tomng.alarme.debug
MAIN_ACTIVITY=$PKG/dev.tomng.alarme.MainActivity
WORK=${WORK:-/tmp/alarme-demo}
mkdir -p "$WORK"

dump_ui() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/ui.xml > "$WORK/ui.xml"
}

# Prints "x y" for the center of the first node whose attributes match $1, using the last dump_ui result
node_center() {
  grep -oE "<node [^>]*$1[^>]*>" "$WORK/ui.xml" | head -1 | sed -nE 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'
}

ring_on_top() {
  adb shell dumpsys activity activities | grep topResumedActivity | grep -q RingActivity
}
