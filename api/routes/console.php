<?php

use App\Enums\ReportResolution;
use App\Models\Report;
use App\Models\Song;
use App\Services\AuthService;
use App\Services\CommunityService;
use App\Services\ModerationService;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Schedule;

Artisan::command('ochords:purge-deleted-accounts', function (AuthService $auth) {
    $this->info($auth->purgeDeletedAccounts().' account(s) deleted.');
})->purpose('Delete accounts whose deletion grace period has passed');

Artisan::command('ochords:purge-old-records', function (CommunityService $community) {
    $purged = $community->purgeOldRecords();
    $this->info("{$purged['reports']} resolved report(s) and {$purged['feedback']} feedback message(s) deleted.");
})->purpose('Delete resolved reports and feedback past their retention period');

Schedule::command('ochords:purge-deleted-accounts')->dailyAt('03:00')->withoutOverlapping();
Schedule::command('ochords:purge-old-records')->dailyAt('03:30')->withoutOverlapping();

// Moderation runs from the console only — there is no admin HTTP surface.

Artisan::command('ochords:reports {--detail : Show the reporter’s free text (personal data — don’t paste it elsewhere)}', function (ModerationService $moderation) {
    $reports = $moderation->openReports();
    if ($reports->isEmpty()) {
        $this->info('No open reports.');

        return;
    }
    $headers = ['ID', 'Song', 'Reason', 'Reports on song', 'Created'];
    $rows = $reports->map(fn (Report $r) => [
        $r->id, $r->song_id, $r->reason->value, $r->getAttribute('song_reports_count'), $r->created_at->toIso8601ZuluString(),
    ])->all();
    if ($this->option('detail')) {
        $headers[] = 'Detail';
        $rows = $reports->map(fn (Report $r) => [
            $r->id, $r->song_id, $r->reason->value, $r->getAttribute('song_reports_count'), $r->created_at->toIso8601ZuluString(), (string) $r->detail,
        ])->all();
    }
    $this->table($headers, $rows);
})->purpose('List open reports');

Artisan::command('ochords:reports:resolve {id : Report ID} {--hide : Hide the sheet (private) and resolve every open report on it} {--dismiss : Close the report without acting}', function (ModerationService $moderation) {
    if ($this->option('hide') && $this->option('dismiss')) {
        $this->error('Choose --hide or --dismiss, not both.');

        return 1;
    }
    $report = Report::query()->open()->find($this->argument('id'));
    if (! $report) {
        $this->error('No open report with that ID.');

        return 1;
    }
    $resolution = match (true) {
        (bool) $this->option('hide') => ReportResolution::Hidden,
        (bool) $this->option('dismiss') => ReportResolution::Dismissed,
        default => ReportResolution::Fixed,
    };
    $moderation->resolve($report, $resolution);
    $this->info("Report {$report->id} resolved as {$resolution->value}.");

    return 0;
})->purpose('Resolve an open report (fixed by default; --hide takes the sheet off Explore, --dismiss closes it)');

Artisan::command('ochords:sheets:hide {songId : Song ID}', function (ModerationService $moderation) {
    $song = Song::query()->find($this->argument('songId'));
    if (! $song) {
        $this->error('No sheet with that ID.');

        return 1;
    }
    $moderation->hideSheet($song);
    $this->info("Sheet {$song->id} is now private.");

    return 0;
})->purpose('Take a sheet off Explore (private) and resolve its open reports');
