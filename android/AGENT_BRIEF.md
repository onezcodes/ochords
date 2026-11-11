# O Chords Android — screen porting brief

You are porting part of the O Chords **iOS** app (SwiftUI, `../ios/OChords`) to the **Android** app in this folder
(Kotlin, Jetpack Compose, Material 3 components only where unavoidable — the look is the app's own glass design, not
Material). The Android foundation already exists and compiles; you add feature screens / services on top of it.

## Read first (do not modify unless told to)
- `app/src/main/java/com/onezcodes/ochords/design/Theme.kt` — colour tokens `OC.*` (adaptive light/dark, read inside
  composables), fonts `outfit(size, weight)`, `mono(size)`, `myanmar(size)` (TextStyle builders), `isDark`.
- `design/Components.kt` — `WarmBackground`, `NightBackground`, `OCPage { }` (page scaffold with status-bar inset),
  `Modifier.glass()`, `Modifier.inkGlass()`, `Modifier.nightGlass()`, `Modifier.plainClickable {}`, buttons
  (`GoldButton`, `InkButton`, `GlassButton`, `DestructiveButton`, `GatedButton`, `CircleIconButton`), `TopBar`,
  `Eyebrow`, `SectionTag`, `StatusPill`, `Chip`, `OCSwitch`, `ToggleRow`, `Segmented`, `GlassList`/`Hairline`/`ListRow`,
  `GroupHeader`, `PlaceholderBox`, `ChoiceCard`, `RadioDot`, `Toast`/`ToastHost`, `GuestAccountCard`, `BottomBar`, `GlassCard`.
- `design/Brand.kt` — `LogoMark`, `Wordmark`, `AppIconTile`, `PoweredByOnez`, `AdBadge`. `design/ChordDiagram.kt` — `ChordDiagram`, `ChordCard`.
- `model/Models.kt` — Song, SongSection, SectionKind, Profile, Tier, Setlist, CommunitySheet, AppNotification, Learn*, ChordVoicing…, `ChordLine.parse/chords`.
- `services/Services.kt` — service interfaces, `ScanSource`, `ScanResult`, `ScanTextLine`/`ScanWord`/`Box`, `ServiceException`, `Throwable.userMessage`.
- `services/ChordTheory.kt`, `services/ChordLibrary.kt`, `services/MockData.kt` (demo content + `MockBackend`).
- `app/AppState.kt` — global state (`LocalApp.current`): session (`profile`, `isGuest`, `isSignedIn`, `isPro`, `displayProfile`),
  prefs (`appearance`, `leftHanded`, `sharpNames`, …), content (`library`, `setlists`, `notifications`, `song(id)`),
  navigation (`push(Route)`, `pop()`, `popToRoot()`, `replaceTop()`, `switchTo(tab, then)`, `modal = Modal.X`), actions
  (`addToLibrary`, `removeFromLibrary`, `saveSetlist`, `signedIn`, `continueAsGuest`, `signOut`, `purchasePro`, `showInterstitial`…),
  `scope` (a Main CoroutineScope), `backend` (AppBackend), `scanner`, `store` (Play Billing), `ads` (AdMob), `activity`.
- `app/Route.kt` — `Route` sealed class (every screen), `MainTab`, `Modal`, `PermissionKind`, `RewardKind`.
- `app/Navigation.kt` — `RouteView` maps routes to composables (signatures are fixed: match them exactly), `tabBarInset`.

## Rules
- **Source of truth is the Swift file(s) named in your task.** Read them completely. Recreate every screen, state,
  copy, colour, spacing and interaction faithfully in native Compose. Multi-state frames are ONE composable with state.
  Text in `[brackets]` is placeholder data — keep as-is. Keep iOS's DEBUG-only screenshot hooks out (not needed).
- One Kotlin file per iOS file (same name, `.kt`), in the package you're told. Replace the stub functions in that file
  (they exist only so the project compiles) — keep their exact signatures, since `Navigation.kt` calls them.
  Private helpers go in the same file (Kotlin has no `fileprivate`; use `private`).
- Use the foundation pieces above instead of re-implementing them. Use `OC.*` tokens, never raw Material colours.
  Icons: `androidx.compose.material.icons.Icons.Outlined/Filled/Rounded.*` (material-icons-extended is in) in place of SF Symbols — pick the closest.
- Navigation: `val app = LocalApp.current; app.push(Route.SongPage(song))`, `app.pop()`. Modals: `app.modal = Modal.Pro`.
  Tab roots must leave `tabBarInset` of bottom padding in their scroll content. Pushed screens draw their own `TopBar`.
  Use `OCPage { }` as the page scaffold (it draws WarmBackground; pass `night = true` for always-dark screens).
- Async work: `LaunchedEffect`/`rememberCoroutineScope`; call suspend backend functions and show `e.userMessage` on failure.
- Permissions (camera/mic/notifications): `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`,
  show the app's `PermissionPrimerView` (via `app.modal = Modal.Permission(kind)`) before the system prompt as iOS does.
- Phone-only features that have no Android equivalent (ShazamKit, Live Activities, WidgetKit, App Tracking prompt):
  keep the screen and its design, implement the nearest Android behaviour or a clearly labelled preview, note it in your report.
- **No new Gradle dependencies.** Everything needed is already in `gradle/libs.versions.toml` (Compose, icons, CameraX, ML Kit
  text + face, AdMob, Billing, Credentials, OkHttp, serialization, haze).
- Code style: idiomatic Kotlin, small composables, comments only where the iOS file had them (translate the intent).
  No logging of personal data or tokens. Synthetic data only.
- Compile: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; ./gradlew :app:compileDebugKotlin -q 2>&1 | grep -E "^e:|error:" | head -40`
  Other agents are editing OTHER packages at the same time, so Gradle may wait for a lock (that's fine) and errors in
  files outside your package are not yours — ignore them and report only your own files as clean. Compile at most a few times,
  near the end; fix every error in your files. Never run `assembleDebug`, `clean` or `--stop`.
- Do not modify files outside your package unless your task says so. If you need a small shared helper, put it in your own file.
- When done, report: the files you wrote, which iOS screens/behaviours are fully ported, what is approximated and why,
  and whether your files compile cleanly.
