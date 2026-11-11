<?php

namespace App\Providers;

use App\Services\Ocr\DisabledOcr;
use App\Services\Ocr\GoogleVisionOcr;
use App\Services\Ocr\OcrEngine;
use App\Services\Social\JwksSocialTokenVerifier;
use App\Services\Social\SocialTokenVerifier;
use App\Services\StoreKit\AppleTransactionVerifier;
use App\Services\StoreKit\TransactionVerifier;
use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\ServiceProvider;

class AppServiceProvider extends ServiceProvider
{
    private const API_PER_MINUTE = 120;

    private const AUTH_PER_MINUTE = 10;

    private const WRITES_PER_MINUTE = 10;

    private const EXPORTS_PER_MINUTE = 3;

    public function register(): void
    {
        // Google Cloud Vision when configured with a key; otherwise scans are read on the phone only.
        $this->app->bind(OcrEngine::class, function () {
            $key = (string) config('services.google_vision.key');

            return config('ochords.scan.driver') === 'google' && $key !== ''
                ? new GoogleVisionOcr($key, (string) config('services.google_vision.endpoint'), (int) config('ochords.scan.timeout_seconds'))
                : new DisabledOcr;
        });

        // Identity tokens (Sign in with Apple / Google) and StoreKit transactions are verified offline against
        // the providers' published keys. Tests bind fakes for both.
        $this->app->bind(SocialTokenVerifier::class, JwksSocialTokenVerifier::class);
        $this->app->singleton(TransactionVerifier::class, fn () => new AppleTransactionVerifier(resource_path('certs/AppleRootCA-G3.pem')));
    }

    public function boot(): void
    {
        // Catch lazy loading, silently discarded attributes and missing attributes while developing.
        Model::shouldBeStrict(! $this->app->isProduction());

        // Plain JSON objects and arrays (no { "data": … } envelope), as the app's decoders expect.
        JsonResource::withoutWrapping();

        $this->configureRateLimits();
    }

    private function configureRateLimits(): void
    {
        RateLimiter::for('api', fn (Request $request) => Limit::perMinute(self::API_PER_MINUTE)
            ->by($request->user()?->id ?: $request->ip()));

        // Sign-in, sign-up and resets: per IP, to slow down password guessing and email probing.
        RateLimiter::for('auth', fn (Request $request) => Limit::perMinute(self::AUTH_PER_MINUTE)->by($request->ip()));

        RateLimiter::for('writes', fn (Request $request) => Limit::perMinute(self::WRITES_PER_MINUTE)
            ->by($request->user()?->id ?: $request->ip()));

        // Data export builds the whole account in one response.
        RateLimiter::for('export', fn (Request $request) => Limit::perMinute(self::EXPORTS_PER_MINUTE)
            ->by($request->user()?->id ?: $request->ip()));
    }
}
