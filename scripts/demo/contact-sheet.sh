#!/bin/bash
# Usage: contact-sheet.sh <video> <output.jpg> [fps] [columns] - tiles frames with timestamps for a quick review
fps=${3:-2}
cols=${4:-12}
duration=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$1")
rows=$(python3 -c "import math; print(max(1, math.ceil($duration * $fps / $cols)))")
ffmpeg -y -v error -i "$1" -vf "fps=$fps,scale=-1:150,drawtext=text='%{pts\\:hms}':x=2:y=2:fontsize=12:fontcolor=red,tile=${cols}x$rows" -frames:v 1 "$2"
echo "$2"
