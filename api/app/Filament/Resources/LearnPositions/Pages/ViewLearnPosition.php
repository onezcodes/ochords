<?php

namespace App\Filament\Resources\LearnPositions\Pages;

use App\Filament\Resources\LearnPositions\LearnPositionResource;
use Filament\Actions\EditAction;
use Filament\Resources\Pages\ViewRecord;

class ViewLearnPosition extends ViewRecord
{
    protected static string $resource = LearnPositionResource::class;

    protected function getHeaderActions(): array
    {
        return [
            EditAction::make(),
        ];
    }
}
