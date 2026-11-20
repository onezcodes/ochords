<?php

namespace App\Filament\Resources\Users\Pages;

use App\Enums\AdminPermission;
use App\Filament\Resources\Users\UserResource;
use App\Models\User;
use App\Services\AdminAccessService;
use App\Services\AdminAuditService;
use Filament\Actions\Action;
use Filament\Actions\EditAction;
use Filament\Forms\Components\DateTimePicker;
use Filament\Forms\Components\Select;
use Filament\Notifications\Notification;
use Filament\Resources\Pages\ViewRecord;
use Illuminate\Validation\ValidationException;

class ViewUser extends ViewRecord
{
    protected static string $resource = UserResource::class;

    protected function getHeaderActions(): array
    {
        return [
            EditAction::make(),
            Action::make('assignRoles')
                ->label('Assign roles')
                ->visible(fn (): bool => $this->actorCan(AdminPermission::UsersManageRoles))
                ->form([
                    Select::make('roles')
                        ->label('Roles')
                        ->multiple()
                        ->options(fn (AdminAccessService $access) => collect($access->assignableRoleNames())->mapWithKeys(fn (string $n) => [$n => $n]))
                        ->default(fn (): array => $this->record->getRoleNames()->all()),
                ])
                ->action(function (array $data, AdminAccessService $access): void {
                    /** @var User $user */
                    $user = $this->record;
                    try {
                        $access->syncRoles($user, $data['roles'] ?? []);
                        Notification::make()->title('Roles updated')->success()->send();
                    } catch (ValidationException $e) {
                        Notification::make()->title(collect($e->errors())->flatten()->first() ?: $e->getMessage())->danger()->send();
                    }
                }),
            Action::make('setPro')
                ->label('Set Pro')
                ->visible(fn (): bool => $this->actorCan(AdminPermission::UsersManagePro))
                ->form([
                    DateTimePicker::make('pro_until')->label('Pro until')->required()->native(false),
                ])
                ->action(function (array $data, AdminAuditService $audit): void {
                    /** @var User $user */
                    $user = $this->record;
                    $user->forceFill(['pro_until' => $data['pro_until']])->save();
                    $audit->log('user.pro_set', $user);
                    Notification::make()->title('Pro updated')->success()->send();
                }),
            Action::make('clearPro')
                ->label('Clear Pro')
                ->color('warning')
                ->requiresConfirmation()
                ->visible(fn (): bool => $this->actorCan(AdminPermission::UsersManagePro) && $this->record->pro_until !== null)
                ->action(function (AdminAuditService $audit): void {
                    /** @var User $user */
                    $user = $this->record;
                    $user->forceFill([
                        'pro_until' => null,
                        'pro_original_transaction_id' => null,
                    ])->save();
                    $audit->log('user.pro_cleared', $user);
                    Notification::make()->title('Pro cleared')->success()->send();
                }),
            Action::make('cancelDeletion')
                ->label('Cancel deletion')
                ->color('success')
                ->requiresConfirmation()
                ->visible(fn (): bool => $this->actorCan(AdminPermission::UsersUpdate) && $this->record->deletion_requested_at !== null)
                ->action(function (): void {
                    /** @var User $user */
                    $user = $this->record;
                    $user->forceFill([
                        'deletion_requested_at' => null,
                        'deletion_reason' => null,
                    ])->save();
                    Notification::make()->title('Deletion cancelled')->success()->send();
                }),
            Action::make('revokeTokens')
                ->label('Sign out everywhere')
                ->color('danger')
                ->requiresConfirmation()
                ->visible(fn (): bool => $this->actorCan(AdminPermission::UsersUpdate))
                ->action(function (): void {
                    /** @var User $user */
                    $user = $this->record;
                    $user->tokens()->delete();
                    Notification::make()->title('Tokens revoked')->success()->send();
                }),
        ];
    }

    private function actorCan(AdminPermission $permission): bool
    {
        $actor = auth()->user();

        return $actor instanceof User && ($actor->isSuperAdmin() || $actor->can($permission->value));
    }
}
