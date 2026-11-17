#!/bin/bash
# Usage: capture.sh <name> - saves a full-size PNG and a 450px JPG preview to $WORK/shots
source "$(dirname "$0")/common.sh"
mkdir -p "$WORK/shots"
adb exec-out screencap -p > "$WORK/shots/$1.png"
vips thumbnail "$WORK/shots/$1.png" "$WORK/shots/$1.small.jpg" 450 >/dev/null 2>&1
echo "$WORK/shots/$1.png"
