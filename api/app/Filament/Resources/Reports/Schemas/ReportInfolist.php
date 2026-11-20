<?php

namespace App\Filament\Resources\Reports\Schemas;

use Filament\Infolists\Components\TextEntry;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;

class ReportInfolist
{
    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                Section::make('Report')
                    ->columns(2)
                    ->schema([
                        TextEntry::make('id')->label('ID'),
                        TextEntry::make('reason')->badge(),
                        TextEntry::make('status'),
                        TextEntry::make('resolution')->badge()->placeholder('open'),
                        TextEntry::make('created_at')->dateTime(),
                        TextEntry::make('resolved_at')->dateTime()->placeholder('—'),
                        TextEntry::make('detail')
                            ->label('Detail (personal data)')
                            ->columnSpanFull()
                            ->placeholder('—'),
                        TextEntry::make('reporter.handle')->label('Reporter'),
                    ]),
                Section::make('Sheet')
                    ->columns(2)
                    ->schema([
                        TextEntry::make('song.title')->label('Title')->placeholder('deleted'),
                        TextEntry::make('song.artist')->label('Artist')->placeholder('—'),
                        TextEntry::make('song_id')->label('Song ID')->copyable(),
                        TextEntry::make('song.is_public')->label('Public')->formatStateUsing(
                            fn (?bool $state): string => $state === null ? '—' : ($state ? 'Yes' : 'No')
                        ),
                        TextEntry::make('song.owner.handle')->label('Owner')->placeholder('—'),
                    ]),
            ]);
    }
}
