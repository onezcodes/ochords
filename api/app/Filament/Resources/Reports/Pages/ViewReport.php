<?php

namespace App\Filament\Resources\Reports\Pages;

use App\Enums\ReportResolution;
use App\Filament\Resources\Reports\ReportResource;
use App\Models\Report;
use App\Services\ModerationService;
use Filament\Actions\Action;
use Filament\Resources\Pages\ViewRecord;

class ViewReport extends ViewRecord
{
    protected static string $resource = ReportResource::class;

    protected function getHeaderActions(): array
    {
        return [
            Action::make('dismiss')
                ->label('Dismiss')
                ->color('gray')
                ->requiresConfirmation()
                ->visible(fn (): bool => $this->record->resolved_at === null)
                ->action(function (ModerationService $moderation): void {
                    /** @var Report $report */
                    $report = $this->record;
                    $moderation->resolve($report, ReportResolution::Dismissed);
                    $this->refreshFormData(['status', 'resolved_at', 'resolution']);
                }),
            Action::make('fixed')
                ->label('Fixed')
                ->color('success')
                ->requiresConfirmation()
                ->visible(fn (): bool => $this->record->resolved_at === null)
                ->action(function (ModerationService $moderation): void {
                    /** @var Report $report */
                    $report = $this->record;
                    $moderation->resolve($report, ReportResolution::Fixed);
                    $this->refreshFormData(['status', 'resolved_at', 'resolution']);
                }),
            Action::make('hide')
                ->label('Hide sheet')
                ->color('danger')
                ->requiresConfirmation()
                ->visible(fn (): bool => $this->record->resolved_at === null)
                ->action(function (ModerationService $moderation): void {
                    /** @var Report $report */
                    $report = $this->record;
                    $moderation->resolve($report, ReportResolution::Hidden);
                    $this->refreshFormData(['status', 'resolved_at', 'resolution']);
                }),
        ];
    }
}
