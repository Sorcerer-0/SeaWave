# SeaWave

Minimal serial-to-call app. Import a JSON dataset, type an SL, press CALL. One press = one call; nothing ever advances or redials automatically.

## Build a debug APK
Requirements: Android Studio (Ladybug or newer) with Android SDK Platform 36, and JDK 17+ (the JDK bundled with Android Studio is fine).

**Android Studio:** File > Open > select this folder, let Gradle sync, then Build > Build Bundle(s) / APK(s) > Build APK(s).
Output: `app/build/outputs/apk/debug/app-debug.apk`

**Command line:** the Gradle wrapper JAR could not be generated offline. Run `gradle wrapper --gradle-version 8.11.1` once (needs a local Gradle install), or let Android Studio create it, then:
`./gradlew assembleDebug`   (Windows: `gradlew.bat assembleDebug`)
Put `sdk.dir=/path/to/Android/Sdk` in `local.properties` if Gradle cannot find the SDK.

## Install on the phone (Lunaris OS / Android 16)
1. Copy `app-debug.apk` to the phone (or `adb install -r app-debug.apk` with USB debugging on).
2. Open it from Files; allow "Install unknown apps" for that file manager if prompted.
3. If Play Protect warns about an unrecognised app, choose "Install anyway" (it is a locally built debug APK).
4. Copy your JSON file to Downloads, open SeaWave, tap Import JSON and pick it.
5. On the first CALL, allow the Phone permission. If you deny it permanently, use the "Open settings" button shown.

## Test checklist
- [ ] Import: pick the real JSON; status shows the expected count (e.g. `546 records loaded`) and "Imported N records".
- [ ] Import a non-JSON or broken file: clear error, old count unchanged.
- [ ] Force-stop and reopen: record count is still shown (private storage persistence).
- [ ] Lookup: enter `1`, `125`, `546`: result area shows matching name, SL, mobile. Check a record whose SL differs from its row position, if any.
- [ ] Unknown SL (e.g. `99999`): "No record found", no call.
- [ ] Record with empty Mobile: "No valid mobile number for this SL.", no call, SL text unchanged.
- [ ] Empty SL field: "Enter an SL first."
- [ ] Permission: first CALL shows the prompt. Deny: message appears, pressing CALL asks again. Deny repeatedly / "Don't ask again": "Open settings" dialog appears.
- [ ] Grant: exactly one call starts to the shown number (leading 0 preserved).
- [ ] After the call: return to app; SL field unchanged, no further call, no auto-advance.
- [ ] Airplane mode / no SIM: error or system message, no crash.
- [ ] Duplicate SL in data (if any): ambiguity message, no call.

## Notes
- Only permission: `CALL_PHONE`. No internet, contacts, call log, SMS, mic or location.
- Numbers are dialled as stored (trimmed). If a value contains spaces, hyphens, dots or parentheses, only those separators are removed. Values that are not 6-15 digits (optional leading +) are rejected.
- A 1.5 s guard ignores an accidental double-tap on CALL so one press cannot place two calls.
- On the very first permission grant, the CALL press that triggered the prompt completes automatically (single call, same SL).
