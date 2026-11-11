<?php

use App\Services\Ocr\ScanUnavailableException;
use App\Services\Social\InvalidSocialTokenException;
use App\Services\Social\SocialSignInUnavailableException;
use App\Services\StoreKit\MalformedTransactionException;
use App\Services\StoreKit\TransactionSignatureException;
use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Request;

return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        web: __DIR__.'/../routes/web.php',
        api: __DIR__.'/../routes/api.php',
        commands: __DIR__.'/../routes/console.php',
        health: '/up',
    )
    ->withMiddleware(function (Middleware $middleware): void {
        // API only: a missing or expired token is a JSON 401, never a redirect to a login page.
        $middleware->redirectGuestsTo(fn () => null);
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        // The app falls back to reading on the phone when it sees a 503 from /scans.
        $exceptions->render(fn (ScanUnavailableException $e) => response()->json(['message' => $e->getMessage()], 503));

        // Sign in with Apple/Google: a token we can't verify is a 401; a provider that isn't set up is a 503.
        $exceptions->render(fn (InvalidSocialTokenException $e) => response()->json(['message' => $e->getMessage()], 401));
        $exceptions->render(fn (SocialSignInUnavailableException $e) => response()->json(['message' => $e->getMessage()], 503));

        // O Chords Pro: a transaction Apple didn't sign is a 401; one we can't use is a validation error.
        $exceptions->render(fn (TransactionSignatureException $e) => response()->json(['message' => $e->getMessage()], 401));
        $exceptions->render(fn (MalformedTransactionException $e) => response()->json([
            'message' => $e->getMessage(), 'errors' => ['transactionJWS' => [$e->getMessage()]],
        ], 422));

        $exceptions->shouldRenderJsonWhen(
            fn (Request $request) => $request->is('api/*') || $request->expectsJson(),
        );
    })->create();
