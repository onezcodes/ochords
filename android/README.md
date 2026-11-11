# O Chords — Android

Kotlin / Jetpack Compose port of the iOS app in `../ios`, feature for feature: the same screens, design tokens,
models and API contract. Everything runs on demo data unless an API URL is set.

## Run

```sh
# Android Studio: open this folder, run the "app" configuration on a phone or the Pixel emulator.
# Command line (the JDK bundled with Android Studio works):
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest          # JVM tests (parser, pitch, chords)
```

Requires Android Studio (its JDK 21 + SDK), compileSdk 37, minSdk 26 (Android 8.0). Portrait phones only.

`local.properties` (gitignored, per machine) holds the settings iOS keeps in `project.yml`:

| Key | Debug default | Meaning |
|---|---|---|
| `API_BASE_URL` | `http://10.0.2.2:8000/api/v1` | The Laravel API in `../api`. `10.0.2.2` is the emulator's name for this Mac; a real phone on the same Wi-Fi uses `http://<your-mac>.local:8000/api/v1` (start the server with `--host=0.0.0.0`). Empty = demo data. Release builds must be `https://`. |
| `ADMOB_APP_ID`, `ADMOB_BANNER`, `ADMOB_INTERSTITIAL`, `ADMOB_REWARDED` | Google's public **test** IDs | AdMob. Empty app ID = the design's placeholder cards, SDK never started. |
| `GOOGLE_WEB_CLIENT_ID` | empty | Sign in with Google: the **web** OAuth client ID from Google Cloud (the ID token's audience; the API checks it as `OCHORDS_GOOGLE_CLIENT_ID`). Empty = the Google button says it isn't set up. An Android OAuth client with the app's SHA-1 must also exist in the same project. |

Plain HTTP is allowed only to local addresses (`res/xml/network_security_config.xml`).

## Layout

```
app/src/main/java/com/onezcodes/ochords/
  OChordsApplication.kt, MainActivity.kt
  app/        AppState (session, prefs, navigation), Route + Navigation (RouteView, tab stacks, floating tab bar)
  design/     Theme tokens (OC colours, Outfit / JetBrains Mono / Noto Sans Myanmar), glass components, brand, ChordDiagram
  model/      Song, SongSection, Profile, Tier, Setlist, lessons, chord voicings (kotlinx.serialization)
  services/   AppBackend interfaces, MockBackend + MockData, RemoteBackend + APIClient (OkHttp), ChordTheory, ChordLibrary,
              ChordSheetParser, OnDeviceScanService (CameraX + ML Kit), ScanImage, Zawgyi, GuestStore, ScanArchive,
              StoreService (Play Billing), AdService (AdMob), SocialSignIn (Credential Manager),
              audio: MicInput, PitchDetector, TuningSession, TunerChime, ChordEstimator, SongListener, ChordFollower,
              ChordListener, ChordCheck, HandsFree
  features/   Screens by area: onboarding, library, explore, importsheet, song, tools, learn, settings, shared
```

Navigation is the iOS model, not Navigation-Compose: `AppState.paths[tab]` is each tab's stack of `Route`s, `authPath`
the welcome stack, `modal` the full-screen covers. `push`/`pop`/`switchTo` work the same as on iOS, and the system back
button pops.

## What maps to what

| iOS | Android |
|---|---|
| `AppBackend` protocols, `RemoteBackend` | same interfaces; `RemoteBackend` over OkHttp + kotlinx.serialization, same JSON shapes (camelCase, string IDs, ISO-8601) |
| Keychain token | AES-GCM with an Android Keystore key, in app-private preferences (`TokenStore`) |
| `GuestStore` (Application Support) | app-private files (`filesDir/Guest`) |
| `ScanArchive` (file protection, no backup) | `filesDir/Scans`; `allowBackup="false"` in the manifest |
| StoreKit 2 (`pro.monthly` / `pro.yearly`) | Play Billing: one subscription `ochords_pro` with base plans `monthly` / `yearly` (set up the same in Play Console, with a 7-day free-trial offer). `PUT me/subscription` sends `{purchaseToken, productId, platform: "android"}` — **the API only verifies Apple JWS today; add Play verification before relying on Pro from Android** |
| Sign in with Apple + Google | Google only (Credential Manager). No Apple button on Android |
| ATT tracking prompt | none; the ad-consent choice sets `npa` on every request |
| Apple Vision OCR | ML Kit text recognition (Latin). Myanmar lyrics still need the server reader (signed in + `OCHORDS_SCAN_DRIVER=google`) |
| `VNDetectDocumentSegmentation` | brightness-profile page finder in `ScanImage.detectPage` + `Matrix.setPolyToPoly` perspective flatten |
| PDFKit text layer | `PdfRenderer` has no text layer: typed PDFs are rendered and read like photos |
| ShazamKit (Listen) | no Android SDK: chords, key, tempo and bars are estimated on the phone; the song name is "not recognised" |
| `GCKeyboard` foot pedal | hardware `KeyEvent`s (DPAD / page keys) |
| Vision face pitch (head nod) | CameraX + ML Kit face detection (head Euler X) |
| `SFSpeechRecognizer` | `SpeechRecognizer`, offline preferred |
| `ochords://reset-password` | `VIEW` intent filter on `MainActivity` (`singleTask`), handled in `AppState.handleUrl` |
| Widgets / Live Activity previews | kept as design previews only |

## Status (2026-11-05)

Every iOS screen is ported and the app runs on the Pixel emulator (API 37) on demo data. Not yet done or approximated:

- **Pro purchases from Android are not verified by the API** (it only verifies Apple's StoreKit JWS); add Google Play Developer API verification before trusting `isPro` from Android.
- **Google sign-in** needs `GOOGLE_WEB_CLIENT_ID` plus an Android OAuth client (package + SHA-1) in the same Google Cloud project; untested against a real client ID.
- **Lesson "listen" questions** use an "I played it" button instead of the microphone chord check (`LessonView.listen()` has the TODO).
- **Listen** never names the song (no ShazamKit on Android); the result always goes through Review.
- **Typed PDFs** are read as images (no text layer), so Myanmar lyrics in PDFs need the server reader like photos.
- **Page detection** is a brightness-profile heuristic, weaker than iOS's document segmentation; the crop step lets the player fix it.
- Material containers remain for sheets, dialogs and dropdown menus; everything inside is the app's glass design.
- Not run yet: `lintDebug`, a real device (microphone/camera quality, variable-font weights in exported PNG/PDF), the AdMob test banner on a device with Play services.

Porting conventions for new screens are in `AGENT_BRIEF.md`.

## Checks

```sh
./gradlew :app:testDebugUnitTest      # parser, pitch tracker, chord estimation, follower, nod gesture
./gradlew :app:lintDebug
```
