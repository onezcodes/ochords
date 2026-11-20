<?php

namespace App\Filament\Resources\Users\Pages;

use App\Filament\Resources\Users\UserResource;
use App\Models\User;
use App\Services\AdminAccessService;
use App\Services\AdminAuditService;
use Filament\Actions\Action;
use Filament\Actions\ViewAction;
use Filament\Notifications\Notification;
use Filament\Resources\Pages\EditRecord;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;

class EditUser extends EditRecord
{
    protected static string $resource = UserResource::class;

    /** @var list<string> */
    private array $rolesBefore = [];

    protected function getHeaderActions(): array
    {
        return [
            ViewAction::make(),
            Action::make('revokeOtherSessions')
                ->label('Sign out other devices')
                ->color('danger')
                ->requiresConfirmation()
                ->visible(fn (): bool => $this->getActor() !== null && $this->record->is($this->getActor()))
                ->action(function (): void {
                    /** @var User $user */
                    $user = $this->record;
                    $user->tokens()->delete();
                    DB::table('sessions')
                        ->where('user_id', $user->id)
                        ->where('id', '!=', session()->getId())
                        ->delete();
                    Notification::make()->title('Other sessions signed out')->success()->send();
                    app(AdminAuditService::class)->log('user.sessions_revoked', $user);
                }),
        ];
    }

    protected function mutateFormDataBeforeFill(array $data): array
    {
        $this->rolesBefore = $this->record->getRoleNames()->all();

        return $data;
    }

    protected function afterSave(): void
    {
        /** @var User $user */
        $user = $this->record;
        $roles = $user->getRoleNames()->all();

        try {
            app(AdminAccessService::class)->syncRoles($user, $roles, $this->getActor());
        } catch (ValidationException $e) {
            $user->syncRoles($this->rolesBefore);
            Notification::make()->title(collect($e->errors())->flatten()->first() ?: $e->getMessage())->danger()->send();
            $this->halt();
        }
    }

    private function getActor(): ?User
    {
        return auth()->user() instanceof User ? auth()->user() : null;
    }
}
