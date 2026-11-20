<?php

namespace App\Filament\Resources\LearnFrets\Schemas;

use App\Models\LearnPosition;
use Filament\Forms\Components\Select;
use Filament\Forms\Components\TextInput;
use Filament\Schemas\Schema;

class LearnFretForm
{
    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                TextInput::make('id')->label('ID')->required()->maxLength(32)->disabledOn('edit')->dehydrated(),
                Select::make('learn_position_id')
                    ->label('Position')
                    ->options(fn () => LearnPosition::query()->orderBy('number')->pluck('title', 'id'))
                    ->required()
                    ->searchable(),
                TextInput::make('sort')->numeric()->required()->minValue(0),
                TextInput::make('title')->required()->maxLength(255),
                Select::make('kind')
                    ->options([
                        'lesson' => 'lesson',
                        'encore' => 'encore',
                        'soundcheck' => 'soundcheck',
                    ])
                    ->required()
                    ->default('lesson'),
            ]);
    }
}
