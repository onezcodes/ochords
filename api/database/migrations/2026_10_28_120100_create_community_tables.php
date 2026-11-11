<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('follows', function (Blueprint $table) {
            $table->foreignId('follower_id')->constrained('users')->cascadeOnDelete();
            $table->foreignId('followee_id')->constrained('users')->cascadeOnDelete();
            $table->timestamps();
            $table->primary(['follower_id', 'followee_id']);
        });

        Schema::create('app_notifications', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('category', 16);
            $table->string('title');
            $table->string('body', 500);
            $table->string('system_image', 64);
            $table->timestamp('read_at')->nullable();
            $table->timestamps();
            $table->index(['user_id', 'created_at']);
        });

        Schema::create('reports', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('song_id', 64)->index();
            $table->string('reason', 32);
            // Free text may contain personal data, so it is encrypted at rest.
            $table->text('detail')->nullable();
            $table->string('status', 16)->default('received');
            $table->timestamps();
        });

        Schema::create('feedback', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->nullable()->constrained()->nullOnDelete();
            $table->string('kind', 32);
            $table->text('message');
            $table->timestamps();
        });

        Schema::create('practice_days', function (Blueprint $table) {
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->date('day');
            $table->unsignedSmallInteger('minutes')->default(0);
            $table->primary(['user_id', 'day']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('practice_days');
        Schema::dropIfExists('feedback');
        Schema::dropIfExists('reports');
        Schema::dropIfExists('app_notifications');
        Schema::dropIfExists('follows');
    }
};
