<?php

namespace App\Filament\Resources\Songs\Schemas;

use App\Enums\SectionKind;
use Filament\Forms\Components\Repeater;
use Filament\Forms\Components\Select;
use Filament\Forms\Components\Textarea;
use Filament\Forms\Components\TextInput;
use Filament\Forms\Components\Toggle;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;

class SongForm
{
    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                Section::make('Metadata')
                    ->columns(2)
                    ->schema([
                        TextInput::make('title')->required()->maxLength(255),
                        TextInput::make('artist')->required()->maxLength(255),
                        TextInput::make('key')->required()->maxLength(16),
                        TextInput::make('tempo')->numeric()->required()->minValue(1)->maxValue(300),
                        TextInput::make('difficulty')->required()->maxLength(64),
                        Toggle::make('is_myanmar')->label('Myanmar'),
                        Toggle::make('is_public')->label('Public on Explore'),
                        Textarea::make('my_note')
                            ->label('Private note (capo, etc.)')
                            ->rows(2)
                            ->columnSpanFull()
                            ->helperText('Shown to the sheet owner in the app — often Capo N from a scan.'),
                    ]),
                Section::make('Sections')
                    ->description('ChordPro lines — one lyric/chord line per row. Bars are space-separated chord names for instrumental rows.')
                    ->schema([
                        Repeater::make('sections')
                            ->collapsible()
                            ->itemLabel(fn (array $state): ?string => $state['label'] ?? ($state['kind'] ?? 'Section'))
                            ->schema([
                                TextInput::make('id')->hidden()->dehydrated(),
                                Select::make('kind')
                                    ->options(collect(SectionKind::cases())->mapWithKeys(
                                        fn (SectionKind $k) => [$k->value => $k->value]
                                    )->all())
                                    ->required(),
                                TextInput::make('label')->maxLength(64),
                                TextInput::make('note')->maxLength(128),
                                TextInput::make('strum')->label('Strum pattern')->maxLength(128),
                                Textarea::make('lines')
                                    ->label('ChordPro lines')
                                    ->rows(4)
                                    ->columnSpanFull()
                                    ->formatStateUsing(fn ($state): string => is_array($state) ? implode("\n", $state) : (string) $state)
                                    ->dehydrateStateUsing(fn ($state): array => self::linesFromText((string) $state)),
                                TextInput::make('bars')
                                    ->label('Bars (space-separated)')
                                    ->formatStateUsing(fn ($state): string => is_array($state) ? implode(' ', $state) : (string) $state)
                                    ->dehydrateStateUsing(fn ($state): array => array_values(array_filter(
                                        preg_split('/\s+/', trim((string) $state)) ?: []
                                    ))),
                            ])
                            ->columns(2)
                            ->columnSpanFull(),
                    ]),
            ]);
    }

    /** @return list<string> */
    private static function linesFromText(string $text): array
    {
        $lines = preg_split("/\r\n|\n|\r/", $text) ?: [];

        return array_values(array_map('strval', $lines));
    }
}
