<?php

namespace App\Filament\Resources\LearnFrets\Schemas;

use Filament\Infolists\Components\TextEntry;
use Filament\Schemas\Schema;

class LearnFretInfolist
{
    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                TextEntry::make('id')->label('ID'),
                TextEntry::make('position.title')->label('Position'),
                TextEntry::make('sort'),
                TextEntry::make('title'),
                TextEntry::make('kind')->badge(),
            ]);
    }
}
