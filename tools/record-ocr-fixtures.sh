#!/usr/bin/env bash
# Records real on-device OCR for every image in samples/images and copies the JSON into samples/.
#
# Driven with `am instrument` rather than `gradlew connectedAndroidTest`, because the Gradle task
# uninstalls the app when it finishes and takes the recorded files with it.
set -euo pipefail
cd "$(dirname "$0")/.."

PKG=dev.jberi.cardscan
REMOTE=/sdcard/Android/data/$PKG/files/ocr-fixtures

python3 tools/make_sample_cards.py
mkdir -p app/src/androidTest/assets/cards
cp samples/images/*.png app/src/androidTest/assets/cards/

./gradlew assembleDebug assembleDebugAndroidTest -q
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

adb shell am instrument -w \
  -e class dev.jberi.cardscan.OcrFixtureRecorder \
  "$PKG.test/androidx.test.runner.AndroidJUnitRunner"

for f in $(adb shell ls "$REMOTE" | tr -d '\r'); do
  adb pull "$REMOTE/$f" "samples/$f"
done
cp samples/*.ocr.json app/src/test/resources/samples/
echo "Recorded fixtures into samples/ and app/src/test/resources/samples/"
