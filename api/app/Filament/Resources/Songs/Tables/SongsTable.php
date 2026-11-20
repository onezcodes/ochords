<?php

namespace App\Filament\Resources\Songs\Tables;

use App\Enums\AdminPermission;
use App\Enums\SongSource;
use App\Models\Song;
use App\Models\User;
use App\Services\ModerationService;
use Filament\Actions\Action;
use Filament\Actions\DeleteAction;
use Filament\Actions\EditAction;
use Filament\Actions\ViewAction;
use Filament\Forms\Components\Textarea;
use Filament\Tables\Columns\IconColumn;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Filters\TernaryFilter;
use Filament\Tables\Table;
use Illuminate\Database\Eloquent\Builder;

class SongsTable
{
    public static function configure(Table $table): Table
    {
        return $table
            ->modifyQueryUsing(fn (Builder $query): Builder => $query->with('owner')->withCount('savers'))
            ->defaultSort('added_at', 'desc')
            ->columns([
                TextColumn::make('title')->searchable()->sortable(),
                TextColumn::make('artist')->searchable()->sortable(),
                TextColumn::make('id')->label('ID')->searchable()->toggleable(isToggledHiddenByDefault: true)->copyable(),
                TextColumn::make('owner.handle')->label('Owner')->placeholder('—'),
                TextColumn::make('key'),
                TextColumn::make('source')->badge()->formatStateUsing(fn (SongSource $state): string => $state->value),
                IconColumn::make('is_public')->label('Public')->boolean(),
                IconColumn::make('featured')
                    ->label('Featured')
                    ->boolean()
                    ->getStateUsing(fn (Song $record): bool => $record->featured_at !== null),
                IconColumn::make('is_myanmar')->label('MM')->boolean()->toggleable(),
                TextColumn::make('plays')->numeric()->sortable()->alignEnd(),
                TextColumn::make('savers_count')->label('Saves')->numeric()->alignEnd(),
                TextColumn::make('added_at')->dateTime()->sortable()->toggleable(),
            ])
            ->filters([
                TernaryFilter::make('is_public')->label('Public'),
                TernaryFilter::make('featured')
                    ->label('Featured')
                    ->queries(
                        true: fn (Builder $query) => $query->whereNotNull('featured_at'),
                        false: fn (Builder $query) => $query->whereNull('featured_at'),
                        blank: fn (Builder $query) => $query,
                    ),
                TernaryFilter::make('is_myanmar')->label('Myanmar'),
                SelectFilter::make('source')->options(collect(SongSource::cases())->mapWithKeys(
                    fn (SongSource $s) => [$s->value => $s->value]
                )->all()),
            ])
            ->recordActions([
                ViewAction::make(),
                EditAction::make(),
                DeleteAction::make(),
                Action::make('hide')
                    ->label('Hide')
                    ->color('danger')
                    ->requiresConfirmation()
                    ->form([
                        Textarea::make('note')->label('Internal note')->rows(2),
                    ])
                    ->visible(fn (Song $record): bool => $record->is_public && self::canModerate())
                    ->action(fn (Song $record, array $data, ModerationService $moderation) => $moderation->hideSheet($record, $data['note'] ?? null)),
                Action::make('unhide')
                    ->label('Unhide')
                    ->color('success')
                    ->form([
                        Textarea::make('note')->label('Internal note')->rows(2),
                    ])
                    ->visible(fn (Song $record): bool => ! $record->is_public && self::canModerate())
                    ->action(fn (Song $record, array $data, ModerationService $moderation) => $moderation->unhideSheet($record, $data['note'] ?? null)),
                Action::make('feature')
                    ->label('Feature')
                    ->visible(fn (Song $record): bool => $record->featured_at === null && self::canModerate())
                    ->action(fn (Song $record, ModerationService $moderation) => $moderation->featureSheet($record)),
                Action::make('unfeature')
                    ->label('Unfeature')
                    ->color('gray')
                    ->visible(fn (Song $record): bool => $record->featured_at !== null && self::canModerate())
                    ->action(fn (Song $record, ModerationService $moderation) => $moderation->unfeatureSheet($record)),
            ]);
    }

    private static function canModerate(): bool
    {
        $actor = auth()->user();

        return $actor instanceof User && ($actor->isSuperAdmin() || $actor->can(AdminPermission::SongsModerate->value));
    }
}
