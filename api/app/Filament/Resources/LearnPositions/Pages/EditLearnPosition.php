<?php

namespace App\Filament\Resources\LearnPositions\Pages;

use App\Filament\Resources\LearnPositions\LearnPositionResource;
use Filament\Actions\DeleteAction;
use Filament\Actions\ViewAction;
use Filament\Resources\Pages\EditRecord;

class EditLearnPosition extends EditRecord
{
    protected static string $resource = LearnPositionResource::class;

    protected function getHeaderActions(): array
    {
        return [
            ViewAction::make(),
            DeleteAction::make(),
        ];
    }
}
