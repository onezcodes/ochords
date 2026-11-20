<?php

namespace App\Filament\Resources\AdminAuditLogs\Tables;

use Filament\Actions\ViewAction;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Table;

class AdminAuditLogsTable
{
    public static function configure(Table $table): Table
    {
        return $table
            ->defaultSort('created_at', 'desc')
            ->columns([
                TextColumn::make('created_at')->dateTime()->sortable(),
                TextColumn::make('actor.handle')->label('Actor')->placeholder('system'),
                TextColumn::make('action')->badge()->searchable(),
                TextColumn::make('subject_type')->label('Subject')->formatStateUsing(
                    fn (?string $state): string => $state ? class_basename($state) : '—'
                ),
                TextColumn::make('subject_id')->label('Subject ID')->toggleable()->copyable(),
                TextColumn::make('ip')->toggleable(isToggledHiddenByDefault: true),
            ])
            ->filters([
                SelectFilter::make('action')
                    ->options([
                        'report.resolved' => 'report.resolved',
                        'sheet.hidden' => 'sheet.hidden',
                        'sheet.unhidden' => 'sheet.unhidden',
                        'sheet.featured' => 'sheet.featured',
                        'sheet.unfeatured' => 'sheet.unfeatured',
                        'user.roles_updated' => 'user.roles_updated',
                        'user.pro_set' => 'user.pro_set',
                        'user.pro_cleared' => 'user.pro_cleared',
                        'admin.granted' => 'admin.granted',
                        'admin.revoked' => 'admin.revoked',
                    ]),
            ])
            ->recordActions([
                ViewAction::make(),
            ]);
    }
}
