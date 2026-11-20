<?php

namespace App\Filament\Widgets;

use App\Filament\Resources\Feedback\FeedbackResource;
use App\Filament\Resources\Reports\ReportResource;
use App\Filament\Resources\Songs\SongResource;
use App\Filament\Resources\Users\UserResource;
use App\Models\Feedback;
use App\Models\Report;
use App\Models\Song;
use App\Models\User;
use Filament\Widgets\StatsOverviewWidget;
use Filament\Widgets\StatsOverviewWidget\Stat;

class OverviewStats extends StatsOverviewWidget
{
    protected function getStats(): array
    {
        return [
            Stat::make('Open reports', Report::query()->open()->count())
                ->description('Waiting for moderation')
                ->url(ReportResource::getUrl('index')),
            Stat::make('New feedback', Feedback::query()->whereNull('reviewed_at')->where('created_at', '>=', now()->subDays(7))->count())
                ->description('Unreviewed in the last 7 days')
                ->url(FeedbackResource::getUrl('index')),
            Stat::make('Public sheets', Song::query()->public()->count())
                ->description('On Explore')
                ->url(SongResource::getUrl('index')),
            Stat::make('Pending deletion', User::query()->whereNotNull('deletion_requested_at')->count())
                ->description('Within grace period')
                ->url(UserResource::getUrl('index')),
            Stat::make('Pro users', User::query()->where('pro_until', '>', now())->count())
                ->description('pro_until in the future')
                ->url(UserResource::getUrl('index')),
        ];
    }
}
