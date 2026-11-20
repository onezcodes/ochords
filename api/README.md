# O Chords API

JSON API for the O Chords iOS app (`../ios`) and Android app (`../android`): accounts, the song library, Explore (community sheets), setlists, notifications, the learning path, reports and feedback. Laravel 13, PHP 8.5, Sanctum bearer tokens.

Sheet scanning: the phone reads chords and English itself; `POST /scans` reads **Myanmar lyrics** with Google Cloud Vision (the phone can't). Listen runs on the phone only.

## Run it locally (≈5 minutes)

```sh
cd api
composer install
cp .env.example .env
php artisan key:generate
# Lookup key for emails (they're stored encrypted) and a demo password — local values only:
php -r 'echo "OCHORDS_LOOKUP_KEY=".bin2hex(random_bytes(32))."\n";' >> .env
echo 'DEMO_PASSWORD=Chords2026' >> .env
touch database/database.sqlite
php artisan migrate --seed          # learning path + synthetic demo data
php artisan serve --host=0.0.0.0    # on your Wi-Fi too, so a real iPhone can reach it (plain `php artisan serve` = this Mac only)
```

### Marketing site (`/`, `/privacy`, `/support`)

The public home page is built from `../web` into `public/marketing`. Before first `artisan serve` (or after marketing changes):

```sh
cd ../web && npm ci && npm run build && cd ../api
```

Dev-only hot reload: `cd web && npm run dev` (port 5174). Details in `../web/README.md`.

Sign in from the app (Debug build) or with curl as `demo@ochords.test` / your `DEMO_PASSWORD`:

```sh
curl -s -X POST http://127.0.0.1:8000/api/v1/auth/sign-in \
  -H 'Accept: application/json' -H 'Content-Type: application/json' \
  -d '{"email":"demo@ochords.test","password":"Chords2026"}'
```

The Debug iOS build talks to `http://<your-mac>.local:8000/api/v1` (set in `ios/project.yml`; find your name with `scutil --get LocalHostName`). The simulator and an iPhone on the same Wi-Fi both reach it — the iPhone only if the server was started with `--host=0.0.0.0`, and after you allow **Local Network** access when the app asks. `--host=0.0.0.0` makes the dev API (debug pages included) reachable by anyone on that network: use it on trusted Wi-Fi only, and stop it when you're done.

### Reading Myanmar sheets (Google Cloud Vision)

Off by default (`OCHORDS_SCAN_DRIVER=none`: the app reads on the phone, without Myanmar lyrics). To turn it on:

1. In Google Cloud: enable the **Cloud Vision API**, create an API key, and restrict it to the Cloud Vision API (and your server's IPs in production). Set a budget alert.
2. In `.env`: `OCHORDS_SCAN_DRIVER=google`, `GOOGLE_VISION_API_KEY=<key>` (never commit it), optionally `OCHORDS_SCAN_DAILY_LIMIT` (default 30 per player per day).

   With the gcloud CLI (writes the key into `.env` without showing it):
   ```sh
   gcloud projects create <project-id> --name="O Chords Vision dev"
   gcloud billing projects link <project-id> --billing-account=<BILLING_ACCOUNT_ID>
   gcloud services enable vision.googleapis.com apikeys.googleapis.com --project=<project-id>
   gcloud services api-keys create --project=<project-id> --display-name="O Chords API - Vision only" \
     --api-target=service=vision.googleapis.com
   gcloud services api-keys get-key-string <KEY_UID> --project=<project-id> --format="value(keyString)" \
     | sed 's/^/GOOGLE_VISION_API_KEY=/' >> .env      # then remove any older GOOGLE_VISION_API_KEY line
   ```
   Production: a separate key, also restricted to the server's IP addresses (`--allowed-ips=…`).
3. Uploads: the app sends a ≤ 2000 px JPEG (usually < 1 MB); make sure PHP's `upload_max_filesize`/`post_max_size` allow 4 MB.

Pricing (check Google's current page): first 1,000 images/month free, then about US$1.50 per 1,000. Google offers no monthly quota for Vision, so the API caps its own calls: `OCHORDS_SCAN_MONTHLY_LIMIT` (default 900, all players together). Past it, `/scans` returns 503 and the app reads on the phone until the month ends. The count lives in the cache store — `php artisan cache:clear` resets it.

Dev project `ochords-vision-dev` also has a Cloud Billing budget, "O Chords Vision dev - keep free" (THB 30/month; emails at 50%, 90%, 100% and forecast 100%). A budget only warns; the monthly limit above is what keeps it free.

## Checks (run before every commit)

```sh
vendor/bin/pint          # PSR-12 formatting
vendor/bin/phpstan       # Larastan, level 6
php artisan test         # feature tests (SQLite in memory)
composer audit           # known-vulnerable dependencies
```

## Endpoints (`/api/v1`)

All requests and responses are JSON; send `Accept: application/json`. Authenticated routes need `Authorization: Bearer <token>`. Response shapes match the app's `Codable` models (camelCase, string IDs, ISO-8601 UTC dates). Lists are plain JSON arrays.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `auth/sign-up` | – | `{name, email, password, device_name?}` → `201 {token, profile}` |
| POST | `auth/sign-in` | – | `{email, password, device_name?}` → `{token, profile}`; cancels a pending account deletion |
| POST | `auth/forgot-password` | – | `{email}` → `202` always (no account enumeration); emails an `ochords://reset-password?token=…` link |
| POST | `auth/reset-password` | – | `{email, token, password}` → `204`; signs out every device |
| POST | `auth/apple` | – | Sign in with Apple: `{identityToken, name?, device_name?}` → `{token, profile}` (`201` when the account was just created, `200` otherwise). Matches by Apple user ID, then by the token's email (links the account), else creates one. `401 {message}` if the token doesn't verify |
| POST | `auth/google` | – | Sign in with Google: `{idToken, device_name?}` → same as Apple. `503 {message}` until `OCHORDS_GOOGLE_CLIENT_ID` is set |
| POST | `auth/sign-out` | ✓ | Revokes the current token → `204` |
| GET | `me` | ✓ | The app's `Profile` (includes `isPro`) |
| DELETE | `me` | ✓ | `{reason?}` → `202`; schedules deletion after the grace period, signs out everywhere |
| GET | `me/export` | ✓ | Data-access request (PDPO): everything the account holds, decrypted, as one JSON document — `{exportedAt, profile, songs, savedSheets, setlists, notifications, learning: {fretProgress, practiceDays}, reports, feedback}`. 3/min |
| PUT | `me/subscription` | ✓ | O Chords Pro: `{transactionJWS}` (StoreKit 2 `Transaction.jwsRepresentation`) → the `Profile` with `isPro`. `401 {message}` if Apple didn't sign it; `422` if it's malformed, for another app/product, or a Sandbox purchase in production |
| DELETE | `me/subscription` | ✓ | "Restore purchases found nothing": clears Pro → the `Profile` |
| GET | `songs` | ✓ | Library: own sheets + saved public sheets |
| GET | `songs/{id}` | ✓ | One sheet (public, own or saved) |
| PUT | `songs/{id}` | ✓ | Create/update own sheet by the app's ID — or, for someone else's public sheet, save it to the library |
| DELETE | `songs/{id}` | ✓ | Delete own sheet, or remove a saved one from the library |
| POST | `songs/{id}/reports` | ✓ | `{reason, detail?}` → `201` |
| POST | `scans` | ✓ | multipart `image` (JPEG/PNG ≤ 4 MB) → `{engine, lines: [{confidence, words: [{text, x, y, width, height}]}]}` (boxes 0–1, top-left). `503` when server reading is off or Google fails — the app then reads on the phone. `429` past the daily limit. The photo is not stored. |
| GET | `explore?q=` | – | Public sheets (`CommunitySheet`), search by title/artist/uploader |
| GET | `explore/popular` | – | Most-played public sheets |
| GET | `users/{id}` | – | A player's `UserSummary` |
| GET | `setlists` | ✓ | The player's setlists with ordered `songIDs` |
| PUT | `setlists/{id}` | ✓ | `{name, note?, songIDs}` → `201` created / `200` updated, the `Setlist`. Every song must be in the library (own or saved), else `422`. `403` for someone else's setlist |
| DELETE | `setlists/{id}` | ✓ | `204`, owner only |
| GET | `notifications` | ✓ | Newest first |
| POST | `notifications/read-all` | ✓ | `204` |
| GET | `learn/path` | – | Positions and frets, with per-player `state`/`stars` when signed in |
| GET | `learn/frets/{id}/questions` | – | Lesson questions (answers included: lessons are checked on the phone) |
| POST | `feedback` | – | `{kind, message}` → `201`; works for guests |

Codes the API accepts: report `reason` — `wrong_chords`, `duplicate`, `copyright`, `offensive`, `spam`, `other`; feedback `kind` — `broken`, `wrong_chords`, `idea`, `other`; song section `kind` — `verse`, `chorus`, `music`, `flow`, `bridge`, `intro`.

Errors: `401 {message}` (no/expired token), `403`/`404 {message}`, `422 {message, errors: {field: [..]}}` (validation; the app shows the first message), `429` (rate limited).

Rate limits: 120 requests/min per user (or IP); sign-in, sign-up, Apple/Google sign-in and password resets 10/min per IP; reports and feedback 10/min; data export 3/min.

### Sign in with Apple and Google

The app does the sign-in and sends the identity token; the API verifies the signature against the provider's published keys (cached for an hour), the issuer, the audience and the expiry, then finds or creates the account. Keys: `OCHORDS_APPLE_BUNDLE_ID` (the token's audience; default `com.onezcodes.ochords`) and `OCHORDS_GOOGLE_CLIENT_ID` (the iOS OAuth client ID; empty = Google sign-in off). Accounts created this way get a random password — "forgot password" still works for them. Linking by email only happens when the provider says the email is verified.

### O Chords Pro

Subscription state lives on the account (`pro_until`), synced by the app after a purchase, renewal or restore. The API verifies the StoreKit 2 transaction offline: the certificate chain in its header must end at **Apple Root CA - G3** (bundled at `resources/certs/AppleRootCA-G3.pem`, SHA-256 `63:34:3A:BF:B8:9A:6A:03:EB:B5:7E:9B:3F:5F:A7:BE:7C:4F:5C:75:6F:30:17:B3:A8:C4:88:C3:65:3E:91:79`, from https://www.apple.com/certificateauthority/), the leaf must carry Apple's in-app purchase marker, and the leaf key must verify the signature. Accepted products: `OCHORDS_PRO_PRODUCTS`; Sandbox/Xcode transactions only when `OCHORDS_ALLOW_SANDBOX_RECEIPTS` (default: on outside production).

**Android:** the Android app sends `PUT me/subscription` with `{purchaseToken, productId, platform: "android"}` (Google Play Billing). The API does not verify Play purchases yet — it rejects them as malformed (`422`), so Pro bought on Android stays phone-only until Play Developer API verification is added.

**Residual risk:** the server never calls the App Store Server API, so a refund, cancellation or billing problem is only seen when the app next syncs — `isPro` is as fresh as the last `PUT me/subscription`. Expiry is enforced (`isPro` turns false when `pro_until` passes). Add App Store Server Notifications before relying on Pro for anything that costs money server-side.

## Commands

```sh
php artisan ochords:purge-deleted-accounts      # daily 03:00 — accounts past the deletion grace period
php artisan ochords:purge-old-records           # daily 03:30 — resolved reports older than OCHORDS_REPORT_RETENTION_DAYS (180), feedback older than OCHORDS_FEEDBACK_RETENTION_DAYS (365)
php artisan ochords:reports [--detail]          # open reports (ID, song, reason, reports on that song, created); --detail adds the reporter's text
php artisan ochords:reports:resolve <id> [--hide|--dismiss]   # fixed by default; --hide makes the sheet private, resolves its other reports, notifies owner and reporters
php artisan ochords:sheets:hide <songId>        # take a sheet off Explore without a report
php artisan ochords:sheets:unhide <songId>      # put a private sheet back on Explore
php artisan ochords:admin:grant <handle>        # promote staff for /admin
php artisan ochords:admin:revoke <handle>       # demote staff (refuses last admin)
php artisan ochords:admin:set-password <handle> <password>
```

### Admin panel (Filament)

Staff ops live at `/admin` (session auth, separate from Sanctum). Access requires a staff **role** (`super_admin`, `admin`, `moderator`, `support`) via Spatie laravel-permission; `users.is_admin` is kept in sync as a fast mirror. Sign-in is email + password (lookup uses `email_hash`). Seed roles with `RolesAndPermissionsSeeder` (runs in `db:seed`).

```sh
php artisan db:seed --class=RolesAndPermissionsSeeder
php artisan ochords:admin:grant {handle} --role=super_admin   # also: admin, moderator, support
php artisan ochords:admin:revoke {handle}
php artisan ochords:admin:set-password {handle} {password}   # for social-only accounts
```

The panel covers reports, feedback, sheets (hide/unhide notes, feature on Explore, **Import from scan** — JPEG/PNG/PDF, margin trim + light deskew, multi-page PDF stitch when Imagick is installed, OCR + ChordSheetParser, catalog owner `ochords`, then edit ChordPro), users (roles, Pro, deletion, tokens), Learn CMS, **Roles / Permissions / Permission matrix**, **audit log**, scan quota stats, and staff **2FA** (required when `OCHORDS_ADMIN_REQUIRE_2FA=true`). Sheet import needs `songs.create` and `OCHORDS_SCAN_DRIVER=google` with a Vision key. Abilities are permission-gated. Player `/api/v1` never exposes `is_admin`, roles, or permissions.

Also: `POST /api/v1/webhooks/app-store` (App Store Server Notifications), Android Play sync when `OCHORDS_PLAY_*` is set, Explore `isFeatured`, Telescope on local/staging only.

Artisan moderation commands remain as a CLI fallback (`ochords:reports`, `ochords:reports:resolve`, `ochords:sheets:hide`, `ochords:sheets:unhide`). Hidden sheets stay in the owner's library (and in libraries that saved them) as private; the owner is told in-app.

See `ARCHITECTURE.md` for how the code is organised and the personal-data inventory, and `DEPLOYMENT.md` for running it in staging/production.

## Who to ask

The Visible One backend team. Security or data-protection questions: it@visibleone.com.
