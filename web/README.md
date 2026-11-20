# O Chords marketing site

Static multi-page site (Vite) for the product landing page, privacy, and support. Production builds land in `../api/public/marketing` and Laravel serves them at `/`, `/privacy`, and `/support`.

## Develop

```sh
cd web
npm install
npm run dev          # http://localhost:5174
```

## Build (required for `php artisan serve` home page)

```sh
cd web
npm ci
npm run build        # writes to api/public/marketing
```

Then open `http://127.0.0.1:8000/` with the API running.

## Pages

| URL (Laravel) | Source |
|---|---|
| `/` | `index.html` |
| `/privacy` | `privacy.html` |
| `/support` | `support.html` |

Brand marks live in `public/brand/` and ship under `/marketing/brand/…` after build. CSS/JS go to `/marketing/assets/…`.

## Deploy note

Rebuild the marketing site whenever you change `web/` and before shipping the API image or tarball, so `api/public/marketing` is present on the server.

Store buttons stay “Coming soon” until `OCHORDS_APP_STORE_URL` / `OCHORDS_PLAY_STORE_URL` are set on the API; the page loads them from `/marketing/store-links.json`.
