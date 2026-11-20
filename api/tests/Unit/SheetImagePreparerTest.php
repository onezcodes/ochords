<?php

namespace Tests\Unit;

use App\Services\SheetImagePreparer;
use Illuminate\Http\UploadedFile;
use Illuminate\Validation\ValidationException;
use Tests\TestCase;

class SheetImagePreparerTest extends TestCase
{
    public function test_trims_white_margins_and_returns_jpeg(): void
    {
        $file = $this->jpegUpload($this->sheetWithMargins());

        $jpeg = app(SheetImagePreparer::class)->toJpeg($file, ['crop' => true]);

        $out = imagecreatefromstring($jpeg);
        $this->assertNotFalse($out);
        $this->assertLessThan(200, imagesx($out));
        $this->assertLessThan(200, imagesy($out));
        imagedestroy($out);
    }

    public function test_crop_false_keeps_full_canvas(): void
    {
        $file = $this->jpegUpload($this->sheetWithMargins());

        $jpeg = app(SheetImagePreparer::class)->toJpeg($file, ['crop' => false]);
        $out = imagecreatefromstring($jpeg);
        $this->assertNotFalse($out);
        // Deskew may add a little padding, but we should stay near the original size.
        $this->assertGreaterThan(150, imagesx($out));
        $this->assertGreaterThan(150, imagesy($out));
        imagedestroy($out);
    }

    public function test_pdf_without_imagick_explains_the_requirement(): void
    {
        if (extension_loaded('imagick')) {
            $this->markTestSkipped('Imagick is installed on this machine.');
        }

        $path = tempnam(sys_get_temp_dir(), 'sheet');
        file_put_contents($path, '%PDF-1.4 fake');
        $file = new UploadedFile($path, 'sheet.pdf', 'application/pdf', null, true);

        try {
            $this->expectException(ValidationException::class);
            app(SheetImagePreparer::class)->toJpeg($file);
        } finally {
            @unlink($path);
        }
    }

    public function test_max_pdf_pages_constant_matches_apps(): void
    {
        $this->assertSame(4, SheetImagePreparer::MAX_PDF_PAGES);
    }

    private function sheetWithMargins(): string
    {
        $img = imagecreatetruecolor(200, 200);
        $white = imagecolorallocate($img, 255, 255, 255);
        $ink = imagecolorallocate($img, 20, 20, 20);
        imagefilledrectangle($img, 0, 0, 199, 199, $white);
        imagefilledrectangle($img, 40, 50, 120, 130, $ink);
        ob_start();
        imagejpeg($img, null, 90);
        $raw = (string) ob_get_clean();
        imagedestroy($img);

        return $raw;
    }

    private function jpegUpload(string $raw): UploadedFile
    {
        $path = tempnam(sys_get_temp_dir(), 'sheet');
        file_put_contents($path, $raw);

        return new UploadedFile($path, 'sheet.jpg', 'image/jpeg', null, true);
    }
}
