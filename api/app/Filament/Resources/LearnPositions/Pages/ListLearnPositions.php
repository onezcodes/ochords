<?php

namespace App\Filament\Resources\LearnPositions\Pages;

use App\Filament\Resources\LearnPositions\LearnPositionResource;
use Filament\Actions\CreateAction;
use Filament\Resources\Pages\ListRecords;

class ListLearnPositions extends ListRecords
{
    protected static string $resource = LearnPositionResource::class;

    protected function getHeaderActions(): array
    {
        return [
            CreateAction::make(),
        ];
    }
}
