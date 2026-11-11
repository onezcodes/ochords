<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            // Sign in with Apple / Google: the provider's stable subject ID, never the email.
            $table->string('apple_id')->nullable()->unique()->after('password');
            $table->string('google_id')->nullable()->unique()->after('apple_id');
            // O Chords Pro, as last synced from the app's StoreKit 2 transaction.
            $table->timestamp('pro_until')->nullable()->after('lessons_done');
            $table->string('pro_original_transaction_id', 64)->nullable()->index()->after('pro_until');
        });
    }

    public function down(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn(['apple_id', 'google_id', 'pro_until', 'pro_original_transaction_id']);
        });
    }
};
