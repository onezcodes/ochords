<?php

namespace App\Filament\Resources\LearnPositions\Schemas;

use Filament\Forms\Components\TextInput;
use Filament\Schemas\Schema;

class LearnPositionForm
{
    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                TextInput::make('id')
                    ->label('ID')
                    ->required()
                    ->maxLength(32)
                    ->disabledOn('edit')
                    ->dehydrated(),
                TextInput::make('number')->numeric()->required()->minValue(1),
                TextInput::make('title')->required()->maxLength(255),
                TextInput::make('subtitle')->maxLength(255)->default(''),
            ]);
    }
}
