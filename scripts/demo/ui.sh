#!/bin/bash
# Prints every node with text, content-desc or a checkable state, prefixed by its center "x,y"
source "$(dirname "$0")/common.sh"
dump_ui
grep -oE '<node [^>]*>' "$WORK/ui.xml" | while read -r n; do
  t=$(sed -nE 's/.* text="([^"]*)".*/\1/p' <<<"$n")
  d=$(sed -nE 's/.* content-desc="([^"]*)".*/\1/p' <<<"$n")
  c=$(sed -nE 's/.* checked="([^"]*)".*/\1/p' <<<"$n")
  ck=$(sed -nE 's/.* checkable="([^"]*)".*/\1/p' <<<"$n")
  read -r x1 y1 x2 y2 <<<"$(sed -nE 's/.* bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p' <<<"$n")"
  [ -n "$t$d" ] || [ "$ck" = true ] || continue
  state=""
  [ "$ck" = true ] && state="checked=$c"
  echo "$(( (x1 + x2) / 2 )),$(( (y1 + y2) / 2 )) t=[$t] d=[$d] $state"
done
