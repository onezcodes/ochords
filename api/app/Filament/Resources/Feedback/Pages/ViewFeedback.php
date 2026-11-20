<?php

namespace App\Filament\Resources\Feedback\Pages;

use App\Filament\Resources\Feedback\FeedbackResource;
use App\Models\Feedback;
use Filament\Actions\Action;
use Filament\Resources\Pages\ViewRecord;

class ViewFeedback extends ViewRecord
{
    protected static string $resource = FeedbackResource::class;

    protected function getHeaderActions(): array
    {
        return [
            Action::make('markReviewed')
                ->label('Mark reviewed')
                ->visible(fn (): bool => $this->record->reviewed_at === null)
                ->action(function (): void {
                    /** @var Feedback $feedback */
                    $feedback = $this->record;
                    $feedback->forceFill(['reviewed_at' => now()])->save();
                }),
            Action::make('markUnreviewed')
                ->label('Mark unreviewed')
                ->color('gray')
                ->visible(fn (): bool => $this->record->reviewed_at !== null)
                ->action(function (): void {
                    /** @var Feedback $feedback */
                    $feedback = $this->record;
                    $feedback->forceFill(['reviewed_at' => null])->save();
                }),
        ];
    }
}
