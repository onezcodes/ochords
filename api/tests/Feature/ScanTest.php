<?php

namespace Tests\Feature;

use App\Models\User;
use App\Services\Ocr\GoogleVisionOcr;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Client\Request;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;
use Tests\TestCase;

class ScanTest extends TestCase
{
    use RefreshDatabase;

    private const ENDPOINT = 'https://vision.googleapis.com/v1/images:annotate';

    protected function setUp(): void
    {
        parent::setUp();
        config([
            'ochords.scan.driver' => 'google',
            'services.google_vision.key' => 'test-key',
            'ochords.scan.daily_limit' => 3,
        ]);
    }

    /**
     * A Vision word: one symbol per character, the last one carrying the break Google reports.
     *
     * @param  array{0: int, 1: int, 2: int, 3: int}  $box  left, top, right, bottom in pixels
     * @return array<string, mixed>
     */
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
            // Google leaves out a coordinate that is 0.
            'boundingBox' => ['vertices' => array_map(
                fn (array $v) => array_filter(['x' => $v[0], 'y' => $v[1]]),
                [[$l, $t], [$r, $t], [$r, $b], [$l, $b]],
            )],
            'symbols' => $symbols,
        ];
    }

    /** A chord row over a Myanmar lyric line, as Vision returns a typical sheet. @return array<string, mixed> */
    private function visionResponse(): array
    {
        return ['responses' => [[
            'fullTextAnnotation' => ['pages' => [[
                'width' => 1000,
                'height' => 2000,
                'blocks' => [['paragraphs' => [
                    ['words' => [
                        $this->word('C', [100, 300, 120, 330]),
                        $this->word('G/B', [480, 300, 540, 330], 'EOL_SURE_SPACE'),
                    ]],
                    ['words' => [
                        $this->word('ငါ', [100, 340, 140, 380]),
                        $this->word('မကြားချင်', [150, 340, 300, 380]),
                        $this->word('မသိချင်', [470, 340, 600, 380], 'LINE_BREAK', 0.91),
                    ]],
                ]]],
            ]]],
        ]]];
    }

    private function photo(): UploadedFile
    {
        return UploadedFile::fake()->image('sheet.jpg', 1000, 2000);
    }

    public function test_reads_myanmar_lines_with_word_positions_in_the_apps_shape(): void
    {
        Http::fake([self::ENDPOINT => Http::response($this->visionResponse())]);
        $user = User::factory()->create();

        $this->actingAs($user)->post('/api/v1/scans', ['image' => $this->photo()], ['Accept' => 'application/json'])
            ->assertOk()
            ->assertJsonPath('engine', 'google')
            ->assertJsonCount(2, 'lines')
            ->assertJsonPath('lines.0.words.1.text', 'G/B')
            ->assertJsonPath('lines.0.words.1.x', 0.48)
            ->assertJsonPath('lines.0.words.1.y', 0.15)
            ->assertJsonPath('lines.1.words.1.text', 'မကြားချင်')
            ->assertJsonPath('lines.1.confidence', 0.91)
            ->assertJsonStructure(['engine', 'lines' => ['*' => ['confidence', 'words' => ['*' => ['text', 'x', 'y', 'width', 'height']]]]]);
    }

    public function test_sends_the_key_in_a_header_and_hints_burmese(): void
    {
        Http::fake([self::ENDPOINT => Http::response($this->visionResponse())]);

        $this->actingAs(User::factory()->create())->post('/api/v1/scans', ['image' => $this->photo()], ['Accept' => 'application/json'])->assertOk();

        Http::assertSent(fn (Request $r) => $r->url() === self::ENDPOINT
            && $r->hasHeader('X-Goog-Api-Key', 'test-key')
            && ! str_contains($r->url(), 'test-key')
            && $r['requests'][0]['imageContext']['languageHints'] === ['my', 'en']
            && $r['requests'][0]['features'][0]['type'] === 'DOCUMENT_TEXT_DETECTION');
    }

    public function test_scanned_text_is_never_logged(): void
    {
        Http::fake([self::ENDPOINT => Http::response($this->visionResponse())]);
        Log::spy();

        $this->actingAs(User::factory()->create())->post('/api/v1/scans', ['image' => $this->photo()], ['Accept' => 'application/json'])->assertOk();

        Log::shouldHaveReceived('info')->withArgs(function (string $event, array $context) {
            return $event === 'scan.read' && ! str_contains(json_encode($context, JSON_UNESCAPED_UNICODE) ?: '', 'မကြား');
        });
    }

    public function test_is_unavailable_when_turned_off_so_the_app_reads_on_the_phone(): void
    {
        config(['ochords.scan.driver' => 'none']);
        Http::fake();

        $this->actingAs(User::factory()->create())->post('/api/v1/scans', ['image' => $this->photo()], ['Accept' => 'application/json'])
            ->assertServiceUnavailable();
        Http::assertNothingSent();
    }

    public function test_a_google_error_is_a_503_not_a_crash(): void
    {
        Http::fake([self::ENDPOINT => Http::response(['responses' => [['error' => ['code' => 3, 'message' => 'Bad image data.']]]])]);

        $this->actingAs(User::factory()->create())->post('/api/v1/scans', ['image' => $this->photo()], ['Accept' => 'application/json'])
            ->assertServiceUnavailable();
    }

    public function test_daily_limit(): void
    {
        Http::fake([self::ENDPOINT => Http::response($this->visionResponse())]);
        $user = User::factory()->create();

        for ($i = 0; $i < 3; $i++) {
            $this->actingAs($user)->post('/api/v1/scans', ['image' => $this->photo()], ['Accept' => 'application/json'])->assertOk();
        }
        $this->actingAs($user)->post('/api/v1/scans', ['image' => $this->photo()], ['Accept' => 'application/json'])
            ->assertTooManyRequests()
            ->assertJsonPath('message', 'You’ve used today’s scans. They reset tomorrow.');
    }

    public function test_monthly_limit_across_all_players_falls_back_to_the_phone(): void
    {
        config(['ochords.scan.monthly_limit' => 2, 'ochords.scan.daily_limit' => 10]);
        Http::fake([self::ENDPOINT => Http::response($this->visionResponse())]);
        [$a, $b] = User::factory()->count(2)->create()->all();

        $this->actingAs($a)->post('/api/v1/scans', ['image' => $this->photo()], ['Accept' => 'application/json'])->assertOk();
        $this->actingAs($b)->post('/api/v1/scans', ['image' => $this->photo()], ['Accept' => 'application/json'])->assertOk();
        $this->actingAs($a)->post('/api/v1/scans', ['image' => $this->photo()], ['Accept' => 'application/json'])
            ->assertServiceUnavailable();
        Http::assertSentCount(2);

        // A new month starts the count again.
        $this->travelTo(now()->addMonthNoOverflow()->startOfMonth());
        $this->actingAs($b)->post('/api/v1/scans', ['image' => $this->photo()], ['Accept' => 'application/json'])->assertOk();
    }

    public function test_requires_sign_in_and_an_image(): void
    {
        $this->post('/api/v1/scans', ['image' => $this->photo()], ['Accept' => 'application/json'])->assertUnauthorized();
        $this->actingAs(User::factory()->create())
            ->post('/api/v1/scans', ['image' => UploadedFile::fake()->create('notes.pdf', 10, 'application/pdf')], ['Accept' => 'application/json'])
            ->assertUnprocessable()->assertJsonValidationErrors('image');
    }

    public function test_words_without_a_space_between_them_are_joined(): void
    {
        // How Vision really returns a slash chord and an unspaced Burmese phrase.
        $lines = GoogleVisionOcr::linesFrom(['fullTextAnnotation' => ['pages' => [[
            'width' => 1000, 'height' => 1000,
            'blocks' => [['paragraphs' => [['words' => [
                $this->word('G', [100, 100, 120, 130], null),
                $this->word('/', [120, 100, 130, 130], null),
                $this->word('B', [130, 100, 150, 130]),
                $this->word('စဉ်း', [200, 100, 260, 130], null),
                $this->word('စား', [260, 100, 300, 130], null),
                $this->word('ပါ', [300, 100, 330, 130], 'LINE_BREAK'),
            ]]]]],
        ]]]]);

        $this->assertSame(['G/B', 'စဉ်းစားပါ'], array_map(fn ($w) => $w->text, $lines[0]->words));
        $this->assertEqualsWithDelta(0.1, $lines[0]->words[0]->x, 0.0001);
        $this->assertEqualsWithDelta(0.05, $lines[0]->words[0]->width, 0.0001);
    }

    public function test_an_empty_page_reads_as_no_lines(): void
    {
        $this->assertSame([], GoogleVisionOcr::linesFrom(['textAnnotations' => []]));
    }
}
