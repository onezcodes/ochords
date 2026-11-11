<?php

namespace Tests\Feature;

use App\Models\User;
use App\Services\ZawgyiConverter;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class ZawgyiTest extends TestCase
{
    use RefreshDatabase;

    /** Zawgyi-encoded text and its Unicode, as converted by ICU's CLDR transform. */
    private const PAIRS = [
        'စဥ္းစားပါ' => 'စဉ်းစားပါ',
        'ငါ မျကားခ်င္ ခဲ့ေသာ စကား' => 'ငါ မကြားချင် ခဲ့သော စကား',
        'ေမ' => 'မေ',
        'ျမန္မာ' => 'မြန်မာ',
        'ေက်ာင္း' => 'ကျောင်း',
        'ကၽြန္ေတာ္' => 'ကျွန်တော်',
    ];

    public function test_detects_and_converts_zawgyi(): void
    {
        $zawgyi = new ZawgyiConverter;

        foreach (self::PAIRS as $zg => $unicode) {
            $this->assertTrue($zawgyi->isZawgyi($zg), "should detect $zg");
            $this->assertSame($unicode, $zawgyi->toUnicode($zg));
            $this->assertFalse($zawgyi->isZawgyi($unicode), "should not flag Unicode $unicode");
        }
    }

    public function test_a_lone_variant_form_is_not_enough_because_shan_and_karen_use_those_code_points(): void
    {
        $zawgyi = new ZawgyiConverter;

        // Kinzi (U+1064) alone: converts correctly, but isn't taken as proof of Zawgyi…
        $this->assertSame('မင်္ဂလာပါ', $zawgyi->toUnicode('မဂၤလာပါ'));
        $this->assertFalse($zawgyi->isZawgyi('မဂၤလာပါ'));
        // …while any decisive pattern alongside it is.
        $this->assertTrue($zawgyi->isZawgyi('မဂၤလာပါ ေမ'));
    }

    public function test_leaves_unicode_and_latin_alone(): void
    {
        $zawgyi = new ZawgyiConverter;

        foreach (['[C]လမ်းခွဲဖို့အတွက် မင်းဘက်က [G/B]အကြောင်းပြချက်တို့', 'ပြောခဲ့ပြီ အခုတော့ ငါ့မှာ', 'Let it shine'] as $text) {
            $this->assertFalse($zawgyi->isZawgyi($text));
            $this->assertSame('Let it shine', $zawgyi->toUnicode('Let it shine'));
        }
    }

    public function test_a_song_saved_in_zawgyi_is_stored_as_unicode(): void
    {
        $user = User::factory()->create();

        $this->actingAs($user)->putJson('/api/v1/songs/zg-1', [
            'title' => 'စဥ္းစားပါ',
            'artist' => 'ေမ',
            'key' => 'C',
            'sections' => [['kind' => 'verse', 'label' => 'VERSE 1', 'lines' => ['[C]ငါ မျကားခ်င္ [G/B]ခဲ့ေသာ စကား']]],
        ])
            ->assertOk()
            ->assertJsonPath('title', 'စဉ်းစားပါ')
            ->assertJsonPath('artist', 'မေ')
            ->assertJsonPath('sections.0.lines.0', '[C]ငါ မကြားချင် [G/B]ခဲ့သော စကား');
    }

    public function test_a_unicode_song_is_saved_unchanged(): void
    {
        $line = '[C]ငါ မကြားချင် ခဲ့သော စကား [G/B]မသိချင် ခဲ့ဆုံး';

        $this->actingAs(User::factory()->create())->putJson('/api/v1/songs/u-1', [
            'title' => 'စဉ်းစားပါ', 'artist' => 'မေ', 'key' => 'C',
            'sections' => [['kind' => 'verse', 'label' => 'VERSE 1', 'lines' => [$line]]],
        ])->assertOk()->assertJsonPath('sections.0.lines.0', $line)->assertJsonPath('title', 'စဉ်းစားပါ');
    }
}
