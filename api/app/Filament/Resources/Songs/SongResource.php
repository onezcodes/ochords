<?php

namespace App\Filament\Resources\Songs;

use App\Filament\Resources\Songs\Pages\EditSong;
use App\Filament\Resources\Songs\Pages\ListSongs;
use App\Filament\Resources\Songs\Pages\ViewSong;
use App\Filament\Resources\Songs\Schemas\SongForm;
use App\Filament\Resources\Songs\Schemas\SongInfolist;
use App\Filament\Resources\Songs\Tables\SongsTable;
use App\Models\Song;
use App\Models\User;
use BackedEnum;
use Filament\Resources\Resource;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Table;
use Illuminate\Database\Eloquent\Model;
use UnitEnum;

class SongResource extends Resource
{
    protected static ?string $model = Song::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedMusicalNote;

    protected static string|UnitEnum|null $navigationGroup = 'Catalog';

    protected static ?int $navigationSort = 1;

    protected static ?string $navigationLabel = 'Sheets';

    protected static ?string $modelLabel = 'Sheet';

    protected static ?string $pluralModelLabel = 'Sheets';

    protected static ?string $recordTitleAttribute = 'title';

    public static function form(Schema $schema): Schema
    {
        return SongForm::configure($schema);
    }

    public static function infolist(Schema $schema): Schema
    {
        return SongInfolist::configure($schema);
    }

    public static function table(Table $table): Table
    {
        return SongsTable::configure($table);
    }

    public static function getPages(): array
    {
        return [
            'index' => ListSongs::route('/'),
            'view' => ViewSong::route('/{record}'),
            'edit' => EditSong::route('/{record}/edit'),
        ];
    }

    public static function canCreate(): bool
    {
        $user = auth()->user();

        return $user instanceof User && $user->can('create', static::getModel());
    }

    public static function canDelete(Model $record): bool
    {
        $user = auth()->user();

        return $user instanceof User && $user->can('delete', $record);
    }
}
