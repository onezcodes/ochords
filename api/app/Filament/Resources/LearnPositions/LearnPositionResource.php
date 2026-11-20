<?php

namespace App\Filament\Resources\LearnPositions;

use App\Filament\Resources\LearnPositions\Pages\CreateLearnPosition;
use App\Filament\Resources\LearnPositions\Pages\EditLearnPosition;
use App\Filament\Resources\LearnPositions\Pages\ListLearnPositions;
use App\Filament\Resources\LearnPositions\Pages\ViewLearnPosition;
use App\Filament\Resources\LearnPositions\RelationManagers\FretsRelationManager;
use App\Filament\Resources\LearnPositions\Schemas\LearnPositionForm;
use App\Filament\Resources\LearnPositions\Schemas\LearnPositionInfolist;
use App\Filament\Resources\LearnPositions\Tables\LearnPositionsTable;
use App\Models\LearnPosition;
use BackedEnum;
use Filament\Resources\Resource;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Table;
use UnitEnum;

class LearnPositionResource extends Resource
{
    protected static ?string $model = LearnPosition::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedAcademicCap;

    protected static string|UnitEnum|null $navigationGroup = 'Learn';

    protected static ?int $navigationSort = 1;

    protected static ?string $navigationLabel = 'Positions';

    protected static ?string $modelLabel = 'Learn position';

    protected static ?string $recordTitleAttribute = 'title';

    public static function form(Schema $schema): Schema
    {
        return LearnPositionForm::configure($schema);
    }

    public static function infolist(Schema $schema): Schema
    {
        return LearnPositionInfolist::configure($schema);
    }

    public static function table(Table $table): Table
    {
        return LearnPositionsTable::configure($table);
    }

    public static function getRelations(): array
    {
        return [
            FretsRelationManager::class,
        ];
    }

    public static function getPages(): array
    {
        return [
            'index' => ListLearnPositions::route('/'),
            'create' => CreateLearnPosition::route('/create'),
            'view' => ViewLearnPosition::route('/{record}'),
            'edit' => EditLearnPosition::route('/{record}/edit'),
        ];
    }
}
