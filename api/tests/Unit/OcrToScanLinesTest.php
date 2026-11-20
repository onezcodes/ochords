<?php

namespace Tests\Unit;

use App\Services\Ocr\OcrLine;
use App\Services\Ocr\OcrWord;
use App\Services\SheetParsing\OcrToScanLines;
use App\Services\SheetParsing\ScanTextLine;
use Tests\TestCase;

class OcrToScanLinesTest extends TestCase
{
    public function test_convert_maps_ocr_lines_to_scan_text_lines(): void
    {
        $ocr = [
            new OcrLine([
                new OcrWord('C', 0.10, 0.20, 0.04, 0.03),
                new OcrWord('G', 0.40, 0.20, 0.04, 0.03),
            ], 0.97),
            new OcrLine([
                new OcrWord('Hello', 0.10, 0.26, 0.12, 0.03),
            ], 0.88),
        ];

        $lines = OcrToScanLines::convert($ocr);

        $this->assertCount(2, $lines);
        $this->assertContainsOnlyInstancesOf(ScanTextLine::class, $lines);

        $this->assertSame(0.97, $lines[0]->confidence);
        $this->assertCount(2, $lines[0]->words);
        $this->assertSame('C', $lines[0]->words[0]->text);
        $this->assertSame(0.10, $lines[0]->words[0]->box->x);
        $this->assertSame(0.20, $lines[0]->words[0]->box->y);
        $this->assertSame(0.04, $lines[0]->words[0]->box->width);
        $this->assertSame(0.03, $lines[0]->words[0]->box->height);
        $this->assertSame('G', $lines[0]->words[1]->text);

        $this->assertSame(0.88, $lines[1]->confidence);
        $this->assertSame('Hello', $lines[1]->text());
    }

    public function test_convert_empty_list(): void
    {
        $this->assertSame([], OcrToScanLines::convert([]));
    }
}
