<?php

namespace App\Filament\Resources\LearnPositions\Schemas;

use Filament\Infolists\Components\TextEntry;
use Filament\Schemas\Schema;

class LearnPositionInfolist
{
    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                TextEntry::make('id')->label('ID'),
                TextEntry::make('number'),
                TextEntry::make('title'),
                TextEntry::make('subtitle'),
            ]);
    }
}
