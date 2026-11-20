<?php

use Illuminate\Support\Facades\Route;
use Symfony\Component\HttpFoundation\BinaryFileResponse;

$serveMarketing = static function (string $name): BinaryFileResponse {
    $path = public_path("marketing/{$name}.html");

    abort_unless(is_file($path), 503, 'Marketing site is not built. Run: cd web && npm ci && npm run build');

    return response()->file($path, [
        'Content-Type' => 'text/html; charset=UTF-8',
        'Cache-Control' => 'public, max-age=300',
    ]);
};

Route::get('/', static fn (): BinaryFileResponse => $serveMarketing('index'));
Route::get('/privacy', static fn (): BinaryFileResponse => $serveMarketing('privacy'));
Route::get('/support', static fn (): BinaryFileResponse => $serveMarketing('support'));

Route::get('/sitemap.xml', static function (): BinaryFileResponse {
    $path = public_path('marketing/sitemap.xml');
    abort_unless(is_file($path), 404);

    return response()->file($path, [
        'Content-Type' => 'application/xml; charset=UTF-8',
        'Cache-Control' => 'public, max-age=3600',
    ]);
});

Route::get('/marketing/store-links.json', static function () {
    return response()->json([
        'appStore' => (string) config('ochords.marketing.app_store_url'),
        'playStore' => (string) config('ochords.marketing.play_store_url'),
    ], 200, ['Cache-Control' => 'public, max-age=300']);
});
