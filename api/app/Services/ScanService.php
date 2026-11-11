<?php

namespace App\Services;

use App\Models\User;
use App\Services\Ocr\OcrEngine;
use App\Services\Ocr\OcrLine;
use App\Services\Ocr\ScanUnavailableException;
use Illuminate\Http\Exceptions\ThrottleRequestsException;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\RateLimiter;

/**
 * Reads a chord-sheet photo on the server. The photo is only held in memory for the request:
 * it is never stored and its text is never logged.
 */
class ScanService
{
    private const DAY_SECONDS = 86400;

    public function __construct(private readonly OcrEngine $ocr) {}

    public function engineName(): string
    {
        return $this->ocr->name();
    }

    /** @return list<OcrLine> */
    public function read(User $user, string $image): array
    {
        $this->countTowardsDailyLimit($user);
        $this->countTowardsMonthlyLimit();

        $started = microtime(true);
        $lines = $this->ocr->read($image);
        Log::info('scan.read', [
            'user_id' => $user->id,
            'engine' => $this->ocr->name(),
            'lines' => count($lines),
            'ms' => (int) ((microtime(true) - $started) * 1000),
        ]);

        return $lines;
    }

    /**
     * All players together, per calendar month — keeps OCR within the provider's free allowance.
     * Over the limit the scan is "unavailable" (503), so the app reads it on the phone instead.
     */
    private function countTowardsMonthlyLimit(): void
    {
        $limit = (int) config('ochords.scan.monthly_limit');
        if ($limit <= 0) {
            return;
        }
        $key = 'scans:month:'.now()->format('Y-m');
        Cache::add($key, 0, now()->endOfMonth()->addDay());
        if ((int) Cache::get($key) >= $limit) {
            Log::warning('scan.monthly_limit_reached', ['limit' => $limit]);
            throw new ScanUnavailableException('Server reading is paused for the rest of the month.');
        }
        Cache::increment($key);
    }

    /** Every attempt counts, since each one costs a call to the OCR provider. */
    private function countTowardsDailyLimit(User $user): void
    {
        $key = 'scans:'.$user->id;
        if (RateLimiter::tooManyAttempts($key, (int) config('ochords.scan.daily_limit'))) {
            throw new ThrottleRequestsException('You’ve used today’s scans. They reset tomorrow.');
        }
        RateLimiter::hit($key, self::DAY_SECONDS);
    }
}
