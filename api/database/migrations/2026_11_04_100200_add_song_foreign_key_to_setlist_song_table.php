<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // A deleted song leaves every setlist it was in. Clear rows that already point nowhere first.
        DB::table('setlist_song')->whereNotIn('song_id', DB::table('songs')->select('id'))->delete();

        Schema::table('setlist_song', function (Blueprint $table) {
            $table->foreign('song_id')->references('id')->on('songs')->cascadeOnDelete();
        });
    }

    public function down(): void
    {
        Schema::table('setlist_song', function (Blueprint $table) {
            $table->dropForeign(['song_id']);
        });
    }
};
