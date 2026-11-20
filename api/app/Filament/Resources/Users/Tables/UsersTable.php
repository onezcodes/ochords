<?php

namespace App\Filament\Resources\Users\Tables;

use App\Enums\AdminPermission;
use App\Models\User;
use App\Services\AdminAccessService;
use App\Services\AdminAuditService;
use Filament\Actions\Action;
use Filament\Actions\EditAction;
use Filament\Actions\ViewAction;
use Filament\Forms\Components\DateTimePicker;
use Filament\Forms\Components\Select;
use Filament\Notifications\Notification;
use Filament\Tables\Columns\IconColumn;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Filters\TernaryFilter;
use Filament\Tables\Table;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Validation\ValidationException;

class UsersTable
{
    public static function configure(Table $table): Table
    {
        return $table
            ->modifyQueryUsing(fn (Builder $query): Builder => $query->with('roles'))
            ->defaultSort('id', 'desc')
            ->columns([
                TextColumn::make('id')->label('ID')->sortable(),
                TextColumn::make('handle')->searchable()->sortable(),
                TextColumn::make('name')->searchable(),
                TextColumn::make('roles.name')->label('Roles')->badge()->separator(',')->placeholder('—'),
                TextColumn::make('tier')
                    ->label('Tier')
                    ->getStateUsing(fn (User $record): string => $record->tier()->name),
                IconColumn::make('is_pro')
                    ->label('Pro')
                    ->boolean()
                    ->getStateUsing(fn (User $record): bool => $record->isPro()),
                TextColumn::make('pro_until')->dateTime()->placeholder('—')->toggleable(),
                TextColumn::make('points')->numeric()->sortable()->alignEnd(),
                TextColumn::make('deletion_requested_at')->label('Deletion')->dateTime()->placeholder('—')->toggleable(),
                IconColumn::make('is_admin')->label('Staff')->boolean(),
            ])
            ->filters([
                TernaryFilter::make('is_admin')
                    ->label('Staff')
                    ->trueLabel('Staff only')
                    ->falseLabel('Players only')
                    ->default(true),
                SelectFilter::make('roles')
                    ->relationship('roles', 'name')
                    ->multiple()
                    ->preload(),
                TernaryFilter::make('pro')
                    ->label('Pro')
                    ->queries(
                        true: fn (Builder $query) => $query->where('pro_until', '>', now()),
                        false: fn (Builder $query) => $query->where(fn (Builder $q) => $q->whereNull('pro_until')->orWhere('pro_until', '<=', now())),
                        blank: fn (Builder $query) => $query,
                    ),
                TernaryFilter::make('pending_deletion')
                    ->label('Pending deletion')
                    ->queries(
                        true: fn (Builder $query) => $query->whereNotNull('deletion_requested_at'),
                        false: fn (Builder $query) => $query->whereNull('deletion_requested_at'),
                        blank: fn (Builder $query) => $query,
                    ),
            ])
            ->recordActions([
                ViewAction::make(),
                EditAction::make(),
                Action::make('assignRoles')
                    ->label('Roles')
                    ->icon('heroicon-o-shield-check')
                    ->visible(fn (): bool => self::actorCan(AdminPermission::UsersManageRoles))
                    ->form([
                        Select::make('roles')
                            ->label('Roles')
                            ->multiple()
                            ->options(fn (AdminAccessService $access) => collect($access->assignableRoleNames())->mapWithKeys(fn (string $n) => [$n => $n]))
                            ->default(fn (User $record) => $record->getRoleNames()->all()),
                    ])
                    ->action(function (User $record, array $data, AdminAccessService $access): void {
                        try {
                            $access->syncRoles($record, $data['roles'] ?? []);
                            Notification::make()->title('Roles updated')->success()->send();
                        } catch (ValidationException $e) {
                            Notification::make()->title($e->getMessage())->danger()->send();
                        }
                    }),
                Action::make('setPro')
                    ->label('Set Pro')
                    ->visible(fn (): bool => self::actorCan(AdminPermission::UsersManagePro))
                    ->form([
                        DateTimePicker::make('pro_until')->label('Pro until')->required()->native(false),
                    ])
                    ->action(function (User $record, array $data, AdminAuditService $audit): void {
                        $record->forceFill(['pro_until' => $data['pro_until']])->save();
                        $audit->log('user.pro_set', $record);
                        Notification::make()->title('Pro updated')->success()->send();
                    }),
                Action::make('clearPro')
                    ->label('Clear Pro')
                    ->color('warning')
                    ->requiresConfirmation()
                    ->visible(fn (User $record): bool => self::actorCan(AdminPermission::UsersManagePro) && $record->pro_until !== null)
                    ->action(function (User $record, AdminAuditService $audit): void {
                        $record->forceFill([
                            'pro_until' => null,
                            'pro_original_transaction_id' => null,
                        ])->save();
                        $audit->log('user.pro_cleared', $record);
                        Notification::make()->title('Pro cleared')->success()->send();
                    }),
                Action::make('cancelDeletion')
                    ->label('Cancel deletion')
                    ->color('success')
                    ->requiresConfirmation()
                    ->visible(fn (User $record): bool => self::actorCan(AdminPermission::UsersUpdate) && $record->deletion_requested_at !== null)
                    ->action(function (User $record): void {
                        $record->forceFill([
                            'deletion_requested_at' => null,
                            'deletion_reason' => null,
                        ])->save();
                        Notification::make()->title('Deletion cancelled')->success()->send();
                    }),
                Action::make('revokeTokens')
                    ->label('Sign out everywhere')
                    ->color('danger')
                    ->requiresConfirmation()
                    ->visible(fn (): bool => self::actorCan(AdminPermission::UsersUpdate))
                    ->action(function (User $record): void {
                        $record->tokens()->delete();
                        Notification::make()->title('Tokens revoked')->success()->send();
                    }),
            ]);
    }

    private static function actorCan(AdminPermission $permission): bool
    {
        $actor = auth()->user();

        return $actor instanceof User && ($actor->isSuperAdmin() || $actor->can($permission->value));
    }
}
