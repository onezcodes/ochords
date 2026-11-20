<?php

namespace App\Filament\Resources\Users\Schemas;

use App\Models\User;
use Filament\Infolists\Components\IconEntry;
use Filament\Infolists\Components\TextEntry;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;

class UserInfolist
{
    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                Section::make('Account')
                    ->columns(2)
                    ->schema([
                        TextEntry::make('id')->label('ID'),
                        TextEntry::make('handle'),
                        TextEntry::make('name'),
                        TextEntry::make('email')->label('Email (personal data)'),
                        TextEntry::make('city')->placeholder('—'),
                        TextEntry::make('tier')
                            ->getStateUsing(fn (User $record): string => $record->tier()->name),
                        IconEntry::make('is_admin')->label('Staff')->boolean(),
                        TextEntry::make('roles.name')
                            ->label('Roles')
                            ->badge()
                            ->separator(',')
                            ->placeholder('—'),
                        IconEntry::make('is_pro')
                            ->label('Pro')
                            ->boolean()
                            ->getStateUsing(fn (User $record): bool => $record->isPro()),
                        TextEntry::make('pro_until')->dateTime()->placeholder('—'),
                        TextEntry::make('pro_original_transaction_id')->placeholder('—'),
                        TextEntry::make('points')->numeric(),
                        TextEntry::make('groove_days')->numeric(),
                        TextEntry::make('deletion_requested_at')->dateTime()->placeholder('—'),
                        TextEntry::make('apple_id')->placeholder('—'),
                        TextEntry::make('google_id')->placeholder('—'),
                        TextEntry::make('created_at')->dateTime(),
                    ]),
            ]);
    }
}
