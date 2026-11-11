<?php

namespace App\Services\Ocr;

use RuntimeException;

/**
 * Server reading isn't possible right now (not configured, or the OCR provider failed).
 * The app falls back to reading on the phone, so this is a 503, not an error to show.
 */
class ScanUnavailableException extends RuntimeException {}
