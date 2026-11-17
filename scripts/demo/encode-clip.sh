#!/bin/bash
# Usage: encode-clip.sh <source> <start:end> [<start:end> ...]
# Concatenates the kept segments (seconds), scales to 540px wide and writes docs/public/media/ring-flow.{mp4,webm} plus the poster.
# Find idle stretches first: ffmpeg -i <source> -vf "freezedetect=n=-75dB:d=0.4" -map 0:v -f null - 2>&1 | grep freeze_
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=${OUT:-$ROOT/docs/public/media}
SRC=$1
shift
filter=""
labels=""
i=0
for seg in "$@"; do
  filter+="[0:v]trim=${seg%:*}:${seg#*:},setpts=PTS-STARTPTS,fps=30[v$i];"
  labels+="[v$i]"
  i=$(( i + 1 ))
done
filter+="${labels}concat=n=$i:v=1:a=0,scale=540:-2[out]"
ffmpeg -y -v error -i "$SRC" -filter_complex "$filter" -map "[out]" -an -c:v libx264 -preset slow -crf 24 -pix_fmt yuv420p -movflags +faststart "$OUT/ring-flow.mp4"
ffmpeg -y -v error -i "$OUT/ring-flow.mp4" -c:v libvpx-vp9 -crf 36 -b:v 0 -row-mt 1 -an "$OUT/ring-flow.webm"
ffmpeg -y -v error -i "$OUT/ring-flow.mp4" -frames:v 1 -c:v libwebp -quality 75 "$OUT/ring-flow-poster.webp"
ls -la "$OUT"
echo "Bump mediaVersion in docs/src/pages/index.astro so browsers drop the cached clip."
