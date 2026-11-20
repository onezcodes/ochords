<?php

namespace Tests\Unit;

use App\Enums\SectionKind;
use App\Services\SheetParsing\BarToken;
use App\Services\SheetParsing\Box;
use App\Services\SheetParsing\ChordSheetParser;
use App\Services\SheetParsing\ScanTextLine;
use App\Services\SheetParsing\ScanWord;
use Tests\TestCase;

/** The sheet parser on hand-positioned text lines (normalised page coordinates, origin top-left). */
class ChordSheetParserTest extends TestCase
{
    /** A text line at $y: each word at its x, as wide as its letters. */
    private function line(float $y, float $h = 0.03, array $words = [], float $confidence = 0.95): ScanTextLine
    {
        return new ScanTextLine(
            array_map(fn (array $pair) => new ScanWord($pair[0], new Box($pair[1], $y, 0.02 * mb_strlen($pair[0]), $h)), $words),
            $confidence,
        );
    }

    private function chordRow(): ScanTextLine
    {
        return $this->line(0.22, 0.03, [['C', 0.10], ['G', 0.40]]);
    }

    private function lyricRow(): ScanTextLine
    {
        return $this->line(0.27, 0.03, [['Morning', 0.10], ['light', 0.26], ['shines', 0.40]]);
    }

    public function test_chord_row_pairs_with_lyric_below_and_preamble_gives_title_artist_and_strum(): void
    {
        $sheet = ChordSheetParser::parse([
            $this->line(0.05, 0.05, [['Morning', 0.30], ['Light', 0.46]]),
            $this->line(0.12, 0.03, [['The', 0.35], ['Wanderers', 0.43]]),
            $this->line(0.16, 0.03, [['↓', 0.30], ['↓', 0.34], ['↑', 0.38], ['↓', 0.42]]),
            $this->chordRow(),
            $this->lyricRow(),
        ], readsMyanmar: false);

        $song = $sheet->song;
        $this->assertSame('Morning Light', $song['title']);
        $this->assertSame('The Wanderers', $song['artist']);
        $this->assertSame('C', $song['key']);
        $this->assertCount(1, $song['sections']);
        $verse = $song['sections'][0];
        $this->assertSame(SectionKind::Verse->value, $verse['kind']);
        $this->assertSame('VERSE 1', $verse['label']);
        $this->assertSame(['[C]Morning light [G]shines'], $verse['lines']);
        $this->assertSame('↓ ↓ ↑ ↓', $verse['strum']);
        $this->assertEmpty($sheet->needsLyrics);
        $this->assertCount(1, $sheet->regions);
    }

    public function test_title_and_singer_on_one_line_are_split(): void
    {
        $song = ChordSheetParser::parse([
            $this->line(0.05, 0.05, [['Morning', 0.20], ['Light', 0.36], ['–', 0.48], ['The', 0.52], ['Wanderers', 0.60]]),
            $this->chordRow(),
            $this->lyricRow(),
        ], readsMyanmar: false)->song;

        $this->assertSame('Morning Light', $song['title']);
        $this->assertSame('The Wanderers', $song['artist']);
    }

    public function test_myanmar_line_with_leading_label_and_end_of_line_chord(): void
    {
        $sheet = ChordSheetParser::parse([
            $this->line(0.05, 0.05, [['ညီလေး', 0.30]]),
            $this->line(0.12, 0.03, [['တေးဆို', 0.30], ['-', 0.40], ['မျိုးကြီး', 0.43]]),
            $this->line(0.20, 0.04, [['V1', 0.05], ['မသေချာ', 0.12], ['Bm', 0.50]]),
        ], readsMyanmar: true);

        $song = $sheet->song;
        $this->assertSame('ညီလေး', $song['title']);
        $this->assertSame('မျိုးကြီး', $song['artist']);
        $this->assertCount(1, $song['sections']);
        $this->assertSame('VERSE 1', $song['sections'][0]['label']);
        $this->assertSame(['မသေချာ [Bm]'], $song['sections'][0]['lines']);
        $this->assertSame('Bm', $song['key']);
        $this->assertTrue($song['isMyanmar']);
    }

    public function test_chord_over_myanmar_word_snaps_to_syllable_start(): void
    {
        $song = ChordSheetParser::parse([
            $this->line(0.20, 0.03, [['G', 0.20]]),
            $this->line(0.25, 0.04, [['စက်ရုပ်', 0.10]]),
        ], readsMyanmar: true)->song;

        $this->assertSame(['စက်[G]ရုပ်'], $song['sections'][0]['lines']);
        $this->assertSame([0, 2], ChordSheetParser::myanmarSyllableStarts('စက်ရုပ်'));
    }

    public function test_glued_chords_are_split_across_the_word(): void
    {
        $this->assertSame(['Am', 'Em'], ChordSheetParser::gluedChords('AmEm'));
        $this->assertSame(['C', 'G/B'], ChordSheetParser::gluedChords('CG/B'));
        $this->assertNull(ChordSheetParser::gluedChords('CAB'));

        $song = ChordSheetParser::parse([
            $this->line(0.20, 0.03, [['AmEm', 0.10]]),
            $this->line(0.25, 0.03, [['hi', 0.10], ['world', 0.14]]),
        ], readsMyanmar: false)->song;

        $this->assertSame(['[Am]hi [Em]world'], $song['sections'][0]['lines']);
    }

    public function test_bar_lines_read_as_letters_make_an_instrumental_row(): void
    {
        $sheet = ChordSheetParser::parse([
            $this->line(0.30, 0.03, [['(ICI', 0.10], ['|', 0.20], ['Am', 0.25], ['|', 0.32], ['G', 0.36], ['|', 0.42], ['IGI)', 0.46]]),
        ], readsMyanmar: false);

        $intro = $sheet->song['sections'][0];
        $this->assertSame(SectionKind::Music->value, $intro['kind']);
        $this->assertSame('INTRO', $intro['label']);
        $this->assertSame(['C', 'Am', 'G', 'G'], $intro['bars']);
        $this->assertSame('4 bars', $intro['note']);
        $this->assertStringStartsWith('Bar line', $sheet->chordNotes['C'] ?? '');
        $this->assertInstanceOf(BarToken::class, ChordSheetParser::token('x2'));
        $this->assertInstanceOf(BarToken::class, ChordSheetParser::barToken('||'));
    }

    public function test_chord_diagram_legend_is_dropped(): void
    {
        $song = ChordSheetParser::parse([
            $this->line(0.05, 0.05, [['Song', 0.30], ['Title', 0.40]]),
            $this->line(0.12, 0.03, [['C', 0.10], ['G', 0.20], ['Am', 0.30], ['F', 0.40]]),
            $this->line(0.16, 0.03, [['x32010', 0.10], ['320003', 0.20], ['x02210', 0.30], ['133211', 0.40]]),
            $this->line(0.25, 0.03, [['C', 0.10]]),
            $this->line(0.30, 0.03, [['la', 0.10], ['la', 0.16], ['la', 0.22]]),
        ], readsMyanmar: false)->song;

        $this->assertSame('Song Title', $song['title']);
        $this->assertCount(1, $song['sections']);
        $this->assertSame(SectionKind::Verse->value, $song['sections'][0]['kind']);
        $this->assertSame(['[C]la la la'], $song['sections'][0]['lines']);
    }

    public function test_repeat_mark_on_heading_becomes_the_section_note(): void
    {
        $song = ChordSheetParser::parse([
            $this->line(0.10, 0.03, [['Chorus', 0.10], ['x2', 0.25]]),
            $this->chordRow(),
            $this->lyricRow(),
        ], readsMyanmar: false)->song;

        $chorus = $song['sections'][0];
        $this->assertSame(SectionKind::Chorus->value, $chorus['kind']);
        $this->assertSame('CHORUS', $chorus['label']);
        $this->assertSame('×2', $chorus['note']);
        $this->assertSame(['[C]Morning light [G]shines'], $chorus['lines']);
    }

    public function test_key_capo_and_tempo_are_read(): void
    {
        $song = ChordSheetParser::parse([
            $this->line(0.05, 0.05, [['Morning', 0.30], ['Light', 0.46]]),
            $this->line(0.12, 0.03, [['Key:', 0.10], ['G', 0.18], ['Capo', 0.30], ['2', 0.40], ['120', 0.50], ['bpm', 0.58]]),
            $this->chordRow(),
            $this->lyricRow(),
        ], readsMyanmar: false)->song;

        $this->assertSame('G', $song['key']);
        $this->assertSame(120, $song['tempo']);
        $this->assertSame('Capo 2', $song['myNote']);
        $this->assertSame('Morning Light', $song['title']);
    }

    public function test_reword_keeps_chords_with_their_words(): void
    {
        $this->assertSame(
            '[C]Morning li[G]ght shine',
            ChordSheetParser::reword('[C]Morning li[G]ght shines', 'Morning light shine'),
        );
        $this->assertSame(
            '[C]Morning [G][Am]shines',
            ChordSheetParser::reword('[C]Morning [G]light [Am]shines', 'Morning shines'),
        );
        $this->assertSame('Morning light', ChordSheetParser::words('[C]Morning [G]light'));
        $this->assertSame('[C]sing [G]along', ChordSheetParser::placeChords('[C]hello [G]world', 'sing along'));
    }

    public function test_credits_and_titles_are_cleaned(): void
    {
        $credit = ChordSheetParser::credit('တေးရေး-ကန် တေးဆို-မျိုးကြီး');
        $this->assertSame('မျိုးကြီး', $credit?->singer);
        $this->assertSame('ကန်', $credit?->writer);
        $this->assertSame('The Wanderers', ChordSheetParser::credit('by The Wanderers')?->singer);
        $this->assertNull(ChordSheetParser::credit('Stand by me'));
        $this->assertSame('Mary', ChordSheetParser::cleanTitle('6/8 beat Mary'));
        $this->assertSame('ညီလေး', ChordSheetParser::cleanTitle('ခေါင်းစဉ်: "ညီလေး"'));
        $this->assertSame('Mary', ChordSheetParser::cleanTitle('Title: Mary.'));
    }
}
