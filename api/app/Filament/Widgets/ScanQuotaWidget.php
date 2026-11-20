<?php

namespace App\Filament\Widgets;

use Filament\Widgets\StatsOverviewWidget;
use Filament\Widgets\StatsOverviewWidget\Stat;
use Illuminate\Support\Facades\Cache;

class ScanQuotaWidget extends StatsOverviewWidget
{
    protected static ?int $sort = 2;

    protected function getStats(): array
    {
        $monthlyLimit = (int) config('ochords.scan.monthly_limit');
        $used = (int) Cache::get('scans:month:'.now()->format('Y-m'), 0);
        $remaining = $monthlyLimit > 0 ? max(0, $monthlyLimit - $used) : null;
        $driver = (string) config('ochords.scan.driver');

        return [
            Stat::make('OCR driver', $driver === 'google' ? 'Google Vision' : 'Off (phone only)')
                ->description('OCHORDS_SCAN_DRIVER'),
            Stat::make('Scans this month', $used)
                ->description($monthlyLimit > 0
                    ? "{$remaining} left of {$monthlyLimit}"
                    : 'No monthly cap (0)'),
            Stat::make('Daily per-user limit', (int) config('ochords.scan.daily_limit'))
                ->description('OCHORDS_SCAN_DAILY_LIMIT'),
        ];
    }
}
