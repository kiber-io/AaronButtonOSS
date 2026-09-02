# Aaron Button OSS

An open-source alternative client for configuring the Aaron Button case with three physical NFC buttons.

The app supports English (the default language) and Russian. The language can be changed from the setup wizard or the main button settings screen.

Aaron Button is a PITAKA phone case accessory. The official [PITAKA app](https://play.google.com/store/apps/details?id=com.pitaka.shortcuts) is available on Google Play.

## Privacy and project purpose

The app is fully local and contains no unnecessary service layer around button setup:

- no analytics, advertising, or tracking;
- no accounts or collection of user data;
- no network code or internet permission;
- settings are stored only on the phone;
- no user agreement or unnecessary setup screens are required.

The project was created to simplify Aaron Button setup. The official app requires extra steps for this basic operation, including accepting a user agreement. Here, you only need to choose an action and write it to the desired button.

## Actions from the original app

| Action | Description |
| --- | --- |
| Flashlight | Toggles the camera flashlight. |
| Camera | Opens the system camera. |
| Open app | Launches the selected app. |
| Open link | Opens the specified link. |
| Sound: silent / ring | Toggles normal and silent mode. |
| NFC settings | Opens NFC settings. |
| Location settings | Opens location settings. |
| Airplane mode settings | Opens airplane mode settings. |

Camera permission is required for the flashlight.

## Additional Aaron Button OSS actions

The OSS client adds three actions that are not available in the original app:

| Action | Description |
| --- | --- |
| Run Termux command | Executes a command in Termux in the background. |
| Custom intent | Executes a manually configured Android Intent. |
| Custom value | Writes an arbitrary action value into the original NFC payload. |

`Custom value` is intended for experimenting with undocumented or unsupported
action values. The value is written as-is; when the button is pressed, Aaron
Button OSS can execute it only if it matches an action known to the app.

### Termux

Termux must be installed for the `Run Termux command` action. The command is sent to `com.termux.app.RunCommandService` and runs without opening the Termux window.

On first use, Android may request the `RUN_COMMAND` permission. Grant it to Aaron Button OSS in the app's system settings.

### Custom intent

Custom intent is configured through separate fields in the editor. The following are supported:

- `Action`;
- `Data URI` and `MIME type`;
- `Package`;
- `Component` in `package/class` format;
- flags in decimal or hex format, for example `0x10000000`;
- any number of categories;
- extras of type `string`, `int`, `long`, `boolean`, `float`, or `double`.

For example, to open Google, specify:

```text
Action: android.intent.action.VIEW
Data URI: https://google.com
```

Before saving, the app builds and validates the Intent. If no app on the phone can handle it, an error is shown.

## NFC format

The app uses the following MIME type:

```text
application/com.pitapolis.nfc
```

The payload is written as an NDEF MIME record in this format:

```text
pita://polis/<ANDROID_ID>:<ACTION>
```

`ANDROID_ID` binds the record to a specific device. When reading it, the app checks this ID and only then executes the action saved for the NFC ID of the detected button.

Writing requires an NFC tag that supports NDEF or formatting through `NdefFormatable`.

Important: physical buttons are identified by the NFC tag UID, not by the NDEF contents. Therefore, identical payloads do not mix buttons when the setup wizard has been completed correctly and the tag UIDs are different.

## Build

From the project root:

```bash
./gradlew assembleDebug
```

On Windows:

```bat
gradlew.bat assembleDebug
```

The debug APK will be created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For a release build, use `assembleRelease`. The APK will be located in `app/build/outputs/apk/release/`.

## Limitations

- A phone with NFC and a compatible Aaron Button case is required.
- NFC must be enabled in the system.
- Initial setup must be completed in the order Button 1 → Button 2 → Button 3.

## Implementation structure

- `MainActivity.kt` — screen state, NFC handling, and tag writing.
- `ActionExecutor.kt` — action execution.
- `ButtonUi.kt` — setup screen, learning wizard, button cards, and bottom-sheet editors.
- `AppModels.kt` — action list, configuration models, app loading, and the Custom Intent parser.
- `AppLocale.kt` — language selection and localized activity context.
- `AppTheme.kt` — Material 3 theme and color schemes.
- `NfcTriggerActivity.kt` — transparent activity for handling NFC events without showing the main screen.
- `NfcPayload.java` — NFC payload encoding and validation.
- `ExampleUnitTest.java` — a small test for NFC payload encoding and validation.
- `AndroidManifest.xml` — NFC, camera, and Termux permissions, NFC filters, and the app launcher.

## Extended automation

Aaron Button OSS handles button setup and basic actions. If you need more complex automation than this project supports, use a separate automation app such as [MacroDroid](https://macrodroid.com/). This may be more convenient if such an automation app is already installed and in use.

Aaron Button OSS intentionally does not include its own complex automation engine and remains a small button configurator.

---
*Note: this project was created with help from an LLM. The author is not an experienced Android developer and does not specialize in design; the goal was simply to get a working alternative to the official but inconvenient app. Suggestions for improving the code and interface are welcome.*
