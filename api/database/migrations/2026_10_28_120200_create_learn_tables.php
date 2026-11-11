<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // The learning path: positions on the guitar neck, each with frets (lessons).
        Schema::create('learn_positions', function (Blueprint $table) {
            $table->string('id', 32)->primary();
            $table->unsignedSmallInteger('number')->unique();
            $table->string('title');
            $table->string('subtitle')->default('');
        });

        Schema::create('learn_frets', function (Blueprint $table) {
            $table->string('id', 32)->primary();
            $table->string('learn_position_id', 32);
            $table->foreign('learn_position_id')->references('id')->on('learn_positions')->cascadeOnDelete();
            $table->unsignedSmallInteger('sort');
            $table->string('title');
            $table->string('kind', 16)->default('lesson');
        });

        Schema::create('lesson_questions', function (Blueprint $table) {
            $table->string('id', 32)->primary();
            $table->string('learn_fret_id', 32);
            $table->foreign('learn_fret_id')->references('id')->on('learn_frets')->cascadeOnDelete();
            $table->unsignedSmallInteger('sort');
            $table->string('kind', 16);
            $table->string('prompt');
            $table->string('detail')->default('');
            $table->json('options');
            $table->string('answer')->default('');
        });

        Schema::create('fret_progress', function (Blueprint $table) {
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('learn_fret_id', 32);
            $table->foreign('learn_fret_id')->references('id')->on('learn_frets')->cascadeOnDelete();
            $table->unsignedTinyInteger('stars')->default(0);
            $table->timestamps();
            $table->primary(['user_id', 'learn_fret_id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('fret_progress');
        Schema::dropIfExists('lesson_questions');
        Schema::dropIfExists('learn_frets');
        Schema::dropIfExists('learn_positions');
    }
};
