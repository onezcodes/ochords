<?php

return [

    /*
    |--------------------------------------------------------------------------
    | Third Party Services
    |--------------------------------------------------------------------------
    |
    | This file is for storing the credentials for third party services such
    | as Resend, Postmark, AWS, and more. This file provides the de facto
    | location for this type of information, allowing packages to have
    | a conventional file to locate the various service credentials.
    |
    */

    // Google Cloud Vision, for reading Myanmar chord sheets. Use a key restricted to the Vision API.
    'google_vision' => [
        'key' => env('GOOGLE_VISION_API_KEY'),
        // Overridable only so a local stand-in can be used in development.
        'endpoint' => env('GOOGLE_VISION_ENDPOINT', 'https://vision.googleapis.com/v1/images:annotate'),
    ],

    /*
    | Apple: Sign in with Apple (identity token audience) and O Chords Pro (StoreKit 2 transactions).
    | pro_products: comma-separated product IDs that grant Pro. Sandbox transactions are only accepted
    | outside production unless OCHORDS_ALLOW_SANDBOX_RECEIPTS says otherwise.
    */
    'apple' => [
        'bundle_id' => env('OCHORDS_APPLE_BUNDLE_ID', 'com.onezcodes.ochords'),
        'pro_products' => array_values(array_filter(array_map('trim', explode(',',
            (string) env('OCHORDS_PRO_PRODUCTS', 'com.onezcodes.ochords.pro.monthly,com.onezcodes.ochords.pro.yearly'),
        )))),
        'allow_sandbox_receipts' => (bool) env('OCHORDS_ALLOW_SANDBOX_RECEIPTS', env('APP_ENV') !== 'production'),
        'jwks_url' => 'https://appleid.apple.com/auth/keys',
    ],

    // Sign in with Google: the iOS OAuth client ID the ID token is issued for. Empty = Google sign-in off (503).
    'google' => [
        'client_id' => env('OCHORDS_GOOGLE_CLIENT_ID'),
        'jwks_url' => 'https://www.googleapis.com/oauth2/v3/certs',
    ],

    /*
    | Google Play Billing Pro verification (Android).
    | Set package name + a short-lived OAuth access token (or wire a service-account token refresher).
    | Until both are set, Android sync returns 422 and Pro stays on-device.
    */
    'google_play' => [
        'package_name' => env('OCHORDS_PLAY_PACKAGE_NAME', ''),
        // Optional short-lived token; prefer service_account_json for automatic refresh.
        'access_token' => env('OCHORDS_PLAY_ACCESS_TOKEN', ''),
        // Path to a Play Console service-account JSON key (absolute or relative to api/).
        'service_account_json' => env('OCHORDS_PLAY_SERVICE_ACCOUNT_JSON', ''),
        'pro_products' => array_values(array_filter(array_map('trim', explode(',',
            (string) env('OCHORDS_PLAY_PRO_PRODUCTS', env('OCHORDS_PRO_PRODUCTS', 'com.onezcodes.ochords.pro.monthly,com.onezcodes.ochords.pro.yearly')),
        )))),
    ],

    'postmark' => [
        'key' => env('POSTMARK_API_KEY'),
    ],

    'resend' => [
        'key' => env('RESEND_API_KEY'),
    ],

    'ses' => [
        'key' => env('AWS_ACCESS_KEY_ID'),
        'secret' => env('AWS_SECRET_ACCESS_KEY'),
        'region' => env('AWS_DEFAULT_REGION', 'us-east-1'),
    ],

    'slack' => [
        'notifications' => [
            'bot_user_oauth_token' => env('SLACK_BOT_USER_OAUTH_TOKEN'),
            'channel' => env('SLACK_BOT_USER_DEFAULT_CHANNEL'),
        ],
    ],

];
