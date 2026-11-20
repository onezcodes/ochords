<?php

namespace App\Filament\Resources\LearnFrets\Pages;

use App\Filament\Resources\LearnFrets\LearnFretResource;
use Filament\Actions\DeleteAction;
use Filament\Actions\ViewAction;
use Filament\Resources\Pages\EditRecord;

class EditLearnFret extends EditRecord
{
    protected static string $resource = LearnFretResource::class;

    protected function getHeaderActions(): array
    {
        return [
            ViewAction::make(),
            DeleteAction::make(),
        ];
    }
}
