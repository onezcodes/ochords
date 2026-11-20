<?php

namespace Tests\Feature;

use App\Enums\AdminPermission;
use App\Enums\AdminRole;
use App\Models\AdminAuditLog;
use App\Models\Song;
use App\Models\User;
use App\Services\AdminSheetImportService;
use App\Services\CatalogOwnerService;
use Database\Seeders\RolesAndPermissionsSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Http;
use Illuminate\Validation\ValidationException;
use Tests\TestCase;

class AdminSheetImportTest extends TestCase
{
    use RefreshDatabase;

    private const ENDPOINT = 'https://vision.googleapis.com/v1/images:annotate';

    protected function setUp(): void
    {
        parent::setUp();
        $this->seed(RolesAndPermissionsSeeder::class);
        config([
            'ochords.scan.driver' => 'google',
            'services.google_vision.key' => 'test-key',
            'ochords.scan.daily_limit' => 1,
            'ochords.scan.monthly_limit' => 100,
        ]);
    }

    /** @return array<string, mixed> */
    private function word(string $text, array $box, ?string $break = 'SPACE', float $confidence = 0.97): array
    {
        $chars = mb_str_split($text);
        $symbols = array_map(fn (string $c) => ['text' => $c], $chars);
        if ($break) {
            $symbols[count($symbols) - 1]['property'] = ['detectedBreak' => ['type' => $break]];
        }
        [$l, $t, $r, $b] = $box;

        return [
            'confidence' => $confidence,
            'boundingBox' => ['vertices' => array_map(
                fn (array $v) => array_filter(['x' => $v[0], 'y' => $v[1]]),
                [[$l, $t], [$r, $t], [$r, $b], [$l, $b]],
            )],
            'symbols' => $symbols,
        ];
    }

    /** @return array<string, mixed> */
    private function visionSheet(): array
    {
        return ['responses' => [[
            'fullTextAnnotation' => ['pages' => [[
                'width' => 1000,
                'height' => 2000,
                'blocks' => [['paragraphs' => [
                    ['words' => [
                        $this->word('Morning', [300, 80, 420, 140]),
                        $this->word('Light', [430, 80, 520, 140], 'EOL_SURE_SPACE'),
                    ]],
                    ['words' => [
                        $this->word('C', [100, 400, 130, 440]),
                        $this->word('G', [400, 400, 430, 440], 'EOL_SURE_SPACE'),
                    ]],
                    ['words' => [
                        $this->word('Morning', [100, 450, 220, 490]),
                        $this->word('light', [230, 450, 310, 490]),
                        $this->word('shines', [400, 450, 520, 490], 'LINE_BREAK', 0.94),
                    ]],
                ]]],
            ]]],
        ]]];
    }

    public function test_staff_import_creates_song_from_scan(): void
    {
        Http::fake([self::ENDPOINT => Http::response($this->visionSheet())]);
        $staff = User::factory()->admin()->create();

        $result = app(AdminSheetImportService::class)->importFromUpload(
            $staff,
            UploadedFile::fake()->image('sheet.jpg', 1000, 2000),
            ['is_public' => true],
        );
        $song = $result->song;

        $this->assertNotSame($staff->id, $song->user_id);
        $this->assertSame('ochords', $song->owner?->handle);
        $this->assertTrue($song->is_public);
        $this->assertSame('Morning Light', $song->title);
        $this->assertNotEmpty($song->sections);
        $this->assertStringContainsString('[C]', implode("\n", $song->sections[0]['lines'] ?? []));
        $this->assertSame(1, AdminAuditLog::query()->where('action', 'sheet.imported_from_scan')->count());
        $this->assertIsInt($result->needsLyrics);
        $this->assertIsArray($result->lowConfidence);
    }

    public function test_staff_import_skips_daily_player_cap(): void
    {
        Http::fake([self::ENDPOINT => Http::response($this->visionSheet())]);
        $staff = User::factory()->admin()->create();
        $import = app(AdminSheetImportService::class);
        $photo = fn () => UploadedFile::fake()->image('sheet.jpg', 1000, 2000);

        $import->importFromUpload($staff, $photo());
        $import->importFromUpload($staff, $photo());

        $catalogId = app(CatalogOwnerService::class)->owner()->id;
        $this->assertSame(2, Song::query()->where('user_id', $catalogId)->count());
        $this->assertSame(0, Song::query()->where('user_id', $staff->id)->count());
    }

    public function test_staff_import_fails_when_scanning_is_off(): void
    {
        config(['ochords.scan.driver' => 'none']);
        $staff = User::factory()->admin()->create();

        $this->expectException(ValidationException::class);
        app(AdminSheetImportService::class)->importFromUpload(
            $staff,
            UploadedFile::fake()->image('sheet.jpg', 1000, 2000),
        );
    }

    public function test_songs_create_and_delete_permissions_are_seeded(): void
    {
        $this->assertContains(AdminPermission::SongsCreate->value, AdminPermission::values());
        $this->assertContains(AdminPermission::SongsDelete->value, AdminPermission::values());
        $this->assertDatabaseHas('permissions', [
            'name' => AdminPermission::SongsCreate->value,
            'guard_name' => 'web',
        ]);
        $this->assertDatabaseHas('permissions', [
            'name' => AdminPermission::SongsDelete->value,
            'guard_name' => 'web',
        ]);
    }

    public function test_support_cannot_delete_or_moderate_sheets(): void
    {
        $support = User::factory()->create(['is_admin' => true]);
        $support->assignRole(AdminRole::Support->value);
        $song = Song::factory()->public()->create();

        $this->assertFalse($support->can('delete', $song));
        $this->assertFalse($support->can('moderate', Song::class));
    }

    public function test_admin_can_delete_imported_sheet(): void
    {
        $admin = User::factory()->admin()->create();
        $song = Song::factory()->create(['user_id' => $admin->id]);

        $this->assertTrue($admin->can('delete', $song));
    }
}
