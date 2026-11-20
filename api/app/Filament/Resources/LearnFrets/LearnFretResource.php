<?php

namespace App\Filament\Resources\LearnFrets;

use App\Filament\Resources\LearnFrets\Pages\CreateLearnFret;
use App\Filament\Resources\LearnFrets\Pages\EditLearnFret;
use App\Filament\Resources\LearnFrets\Pages\ListLearnFrets;
use App\Filament\Resources\LearnFrets\Pages\ViewLearnFret;
use App\Filament\Resources\LearnFrets\RelationManagers\QuestionsRelationManager;
use App\Filament\Resources\LearnFrets\Schemas\LearnFretForm;
use App\Filament\Resources\LearnFrets\Schemas\LearnFretInfolist;
use App\Filament\Resources\LearnFrets\Tables\LearnFretsTable;
use App\Models\LearnFret;
use BackedEnum;
use Filament\Resources\Resource;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Table;
use UnitEnum;

class LearnFretResource extends Resource
{
    protected static ?string $model = LearnFret::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedQueueList;

    protected static string|UnitEnum|null $navigationGroup = 'Learn';

    protected static ?int $navigationSort = 2;

    protected static ?string $navigationLabel = 'Frets';

    protected static ?string $modelLabel = 'Learn fret';

    protected static ?string $recordTitleAttribute = 'title';

    public static function form(Schema $schema): Schema
    {
        return LearnFretForm::configure($schema);
    }

    public static function infolist(Schema $schema): Schema
    {
        return LearnFretInfolist::configure($schema);
    }

    public static function table(Table $table): Table
    {
        return LearnFretsTable::configure($table);
    }

    public static function getRelations(): array
    {
        return [
            QuestionsRelationManager::class,
        ];
    }

    public static function getPages(): array
    {
        return [
            'index' => ListLearnFrets::route('/'),
            'create' => CreateLearnFret::route('/create'),
            'view' => ViewLearnFret::route('/{record}'),
            'edit' => EditLearnFret::route('/{record}/edit'),
        ];
    }
}
