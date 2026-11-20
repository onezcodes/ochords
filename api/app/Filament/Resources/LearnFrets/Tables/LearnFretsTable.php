<?php

namespace App\Filament\Resources\LearnFrets\Tables;

use Filament\Actions\EditAction;
use Filament\Actions\ViewAction;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Table;

class LearnFretsTable
{
    public static function configure(Table $table): Table
    {
        return $table
            ->defaultSort('sort')
            ->columns([
                TextColumn::make('sort')->sortable(),
                TextColumn::make('id')->label('ID')->searchable(),
                TextColumn::make('title')->searchable(),
                TextColumn::make('position.title')->label('Position')->sortable(),
                TextColumn::make('kind')->badge(),
                TextColumn::make('questions_count')->counts('questions')->label('Questions'),
            ])
            ->filters([
                SelectFilter::make('learn_position_id')
                    ->label('Position')
                    ->relationship('position', 'title'),
            ])
            ->recordActions([
                ViewAction::make(),
                EditAction::make(),
            ]);
    }
}
