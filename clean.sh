#!/usr/bin/env bash
set -e

# 1) Clean out build artifacts & IDE cruft
rm -rf \
  .gradle/ build/ dist/ out/ target/ \
  .idea/ .vscode/ \
  node_modules/ __pycache__/ \
  *.iml \
  .kotlin/

# 2) Remove lockfiles, generated summaries & helper scripts we don't need
rm -f \
  package-lock.json yarn.lock \
  combined_files.txt

# 3) Regenerate our combined summary (code.py will only pick .kt/.py/.ts/etc.)
python3 scripts/code.py

# 4) (Re)install frontend deps for a clean state
npm install
