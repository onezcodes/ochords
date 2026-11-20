# Deployment

> Align this with the team's deployment runbook in Notion before the first staging deploy; this file lists what the app itself needs.

## Requirements

- PHP 8.5 with `pdo_mysql`, `intl` (ICU ≥ 60, for Zawgyi conversion), `mbstring`, `openssl`
- MySQL 8 (InnoDB, utf8mb4 — Myanmar text), encrypted at rest
- A queue worker (password-reset emails are queued) and the scheduler (account purge)
- HTTPS only (TLS 1.2 minimum, 1.3 preferred) in front of the app; the iOS Release build refuses plain HTTP

## Configuration (environment / secrets manager — never committed)

| Variable | Notes |
|---|---|
| `APP_ENV=production`, `APP_DEBUG=false` | Debug off: error responses must not include stack traces |
| `APP_KEY` | Encrypts emails, notes, reports, feedback. **Losing or rotating it without re-encryption makes that data unreadable.** Keep it in the secrets manager and back it up separately from the database. |
| `OCHORDS_LOOKUP_KEY` | HMAC key for email lookups, separate from `APP_KEY`. Changing it makes every account unfindable. |
| `APP_URL` | Public HTTPS URL |
| `DB_CONNECTION=mysql`, `DB_HOST`, `DB_DATABASE`, `DB_USERNAME`, `DB_PASSWORD` | Least-privilege DB user (no root) |
| `MAIL_*` | Transactional mail provider for reset emails |
| `QUEUE_CONNECTION` | `database` or `redis` |
| `OCHORDS_DELETION_GRACE_DAYS` | Default 30 |
| `OCHORDS_REPORT_RETENTION_DAYS`, `OCHORDS_FEEDBACK_RETENTION_DAYS` | Defaults 180 / 365 — purged daily by `ochords:purge-old-records` |
| `OCHORDS_APPLE_BUNDLE_ID` | Audience for Sign in with Apple tokens and StoreKit transactions (default `com.onezcodes.ochords`) |
| `OCHORDS_GOOGLE_CLIENT_ID` | iOS OAuth client ID for Sign in with Google; empty = Google sign-in returns 503 |
| `OCHORDS_PRO_PRODUCTS` | Comma-separated product IDs that grant Pro |
| `OCHORDS_ALLOW_SANDBOX_RECEIPTS` | Leave unset in production (Sandbox transactions rejected) |
| `OCHORDS_SCAN_DRIVER` | `google` to read Myanmar lyrics on the server; `none` = phone only |
| `GOOGLE_VISION_API_KEY` | Secret. Restrict to the Cloud Vision API and the server's IPs; set a Google budget alert |
| `OCHORDS_SCAN_DAILY_LIMIT` | Scans per player per day (default 30) — bounds Google cost |
| `OCHORDS_SCAN_MONTHLY_LIMIT` | Google calls per month, all players (default 900 = inside the free tier). Raise it — or `0` for no limit — once production billing and a budget alert are set up. Use a shared cache store (database/redis) so all servers count together. |

Do **not** set `DEMO_PASSWORD` in production; `DemoSeeder` doesn't run when `APP_ENV=production`.

## Deploy steps

```sh
# Marketing site (landing / privacy / support) → public/marketing
(cd ../web && npm ci && npm run build)

composer install --no-dev --optimize-autoloader
php artisan migrate --force
php artisan db:seed --class=LearnPathSeeder --force   # learning-path content; upserts by id (overwrites admin edits for those ids)
php artisan config:cache && php artisan route:cache
php artisan queue:restart
```

`GET /`, `/privacy`, and `/support` serve the built HTML. Assets are static under `/marketing/…`. If `public/marketing` is missing, those routes return 503.

Processes to keep running:

```sh
php artisan queue:work --tries=3          # supervisor / systemd
* * * * * php artisan schedule:run         # cron: ochords:purge-deleted-accounts (03:00), ochords:purge-old-records (03:30)
```

## Admin panel (`/admin`)

- Filament uses **session cookies**; set `APP_URL` to the public HTTPS origin so cookies are scoped correctly.
- Idle timeout: `OCHORDS_ADMIN_SESSION_MINUTES` (default 60). Staff can enable **app 2FA** from the Filament profile page.
- After migrate, seed roles/permissions, then promote the first staff user (any existing account with email+password):

```sh
php artisan db:seed --class=RolesAndPermissionsSeeder --force
php artisan ochords:admin:grant {handle} --role=super_admin
# social-only accounts need a password first:
php artisan ochords:admin:set-password {handle} '{strong-password}'
# non-production shortcut:
php artisan db:seed --class=StaffSeeder
```

### Restrict `/admin` (do both)

1. **Reverse proxy** (preferred): IP allowlist and/or HTTP basic auth in front of `/admin`.
2. **App-level**: set `OCHORDS_ADMIN_ALLOWED_IPS=203.0.113.10,10.0.0.0/8` (comma-separated IPs/CIDRs). Empty = no app IP filter.

Player API tokens cannot access `/admin`; `/api/v1` never exposes `is_admin` / roles.

### Telescope (local/staging only)

- Dev dependency. Keep `TELESCOPE_ENABLED=false` in production.
- Staff with `is_admin` can open `/telescope` when enabled.

## Subscriptions / IAP

- **App Store Server Notifications V2**: point App Store Connect at `POST https://<host>/api/v1/webhooks/app-store`.
- **Google Play**: set `OCHORDS_PLAY_PACKAGE_NAME` and either `OCHORDS_PLAY_SERVICE_ACCOUNT_JSON` (Play Console service-account key; tokens auto-refresh) or a short-lived `OCHORDS_PLAY_ACCESS_TOKEN`, plus product IDs in `OCHORDS_PLAY_PRO_PRODUCTS`. Until configured, Android `PUT /me/subscription` with `purchaseToken` returns 422.
- **Admin 2FA**: set `OCHORDS_ADMIN_REQUIRE_2FA=true` in production so staff must enrol TOTP after login.
- **Store links**: set `OCHORDS_APP_STORE_URL` / `OCHORDS_PLAY_STORE_URL` when the apps are live (marketing fetches `/marketing/store-links.json`).

## Point the app at it

Set `OCHORDS_API_URL` for the Release configuration in `ios/project.yml` to `https://<host>/api/v1`, run `xcodegen generate`, and build.

## Before go-live

- [ ] Confirm hosting region / cross-border transfer with the client (PDPO)
- [ ] Confirm the retention periods (reports 180 days after resolution, feedback 365 days) with the client
- [ ] App Store Server Notifications URL live and refunds clear Pro
- [ ] Google Play verification credentials if Android Pro must sync server-side
- [ ] Rate limits behind the proxy use the real client IP (configure trusted proxies)
- [ ] Backups encrypted with a key separate from the DB
- [ ] Google Cloud data processing terms accepted for the Vision project; privacy notice reviewed (photos sent to Google)
- [ ] PHP upload limits allow 4 MB (`upload_max_filesize`, `post_max_size`)
- [ ] First admin granted; `/admin` restricted at proxy; staff 2FA enabled; `TELESCOPE_ENABLED=false`
