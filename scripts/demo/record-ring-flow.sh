#!/bin/bash
# Records the 6:30 alarm ringing, the math mission and the shake mission with the emulator recorder (30 fps, written on the host).
# Everything runs in one go because pauses between separate commands end up in the video.
# Output: $WORK/flow.webm (1080x2400). Trim idle stretches afterwards with encode-clip.sh.
DIR=$(dirname "$0")
source "$DIR/common.sh"
rel() { python3 -c "import time; print(round(time.time() - $T0, 2))"; }
switch_y() { "$DIR/ui.sh" | grep -F "t=[$1]" -A3 | grep -m1 checked= | cut -d" " -f1 | cut -d, -f2; }
switch_state() { "$DIR/ui.sh" | grep -F "t=[$1]" -A3 | grep -m1 -o "checked=[a-z]*"; }

adb shell settings put global auto_time 0
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb emu sensor set acceleration 0:9.8:0 >/dev/null
adb shell cmd uimode night no
adb shell cmd alarm set-time "$(date -d "2026-11-23 05:00:00" +%s)000"
adb shell am force-stop $PKG
adb shell am start -n $MAIN_ACTIVITY >/dev/null
sleep 3

for alarm in "7:15 AM" "6:30 AM"; do
  if [ "$(switch_state "$alarm")" != "checked=true" ]; then
    adb shell input tap 927 "$(switch_y "$alarm")"
    sleep 1.5
  fi
done
# Toggle 6:30 off and on so it is re-armed against the shifted clock
adb shell input tap 927 "$(switch_y "6:30 AM")"; sleep 1.5
adb shell input tap 927 "$(switch_y "6:30 AM")"; sleep 1.5
"$DIR/status-bar.sh" enter
adb shell cmd alarm set-time "$(date -d "2026-11-23 06:29:53" +%s)000"

rm -f "$WORK/flow.webm"
adb emu screenrecord start --time-limit 150 --fps 30 "$WORK/flow.webm" >/dev/null
T0=$(date +%s.%N)
for i in $(seq 1 120); do
  ring_on_top && break
  sleep 0.25
done
echo "ring at $(rel)s"
sleep 2.5
adb shell input swipe 120 2222 1000 2222 400
sleep 0.6

prev=""
for i in 1 2 3 4 5 6; do
  dump_ui
  q=$(grep -oE 'text="[0-9]+\+[0-9]+="' "$WORK/ui.xml" | grep -oE '[0-9]+\+[0-9]+')
  echo "q=$q"
  [ -z "$q" ] && break
  if [ "$q" = "$prev" ]; then
    adb shell input tap $(node_center 'text="Check"')
    sleep 0.9
    continue
  fi
  prev=$q
  [ "$i" = 1 ] && sleep 1.2
  adb shell input tap $(node_center 'text="Your answer"'); sleep 0.4
  adb shell input text $(( q )); sleep 0.3
  dump_ui
  adb shell input tap $(node_center 'text="Check"')
  sleep 0.9
done

for i in $(seq 1 60); do
  adb emu sensor set acceleration 25:9.8:0 >/dev/null; sleep 0.12
  adb emu sensor set acceleration 0:9.8:0 >/dev/null; sleep 0.22
  if [ $(( i % 4 )) = 0 ] && [ "$i" -ge 12 ]; then
    ring_on_top || break
  fi
done
echo "shakes $i"
sleep 3
echo "end at $(rel)s"
adb emu screenrecord stop >/dev/null
sleep 3
echo "$WORK/flow.webm"
