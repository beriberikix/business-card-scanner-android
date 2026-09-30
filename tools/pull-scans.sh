#!/usr/bin/env bash
# Copies scans kept on the phone into samples/, ready to become parser fixtures.
#
# Workflow for adding real cards to the regression set:
#   1. Scan cards in the app with "Keep scans on device for parser testing" ticked.
#   2. Run this script.
#   3. Rename each pair to something meaningful, write <name>.expected.json by hand,
#      and add the name to app/src/test/resources/samples/index.txt.
set -euo pipefail
cd "$(dirname "$0")/.."

PKG=dev.jberi.cardscan
REMOTE=/sdcard/Android/data/$PKG/files/captures
DEST=samples/scans
mkdir -p "$DEST"

if ! adb shell "ls $REMOTE" >/dev/null 2>&1; then
  echo "No scans on the device yet. Scan some cards first."
  exit 0
fi

for f in $(adb shell ls "$REMOTE" | tr -d '\r'); do
  adb pull "$REMOTE/$f" "$DEST/$f" >/dev/null && echo "pulled $f"
done
echo
echo "Scans are in $DEST. To promote one into the test suite:"
echo "  cp $DEST/<stamp>.ocr.json samples/<name>.ocr.json"
echo "  cp samples/<name>.ocr.json app/src/test/resources/samples/"
echo "  write samples/<name>.expected.json, copy it there too,"
echo "  then add <name> to app/src/test/resources/samples/index.txt"
echo
echo "To clear the device: adb shell rm $REMOTE/*"
