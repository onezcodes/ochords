<?php

namespace App\Services\Ocr;

use Illuminate\Http\Client\ConnectionException;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

/**
 * Google Cloud Vision "document text detection", hinted to Burmese and English.
 * Unlike the phone's Apple Vision, it reads Myanmar script.
 */
class GoogleVisionOcr implements OcrEngine
{
    /** Google marks the end of a line with one of these breaks on the last letter. */
    private const LINE_ENDS = ['LINE_BREAK', 'EOL_SURE_SPACE'];

    public function __construct(
        private readonly string $key,
        private readonly string $endpoint,
        private readonly int $timeoutSeconds,
    ) {}

    public function name(): string
    {
        return 'google';
    }

    public function read(string $image): array
    {
        try {
            // The key goes in a header, never the URL, so it can't end up in proxy or server logs.
            $response = Http::withHeaders(['X-Goog-Api-Key' => $this->key])
                ->timeout($this->timeoutSeconds)
                ->post($this->endpoint, [
                    'requests' => [[
                        'image' => ['content' => base64_encode($image)],
                        'features' => [['type' => 'DOCUMENT_TEXT_DETECTION']],
                        'imageContext' => ['languageHints' => ['my', 'en']],
                    ]],
                ]);
        } catch (ConnectionException) {
            Log::warning('scan.google.unreachable');
            throw new ScanUnavailableException('The text reader can’t be reached.');
        }

        $result = $response->json('responses.0');
        if ($response->failed() || ! is_array($result) || isset($result['error'])) {
            Log::warning('scan.google.failed', ['status' => $response->status(), 'code' => $result['error']['code'] ?? null]);
            throw new ScanUnavailableException('The text reader didn’t answer.');
        }

        return self::linesFrom($result);
    }

    /**
     * Turns one Vision response into lines of positioned words.
     *
     * @param  array<string, mixed>  $result  `responses[0]` from images:annotate
     * @return list<OcrLine>
     */
    public static function linesFrom(array $result): array
    {
        $page = $result['fullTextAnnotation']['pages'][0] ?? null;
        if (! is_array($page) || empty($page['width']) || empty($page['height'])) {
            return [];
        }

        $lines = [];
        foreach ($page['blocks'] ?? [] as $block) {
            foreach ($block['paragraphs'] ?? [] as $paragraph) {
                $lines = [...$lines, ...self::paragraphLines($paragraph, (float) $page['width'], (float) $page['height'])];
            }
        }

        return $lines;
    }

    /**
     * @param  array<string, mixed>  $paragraph
     * @return list<OcrLine>
     */
    private static function paragraphLines(array $paragraph, float $width, float $height): array
    {
        $lines = [];
        $words = [];
        $confidence = 1.0;
        // Google splits text into dictionary words even where the page has no space ("G" "/" "B",
        // Burmese phrases); only a break on a word's last letter is a real space, so join the rest.
        // (The gap between pieces can't tell instead: inside a Burmese word it's as wide as a space.)
        $joinNext = false;

        foreach ($paragraph['words'] ?? [] as $word) {
            $symbols = $word['symbols'] ?? [];
            $text = implode('', array_map(fn (array $s) => (string) ($s['text'] ?? ''), $symbols));
            if ($text === '') {
                continue;
            }
            $box = self::word($text, $word['boundingBox']['vertices'] ?? [], $width, $height);
            $words[] = $joinNext && $words !== [] ? self::joined(array_pop($words), $box) : $box;
            $confidence = min($confidence, (float) ($word['confidence'] ?? 1));

            $lastBreak = end($symbols)['property']['detectedBreak']['type'] ?? null;
            $joinNext = $lastBreak === null;
            if (in_array($lastBreak, self::LINE_ENDS, true)) {
                $lines[] = new OcrLine($words, $confidence);
                $words = [];
                $confidence = 1.0;
            }
        }
        if ($words !== []) {
            $lines[] = new OcrLine($words, $confidence);
        }

        return $lines;
    }

    private static function joined(OcrWord $a, OcrWord $b): OcrWord
    {
        $left = min($a->x, $b->x);
        $top = min($a->y, $b->y);

        return new OcrWord(
            text: $a->text.$b->text,
            x: $left,
            y: $top,
            width: max($a->x + $a->width, $b->x + $b->width) - $left,
            height: max($a->y + $a->height, $b->y + $b->height) - $top,
        );
    }

    /** @param list<array{x?: int|float, y?: int|float}> $vertices Google omits a coordinate when it is 0. */
    private static function word(string $text, array $vertices, float $width, float $height): OcrWord
    {
        $xs = array_map(fn (array $v) => (float) ($v['x'] ?? 0), $vertices) ?: [0.0];
        $ys = array_map(fn (array $v) => (float) ($v['y'] ?? 0), $vertices) ?: [0.0];

        return new OcrWord(
            text: $text,
            x: min($xs) / $width,
            y: min($ys) / $height,
            width: (max($xs) - min($xs)) / $width,
            height: (max($ys) - min($ys)) / $height,
        );
    }
}
