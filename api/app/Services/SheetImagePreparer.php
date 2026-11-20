<?php

namespace App\Services;

use Illuminate\Http\UploadedFile;
use Illuminate\Validation\ValidationException;

/**
 * Turns an uploaded sheet photo/PDF into a JPEG suitable for Vision OCR.
 * Approximates the apps' prep: upright resize, optional page trim, light deskew.
 * Multi-page PDFs (≤4) are stacked vertically when Imagick is available.
 */
class SheetImagePreparer
{
    public const MAX_PDF_PAGES = 4;

    private const WHITE_THRESHOLD = 245;

    private const MARGIN_PAD = 12;

    private const MAX_SIDE = 2000;

    /**
     * @param  array{crop?: bool}  $options
     */
    public function toJpeg(UploadedFile $file, array $options = []): string
    {
        $crop = array_key_exists('crop', $options) ? (bool) $options['crop'] : true;
        $mime = (string) $file->getMimeType();
        $bytes = $file->getContent();
        if ($bytes === '') {
            throw ValidationException::withMessages(['image' => 'Could not read the uploaded file.']);
        }

        $name = strtolower($file->getClientOriginalName());
        if ($mime === 'application/pdf' || str_ends_with($name, '.pdf')) {
            $bytes = $this->pdfToStackedJpeg($bytes);
        }

        return $this->prepareJpeg($bytes, $crop);
    }

    private function pdfToStackedJpeg(string $pdf): string
    {
        if (! extension_loaded('imagick') || ! class_exists(\Imagick::class)) {
            throw ValidationException::withMessages([
                'image' => 'PDF import needs the Imagick PHP extension (or upload a JPEG/PNG of the page).',
            ]);
        }

        try {
            /** @var \Imagick $document */
            $document = new \Imagick;
            $document->setResolution(200, 200);
            $document->readImageBlob($pdf);
            $count = min($document->getNumberImages(), self::MAX_PDF_PAGES);
            if ($count < 1) {
                throw ValidationException::withMessages(['image' => 'That PDF has no pages to read.']);
            }

            /** @var list<\GdImage> $pages */
            $pages = [];
            $maxW = 0;
            $totalH = 0;
            for ($i = 0; $i < $count; $i++) {
                $document->setIteratorIndex($i);
                $page = $document->getImage();
                $page->setImageBackgroundColor('white');
                $page->setImageAlphaChannel(\Imagick::ALPHACHANNEL_REMOVE);
                $page->mergeImageLayers(\Imagick::LAYERMETHOD_FLATTEN);
                $page->setImageFormat('jpeg');
                $page->setImageCompressionQuality(88);
                $gd = @imagecreatefromstring($page->getImageBlob());
                $page->clear();
                if ($gd === false) {
                    continue;
                }
                $maxW = max($maxW, imagesx($gd));
                $totalH += imagesy($gd);
                $pages[] = $gd;
            }
            $document->clear();

            if ($pages === [] || $maxW < 1 || $totalH < 1) {
                throw ValidationException::withMessages(['image' => 'Could not render that PDF.']);
            }

            if (count($pages) === 1) {
                ob_start();
                imagejpeg($pages[0], null, 88);
                $jpeg = (string) ob_get_clean();
                imagedestroy($pages[0]);

                return $jpeg;
            }

            $stack = imagecreatetruecolor($maxW, $totalH);
            if ($stack === false) {
                foreach ($pages as $p) {
                    imagedestroy($p);
                }
                throw ValidationException::withMessages(['image' => 'Could not stitch PDF pages.']);
            }
            $white = imagecolorallocate($stack, 255, 255, 255);
            imagefilledrectangle($stack, 0, 0, $maxW - 1, $totalH - 1, $white);

            $y = 0;
            foreach ($pages as $page) {
                $w = imagesx($page);
                $h = imagesy($page);
                $x = (int) max(0, ($maxW - $w) / 2);
                imagecopy($stack, $page, $x, $y, 0, 0, $w, $h);
                $y += $h;
                imagedestroy($page);
            }

            ob_start();
            imagejpeg($stack, null, 88);
            $jpeg = (string) ob_get_clean();
            imagedestroy($stack);

            return $jpeg;
        } catch (ValidationException $e) {
            throw $e;
        } catch (\Throwable) {
            throw ValidationException::withMessages([
                'image' => 'Could not read that PDF. Try exporting pages as JPEGs.',
            ]);
        }
    }

    private function prepareJpeg(string $bytes, bool $crop): string
    {
        $source = @imagecreatefromstring($bytes);
        if ($source === false) {
            throw ValidationException::withMessages(['image' => 'That file is not a readable image.']);
        }

        $source = $this->deskew($source);

        if ($crop) {
            $source = $this->trimMargins($source);
        }

        $source = $this->fitMaxSide($source, self::MAX_SIDE);

        ob_start();
        imagejpeg($source, null, 85);
        $jpeg = (string) ob_get_clean();
        imagedestroy($source);

        if ($jpeg === '') {
            throw ValidationException::withMessages(['image' => 'Could not encode that image.']);
        }

        return $jpeg;
    }

    private function trimMargins(\GdImage $source): \GdImage
    {
        $width = imagesx($source);
        $height = imagesy($source);
        [$minX, $minY, $maxX, $maxY] = $this->contentBounds($source, $width, $height);

        $pad = self::MARGIN_PAD;
        $minX = max(0, $minX - $pad);
        $minY = max(0, $minY - $pad);
        $maxX = min($width - 1, $maxX + $pad);
        $maxY = min($height - 1, $maxY + $pad);
        $cropW = max(1, $maxX - $minX + 1);
        $cropH = max(1, $maxY - $minY + 1);

        if ($cropW >= $width - 2 && $cropH >= $height - 2) {
            return $source;
        }

        $canvas = imagecreatetruecolor($cropW, $cropH);
        if ($canvas === false) {
            return $source;
        }
        imagecopy($canvas, $source, 0, 0, $minX, $minY, $cropW, $cropH);
        imagedestroy($source);

        return $canvas;
    }

    private function fitMaxSide(\GdImage $source, int $maxSide): \GdImage
    {
        $width = imagesx($source);
        $height = imagesy($source);
        $longest = max($width, $height);
        if ($longest <= $maxSide) {
            return $source;
        }

        $scale = $maxSide / $longest;
        $outW = max(1, (int) round($width * $scale));
        $outH = max(1, (int) round($height * $scale));
        $canvas = imagecreatetruecolor($outW, $outH);
        if ($canvas === false) {
            return $source;
        }
        imagecopyresampled($canvas, $source, 0, 0, 0, 0, $outW, $outH, $width, $height);
        imagedestroy($source);

        return $canvas;
    }

    /**
     * Small-angle deskew via horizontal projection variance (searched on a downscaled copy).
     */
    private function deskew(\GdImage $source): \GdImage
    {
        $probe = $this->cloneImage($source);
        if ($probe === null) {
            return $source;
        }
        $probe = $this->fitMaxSide($probe, 800);

        $bestAngle = 0.0;
        $bestScore = $this->projectionVariance($probe);
        for ($angle = -3.5; $angle <= 3.5; $angle += 0.5) {
            if (abs($angle) < 0.01) {
                continue;
            }
            $bg = imagecolorallocate($probe, 255, 255, 255);
            $rotated = imagerotate($probe, $angle, $bg === false ? 0 : $bg);
            if ($rotated === false) {
                continue;
            }
            $score = $this->projectionVariance($rotated);
            imagedestroy($rotated);
            if ($score > $bestScore) {
                $bestScore = $score;
                $bestAngle = $angle;
            }
        }
        imagedestroy($probe);

        if (abs($bestAngle) < 0.4) {
            return $source;
        }

        $bg = imagecolorallocate($source, 255, 255, 255);
        $rotated = imagerotate($source, $bestAngle, $bg === false ? 0 : $bg);
        if ($rotated === false) {
            return $source;
        }
        imagedestroy($source);

        return $rotated;
    }

    private function cloneImage(\GdImage $source): ?\GdImage
    {
        $w = imagesx($source);
        $h = imagesy($source);
        $copy = imagecreatetruecolor($w, $h);
        if ($copy === false) {
            return null;
        }
        imagecopy($copy, $source, 0, 0, 0, 0, $w, $h);

        return $copy;
    }

    private function projectionVariance(\GdImage $image): float
    {
        $width = imagesx($image);
        $height = imagesy($image);
        if ($height < 2) {
            return 0.0;
        }

        $stepX = max(1, (int) floor($width / 200));
        $rows = [];
        for ($y = 0; $y < $height; $y++) {
            $ink = 0;
            for ($x = 0; $x < $width; $x += $stepX) {
                $rgb = imagecolorat($image, $x, $y);
                $r = ($rgb >> 16) & 0xFF;
                $g = ($rgb >> 8) & 0xFF;
                $b = $rgb & 0xFF;
                if ($r < self::WHITE_THRESHOLD || $g < self::WHITE_THRESHOLD || $b < self::WHITE_THRESHOLD) {
                    $ink++;
                }
            }
            $rows[] = $ink;
        }

        $n = count($rows);
        $mean = array_sum($rows) / $n;
        $var = 0.0;
        foreach ($rows as $v) {
            $d = $v - $mean;
            $var += $d * $d;
        }

        return $var / $n;
    }

    /**
     * @return array{0:int,1:int,2:int,3:int}
     */
    private function contentBounds(\GdImage $image, int $width, int $height): array
    {
        $step = max(1, (int) floor(min($width, $height) / 400));
        $minX = $width;
        $minY = $height;
        $maxX = 0;
        $maxY = 0;
        $found = false;

        for ($y = 0; $y < $height; $y += $step) {
            for ($x = 0; $x < $width; $x += $step) {
                $rgb = imagecolorat($image, $x, $y);
                $r = ($rgb >> 16) & 0xFF;
                $g = ($rgb >> 8) & 0xFF;
                $b = $rgb & 0xFF;
                if ($r < self::WHITE_THRESHOLD || $g < self::WHITE_THRESHOLD || $b < self::WHITE_THRESHOLD) {
                    $found = true;
                    $minX = min($minX, $x);
                    $minY = min($minY, $y);
                    $maxX = max($maxX, $x);
                    $maxY = max($maxY, $y);
                }
            }
        }

        if (! $found) {
            return [0, 0, $width - 1, $height - 1];
        }

        // Expand by step so we don't clip ink between samples.
        return [
            max(0, $minX - $step),
            max(0, $minY - $step),
            min($width - 1, $maxX + $step),
            min($height - 1, $maxY + $step),
        ];
    }
}
