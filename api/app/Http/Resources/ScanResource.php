<?php

namespace App\Http\Resources;

use App\Services\Ocr\OcrLine;
use App\Services\Ocr\OcrWord;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * Lines of positioned text, in the shape of the app's `ScanTextLine` / `ScanWord`
 * (boxes normalised 0–1, origin top-left). The app turns them into a song.
 *
 * @property array{engine: string, lines: list<OcrLine>} $resource
 */
class ScanResource extends JsonResource
{
    /** @return array<string, mixed> */
    public function toArray(Request $request): array
    {
        return [
            'engine' => $this->resource['engine'],
            'lines' => array_map(fn (OcrLine $line) => [
                'confidence' => round($line->confidence, 3),
                'words' => array_map(fn (OcrWord $w) => [
                    'text' => $w->text,
                    'x' => round($w->x, 5),
                    'y' => round($w->y, 5),
                    'width' => round($w->width, 5),
                    'height' => round($w->height, 5),
                ], $line->words),
            ], $this->resource['lines']),
        ];
    }
}
