<?php

namespace App\Filament\Resources\LearnFrets\RelationManagers;

use Filament\Actions\CreateAction;
use Filament\Actions\DeleteAction;
use Filament\Actions\EditAction;
use Filament\Forms\Components\Select;
use Filament\Forms\Components\TagsInput;
use Filament\Forms\Components\Textarea;
use Filament\Forms\Components\TextInput;
use Filament\Resources\RelationManagers\RelationManager;
use Filament\Schemas\Schema;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Table;

class QuestionsRelationManager extends RelationManager
{
    protected static string $relationship = 'questions';

    public function form(Schema $schema): Schema
    {
        return $schema
            ->components([
                TextInput::make('id')->label('ID')->required()->maxLength(32)->disabledOn('edit')->dehydrated(),
                TextInput::make('sort')->numeric()->required()->minValue(0),
                Select::make('kind')
                    ->options([
                        'pickChord' => 'pickChord',
                        'buildBar' => 'buildBar',
                        'listen' => 'listen',
                    ])
                    ->required(),
                TextInput::make('prompt')->required()->maxLength(255),
                TextInput::make('detail')->maxLength(255)->default(''),
                TagsInput::make('options')->placeholder('Add option')->default([]),
                Textarea::make('answer')->rows(2)->default(''),
            ]);
    }

    public function table(Table $table): Table
    {
        return $table
            ->recordTitleAttribute('prompt')
            ->defaultSort('sort')
            ->reorderable('sort')
            ->columns([
                TextColumn::make('sort')->sortable(),
                TextColumn::make('id')->label('ID'),
                TextColumn::make('kind')->badge(),
                TextColumn::make('prompt')->limit(50)->searchable(),
                TextColumn::make('answer')->limit(30),
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
