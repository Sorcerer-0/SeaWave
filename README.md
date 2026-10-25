# SeaWave

Native Android serial-to-call app (Kotlin, framework Views, zero third-party dependencies) for Android 16 / Lunaris OS.

Developed by Abdullah Al Saad.

## Screens
- **Home** – total record count, serial-number input, search ribbon and a result/status card.
- **Contact details** – avatar, name, No, SL badge, mobile / division / university / email / remarks (copy and edit shortcuts), CALL, add/edit remark and JSON export.
- **Remarks dialog** – 200-character counter, Cancel / Save, plus Clear.
- **Settings** (gear icon) – Import JSON, Export JSON, record count and About.

All UI text is Bengali; the footer reads "Developed by Abdullah Al Saad".

## Features (unchanged)
- Import a JSON file with top-level `records` and an `SL` field.
- Look up a record by its actual `SL`.
- Show `Name`, `No`, `Mobile`, `Division`, `Uni`, `Email` and `Remarks`.
- Place one call only after tapping **কল করুন** and granting `CALL_PHONE`.
- Add or edit `Remarks`; saved in SeaWave's private local JSON copy.
- Export the updated JSON through Android's file picker.
- No automatic next-call behaviour.

## Custom UI pieces
- `RibbonButtonView` – slanted ribbon (BUTTON / SMALL / TITLE / BANNER kinds, optional `ribbonLight`).
- `WaveView` – decorative wave along the bottom edge.
- Vector icons and the wave logo live in `res/drawable` (`ic_logo.xml`).

## Build
GitHub Actions workflow `.github/workflows/build.yml` builds `app/build/outputs/apk/debug/app-debug.apk` and uploads it as the `SeaWave-debug-apk` artifact.

## JSON format
Accepts the existing structure with `columns` and `records`. Unknown fields are preserved when remarks are edited. `SL` and `Mobile` should be strings to keep leading zeros.
