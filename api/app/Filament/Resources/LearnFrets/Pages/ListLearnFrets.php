<?php

namespace App\Filament\Resources\LearnFrets\Pages;

use App\Filament\Resources\LearnFrets\LearnFretResource;
use Filament\Actions\CreateAction;
use Filament\Resources\Pages\ListRecords;

class ListLearnFrets extends ListRecords
{
    protected static string $resource = LearnFretResource::class;

    protected function getHeaderActions(): array
    {
        return [
            CreateAction::make(),
        ];
    }
}
