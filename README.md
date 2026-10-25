# SeaWave

Minimal native Android serial-to-call app for Android 16 / Lunaris OS.

## Features
- Import a JSON file with top-level `records` and an `SL` field.
- Look up a record by its actual `SL`.
- Display all supported user-facing fields: `Name`, `No`, `Mobile`, `Division`, `Uni`, `Email`, and `Remarks`.
- Directly initiate one call only after tapping **CALL NOW** and granting Android's `CALL_PHONE` permission.
- Manually add or edit a record's `Remarks`. The change is saved in SeaWave's private local JSON copy.
- Export the updated JSON using Android's file picker to preserve the updated remarks outside the app.
- No automatic next-call behavior.

## Build
The GitHub Actions workflow `.github/workflows/build.yml` builds `app/build/outputs/apk/debug/app-debug.apk` and uploads it as the `SeaWave-debug-apk` artifact.

## JSON format
The app accepts the existing JSON structure with `columns` and `records`. Existing fields and metadata are preserved when remarks are edited. `SL` and `Mobile` should ideally be strings to preserve leading zeros.
