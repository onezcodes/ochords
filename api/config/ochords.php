<?php

return [

    /*
    | Key for the HMAC that turns an email into a lookup hash (emails are stored encrypted).
    | Keep it separate from APP_KEY and out of source control; changing it orphans every account.
    */
    'lookup_key' => env('OCHORDS_LOOKUP_KEY', env('APP_KEY')),

    // Days between "delete my account" and the data being purged (the app says "within [n] days").
    'account_deletion_grace_days' => (int) env('OCHORDS_DELETION_GRACE_DAYS', 30),

    // Minutes a password reset link stays valid (the app says 30).
    'password_reset_minutes' => 30,

    // Password for the synthetic demo accounts (DemoSeeder, local only).
    'demo_password' => env('DEMO_PASSWORD', 'Chords2026'),

    // Explore page size.
    'explore_limit' => 50,

    /*
    | Retention (PDPO DPP2): resolved reports and feedback hold free text, so they are purged by
    | `ochords:purge-old-records` once they are this old. Open reports are never purged.
    */
    'report_retention_days' => (int) env('OCHORDS_REPORT_RETENTION_DAYS', 180),
    'feedback_retention_days' => (int) env('OCHORDS_FEEDBACK_RETENTION_DAYS', 365),

    /*
    | Server-side sheet reading (POST /api/v1/scans) — reads Myanmar lyrics, which the phone can't.
    | driver: "google" (Cloud Vision) or "none" (the app then reads on the phone only).
    | Photos are sent to Google and never stored here; record this transfer in the privacy notice.
    */
    'scan' => [
        'driver' => env('OCHORDS_SCAN_DRIVER', 'none'),
        // Scans per player per day — bounds the per-scan cost.
        'daily_limit' => (int) env('OCHORDS_SCAN_DAILY_LIMIT', 30),
        // Calls to the OCR provider per calendar month, across all players. Google Cloud Vision's free tier is
        // 1,000 images a month (and offers no monthly quota of its own), so 900 keeps a margin. Past it, scans
        // are read on the phone. 0 = no limit (production, once billing is budgeted).
        'monthly_limit' => (int) env('OCHORDS_SCAN_MONTHLY_LIMIT', 900),
        'max_kilobytes' => 4096,
        'timeout_seconds' => 20,
    ],

    /*
    | Filament /admin session: idle lifetime in minutes (overrides SESSION_LIFETIME for the admin panel
    | via middleware). Keep shorter than the player API (which uses Sanctum tokens).
    */
    'admin' => [
        'session_lifetime_minutes' => (int) env('OCHORDS_ADMIN_SESSION_MINUTES', 60),
        // Comma-separated CIDRs/IPs; empty = no app-level IP check (still recommend proxy allowlist).
        'allowed_ips' => array_values(array_filter(array_map('trim', explode(',', (string) env('OCHORDS_ADMIN_ALLOWED_IPS', ''))))),
        // Non-production staff seeder credentials (StaffSeeder). Never set in production.
        'seed_handle' => env('OCHORDS_ADMIN_SEED_HANDLE', 'ochordsadmin'),
        'seed_email' => env('OCHORDS_ADMIN_SEED_EMAIL', 'admin@ochords.local'),
        'seed_password' => env('OCHORDS_ADMIN_SEED_PASSWORD', 'AdminChords2026!'),
        // Require TOTP app MFA after staff sign-in. Default on outside local.
        'require_2fa' => filter_var(
            env('OCHORDS_ADMIN_REQUIRE_2FA', env('APP_ENV') === 'production' ? 'true' : 'false'),
            FILTER_VALIDATE_BOOL,
        ),
        // Explore uploader for staff-imported sheets (not the person who scanned).
        'catalog_handle' => env('OCHORDS_CATALOG_HANDLE', 'ochords'),
        'catalog_email' => env('OCHORDS_CATALOG_EMAIL', 'catalog@ochords.local'),
        'catalog_name' => env('OCHORDS_CATALOG_NAME', 'O Chords'),
    ],

    'marketing' => [
        'app_store_url' => env('OCHORDS_APP_STORE_URL', ''),
        'play_store_url' => env('OCHORDS_PLAY_STORE_URL', ''),
    ],

];
