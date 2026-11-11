# O Chords — iOS demo

SwiftUI demo of the O Chords app, built from the design canvas (`docs/design.txt`). Everything runs on mock data; the API and server come later.

## Run

```sh
brew install xcodegen      # once
cd ios
xcodegen generate          # regenerate OChords.xcodeproj after adding/removing files
open OChords.xcodeproj     # run the "OChords" scheme on an iPhone simulator
```

Requires Xcode 16+ and iOS 18+. Portrait iPhone only. Packages (Swift Package Manager, pinned in `project.yml`): Google Mobile Ads, Google Sign-In. From the command line:

```sh
xcodebuild build -project OChords.xcodeproj -scheme OChords -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -clonedSourcePackagesDirPath build/SourcePackages -derivedDataPath build/DerivedData
```

## Layout

```
OChords/
  App/            AppState (session, prefs, navigation), Route + RouteView, root/tab views
  DesignSystem/   Theme tokens (colours, fonts), glass surfaces, buttons, chips, ChordDiagram, brand/logo, ad placeholders
  Models/         Song, SongSection, Profile, Tier, Setlist, lessons, chord voicings (all Codable)
  Services/       Service protocols, MockBackend + MockData, ChordTheory (transpose/capo/easy chords), ChordLibrary
  Features/       Screens by area: Onboarding, Library, Import, Song, Tools, Explore, Learn, Settings
  Resources/      Fonts (Outfit, JetBrains Mono, Noto Sans Myanmar), asset catalog (app icon, Onez logo)
```

## The API

The UI only talks to the `AppBackend` protocols in `Services/Services.swift`. Two implementations:

- `RemoteBackend` (`Services/RemoteBackend.swift` + `APIClient.swift`) — the Laravel API in `../api`. Used whenever `OChordsAPIBaseURL` is set: Debug builds point at the dev server on this Mac by its Bonjour name, `http://Zeno-Macbook.local:8000/api/v1` (change it to your own Mac in `project.yml`; on a real iPhone run the server with `php artisan serve --host=0.0.0.0` and allow Local Network access — see `api/README.md`); set the Release URL in `project.yml` before shipping. The token lives in the Keychain; a saved session is restored during the splash.
- `MockBackend` — demo data, no server. Used when the URL is empty, and always for the `-demoScreen` / `-demoScanFile` screenshot harness or with `-useDemoData YES`.

**Guest mode** ("Browse shared chords first"): no account needed. Guests browse Explore and Learn, scan (on the phone only — Myanmar lyrics need an account), and keep songs and setlists on the phone (`Services/GuestStore.swift`, Application Support). Guest mode is remembered across launches. Creating an account or logging in moves the guest's songs and then their setlists into the account (a setlist whose songs didn't all upload stays on the phone). Reporting a sheet needs an account.

**Setlists** save like songs: `AppState.saveSetlist` / `deleteSetlist` keep the account's copy through `PUT`/`DELETE setlists/{id}` (guests: the phone). Long-press the current setlist to delete it, or a song row to take it out.

**Sign in with Apple / Google** (`Services/SocialSignIn.swift`): Apple's sheet gives an identity token that goes to `POST auth/apple` (the Sign in with Apple capability is in `project.yml`; on a real device the app ID needs it enabled in the developer account). Google uses the GoogleSignIn package and needs the iOS OAuth client ID in `project.yml` (`OCHORDS_GOOGLE_CLIENT_ID`, plus its reversed form as `OCHORDS_GOOGLE_URL_SCHEME`); until it's set the button says Google sign-in isn't set up. The demo build fakes both.

**Password reset:** the email's `ochords://reset-password?token=…` link opens `ResetPasswordView` (email + new password → `POST auth/reset-password`). The `ochords` URL scheme is registered in `project.yml`.

Listening stays on the phone. Sheet scanning reads on the phone, plus on the server for Myanmar lyrics when signed in (see below). If you change a JSON resource in the API, change the matching `Codable` model here in the same change.

DEBUG-only: `-demoSignInEmail demo@ochords.test -demoSignInPassword <DEMO_PASSWORD> [-demoTab explore|setlists|learn] [-demoNotifications YES] [-demoScanFile <path>]` signs in through the API at launch (and then scans the file, through the server), for checking screens against the local server.

## Scanning a sheet (real, on device)

The Library has three ways in: **Scan** (camera), **Photos** (photo picker), and **Files** (PDF or image). Photos and Files open the scan screen with the picked sheet (`Route.importPicked`), without the camera. The free-scan limit applies to all three.

**Print or PDF:** the whole song, laid out by measuring each section at the real column width (`PrintPaginator`). Nothing is clipped.
- A section that doesn't fit the space left moves to the next column (2 columns: left, then right) or page.
- A section taller than a whole column is split between its lines and continues as "VERSE 1 (CONT.)".
- **Breaks:**
  - **Auto** keeps sections together where possible.
  - **Each section** starts every section on a new page.
  - **Manual** has a "New page before …" switch per section.
- The footer shows the real date.
- DEBUG: `-demoScreen print -demoPrintExport YES` writes the PDF to the app's tmp/print.pdf. `-demoPrintLayout`, `-demoPrintBreaks` and `-demoPrintLarge` set the options, and `-demoScreen printScanned` prints a one-long-section song.

**Save as image:** song images are always **1080 × 1648 px**, the size Myanmar chord sheets are shared at (`SaveImageSheet`, 360 × 549⅓ pt rendered at 3×).
- Layout: title and artist top left, chord diagrams top right, section labels in a left gutter ("V1.", "Cho.", "Cho 2.", "Br."), chords over the lyrics, instrumental parts as "Music – C / D / Em".
- The text size is fitted to the page (`SaveImageSheet.fitting`, a search over 0.35–1.8× the design size): larger for a short song, smaller for a long one, so the whole song always fits and nothing is cut off.
- "How much of the song" picks Chorus, Verse + chorus or Whole song (the default). Size options apply only to single-chord cards.
- DEBUG: `-demoScreen saveImage -demoSaveImageExport YES` writes the rendered image to the app's tmp/save-image.png.

**Editing lyrics:** in the song page's More menu, **Edit lyrics** (your own sheets) turns on edit mode. Tap a line to edit it, use "+ Add line" at the end of a section, or delete a line; Done leaves edit mode. Changes save like any library change.
- The editor (`Features/Shared/LyricLineEditor.swift`, also used by Review after a scan) edits just the words by default.
- The chords stay with their words (`ChordSheetParser.reword`). Old and new words are lined up (longest common subsequence): a chord on a word that's still there stays on it, at the same spot inside the word, so a fixed typo keeps its chord exactly. A chord on a reworded word moves to the matching spot between the unchanged words around it, and a chord on a deleted word moves to the next word left, or the line's end.
- "Edit chords too" switches to the line as text with chords in [brackets].
- DEBUG: `-demoScreen song -demoEditLyrics YES` (add `-demoEditLine YES` to open a line).

**Fixing title and artist later:** in the song page's More menu, **Title & artist** edits them for your own sheets (scanned or listened, not community sheets). Zawgyi typed in is converted, an empty title isn't allowed, and the change saves like any library change: to the account when signed in, on the phone for guests. The song page shows the library's copy, so the new name appears straight away. DEBUG: `-demoScreen song -demoEditDetails YES` opens the editor.

**Crop step** (`Features/Import/CropView.swift`), for a picked photo or image file (PDFs and camera captures skip it):
- Four draggable corners start on the page edges `ScanImage.detectPage` finds. It uses document segmentation, with the rectangle detector as a fallback because segmentation can't run on the simulator.
- Outside the page is dimmed, and the corners can't be dragged into a crossed shape.
- Rotate turns the photo 90° and looks for the page again; "Whole photo" / "Detect page" switches between the two.
- Scan straightens the area (`ScanImage.flatten`, perspective correction) and reads it with the automatic page crop off.
- DEBUG: `-demoScreen crop -demoCropFile <image>` opens the crop step with a photo; `-demoCropAuto YES` presses Scan.

Add a song → Scan sheet uses the camera, the photo library, or Files (PDF or image). `OnDeviceScanService` (`Services/SheetScanner.swift`) straightens the photo to the page, reads it with Apple Vision (`Services/VisionOCR.swift`), and `ChordSheetParser` turns the text into sections of ChordPro lines (`[C]lyric [G/B]lyric`): title, artist, key, capo, tempo, section headers, intro bars, and each chord placed over the syllable under it.

- **Myanmar lyrics are read on the server** (`ServerOCREngine` → `POST /api/v1/scans` → Google Cloud Vision) when the player is signed in and the API has it turned on. Apple Vision has no Burmese model, so otherwise — guest, offline, server reading off, daily limit — the scan falls back to the phone and Myanmar lines come through as "type the lyrics" lines with their chords in place. PDFs with a text layer keep their real Myanmar text.
- Chords over Myanmar lyrics snap to syllable starts (never splitting a killed final like စက်).
- **Chords the server skipped** (`VisionOCREngine.addMissedChords`, run after every server read). Google Vision drops some lone short words: a single "C" or "F" over a lyric, a chord after the last word. Ink no returned word covers is read on the phone. This only happens in chord rows, rows without Myanmar text, or past the last word of a Myanmar line, and a read is kept only if it's a chord name.
- **Glued chords** (`ChordSheetParser.gluedChords`): close chords read as one word ("AmEm", "CG/B", "Am7F") are split into separate chords, placed across the word's width. At least one part must be longer than a letter, so words like "CAB" stay words.
- **Bar lines read as letters** (`ChordSheetParser.barToken`): Google reads "( | C | Am | G | )" as "(ICI | Am | IGI)". In a row that already has two clean chords, bar-line look-alikes ("|", "I", "l", "1", "!") and brackets stuck to a chord are dropped ("(IC" → C), and words made only of them are ignored. English words like "I" in lyric rows are never touched. Chord rows in brackets or with bar lines are instrumental: kept as their own chord line, not put over the lyric below.
- **Time signature before the title** ("6/8 beat Mary") is removed from the title.
- **Stray dots:** a lone "." or "," in a Myanmar line (often in the gap before an end-of-line chord) is dropped; ellipses are kept.
- **Measured** on 9 rendered Myanmar sheets read by the real server OCR (normal, tight, black chords, chords at line ends, gutter labels, dense 7th/slash chords, mixed English, serif with red chords, rotated photo):

  | | Before | After |
  |---|---|---|
  | Lines with exactly the right chords | 67 / 72 | 72 / 72 |
  | Chords on the right word | 65 / 72 | 71 / 72 |
  | Chords inside lyrics | 0 | 0 |
  | Titles | 9 / 9 | 9 / 9 |

  The remaining miss is a space Google dropped between two Myanmar words. Gap size can't recover it: inside a Burmese word, Google's pieces are as far apart as words (measured).
- **Sheet furniture** (`ChordSheetParser.sheetFurniture`).
  - Rows of chord-diagram marks ("x o X X 0 0", fret numbers like "23 020", "x32010") are dropped, along with the row of chord names right next to them (a legend, not chords to play). Fret numbers beside the title are removed from it.
  - Strumming patterns written as arrows (↓ ↑) are read into `SongSection.strumPattern` (sent to the API as `strum`). They're joined across lines and split from a credit on the same line. A pattern written with the title applies to every non-instrumental section; one under a section heading applies to that section.
  - A chord row in the top four lines whose chords all sit past the end of the line below is a legend too ("F Em Am Dm" beside the title), even when no diagram marks were read.
  - The title/credit window skips the legend, so a sheet that opens with chord diagrams still gets its title, singer and songwriter.
  - A section label in the first nine lines ("V1, …", "Chorus") with no chords to play above it marks where the song starts. Everything above it is read as title and credits. Labels may end in ":", ".", "," or "-".
- **Title and credits** (`ChordSheetParser.readPreamble`, `credit`, `cleanTitle`, `withoutMeta`). The first four lines above the chords are read as title, credits and key/capo/tempo.
  - Credit lines never become the title. They're recognised by Myanmar keywords (တေးဆို, ဆိုသူ, တေးသံရှင်, သီဆို, တေးရေး, ရေးစပ်, သံစဉ်, ဂီတ…, which must be followed by a separator or a space) or by English keywords at the line start (by, singer, vocals, lyrics, music, composer, arr.…).
  - The artist is the singer: a singer credit, else the short plain line under the title, else the songwriter.
  - A title sharing a line with the key ("ညီလေး   Key-G") is kept, and "Title – Singer" on one line is split.
  - The title is the remaining line with the largest type, with a small boost for being centred and near the top and a penalty for being long like a lyric. Quotes and "Title:" / "ခေါင်းစဉ်:" labels are removed.
  - With no title on the sheet, the title is left empty and Review asks for one (Save stays disabled until it's typed).
- **Chords and labels inside Myanmar lines** (`ChordSheetParser.separateInline`). Myanmar sheets often put chords at the end of the lyric line ("…တာတွေ  Bm"), or the OCR merges a tight chord row into the tall Myanmar line, and put the section label at its start ("V1 မသေချာ…").
  - In a Myanmar line, a Latin word that reads as a chord is a chord (Myanmar lyrics never contain "Bm"). It's lifted into the chord row over that line, joining the chord row just above if there is one.
  - A leading "V1", "Ch", "C1", "Chorus", "Bridge:" etc. starts that section. One-letter labels need a number, so a bare C or B stays a chord.
  - Hold marks after a chord ("D-", "D…") read as the chord.
  - Chords on a verse/chorus/bridge heading row ("Ch  G  Em") go over the next lyric line; intro and solo chords stay as bars.
  - English lines are left alone, because "A" and "Am" can be words.
- **Zawgyi** (the older Myanmar font encoding) is detected and converted to Unicode (`Services/Zawgyi.swift`, using iOS's built-in ICU "Zawgyi-my" transform). Scanned sheets — mostly typed PDFs — show the "We fixed the Myanmar text" screen (skipped with *Always convert automatically*); text typed in Review on a Zawgyi keyboard is converted on save. Detection weighs patterns that can't occur in Unicode Burmese against signs only Unicode has. Those signs are ှ (U+103E), Unicode kinzi, and an asat closing a word. Text counts as Zawgyi only when the Zawgyi patterns win. One odd mark from the reader therefore can't send a Unicode page through the converter, which would break every line (ရပ်→ရပျ). A lone Zawgyi stacked letter isn't enough on its own because Shan/Karen use those code points.
- Vision skips lone chord letters and chord rows next to Myanmar script, so `VisionOCR` also cuts the page into rows and words by ink and re-reads anything the first pass missed.
- Test from the simulator (DEBUG only): `xcrun simctl launch booted com.onezcodes.ochords -demoScanFile <path-in-app-container> [-demoAutoReview YES]`. The simulator's Vision is noticeably weaker than on device/macOS for blurry single letters.

### Re-reading songs when the scanner improves

- Saving a scanned song keeps what the reader saw (`Services/ScanArchive.swift`). For a server read, that's the server's words and the cropped page as a JPEG. For an on-device read, it's the page. For a typed PDF, it's the PDF's text lines.
- This is stored only on the phone, in Application Support/Scans: file protection is on and it's excluded from backup. It's never uploaded again and makes no new call to the server's reader.
- It's deleted with the song. When the player signs out, an account's readings are deleted and a guest's are kept.
- **Raise `Rescan.scannerVersion` whenever reading or parsing improves.** After the library loads, `AppState.rescanImproved()` re-reads every stored song saved under an older version, then merges the result part by part:
  - Title, artist and key: a part the player left as scanned takes the new reading; one they changed is kept.
  - Lyrics and chords: if the player didn't change them, they're replaced. If they did, nothing is replaced. The song page instead shows "The scanner reads this sheet better now", with **Use the new reading** or **Keep mine**.
- The Library shows a toast with the number of songs updated.
- Only songs scanned after this was added can be re-read, since older songs have no stored reading.

## O Chords Pro (StoreKit 2)

`Services/StoreService.swift`: two auto-renewing subscriptions, `com.onezcodes.ochords.pro.monthly` and `.yearly`, in one group with a 7-day free trial (set them up the same way in App Store Connect). The paywall shows the App Store's prices (the design's `[price]` placeholders until they load, and always in the demo build). Pro on the phone is whatever `Transaction.currentEntitlements` says; every verified transaction is also sent to the account (`PUT me/subscription`, the signed JWS) so Pro follows the player to other devices, and an account marked Pro counts on a phone with no subscription of its own. "Restore purchases" calls `AppStore.sync()`.

Local testing: the `OChords` scheme runs with `OChords/OChords.storekit` (Xcode's local App Store — run from Xcode, not `simctl`, for products to load; purchases there are free and renew every few minutes). Sandbox purchases on a device need a sandbox tester account.

## Ads (Google AdMob)

`Services/AdService.swift` + `BannerAdView` / `RewardAdView` / `InterstitialAdView`: an adaptive banner on Library, Popular, Chords by key, Profile and Tiers; rewarded ads for an extra scan or a refill of strings; an interstitial after a finished lesson or session. All only on the free plan. IDs come from `project.yml`: Debug uses Google's public **test** app ID and ad units (always test ads, as the "Test mode" banner shows); Release is empty until the real IDs from the AdMob console go in — with no app ID the SDK isn't started and the app shows its own placeholder cards. The ad-consent screen's choice sets personalised vs non-personalised requests (`npa`), and "Ads that fit me" triggers the iOS tracking prompt (`NSUserTrackingUsageDescription`). Before shipping also add the ad partners' `SKAdNetworkItems` from Google's list (only Google's own is in now).

## Not wired up in the demo

- Nothing: every screen now reaches the API, the App Store or AdMob in a Debug build. The demo build (`-demoScreen`, `-useDemoData YES`) still fakes sign-in, purchases and ads.

## Listen (real)

Library → Listen hears music playing nearby: a speaker, the radio or a band.

- **One microphone stream, two jobs** (`Services/SongListener.swift`):
  - ShazamKit names the song from an audio fingerprint, for up to 30 s. Only the fingerprint leaves the phone, and the sound is never recorded.
  - `ChordEstimator` (`Services/ChordEstimator.swift`, pure logic) estimates the chords.
- **Live:** the chord now, with how sure it is, "next likely" (what followed this chord before), the changes heard so far, and key, tempo and capo, refreshed every 2 s.
- **Chords:** each 0.1 s of sound is matched against major, minor and 7th chords on all 12 roots.
  - A 7th must clearly beat its triad, because a sung melody often touches the 7th.
  - Sound that fits no chord well (under 0.8: room noise, talk, drums alone) counts as silence.
- **Tempo:** autocorrelation of the loudness rises (strums, hits), 60–180 BPM, leaning towards ≈ 100 so half and double tempo don't win.
- **Bars:**
  - One chord per bar of 4 beats. The bar line goes where the chords change most often.
  - A pickup on the same chord merges into the first bar, and the ring-out after the last strum isn't a bar.
- **Key:** a Krumhansl profile fitted to the chords heard.
- **Capo:** suggested only when most chords are barre chords, and only if it makes at least 2 more open shapes.
- **Result:**
  - 4-bar phrases that repeat get the same letter (Part A, B …).
  - A bar under 70 % is dashed for checking.
  - A matched song looks up a community sheet with the same title.
  - A song that wasn't recognised goes through Review to be named.
- **Measured on synthesised strumming** (`scratchpad est` harness: 5 progressions, 76–120 BPM, with and without singing and room noise):
  - every bar right in all 10 runs;
  - tempo within 1 BPM;
  - key right.
- **Over the air** (Mac speakers → simulator mic):
  - 16 of 20 bars right, and the 4 wrong ones were flagged;
  - key and tempo right.
- **ShazamKit setup:** turn on the ShazamKit App Service for the App ID `com.onezcodes.ochords` in the Apple Developer portal. Without it, the song shows as "not recognised" and the chords still work.
- DEBUG: `-demoScreen listen -demoListenAuto <seconds>` starts listening and stops after that long.

## Hands-free page turns (real)

Stage → the hands-free button. The chosen mode is remembered, and runs only while Stage or the Hands-free screen is open. That screen runs the real watcher, so it can be tried there.

- **Foot pedal:**
  - Page-turners are Bluetooth keyboards. Right, down or page down goes to the next section; left, up or page up goes back.
  - Holding the right pedal starts or stops auto-scroll.
  - The screen shows the connected pedal's name (`GCKeyboard`) and the last press.
- **Head nod** (`HeadNodWatcher`, `Services/HandsFree.swift`):
  - The front camera runs at 15 fps, and Vision reads the face's pitch. Frames are never kept.
  - `NodGesture` (pure logic) counts a quick turn of ≥ 13° that comes back within 1 s: nod down for next, tilt up for back.
  - Looking down at the guitar for a while just moves the resting position.
  - "Set up: nod down once" learns which way is down for the phone's angle.
- **Voice** (`VoiceCommands`):
  - English words, on-device recognition when the phone supports it: "next" / "forward", "back" / "previous", "chorus" (next chorus), "stop" / "pause", "play" / "start".
  - Off while Follow me is on, because both use the microphone.
- **Auto-scroll:** at the song's tempo, as before.

## Tuner (real)

The Tuner tab listens through the microphone (`Services/PitchDetector.swift`): `AVAudioEngine` input in `.measurement` mode, pitch by the YIN algorithm (60–1100 Hz, ≈0.1 ¢ on clean tones, no octave errors on strings whose 2nd harmonic is louder), median-smoothed. Tuning for weak and fading notes:

- **Logic lives in `PitchTracker`** (pure Swift, no audio APIs), so it can be tested on recorded or synthesised sound.
- **Noise gate:** the room's noise is the quietest moment of the last 3 s. A new note must be 2.5× louder than that, capped at an RMS of 0.004 so a noisy room never makes soft plucks inaudible. A locked note is followed until it fades to 1.6× the noise.
- **Note lock:** once a clear note is found, readings near it are accepted while the string fades. The lock is released after 0.4 s of silence.
- **Octave check:** if a nearly as deep YIN dip sits at ½, ⅓ or ¼ of the chosen period, the shorter period wins. This removes the octave-down readings real microphones produced.
- **True pitch, not the overtones:** each reading is re-measured from the fundamental and 2nd harmonic only (band 0.75–2.2×). A plucked string's higher overtones run sharp and are loudest just after the pluck, which used to read 7–20 ¢ high and send players chasing "too high → too low".
- **Manual mode:** picking a string makes the tracker listen only to a band of about ±5 semitones around it and search within a fifth. Other strings still ringing, and octaves, can't be taken for it.
- **Window size:** an 85 ms window while the string is loud, so the needle follows the peg; a 170 ms window once a locked note fades below 5× the noise.
- **Stale readings:** 0.3 s after the sound stops, the bubble greys out where it was and the status says "Pluck again", so nobody turns a peg against an old reading.
- **Session rules** (`Services/TuningSession.swift`, pure logic, used by `TunerView`):
  - Auto switches strings after 3 consistent readings (≈ 60 ms), so one stray reading from a still-ringing string can't pull it away.
  - A new pluck (sound jumping 1.8× within 100 ms) releases the lock on the string that was ringing.
  - A reading more than 20 ¢ from an already-tuned string goes to the nearest untuned string, if that is at most 250 ¢ further away. A next string tuned two semitones flat is then not mistaken for its tuned neighbour.
  - A tick is removed only after the string has been more than 12 ¢ off for 1.5 s, not counting the sharp first half-second of each pluck.
  - The number shows the true offset; the bubble stops at ±50 ¢.
- **Synthetic guitar test** (pick position, inharmonic wound strings, sharp attack, pick noise, room noise and 50 Hz hum):
  - firm plucks: 98–99% of readings within 5 ¢;
  - soft plucks: 92–96%;
  - no octave errors;
  - turning a peg: median error 1.2 ¢.
  - Weak spot: a soft pluck while five other strings still ring loudly. Mute the other strings, as with any single-note tuner.
- **Display:** a pitch trail (`TunerTrace`). The note bubble slides toward ♭ or ♯ as soon as a reading arrives (no spring); the line under it scrolls the last 2.5 s, teal inside ±3 ¢ and gold outside, so the direction of each turn of the peg is visible. Readings are smoothed by a median of 3 (≈ 65 ms).
- **Speed:** YIN uses `vDSP` dot products (about 16× faster, identical results).
- **Audio path:** sound comes from an `AVAudioSinkNode` (about every 5–10 ms) into a lock-free ring buffer, read on a 20 ms timer, so 50 readings a second. An input tap was too slow: iOS delivers it in about 100 ms chunks whatever buffer size you ask for.
- **Feedback:** when a string comes into tune, a two-note bell (`Services/TunerChime.swift`, synthesised, E6→B6) plays, a teal ring bursts from the bubble, the string gets its tick and the phone taps. When all six are tuned there is a rising arpeggio, confetti and an "All 6 strings in tune" banner. The bells sit above the 60–1100 Hz detection range, and the detector also ignores the mic while they play (`PitchDetector.ignore(for:)`), so the app never tunes to its own chime. The speaker button next to Auto turns sounds off (`tuner.sounds` in UserDefaults). The tuner session is `.playAndRecord` + `.measurement`, so sound comes from the loudspeaker even with the silent switch on.

DEBUG: `-demoTab tuner -demoTunedStrings 5` starts with five strings ticked, so tuning the last one shows the celebration.

Auto mode picks the nearest string of the chosen tuning; a string is ticked after holding within ±3 ¢ for 0.4 s. The mic runs only while the Tuner tab is visible and the app is in front; audio never leaves the phone.

## Stage: Follow me (lyrics follow your strumming)

In Stage, the switch above the play button chooses **Auto-scroll** (set tempo) or **Follow me**. In Follow me, the app listens through the microphone and moves through the song as the chords are played. The line being played gets a gold bar and is kept in view, other lines dim, and its chord lights up in the chord strip. Tap a line to start or re-sync there; swiping to a section re-syncs to its start.

- **`Services/MicInput.swift`:** the shared low-latency microphone (sink node, ring buffer, 20 ms windows), also used by the tuner.
- **`ChordRecognizer`** (`Services/ChordFollower.swift`): a 0.25 s window becomes a 12-note chroma (spectral peaks 75 Hz–2 kHz, square-root compressed). It's compared with each chord's expected chroma, built from its notes plus 6 overtones per note, so a strum's overtones count for the right chord.
- **`ChordFollower`** (pure logic): the song's chords in play order, sections repeated as the stage plan says.
  - It looks only one or two chords ahead, so repeated progressions are followed in order.
  - It moves on when the next chord beats the current one by 0.04 for 2 readings (≈ 0.2 s).
  - Skipping a chord needs a strong match (0.85) for 4 readings, so a wrong chord that resembles the one after next doesn't jump ahead.
  - Chords one note apart (C → Cmaj7, Am → Am7, G → G/B) are followed as one step, because a strum can't tell them apart.
  - It starts only on the song's first chord, or the first sung line's.
  - Readings must be 2.5× louder than the quietest moment of the last 3 s (gate capped at RMS 0.01).
- **Repeat marks in the lyrics** (`ChordFollower.playOrder`, `repeatCount`).
  - "…lyrics… x2" or "( … ) x 2" at the end of a line repeats that line.
  - A "(" opening one line and ") x2" closing a later one repeats the lines in between, up to 12 lines.
  - "×2", "(x2)", "2x" and "2 times" also work. A ")" without a count is just an aside.
  - Follow me plays each pass, and the highlighted line shows "1st time · ×2" / "2nd time · ×2". Auto-scroll gives repeated lines their extra time.
  - A repeated line with a single chord can't be heard restarting, so the second pass is followed when the next chord comes.
- **`ChordListener`:** mic + follower, ten readings a second; moves arrive on the main thread.
- **Synthetic strumming test** (realistic strings, 8th-note pattern with up-strums, room noise): every chord change in typical progressions was followed, with no early moves, about 0.3 s after the change. This held with singing along, at 120 BPM and with soft strums. It never runs on when the player stops, and stays put when a wrong chord is played.
- **On the simulator** (speakers into the mic), with 6 s of room noise first, it made 16 moves for 16 chords played, in order.
- Weak spot: chords sharing three of four notes (Cmaj7 → Am7) can move a little early.

DEBUG: `-demoScreen stage -stage.followMe YES` opens Stage in Follow me; `-demoScreen stageRepeat` opens a verse with a "( … ) x2" pair of lines.

## Chord check (real)

Tools → Chord check listens for one strum (`Services/ChordCheck.swift`):

- `StrumListener` learns the room's noise for 0.3 s, waits up to 6 s for the level to jump 3× above it, skips the pick's click (50 ms), and keeps 0.5 s of the ringing chord.
- `ChordCheck.verdicts` takes a 65 536-point FFT and fits the spectrum as six strings, each a fundamental plus 10 overtones in a bright and a mellow shape, by non-negative least squares. A string the fit credits with more than its own note shows above the nearby noise is dropped and the rest refitted; this stops a muted low E "ringing" from the chord's own E3/E4.
- Verdicts per string: **Clear / Muted** (good), **Not ringing** (below 12 % of the loudest string), **Ringing** (a muted string above 22 %), **Can't tell** (its note is an octave or twelfth of a lower sounding string, e.g. B and e in C, so the mic can't separate them).
- Synthetic test (7 chords × 20 random strums): clean strums pass 100 %, dead strings found 99 %, ringing muted strings found 99 %.
- Live test: synthesized strums played through Mac speakers into the simulator were read correctly; one run out of nine didn't register a strum. Laptop speakers can't reproduce 82 Hz, so a ringing low E can only be tested with a real guitar.

DEBUG: `-demoScreen practice -demoAutoListen YES` opens the check and starts listening straight away.
- Sign-in, purchases (Pro) and AdMob are simulated. Ads show as labelled placeholders.
- Widgets and the Live Activity are preview screens, not real WidgetKit/ActivityKit extensions.
