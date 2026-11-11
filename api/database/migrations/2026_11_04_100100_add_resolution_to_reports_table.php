<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('reports', function (Blueprint $table) {
            // Set by the moderation commands; resolved reports are purged after OCHORDS_REPORT_RETENTION_DAYS.
            $table->timestamp('resolved_at')->nullable()->index()->after('status');
            $table->string('resolution', 16)->nullable()->after('resolved_at');
        });
    }

    public function down(): void
    {
        Schema::table('reports', function (Blueprint $table) {
            $table->dropColumn(['resolved_at', 'resolution']);
        });
    }
};
