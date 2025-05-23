#!/bin/bash
for f in $(find . -name '*.md' -not -path "./node_modules/*"); do
  echo "Checking $f"
  PATHS=$(grep -oP "(?<=\]\()[^)]*(?=\))|(?<=src=\")[^\"]*(?=\")" "$f" | grep -v '^http')
  for l in $PATHS; do
    path=$(dirname "$f")/$l
    if [ ! -e "$path" ]; then
      echo "$path doesn't exist" && exit 1
    fi
  done
done
