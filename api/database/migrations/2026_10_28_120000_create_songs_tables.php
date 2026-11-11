<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // A song is a chord sheet owned by one user. Public songs appear in Explore.
        Schema::create('songs', function (Blueprint $table) {
            // IDs come from the app (UUIDs), so songs created offline keep their ID when synced.
            $table->string('id', 64)->primary();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('title');
            $table->string('artist')->default('');
            $table->string('key', 8);
            $table->unsignedSmallInteger('tempo')->default(80);
            $table->json('sections');
            $table->string('source', 16)->default('imported');
            $table->string('version_name')->nullable();
            $table->string('difficulty', 16)->default('Beginner');
            $table->boolean('is_myanmar')->default(true);
            $table->boolean('is_public')->default(false)->index();
            $table->text('my_note')->nullable();
            $table->unsignedInteger('plays')->default(0);
            $table->unsignedInteger('comments_count')->default(0);
            $table->timestamp('added_at');
            $table->timestamps();
        });

        // Another player's sheet saved into your library.
        Schema::create('song_user', function (Blueprint $table) {
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('song_id', 64);
            $table->foreign('song_id')->references('id')->on('songs')->cascadeOnDelete();
            $table->unsignedTinyInteger('rating')->nullable();
            $table->timestamps();
            $table->primary(['user_id', 'song_id']);
        });

        Schema::create('setlists', function (Blueprint $table) {
            $table->string('id', 64)->primary();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('name');
            $table->string('note')->default('');
            $table->string('date')->nullable();
            $table->timestamps();
        });

        Schema::create('setlist_song', function (Blueprint $table) {
            $table->string('setlist_id', 64);
            $table->foreign('setlist_id')->references('id')->on('setlists')->cascadeOnDelete();
            $table->string('song_id', 64);
            $table->unsignedSmallInteger('position');
            $table->primary(['setlist_id', 'song_id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('setlist_song');
        Schema::dropIfExists('setlists');
        Schema::dropIfExists('song_user');
        Schema::dropIfExists('songs');
    }
};
