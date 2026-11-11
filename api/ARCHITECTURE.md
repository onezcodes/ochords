# Architecture

## Pattern: Service Layer

```
routes/api.php                 /api/v1 routes, rate limits per group
app/Http/Controllers/Api/      HTTP only: Form Request in → service → API Resource out
app/Http/Requests/             Validation (Auth/, Songs/, Community/)
app/Http/Resources/            JSON shapes — mirror the iOS app's Codable models exactly
app/Policies/                  SongPolicy (view / save / remove a sheet) · SetlistPolicy (owner only)
app/Services/Ocr/              OcrEngine interface · GoogleVisionOcr · DisabledOcr (bound in AppServiceProvider from config)
app/Services/Social/           SocialTokenVerifier interface · JwksSocialTokenVerifier (Apple/Google identity tokens; tests bind a fake)
app/Services/StoreKit/         TransactionVerifier interface · AppleTransactionVerifier (StoreKit 2 JWS, chain to Apple Root CA - G3)
app/Services/                  Business logic; the only layer that queries Eloquent (Auth, Song, Community, Subscription, Export, Moderation…)
app/Models/                    Eloquent models, relations, scopes, casts
app/Enums/                     Tier, SongSource, SectionKind, ReportReason, ReportResolution, FeedbackKind, NotificationCategory, SocialProvider
resources/certs/               AppleRootCA-G3.pem (fingerprint pinned in AppleTransactionVerifier)
database/seeders/              LearnPathSeeder (content, every env) · DemoSeeder (synthetic data, non-production only)
routes/console.php             ochords:purge-deleted-accounts (03:00) · ochords:purge-old-records (03:30) · moderation: ochords:reports, ochords:reports:resolve, ochords:sheets:hide
```

No repository layer: most endpoints are straight reads, so the indirection wouldn't pay for itself. Keep to this pattern — controllers never query models directly.

## Contract with the iOS app

`app/Http/Resources/*` produce exactly what `ios/OChords/Models/Models.swift` decodes, and the iOS client (`ios/OChords/Services/RemoteBackend.swift`) maps each `Service` protocol method onto one endpoint. **If you change a resource, change the Swift model in the same PR** — Swift's decoder fails on a missing non-optional key. `tests/Feature/*` pin the shapes the app relies on.

Notable choices:

- **Song IDs come from the app** (UUIDs) so a sheet created offline keeps its ID when it syncs. `PUT /songs/{id}` is create-or-update; on someone else's *public* sheet it saves it to your library instead (the app calls `save` for both).
- **Sections are JSON** (`songs.sections`), normalised on write so every section has all keys the app needs.
- **Private note (`myNote`) and `isPublic`** are only returned to the sheet's owner. `isPublic` left out of an update keeps the current value.
- **Explore "votes"** are saves (rows in `song_user`); `rating` is the average saver rating.
- **Learning progress** is `fret_progress`; `LearnService` derives each fret's `done/current/locked` state per player.
- **Setlists** are `PUT /setlists/{id}` with the app's ID, like songs; `setlist_song` cascades from both setlists and songs, and removing a saved sheet also takes it out of that player's setlists. `date` is the last edit date, for the card.
- **Sign in with Apple/Google** stores only the provider's subject ID (`users.apple_id` / `google_id`); matching by email requires the provider to say it is verified. **Pro** is `users.pro_until` (+ the original transaction ID) as last synced from a verified StoreKit 2 transaction — no App Store Server API calls, see README.
- **Songs are stored as Unicode.** `ZawgyiConverter` checks each saved song's text (all fields together) for Zawgyi and converts it with ICU's `Zawgyi-my` transform (PHP `intl`, ICU ≥ 60). The app does the same before saving; the server check covers other clients and older app versions. Detection rules match the app's `Zawgyi.swift` — keep them in step.

## Personal data (Hong Kong PDPO inventory)

| Data | Where | Purpose (DPP1) | Protection | Retention (DPP2) |
|---|---|---|---|---|
| Name, handle | `users` | Shown on shared sheets and profiles | — (public by design) | Until the account is deleted |
| Email | `users.email` (encrypted), `users.email_hash` (HMAC) | Sign-in, password reset | Laravel `encrypted` cast (AES-256, APP_KEY); lookups by HMAC with `OCHORDS_LOOKUP_KEY` | Until the account is deleted |
| Password | `users.password` | Sign-in | bcrypt | Until the account is deleted |
| City | `users.city` (encrypted) | Shown on profile (optional) | `encrypted` cast | Until the account is deleted |
| Private song note | `songs.my_note` (encrypted) | The player's own reminder | `encrypted` cast; only returned to the owner | With the song |
| Report detail | `reports.detail` (encrypted) | Moderation | `encrypted` cast; shown on the console only with `--detail` | Open reports until resolved; resolved ones purged after `OCHORDS_REPORT_RETENTION_DAYS` (180) |
| Feedback message | `feedback.message` (encrypted) | Product support | `encrypted` cast | Purged after `OCHORDS_FEEDBACK_RETENTION_DAYS` (365); the account link is dropped when the account is deleted |
| Deletion reason | `users.deletion_reason` (encrypted) | Product feedback | `encrypted` cast | Deleted with the account |
| Apple / Google user ID | `users.apple_id`, `users.google_id` | Sign in with Apple / Google | Opaque provider IDs; tokens are never stored or logged | Until the account is deleted |
| Pro subscription | `users.pro_until`, `users.pro_original_transaction_id` | O Chords Pro entitlement | The transaction JWS is verified and discarded; only expiry and the Apple transaction ID are kept | Until the account is deleted |
| Practice minutes, points, progress | `practice_days`, `users`, `fret_progress` | Streaks, tiers, learning path | — | Until the account is deleted |
| Sheet photos (`POST /scans`) | Not stored — memory only for the request; text never logged | Read Myanmar lyrics | Sent over HTTPS to **Google Cloud Vision (outside Hong Kong)** with the key in a header | Not kept by us; Google's retention per its Cloud data terms |

- **Account deletion (DPP2/DPP6):** `DELETE /me` signs out everywhere and stamps `deletion_requested_at`; `ochords:purge-deleted-accounts` deletes the user and cascades their songs, setlists, notifications, progress and saves after `OCHORDS_DELETION_GRACE_DAYS` (30). Signing in during the grace period cancels it, as the app promises.
- **Data access (DPP6):** `GET /me/export` returns everything the account holds, decrypted, in one JSON document; other players appear only as the titles of public sheets this player saved.
- **Retention:** `ochords:purge-old-records` runs daily; open reports are never purged.
- **Moderation:** console commands only (`ochords:reports`, `ochords:reports:resolve`, `ochords:sheets:hide`); hiding makes the sheet private and notifies the owner and reporters in-app.
- **Logs** record event names and user/record IDs only — never emails, passwords, tokens, identity tokens, transactions or free text. Auth successes and failures are logged.
- **Not yet built:** App Store Server Notifications (so a refund/cancellation reaches the account without the app syncing); account linking/unlinking from the profile screen; a way to answer a data-access request by email rather than in-app.
- Hosting location and any cross-border transfer must be confirmed with the client before production (PDPO). **Scan photos go to Google** — the app's camera screen and privacy policy now say so; confirm Google's Cloud data processing terms are in place before turning server reading on.

## Sheet reading

The app reads every photo on the phone (Apple Vision: chords, English) unless the server can do better: when signed in, it uploads the page to `POST /scans`, which asks Google Cloud Vision (hinted to Burmese + English) for words and their boxes. Any failure — guest, offline, `503` (reading off / Google down), `429` (daily limit) — falls back to the phone, so a scan never fails because of the server. Each attempt counts towards `OCHORDS_SCAN_DAILY_LIMIT` because each costs a Google call.

## Not in this API

- **Turning text into a song** — `POST /scans` only returns positioned text; the app's `ChordSheetParser` builds the song, so on-device and server reading share one parser.
- **Listen** (song identification and chord estimation) — simulated in the app.
- Jam rooms, ads, push notifications. Pro purchases happen in StoreKit on the phone; the API only mirrors the verified result.
