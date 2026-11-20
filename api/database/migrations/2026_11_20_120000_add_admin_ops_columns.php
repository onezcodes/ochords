<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->text('app_authentication_secret')->nullable()->after('is_admin');
            $table->text('app_authentication_recovery_codes')->nullable()->after('app_authentication_secret');
        });

        Schema::table('songs', function (Blueprint $table) {
            $table->timestamp('featured_at')->nullable()->after('is_public');
            $table->text('moderation_note')->nullable()->after('featured_at');
            $table->index('featured_at');
        });

        Schema::create('admin_audit_logs', function (Blueprint $table) {
            $table->id();
            $table->foreignId('actor_id')->nullable()->constrained('users')->nullOnDelete();
            $table->string('action', 64);
            $table->string('subject_type', 64)->nullable();
            $table->string('subject_id', 64)->nullable();
            // IDs and enums only — never emails, report detail, or feedback text.
            $table->json('meta')->nullable();
            $table->string('ip', 45)->nullable();
            $table->timestamps();
            $table->index(['action', 'created_at']);
            $table->index(['subject_type', 'subject_id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('admin_audit_logs');
        Schema::table('songs', function (Blueprint $table) {
            $table->dropIndex(['featured_at']);
            $table->dropColumn(['featured_at', 'moderation_note']);
        });
        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn(['app_authentication_secret', 'app_authentication_recovery_codes']);
        });
    }
};
