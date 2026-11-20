<?php

namespace App\Filament\Resources\Roles\Schemas;

use Filament\Infolists\Components\TextEntry;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;

class RoleInfolist
{
    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                Section::make('Role')
                    ->columns(2)
                    ->schema([
                        TextEntry::make('name'),
                        TextEntry::make('guard_name'),
                        TextEntry::make('permissions.name')
                            ->label('Permissions')
                            ->badge()
                            ->separator(',')
                            ->columnSpanFull(),
                        TextEntry::make('users_count')
                            ->label('Users')
                            ->state(fn ($record) => $record->users()->count()),
                    ]),
            ]);
    }
}
