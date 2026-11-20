<?php

namespace App\Filament\Resources\Users\Schemas;

use App\Enums\AdminPermission;
use App\Models\User;
use Filament\Forms\Components\CheckboxList;
use Filament\Forms\Components\DateTimePicker;
use Filament\Forms\Components\TextInput;
use Filament\Forms\Components\Toggle;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;

class UserForm
{
    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                Section::make('Profile')
                    ->columns(2)
                    ->schema([
                        TextInput::make('name')->disabled()->dehydrated(false),
                        TextInput::make('handle')->disabled()->dehydrated(false),
                        DateTimePicker::make('pro_until')
                            ->label('Pro until')
                            ->native(false)
                            ->visible(fn (): bool => self::actorCan(AdminPermission::UsersManagePro)),
                    ]),
                Section::make('Staff access')
                    ->description('Roles grant Filament /admin abilities. is_admin is kept in sync automatically.')
                    ->schema([
                        Toggle::make('is_admin')
                            ->label('Staff (is_admin)')
                            ->disabled()
                            ->dehydrated(false)
                            ->helperText('Derived from assigned staff roles.'),
                        CheckboxList::make('roles')
                            ->relationship('roles', 'name')
                            ->columns(2)
                            ->visible(fn (): bool => self::actorCan(AdminPermission::UsersManageRoles)),
                    ]),
            ]);
    }

    private static function actorCan(AdminPermission $permission): bool
    {
        $actor = auth()->user();

        return $actor instanceof User && ($actor->isSuperAdmin() || $actor->can($permission->value));
    }
}
