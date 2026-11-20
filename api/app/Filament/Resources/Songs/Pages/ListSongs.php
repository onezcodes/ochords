<?php

namespace App\Filament\Resources\Songs\Pages;

use App\Filament\Resources\Songs\SongResource;
use App\Services\AdminSheetImportService;
use App\Services\Ocr\OcrEngine;
use Filament\Actions\Action;
use Filament\Forms\Components\FileUpload;
use Filament\Forms\Components\TextInput;
use Filament\Forms\Components\Toggle;
use Filament\Notifications\Notification;
use Filament\Resources\Pages\ListRecords;
use Filament\Support\Icons\Heroicon;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Auth;
use Livewire\Features\SupportFileUploads\TemporaryUploadedFile;

class ListSongs extends ListRecords
{
    protected static string $resource = SongResource::class;

    protected function getHeaderActions(): array
    {
        return [
            Action::make('importScan')
                ->label('Import from scan')
                ->icon(Heroicon::OutlinedCamera)
                ->color('warning')
                ->visible(fn (): bool => SongResource::canCreate())
                ->modalHeading('Import sheet from photo')
                ->modalDescription('Upload a chord-sheet photo. The server reads it like the apps (OCR → ChordPro). Photos are sent to Google Vision when scanning is on — they are not stored on O Chords.')
                ->modalSubmitActionLabel('Scan & create')
                ->form([
                    FileUpload::make('image')
                        ->label('Sheet photo or PDF')
                        ->acceptedFileTypes(['image/jpeg', 'image/png', 'application/pdf'])
                        ->maxSize((int) config('ochords.scan.max_kilobytes'))
                        ->required()
                        ->storeFiles(false)
                        ->helperText(fn (): string => 'JPEG/PNG, or PDF (Imagick required for PDF). Margins are trimmed before OCR. Engine: '.app(OcrEngine::class)->name().'.'),
                    TextInput::make('title')->label('Title override')->maxLength(255),
                    TextInput::make('artist')->label('Artist override')->maxLength(255),
                    Toggle::make('crop')->label('Crop to page (trim margins + deskew)')->default(true),
                    Toggle::make('is_public')->label('Publish to Explore')->default(false),
                ])
                ->action(function (array $data, AdminSheetImportService $import): void {
                    $staff = Auth::user();
                    abort_unless($staff !== null, 403);

                    $file = $data['image'] ?? null;
                    if (is_array($file)) {
                        $file = reset($file) ?: null;
                    }
                    if (! $file instanceof TemporaryUploadedFile && ! $file instanceof UploadedFile) {
                        Notification::make()->title('Upload a JPEG or PNG photo')->danger()->send();

                        return;
                    }

                    $result = $import->importFromUpload($staff, $file, [
                        'title' => $data['title'] ?? null,
                        'artist' => $data['artist'] ?? null,
                        'is_public' => (bool) ($data['is_public'] ?? false),
                        'crop' => (bool) ($data['crop'] ?? true),
                    ]);

                    session()->flash('sheet_import_review', [
                        'low_confidence' => $result->lowConfidence,
                        'needs_lyrics' => $result->needsLyrics,
                        'chord_notes' => $result->chordNotes,
                    ]);

                    $checks = [];
                    if ($result->needsLyrics > 0) {
                        $checks[] = $result->needsLyrics.' line'.($result->needsLyrics === 1 ? '' : 's').' need lyrics';
                    }
                    if ($result->lowConfidence !== []) {
                        $checks[] = 'unclear chords: '.implode(', ', $result->lowConfidence);
                    }

                    $notification = Notification::make()
                        ->title('Sheet imported')
                        ->body($checks === []
                            ? 'Review the ChordPro sections, then publish if you want it on Explore.'
                            : 'Check before publishing — '.implode('; ', $checks).'.');

                    if ($checks === []) {
                        $notification->success()->send();
                    } else {
                        $notification->warning()->send();
                    }

                    $this->redirect(SongResource::getUrl('edit', ['record' => $result->song]));
                }),
        ];
    }
}
