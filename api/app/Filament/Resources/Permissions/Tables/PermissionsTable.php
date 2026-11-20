<?php

namespace App\Filament\Resources\Permissions\Tables;

use Filament\Actions\ViewAction;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Table;

class PermissionsTable
{
    public static function configure(Table $table): Table
    {
        return $table
            ->defaultSort('name')
            ->columns([
                TextColumn::make('name')->searchable()->sortable(),
                TextColumn::make('guard_name')->badge(),
                TextColumn::make('roles_count')->counts('roles')->label('Roles'),
            ])
            ->recordActions([
                ViewAction::make(),
            ]);
    }
}
