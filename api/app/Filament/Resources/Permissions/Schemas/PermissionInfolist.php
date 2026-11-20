<?php

namespace App\Filament\Resources\Permissions\Schemas;

use Filament\Infolists\Components\TextEntry;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;

class PermissionInfolist
{
    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                Section::make('Permission')
                    ->columns(2)
                    ->schema([
                        TextEntry::make('name'),
                        TextEntry::make('guard_name'),
                        TextEntry::make('roles.name')
                            ->label('Roles')
                            ->badge()
                            ->separator(',')
                            ->columnSpanFull(),
                    ]),
            ]);
    }
}
