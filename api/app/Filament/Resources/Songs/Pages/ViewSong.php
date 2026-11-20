<?php

namespace App\Filament\Resources\Songs\Pages;

use App\Enums\AdminPermission;
use App\Filament\Resources\Songs\SongResource;
use App\Models\Song;
use App\Models\User;
use App\Services\ModerationService;
use Filament\Actions\Action;
use Filament\Actions\DeleteAction;
use Filament\Actions\EditAction;
use Filament\Forms\Components\Textarea;
use Filament\Resources\Pages\ViewRecord;

class ViewSong extends ViewRecord
{
    protected static string $resource = SongResource::class;

    protected function getHeaderActions(): array
    {
        return [
            EditAction::make(),
            DeleteAction::make()
                ->successRedirectUrl(SongResource::getUrl('index')),
            Action::make('hide')
                ->label('Hide')
                ->color('danger')
                ->requiresConfirmation()
                ->form([
                    Textarea::make('note')->label('Internal note')->rows(2),
                ])
                ->visible(fn (): bool => $this->record->is_public && self::canModerate())
                ->action(function (array $data, ModerationService $moderation): void {
                    /** @var Song $song */
                    $song = $this->record;
                    $moderation->hideSheet($song, $data['note'] ?? null);
                    $this->refreshFormData(['is_public', 'moderation_note']);
                }),
            Action::make('unhide')
                ->label('Unhide')
                ->color('success')
                ->requiresConfirmation()
                ->form([
                    Textarea::make('note')->label('Internal note')->rows(2),
                ])
                ->visible(fn (): bool => ! $this->record->is_public && self::canModerate())
                ->action(function (array $data, ModerationService $moderation): void {
                    /** @var Song $song */
                    $song = $this->record;
                    $moderation->unhideSheet($song, $data['note'] ?? null);
                    $this->refreshFormData(['is_public', 'moderation_note']);
                }),
        ];
    }

    private static function canModerate(): bool
    {
        $actor = auth()->user();

        return $actor instanceof User && ($actor->isSuperAdmin() || $actor->can(AdminPermission::SongsModerate->value));
    }
}
