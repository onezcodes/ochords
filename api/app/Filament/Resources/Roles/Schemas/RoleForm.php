<?php

namespace App\Filament\Resources\Roles\Schemas;

use Filament\Forms\Components\CheckboxList;
use Filament\Forms\Components\TextInput;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;

class RoleForm
{
    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                Section::make('Role')
                    ->schema([
                        TextInput::make('name')
                            ->required()
                            ->unique(ignoreRecord: true)
                            ->maxLength(125)
                            ->helperText('Use snake_case (e.g. content_editor).'),
                        TextInput::make('guard_name')
                            ->default('web')
                            ->required()
                            ->maxLength(125)
                            ->disabledOn('edit')
                            ->dehydrated(),
                    ]),
                Section::make('Permissions')
                    ->schema([
                        CheckboxList::make('permissions')
                            ->relationship('permissions', 'name')
                            ->columns(2)
                            ->searchable()
                            ->bulkToggleable(),
                    ]),
            ]);
    }
}
