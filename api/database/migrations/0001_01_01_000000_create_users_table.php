<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('users', function (Blueprint $table) {
            $table->id();
            $table->string('name');
            $table->string('handle')->unique();
            // Personal data is encrypted at rest; `email_hash` (HMAC) is the lookup key.
            $table->text('email');
            $table->string('email_hash', 64)->unique();
            $table->string('password');
            $table->text('city')->nullable();
            $table->unsignedInteger('points')->default(0);
            $table->unsignedInteger('groove_days')->default(0);
            $table->unsignedInteger('best_groove')->default(0);
            $table->unsignedSmallInteger('daily_goal_minutes')->default(10);
            $table->unsignedSmallInteger('rest_notes')->default(0);
            $table->unsignedInteger('songs_learned')->default(0);
            $table->unsignedInteger('lessons_done')->default(0);
            $table->timestamp('deletion_requested_at')->nullable()->index();
            $table->text('deletion_reason')->nullable();
            $table->timestamps();
        });

        // Keyed by the email hash, never the plain email.
        Schema::create('password_reset_tokens', function (Blueprint $table) {
            $table->string('email_hash', 64)->primary();
            $table->string('token');
            $table->timestamp('created_at')->nullable();
        });

        Schema::create('sessions', function (Blueprint $table) {
            $table->string('id')->primary();
            $table->foreignId('user_id')->nullable()->index();
            $table->string('ip_address', 45)->nullable();
            $table->text('user_agent')->nullable();
            $table->longText('payload');
            $table->integer('last_activity')->index();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('users');
        Schema::dropIfExists('password_reset_tokens');
        Schema::dropIfExists('sessions');
    }
};
