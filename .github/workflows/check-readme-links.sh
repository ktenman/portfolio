#!/bin/bash
for f in $(find . -name '*.md' -not -path "./node_modules/*"); do
  echo "Checking $f"
  PATHS=$(awk '/^ *```/ { fenced = !fenced; next } !fenced' "$f" | grep -oP "(?<=\]\()[^)]*(?=\))|(?<=src=\")[^\"]*(?=\")" | grep -v '^http' | grep -v '[`$]')
  for l in $PATHS; do
    path=$(dirname "$f")/$l
    if [ ! -e "$path" ]; then
      echo "$path doesn't exist" && exit 1
    fi
  done
done
