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
composer install --no-dev --optimize-autoloader
php artisan migrate --force
php artisan db:seed --class=LearnPathSeeder --force   # learning-path content; safe to re-run
php artisan config:cache && php artisan route:cache
php artisan queue:restart
```

Processes to keep running:

```sh
php artisan queue:work --tries=3          # supervisor / systemd
* * * * * php artisan schedule:run         # cron: ochords:purge-deleted-accounts (03:00), ochords:purge-old-records (03:30)
```

## Point the app at it

Set `OCHORDS_API_URL` for the Release configuration in `ios/project.yml` to `https://<host>/api/v1`, run `xcodegen generate`, and build.

## Before go-live

- [ ] Confirm hosting region / cross-border transfer with the client (PDPO)
- [ ] Confirm the retention periods (reports 180 days after resolution, feedback 365 days) with the client
- [ ] App Store Server Notifications, so refunds/cancellations reach the account without the app syncing
- [ ] Rate limits behind the proxy use the real client IP (configure trusted proxies)
- [ ] Backups encrypted with a key separate from the DB
- [ ] Google Cloud data processing terms accepted for the Vision project; privacy notice reviewed (photos sent to Google)
- [ ] PHP upload limits allow 4 MB (`upload_max_filesize`, `post_max_size`)
