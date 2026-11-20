<?php

namespace App\Filament\Resources\Songs\Schemas;

use App\Models\Song;
use Filament\Infolists\Components\IconEntry;
use Filament\Infolists\Components\TextEntry;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;

class SongInfolist
{
    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                Section::make('Sheet')
                    ->columns(2)
                    ->schema([
                        TextEntry::make('title'),
                        TextEntry::make('artist'),
                        TextEntry::make('id')->label('ID')->copyable(),
                        TextEntry::make('owner.handle')->label('Owner'),
                        TextEntry::make('key'),
                        TextEntry::make('tempo'),
                        TextEntry::make('difficulty'),
                        TextEntry::make('source')->badge(),
                        IconEntry::make('is_public')->label('Public')->boolean(),
                        IconEntry::make('is_myanmar')->label('Myanmar')->boolean(),
                        IconEntry::make('featured')
                            ->label('Featured')
                            ->boolean()
                            ->getStateUsing(fn (Song $record): bool => $record->featured_at !== null),
                        TextEntry::make('featured_at')->dateTime()->placeholder('—'),
                        TextEntry::make('plays')->numeric(),
                        TextEntry::make('comments_count')->numeric(),
                        TextEntry::make('added_at')->dateTime(),
                        TextEntry::make('moderation_note')
                            ->label('Internal moderation note')
                            ->placeholder('—')
                            ->columnSpanFull(),
                    ]),
                Section::make('Chord sheet preview')
                    ->schema([
                        TextEntry::make('sections')
                            ->label('')
                            ->html()
                            ->formatStateUsing(fn ($state): string => self::renderSections(is_array($state) ? $state : []))
                            ->columnSpanFull(),
                    ]),
            ]);
    }

    /** @param  list<array<string, mixed>>  $sections */
    private static function renderSections(array $sections): string
    {
        if ($sections === []) {
            return '<p class="text-gray-500">No sections.</p>';
        }

        $html = '';
        foreach ($sections as $section) {
            $kind = e((string) ($section['kind'] ?? 'verse'));
            $label = e((string) ($section['label'] ?? $kind));
            $html .= '<div class="mb-4 rounded-lg border border-gray-200 dark:border-white/10 p-3">';
            $html .= '<div class="mb-2 text-xs font-semibold uppercase tracking-wide text-amber-700">'.$label.' <span class="opacity-60">('.$kind.')</span></div>';

            $lines = $section['lines'] ?? $section['chords'] ?? null;
            if (is_array($lines)) {
                foreach ($lines as $line) {
                    if (is_string($line)) {
                        $html .= '<div class="font-mono text-sm whitespace-pre-wrap">'.e($line).'</div>';
                    } elseif (is_array($line)) {
                        $chords = e((string) ($line['chords'] ?? $line['chord'] ?? ''));
                        $lyrics = e((string) ($line['lyrics'] ?? $line['text'] ?? ''));
                        if ($chords !== '') {
                            $html .= '<div class="font-mono text-sm text-amber-800 dark:text-amber-300">'.$chords.'</div>';
                        }
                        if ($lyrics !== '') {
                            $html .= '<div class="font-mono text-sm whitespace-pre-wrap">'.$lyrics.'</div>';
                        }
                    }
                }
            } else {
                $html .= '<pre class="text-xs overflow-x-auto">'.e(json_encode($section, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE) ?: '').'</pre>';
            }
            $html .= '</div>';
        }

        return $html;
    }
}
