<?php

namespace App\Filament\Resources\Songs\Pages;

use App\Filament\Resources\Songs\SongResource;
use Filament\Actions\DeleteAction;
use Filament\Actions\ViewAction;
use Filament\Notifications\Notification;
use Filament\Resources\Pages\EditRecord;
use Illuminate\Support\Str;

class EditSong extends EditRecord
{
    protected static string $resource = SongResource::class;

    public function mount(int|string $record): void
    {
        parent::mount($record);

        $review = session('sheet_import_review');
        if (! is_array($review)) {
            return;
        }

        $parts = [];
        $needs = (int) ($review['needs_lyrics'] ?? 0);
        if ($needs > 0) {
            $parts[] = "{$needs} line".($needs === 1 ? '' : 's').' still need lyrics (often shown as � or spaced chord-only rows)';
        }
        $low = $review['low_confidence'] ?? [];
        if (is_array($low) && $low !== []) {
            $parts[] = 'double-check chords: '.implode(', ', array_map('strval', $low));
        }
        $notes = $review['chord_notes'] ?? [];
        if (is_array($notes) && $notes !== []) {
            $sample = collect($notes)->take(3)->map(fn ($note, $chord) => "{$chord}: {$note}")->implode('; ');
            $parts[] = $sample;
        }

        if ($parts !== []) {
            Notification::make()
                ->title('Review this scan')
                ->body(implode(' · ', $parts))
                ->warning()
                ->send();
        }
    }

    protected function getHeaderActions(): array
    {
        return [
            ViewAction::make(),
            DeleteAction::make()
                ->successRedirectUrl(SongResource::getUrl('index')),
        ];
    }

    /**
     * @param  array<string, mixed>  $data
     * @return array<string, mixed>
     */
    protected function mutateFormDataBeforeSave(array $data): array
    {
        $sections = $data['sections'] ?? [];
        if (! is_array($sections)) {
            return $data;
        }

        $data['sections'] = array_values(array_map(function (mixed $section): array {
            $section = is_array($section) ? $section : [];

            return [
                'id' => (string) ($section['id'] ?? Str::uuid()),
                'kind' => (string) ($section['kind'] ?? 'verse'),
                'label' => (string) ($section['label'] ?? ''),
                'note' => $section['note'] ?? null,
                'strum' => $section['strum'] ?? null,
                'lines' => array_values(array_map('strval', $section['lines'] ?? [])),
                'bars' => array_values(array_map('strval', $section['bars'] ?? [])),
                'flow' => array_values($section['flow'] ?? []),
            ];
        }, $sections));

        return $data;
    }
}
