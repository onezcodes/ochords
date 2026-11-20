<?php

namespace App\Filament\Resources\AdminAuditLogs\Schemas;

use Filament\Infolists\Components\TextEntry;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;

class AdminAuditLogInfolist
{
    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                Section::make('Event')
                    ->columns(2)
                    ->schema([
                        TextEntry::make('created_at')->dateTime(),
                        TextEntry::make('actor.handle')->label('Actor')->placeholder('system'),
                        TextEntry::make('action')->badge(),
                        TextEntry::make('ip')->placeholder('—'),
                        TextEntry::make('subject_type')->label('Subject type'),
                        TextEntry::make('subject_id')->label('Subject ID')->copyable(),
                        TextEntry::make('meta')
                            ->label('Meta (IDs only)')
                            ->formatStateUsing(fn ($state): string => json_encode($state ?? [], JSON_PRETTY_PRINT) ?: '{}')
                            ->columnSpanFull(),
                    ]),
            ]);
    }
}
