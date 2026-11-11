<?php

namespace App\Services\Ocr;

/**
 * Used when OCHORDS_SCAN_DRIVER=none: every scan is read on the phone instead.
 */
class DisabledOcr implements OcrEngine
{
    public function name(): string
    {
        return 'none';
    }

    public function read(string $image): array
    {
        throw new ScanUnavailableException('Server scanning is turned off.');
    }
}
