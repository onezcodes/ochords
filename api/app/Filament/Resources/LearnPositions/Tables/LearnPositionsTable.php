<?php

namespace App\Filament\Resources\LearnPositions\Tables;

use Filament\Actions\EditAction;
use Filament\Actions\ViewAction;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Table;

class LearnPositionsTable
{
    public static function configure(Table $table): Table
    {
        return $table
            ->defaultSort('number')
            ->columns([
                TextColumn::make('number')->sortable(),
                TextColumn::make('id')->label('ID')->copyable(),
                TextColumn::make('title')->searchable(),
                TextColumn::make('subtitle'),
                TextColumn::make('frets_count')->counts('frets')->label('Frets'),
            ])
            ->recordActions([
                ViewAction::make(),
                EditAction::make(),
            ]);
    }
}
