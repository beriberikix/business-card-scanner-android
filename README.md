# Card Scanner

Personal Android app: point it at a business card, get parsed contact fields, review and edit
them, then save to Contacts. Everything runs on the device. No accounts, no backend, no network
calls for the actual work.

Built for a Pixel 10a on Android 17, but nothing is specific to that device beyond the
verification notes below.

## Build and run

You need a JDK 17+ and the Android SDK. There is no Android Studio requirement.

```bash
# One-time setup, if you do not already have them
curl -sL -o /tmp/t21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
mkdir -p ~/.jdks && tar xzf /tmp/t21.tar.gz -C ~/.jdks
android sdk install platforms/android-37.0 build-tools/37.0.0 platform-tools

export JAVA_HOME=~/.jdks/jdk-21.0.12.1+1
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

./gradlew assembleDebug                      # APK at app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest                  # parser accuracy + unit tests, no device needed
```

The debug APK is about 34 MB, most of which is the bundled ML Kit text recognition model. The
build is restricted to `arm64-v8a`; drop the `abiFilters` line in `app/build.gradle.kts` to build
for every ABI, which roughly doubles the size.

## Using it

Three ways in: **Scan card** opens the ML Kit document scanner, **Import image** opens the system
photo picker, and the app also registers as a share target for `image/*`, so you can share a photo
to it from anywhere.

The parsed fields appear in an editable form. Anything the parser is unsure about is flagged:
a phone number that could not be normalized to E.164, or an email address reconstructed after OCR
damaged it, both show in red with the raw OCR text in the label. **Save to Contacts** opens the
system contact editor prefilled, so nothing is written until you confirm there too.

## Architecture

One module, roughly 1,200 lines of Kotlin.

```
capture/   DocumentScanner (Play services UI), photo picker, image decoding
ocr/       OcrEngine interface, MlKitOcrEngine, QrContactDetector
parse/     CardParser interface, HeuristicCardParser, LineGrouper,
           PhoneNormalizer, EmailRepair, Lexicons
model/     ContactCard, Phone, Email
save/      ContactInsert (ContactsContract insert intent)
ui/        ScanViewModel, ReviewScreen
spike/     NanoSpike (on-device Gemini Nano availability probe)
```

OCR and parsing each sit behind a one-method interface, so either backend can be swapped without
touching the rest of the pipeline.

## Decisions and tradeoffs

### Gemini Nano is not available on this device, and that was measured, not assumed

The plan was to use on-device Gemini Nano for field extraction if it was good enough. `NanoSpike`
probes it through the ML Kit GenAI Prompt API. On the Pixel 10a running Android 17:

```
checkStatus() = UNAVAILABLE
getBaseModelName() -> GenAiException 606 FEATURE_NOT_FOUND: "Feature 636 is not available"
structuredOutput / systemPrompt / thinkingMode / caching = false
all four ModelReleaseStage x ModelPreference combinations = UNAVAILABLE
```

AICore itself is a real production build (`0.release.prod_aicore_20260820`), not the `0.stub`
placeholder, and it is actively used by the dialer and TTS. The Prompt API feature is simply not
provisioned for this SKU, which matches Google's published device list: Pixel 9, 10 and 11 series,
no "a" models. You can re-run the probe any time from the link at the bottom of the home screen.

So parsing uses deterministic heuristics. Given the measured accuracy below, this is not a
consolation prize: contact channels come out exact, and the heuristics handle name, title and
company correctly on every card tested.

### Why each component won

| Stage | Chosen | Why not the alternative |
|---|---|---|
| Capture | ML Kit Document Scanner | Auto crop and perspective correction are the single biggest lever on OCR quality, and the UI runs inside Play services so the app needs no CAMERA permission. CameraX would mean writing a viewfinder plus crop and deskew by hand. |
| OCR | ML Kit Text Recognition v2, bundled Latin | Tesseract4Android trails badly on phone photos and has no text detector. PaddleOCR PP-OCRv5 on ONNX is the credible open alternative and the better choice if CJK cards matter, but it is ~20 MB of models plus a pre/post-processing port. ML Kit measured 280-380 ms per card here. |
| Parsing | Layout-aware heuristics | Gemini Nano is unavailable. A local VLM (Gemma 4 E2B) means a 2.6 GB download and seconds per card on a Tensor G4. Email, phone and URL are pattern problems, not judgement problems, and layout cues resolve the rest. |
| Save | `ContactsContract.Intents.Insert` | Zero permissions, and the system editor is a second review step. A direct ContentProvider write needs `WRITE_CONTACTS`, plus `READ_CONTACTS` for deduplication. |

### Proprietary components

ML Kit Document Scanner, Text Recognition and Barcode Scanning are Google's, free, closed source,
and covered by the ML Kit terms. They run entirely on the device. Everything else is open source:
AndroidX, Kotlin, and libphonenumber (Apache-2.0).

Swapping `MlKitOcrEngine` for a PaddleOCR implementation would leave the scanner UI as the only
proprietary piece, and dropping that in favour of the photo picker would make the whole thing open.

### Smaller calls worth recording

- **libphonenumber instead of `android.telephony.PhoneNumberUtils`.** The framework class wraps the
  same library but is unavailable in plain JVM tests. A 360 KB dependency makes phone normalization
  testable without a device or Robolectric, which matters because a wrong digit in a phone number
  is the failure mode that makes a saved contact useless.
- **Only `Insert.DATA` rows, never the flat extras.** Verified in the Google Contacts editor:
  setting both makes every phone number and email appear twice. `Insert.DATA` alone is honoured in
  full, including structured name parts, organization, website and postal address.
- **Barcode detection runs on the captured still, not as a separate mode.** Many cards have a QR
  code that is just a URL, so a dedicated scan mode is not worth the UI. When a card does carry a
  vCard, its data is exact and overrides the OCR parse.
- **Scans are kept on the device by default** (toggle on the home screen) so real cards can be
  promoted into the test suite. See "Growing the test set".

## Accuracy

Measured by `ParserAccuracyTest` against real ML Kit output recorded on the Pixel 10a. Contact
channels are asserted; name, title and company are reported so regressions are visible without
blocking the build.

| Card | Shape it tests | Result |
|---|---|---|
| wrapped-name-broken-email | Real card. Name wrapped over two lines, email damaged by OCR | all fields correct |
| us-northwind | Three labelled phones, legal-form company | all fields correct |
| uk-calder | Title wrapped with a trailing comma, UK numbers, postcode | all fields correct |
| de-mueller | German, umlaut surname, non-English job title | all fields correct |
| dark-vanta | Light text on dark, OCR split the email domain | all fields correct |
| two-column | Two columns, phone labels on their own lines | all fields correct |
| minimal | Name and email only, surname also in the email domain | all fields correct |

**49/49 heuristic fields, and every phone number, email and URL exact.**

That number is honest about what it covers and no more: seven cards, one of them real, all Latin
script, all captured under good light by a scanner that had already deskewed them. It says the
parser handles these layout shapes, not that it handles business cards in general.

### Real OCR errors this had to survive

Every one of these came from actual ML Kit output, not from imagination:

| What the card said | What OCR read | Handling |
|---|---|---|
| `marisol@veltrix.io` | `marisol@veltrixio` | `EmailRepair` reinserts a dot before a known TLD |
| `marcus@vantalabs.io` | `marcus@vantalabs. io` | spaces around dots and at signs are squeezed |
| `sofia@mvlaw.com` | `sofia @mvlaw.com` | same |
| `+1 415 555 2671` | `+1415 555 2671` | separators are irrelevant to parsing |

Repaired addresses are marked and shown to you with the raw text, because a repair is a guess.

### Bugs the tests caught that the device did not

- The phone candidate pattern accepted only digits, so a letter OCR produced inside a number
  truncated the match before digit repair could run.
- Country detection was a substring test, so "Marcus" contains "us" and a person's name was
  classified as an address.
- The noise filter dropped standalone labels like "Mobile" before they could type the number
  beneath them on a two-column card.

### Known limitations

- **Latin script only.** The bundled model is Latin. Chinese, Japanese, Korean and Devanagari are
  separate ~4 MB artifacts that can be added; vertical text will still not work.
- **No duplicate detection.** Deferred deliberately, since checking needs `READ_CONTACTS` and
  Google Contacts already offers Merge and fix. The v1 app requests no permissions at all.
- **One side of the card.** The scanner captures two pages but only the first is parsed.
- **`+44 7700 900xxx` and similar are rejected**, correctly: Ofcom reserves that range for fiction
  and libphonenumber treats it as invalid. The number is kept verbatim and flagged for review.
- **Company is left blank rather than guessed** when a card carries no company text, even if the
  email domain implies one.

### What I would do next

1. More real cards. The synthetic set covers layout shapes, but real cards bring glare, texture,
   foil, unusual type and bad crops. The harness below exists for exactly this.
2. Two-sided merge, which is mostly plumbing since the scanner already returns both pages.
3. Optional duplicate check behind a `READ_CONTACTS` prompt, shown as a warning on the review
   screen rather than blocking the save.
4. Re-run the Nano probe after Android updates. If the Prompt API ever lands on this device,
   `GeminiNanoCardParser` slots in behind the existing `CardParser` interface, and the fixture
   suite immediately says whether it is actually better than the heuristics.

## Growing the test set

Fixtures are real ML Kit output, recorded on the device and committed, so parser tests run on the
JVM in seconds without a phone.

```bash
./tools/record-ocr-fixtures.sh   # regenerate synthetic cards and re-record their OCR
./tools/pull-scans.sh            # copy scans kept on the phone into samples/scans/
```

To promote a real card into the suite: scan it in the app with the toggle on, run
`tools/pull-scans.sh`, then copy the `.ocr.json` to `samples/<name>.ocr.json` and to
`app/src/test/resources/samples/`, write `<name>.expected.json` next to it, and add `<name>` to
`app/src/test/resources/samples/index.txt`.

**Privacy note:** a fixture contains the cardholder's real name, phone number and email address in
plain text. `samples/wrapped-name-broken-email.*` is a real card. `samples/scans/` is gitignored for this
reason; think before committing anything from it.
