#!/bin/bash
# Usage: status-bar.sh [enter|exit] - toggles the SystemUI demo mode (06:00, full battery, full signal, no notifications)
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
if [ "${1:-enter}" = exit ]; then
  demo exit
  exit 0
fi
adb shell settings put global sysui_demo_allowed 1
demo enter
demo clock -e hhmm 0600
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4
demo network -e mobile show -e datatype none -e level 4
demo notifications -e visible false
