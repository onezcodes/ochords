<?php

use App\Http\Controllers\Api\AppStoreWebhookController;
use App\Http\Controllers\Api\AuthController;
use App\Http\Controllers\Api\CommunityController;
use App\Http\Controllers\Api\ExploreController;
use App\Http\Controllers\Api\LearnController;
use App\Http\Controllers\Api\ScanController;
use App\Http\Controllers\Api\SetlistController;
use App\Http\Controllers\Api\SongController;
use App\Http\Controllers\Api\SubscriptionController;
use Illuminate\Support\Facades\Route;

// Song and setlist IDs come from the app (UUIDs); keep them to safe characters.
Route::pattern('song', '[A-Za-z0-9\-]{1,64}');
Route::pattern('id', '[A-Za-z0-9\-]{1,64}');
Route::pattern('setlist', '[A-Za-z0-9\-]{1,64}');

Route::prefix('v1')->name('api.')->middleware('throttle:api')->group(function () {

    Route::prefix('auth')->name('auth.')->middleware('throttle:auth')->group(function () {
        Route::post('sign-up', [AuthController::class, 'signUp'])->name('sign-up');
        Route::post('sign-in', [AuthController::class, 'signIn'])->name('sign-in');
        Route::post('forgot-password', [AuthController::class, 'forgotPassword'])->name('forgot-password');
        Route::post('reset-password', [AuthController::class, 'resetPassword'])->name('reset-password');
        // Token exchange: the app signs in with Apple/Google and sends the identity token.
        Route::post('apple', [AuthController::class, 'apple'])->name('apple');
        Route::post('google', [AuthController::class, 'google'])->name('google');
    });

    // Open to guests (the app's "continue as guest").
    Route::get('explore', [ExploreController::class, 'index'])->name('explore.index');
    Route::get('explore/popular', [ExploreController::class, 'popular'])->name('explore.popular');
    Route::get('users/{user}', [CommunityController::class, 'player'])->name('users.show');
    Route::get('learn/path', [LearnController::class, 'path'])->name('learn.path');
    Route::get('learn/frets/{fret}/questions', [LearnController::class, 'questions'])->name('learn.questions');
    Route::post('feedback', [CommunityController::class, 'feedback'])->middleware('throttle:writes')->name('feedback.store');

    // App Store Server Notifications V2 (no Sanctum — Apple signs the payload).
    Route::post('webhooks/app-store', AppStoreWebhookController::class)
        ->middleware('throttle:api')
        ->name('webhooks.app-store');

    Route::middleware('auth:sanctum')->group(function () {
        Route::post('auth/sign-out', [AuthController::class, 'signOut'])->name('auth.sign-out');
        Route::get('me', [AuthController::class, 'me'])->name('me.show');
        Route::delete('me', [AuthController::class, 'deleteAccount'])->name('me.destroy');
        Route::get('me/export', [AuthController::class, 'export'])->middleware('throttle:export')->name('me.export');
        Route::put('me/subscription', [SubscriptionController::class, 'update'])->name('me.subscription.update');
        Route::delete('me/subscription', [SubscriptionController::class, 'destroy'])->name('me.subscription.destroy');

        Route::get('songs', [SongController::class, 'index'])->name('songs.index');
        Route::get('songs/{song}', [SongController::class, 'show'])->name('songs.show');
        Route::put('songs/{id}', [SongController::class, 'update'])->name('songs.update');
        Route::delete('songs/{song}', [SongController::class, 'destroy'])->name('songs.destroy');
        Route::post('songs/{song}/reports', [CommunityController::class, 'report'])->middleware('throttle:writes')->name('songs.reports.store');
        Route::post('scans', [ScanController::class, 'store'])->middleware('throttle:writes')->name('scans.store');

        Route::get('setlists', [SetlistController::class, 'index'])->name('setlists.index');
        Route::put('setlists/{id}', [SetlistController::class, 'update'])->name('setlists.update');
        Route::delete('setlists/{setlist}', [SetlistController::class, 'destroy'])->name('setlists.destroy');
        Route::get('notifications', [CommunityController::class, 'notifications'])->name('notifications.index');
        Route::post('notifications/read-all', [CommunityController::class, 'readAllNotifications'])->name('notifications.read-all');
    });
});
