<?php

namespace App\Filament\Resources\LearnFrets\Pages;

use App\Filament\Resources\LearnFrets\LearnFretResource;
use Filament\Actions\EditAction;
use Filament\Resources\Pages\ViewRecord;

class ViewLearnFret extends ViewRecord
{
    protected static string $resource = LearnFretResource::class;

    protected function getHeaderActions(): array
    {
        return [
            EditAction::make(),
        ];
    }
}
