<?php

namespace App\Filament\Resources\Feedback\Schemas;

use Filament\Infolists\Components\TextEntry;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;

class FeedbackInfolist
{
    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                Section::make('Feedback')
                    ->columns(2)
                    ->schema([
                        TextEntry::make('id')->label('ID'),
                        TextEntry::make('kind')->badge(),
                        TextEntry::make('user.handle')->label('User')->placeholder('guest'),
                        TextEntry::make('created_at')->dateTime(),
                        TextEntry::make('reviewed_at')->dateTime()->placeholder('Unreviewed'),
                        TextEntry::make('message')
                            ->label('Message (personal data)')
                            ->columnSpanFull(),
                    ]),
            ]);
    }
}
