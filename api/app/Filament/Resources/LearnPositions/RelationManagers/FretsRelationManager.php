<?php

namespace App\Filament\Resources\LearnPositions\RelationManagers;

use Filament\Actions\CreateAction;
use Filament\Actions\DeleteAction;
use Filament\Actions\EditAction;
use Filament\Forms\Components\Select;
use Filament\Forms\Components\TextInput;
use Filament\Resources\RelationManagers\RelationManager;
use Filament\Schemas\Schema;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Table;

class FretsRelationManager extends RelationManager
{
    protected static string $relationship = 'frets';

    public function form(Schema $schema): Schema
    {
        return $schema
            ->components([
                TextInput::make('id')->label('ID')->required()->maxLength(32)->disabledOn('edit')->dehydrated(),
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

    public function table(Table $table): Table
    {
        return $table
            ->recordTitleAttribute('title')
            ->defaultSort('sort')
            ->reorderable('sort')
            ->columns([
                TextColumn::make('sort')->sortable(),
                TextColumn::make('id')->label('ID'),
                TextColumn::make('title')->searchable(),
                TextColumn::make('kind')->badge(),
            ])
            ->headerActions([
                CreateAction::make(),
            ])
            ->recordActions([
                EditAction::make(),
                DeleteAction::make(),
            ]);
    }
}
