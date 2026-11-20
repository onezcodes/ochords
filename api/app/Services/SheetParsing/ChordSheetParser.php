<?php

namespace App\Services\SheetParsing;

use App\Enums\SectionKind;
use Illuminate\Support\Str;

/** Placeholder for a word that is on the page but couldn't be read (e.g. Myanmar script). */
const UNREADABLE_TEXT = "\u{FFFD}";

// ─── Token ───────────────────────────────────────────────────────────────────

abstract class Token {}

final class ChordToken extends Token
{
    public function __construct(
        public readonly string $name,
        public readonly ?string $note = null,
    ) {}
}

final class WordToken extends Token
{
    public function __construct(public readonly string $text) {}
}

final class BarToken extends Token {}

// ─── RowKind ─────────────────────────────────────────────────────────────────

abstract class RowKind {}

final class HeaderRowKind extends RowKind
{
    /** @param list<PlacedChord> $chords */
    public function __construct(
        public readonly SectionKind $kind,
        public readonly ?string $label,
        public readonly ?int $number,
        public readonly ?string $note,
        public readonly array $chords,
    ) {}
}

final class ChordsRowKind extends RowKind
{
    /** @param list<PlacedChord> $chords */
    public function __construct(public readonly array $chords) {}
}

final class MetaRowKind extends RowKind {}

final class LyricRowKind extends RowKind {}

final class StrumRowKind extends RowKind
{
    public function __construct(public readonly string $pattern) {}
}

// ─── PlacedChord ─────────────────────────────────────────────────────────────

final class PlacedChord
{
    public function __construct(
        public readonly string $name,
        public readonly float $x,
        public readonly ?string $note = null,
    ) {}
}

// ─── Row ─────────────────────────────────────────────────────────────────────

final class Row
{
    /** @param list<ScanWord> $words */
    public function __construct(
        public readonly array $words,
        public readonly float $confidence,
        public readonly RowKind $kind = new LyricRowKind,
    ) {}

    public function text(): string
    {
        return implode(' ', array_map(fn (ScanWord $w) => $w->text, $this->words));
    }

    public function box(): Box
    {
        if (empty($this->words)) {
            return Box::zero();
        }
        $rest = array_slice($this->words, 1);

        return array_reduce($rest, fn (Box $a, ScanWord $w) => $a->union($w->box), $this->words[0]->box);
    }

    /** @return list<ScanWord> */
    public function readWords(): array
    {
        return array_values(array_filter($this->words, fn (ScanWord $w) => $w->text !== UNREADABLE_TEXT));
    }

    public function readText(): string
    {
        return implode(' ', array_map(fn (ScanWord $w) => $w->text, $this->readWords()));
    }

    public function withWords(array $words, ?RowKind $kind = null): self
    {
        return new self($words, $this->confidence, $kind ?? $this->kind);
    }

    public function withKind(RowKind $kind): self
    {
        return new self($this->words, $this->confidence, $kind);
    }
}

// ─── InlineLabel / Credit ────────────────────────────────────────────────────

final readonly class InlineLabel
{
    public function __construct(
        public SectionKind $kind,
        public ?string $label,
        public ?int $number,
    ) {}
}

final readonly class Credit
{
    public function __construct(
        public ?string $singer,
        public ?string $writer,
    ) {}
}

// ─── Main parser ─────────────────────────────────────────────────────────────

final class ChordSheetParser
{
    private const CHORD_REGEX = '~^[A-G][#b]?(?:maj|min|dim|aug|sus|add|m|M|°|ø|\+)?\d{0,2}(?:(?:maj|sus|add|b|#|\+|-)\d{1,2})*(?:\([^)]*\))?(?:/[A-G][#b]?)?$~u';

    /** @var list<string> */
    private const NEUTRAL_TOKENS = ['x2', 'x3', 'x4', '×2', '×3', '×4', '(x2)', '(x3)', '(x4)', 'n.c.', 'nc', 'n.c', '…', '...'];

    private const BAR_MARKS = '|Il1!()[]';

    private const SUPERSCRIPT_BASS_REGEX = '/^([A-G][#b]?(?:m7|maj7|m|7|sus2|sus4)?)([A-G])$/u';

    private const HEADER_REGEX = '/^[\[(]?\s*(intro|verse|chorus|refrain|hook|pre[- ]?chorus|bridge|outro|ending|coda|music|interlude|instrumental|solo|break|tag|v|ch|cho)\.?\s*(\d+)?\s*[\])]?\s*(:)?\s*((?:x|×)\s*\d|\((?:x|×)\s*\d\))?/iu';

    private const META_REGEX = '/\b(key|capo|tempo|bpm|time\s*sig|strum)\b/iu';

    private const DIAGRAM_REGEX = '/^[xXoO0-9○●•×]+$/u';

    private const REPEAT_MARK_REGEX = '/^[x×][2-9]$/iu';

    private const INLINE_LABEL_REGEX = '/^[\[(]?(verse|v|chorus|cho|ch|c|refrain|ref|r|pre[- ]?chorus|pre|bridge|b|intro|int|outro|solo|music)\.?(\d+)?[\])]?[:.,\-–]?$/iu';

    private const META_WORD_REGEX = '/^(key|capo|tempo|bpm)\b[:=\-]?(.*)$/iu';

    private const META_VALUE_REGEX = '/^\d{2,3}\s*bpm$|^[:=\-]$/iu';

    private const KEY_REGEX = '/key\s*(?:of)?\s*[:=\-]?\s*([A-Ga-g][#b]?m?)\b/iu';

    private const CAPO_REGEX = '/capo\s*[:=\-]?\s*(\d{1,2})/iu';

    private const TEMPO_REGEX = '/(\d{2,3})\s*bpm|tempo\s*[:=\-]?\s*(\d{2,3})/iu';

    private const TITLE_SINGER_REGEX = '/^(.+?)\s+[-–—]\s+(.+)$/u';

    private const TITLE_LABEL_REGEX = '/^(?:title|song|ခေါင်းစဉ်|သီချင်းအမည်|သီချင်း)\s*[:\-–]\s*/iu';

    private const TIME_SIG_REGEX = '/^\d{1,2}\s*\/\s*\d{1,2}\s*(?:beat|time)?\s*[:\-–]?\s*/iu';

    private const WORD_LIKE_REGEX = '/^[\x{201C}\x{201D}(]?(?:[A-Za-z][a-z]*(?:[\x{2018}\x{2019}\x{27}][a-z]+)?|[A-Z]{2,})[,.!?;:\x{201C}\x{201D}")]*$|^\d+[,.]?$|^[&\-\x{2014}]$/u';

    private const COLUMNS = 48;

    /** @var list<int> */
    private const STANDALONE_SPACING_MARKS = [
        0x102B, 0x102C, 0x1038, 0x1062, 0x1063, 0x1064, 0x1067, 0x1068, 0x1069, 0x106A, 0x106B, 0x106C, 0x106D,
        0x1083, 0x1087, 0x1088, 0x1089, 0x108A, 0x108B, 0x108C, 0x108F, 0x109A, 0x109B, 0x109C,
    ];

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * @param  list<ScanTextLine>  $lines
     */
    public static function parse(array $lines, bool $readsMyanmar): ParsedSheet
    {
        $rows = self::sheetFurniture(
            self::separateInline(
                array_map(
                    fn (Row $r) => self::classify(self::withoutStrayDots($r)),
                    self::groupRows(array_map(fn (ScanTextLine $l) => self::splittingGluedChords($l), $lines)),
                )
            )
        );

        $margin = INF;
        $width = 0.0;
        foreach ($rows as $r) {
            if ($r->box()->minX < $margin) {
                $margin = $r->box()->minX;
            }
            if ($r->box()->maxX > $margin + $width) {
                $width = $r->box()->maxX;
            }
        }
        if ($margin === INF) {
            $margin = 0.0;
        }
        $width = max($width - $margin, 0.0);

        $builder = new SheetBuilder($readsMyanmar, $margin, $width);

        $bodyStart = count($rows);
        foreach ($rows as $i => $r) {
            if ($r->kind instanceof HeaderRowKind || $r->kind instanceof ChordsRowKind) {
                $bodyStart = $i;
                break;
            }
        }

        $preambleRows = 4;
        $label = null;
        for ($i = 0; $i < min(9, count($rows)); $i++) {
            if ($rows[$i]->kind instanceof HeaderRowKind) {
                $label = $i;
                break;
            }
        }

        if ($label !== null) {
            $noChordsBeforeLabel = true;
            for ($i = 0; $i < $label; $i++) {
                if ($rows[$i]->kind instanceof ChordsRowKind) {
                    $noChordsBeforeLabel = false;
                    break;
                }
            }
            if ($noChordsBeforeLabel) {
                $bodyStart = $label;
                $preambleRows = $label;
            } elseif ($bodyStart < count($rows) && $rows[$bodyStart]->kind instanceof ChordsRowKind && $label === $bodyStart + 1) {
                $preambleRows = $bodyStart;
            }
        }

        $split = 0;
        $counted = 0;
        while ($split < $bodyStart && $counted < $preambleRows) {
            if (! ($rows[$split]->kind instanceof StrumRowKind)) {
                $counted++;
            }
            $split++;
        }

        $builder->readPreamble(array_slice($rows, 0, $split));
        $builder->readBody(array_slice($rows, $split));

        return $builder->result();
    }

    // ─── Chord classification ─────────────────────────────────────────────

    public static function isChord(string $s): bool
    {
        return (bool) preg_match(self::CHORD_REGEX, $s);
    }

    public static function barToken(string $text): Token
    {
        $allBarMarks = true;
        for ($i = 0; $i < mb_strlen($text); $i++) {
            $c = mb_substr($text, $i, 1);
            if (strpos(self::BAR_MARKS, $c) === false && $c !== ':' && $c !== '/') {
                $allBarMarks = false;
                break;
            }
        }
        if ($allBarMarks) {
            return new BarToken;
        }

        $barNote = 'Bar line read as a letter — please check';
        $t = $text;
        while ($t !== '' && strpos(self::BAR_MARKS, $t[0]) !== false) {
            $t = mb_substr($t, 1);
        }
        $tok = self::token($t);
        if ($tok instanceof ChordToken) {
            return new ChordToken($tok->name, $barNote);
        }

        while (mb_strlen($t) > 1 && strpos(self::BAR_MARKS, mb_substr($t, -1)) !== false) {
            $t = mb_substr($t, 0, mb_strlen($t) - 1);
            $tok = self::token($t);
            if ($tok instanceof ChordToken) {
                return new ChordToken($tok->name, $barNote);
            }
        }

        return new WordToken($text);
    }

    public static function withoutStrayDots(Row $row): Row
    {
        $hasMy = false;
        foreach ($row->words as $w) {
            if (self::isMyanmar($w->text)) {
                $hasMy = true;
                break;
            }
        }
        if (! $hasMy) {
            return $row;
        }

        return $row->withWords(array_values(array_filter(
            $row->words,
            fn (ScanWord $w) => $w->text !== '.' && $w->text !== ',' && $w->text !== '·',
        )));
    }

    /** @return list<string>|null */
    public static function gluedChords(string $text): ?array
    {
        if (self::isChord($text) || self::isMyanmar($text) || mb_strlen($text) < 3) {
            return null;
        }
        $n = mb_strlen($text);
        $best = array_fill(0, $n + 1, null);
        $best[0] = [];
        for ($end = 1; $end <= $n; $end++) {
            for ($start = 0; $start < $end; $start++) {
                if ($best[$start] === null) {
                    continue;
                }
                $part = mb_substr($text, $start, $end - $start);
                if (self::isChord($part)) {
                    $newCount = count($best[$start]) + 1;
                    if ($best[$end] === null || count($best[$end]) > $newCount) {
                        $best[$end] = [...$best[$start], $part];
                    }
                }
            }
        }
        $parts = $best[$n];
        if ($parts === null || count($parts) < 2) {
            return null;
        }
        $hasLong = false;
        foreach ($parts as $p) {
            if (mb_strlen($p) > 1) {
                $hasLong = true;
                break;
            }
        }
        if (! $hasLong) {
            return null;
        }

        return $parts;
    }

    public static function splittingGluedChords(ScanTextLine $line): ScanTextLine
    {
        $words = [];
        foreach ($line->words as $w) {
            $parts = self::gluedChords($w->text);
            if ($parts === null) {
                $words[] = $w;

                continue;
            }
            $total = array_sum(array_map('mb_strlen', $parts));
            $at = 0.0;
            foreach ($parts as $part) {
                $len = mb_strlen($part);
                $words[] = new ScanWord(
                    $part,
                    new Box(
                        $w->box->minX + $w->box->width * $at / $total,
                        $w->box->minY,
                        $w->box->width * $len / $total,
                        $w->box->height,
                    )
                );
                $at += $len;
            }
        }

        return new ScanTextLine($words, $line->confidence);
    }

    public static function token(string $raw): Token
    {
        $lower = mb_strtolower($raw);
        if ($raw === UNREADABLE_TEXT || in_array($lower, self::NEUTRAL_TOKENS, true)) {
            return new BarToken;
        }
        if (preg_match('/^[|\/\-–—.:*%]+$/u', $raw)) {
            return new BarToken;
        }

        $t = str_replace(['♯', '♭'], ['#', 'b'], $raw);
        $t = trim($t, '|[],.:;');
        if (mb_strlen($t) > 1 && preg_match('/^[ABCDEFG]/u', $t)) {
            $t = rtrim($t, '-–—~…');
        }
        if (str_starts_with($t, '(') && str_ends_with($t, ')') && substr_count($t, '(') === 1) {
            $t = mb_substr($t, 1, mb_strlen($t) - 2);
        }
        if ($t === '') {
            return new BarToken;
        }
        if (self::isChord($t)) {
            return new ChordToken($t);
        }

        $f = $t;
        $firstChar = mb_substr($f, 0, 1);
        if (preg_match('/^[a-g]$/u', $firstChar)) {
            $f = mb_strtoupper($firstChar).mb_substr($f, 1);
        }
        $f = str_replace('rn', 'm', $f);
        $slash = strpos($f, '/');
        if ($slash !== false) {
            $bass = str_replace(['8', '0'], ['B', 'D'], substr($f, $slash + 1));
            $f = substr($f, 0, $slash + 1).$bass;
        }
        if (self::isChord($f)) {
            return new ChordToken($f, 'Unclear chord — please check it');
        }

        if (preg_match(self::SUPERSCRIPT_BASS_REGEX, $f, $m)) {
            $slashChord = $m[1].'/'.$m[2];
            if (self::isChord($slashChord)) {
                return new ChordToken($slashChord, 'Superscript bass note read as a slash chord');
            }
        }

        return new WordToken($raw);
    }

    // ─── Row grouping & classification ───────────────────────────────────────

    /** @param list<ScanTextLine> $lines
     * @return list<Row>
     */
    public static function groupRows(array $lines): array
    {
        usort($lines, fn (ScanTextLine $a, ScanTextLine $b) => $a->box()->midY <=> $b->box()->midY);

        $rows = [];
        foreach ($lines as $line) {
            if (empty($line->words)) {
                continue;
            }
            $b = $line->box();
            if (! empty($rows)) {
                $last = $rows[count($rows) - 1];
                $lb = $last->box();
                $overlap = min($lb->maxY, $b->maxY) - max($lb->minY, $b->minY);
                if ($overlap > 0.5 * min($lb->height, $b->height)) {
                    $fresh = array_values(array_filter($line->words, function (ScanWord $w) use ($last) {
                        foreach ($last->words as $o) {
                            $inter = $o->box->intersection($w->box);
                            if (! $inter->isEmpty && $inter->width * $inter->height > 0.5 * min(
                                $o->box->width * $o->box->height,
                                $w->box->width * $w->box->height,
                            )) {
                                return false;
                            }
                        }

                        return true;
                    }));
                    if (empty($fresh)) {
                        continue;
                    }
                    $unreadable = count(array_filter($fresh, fn (ScanWord $w) => $w->text !== UNREADABLE_TEXT)) === 0;
                    $merged = [...$last->words, ...$fresh];
                    usort($merged, fn (ScanWord $a, ScanWord $b) => $a->box->minX <=> $b->box->minX);
                    $conf = $unreadable ? $last->confidence : min($last->confidence, $line->confidence);
                    $rows[count($rows) - 1] = new Row($merged, $conf, $last->kind);

                    continue;
                }
            }
            $sorted = $line->words;
            usort($sorted, fn (ScanWord $a, ScanWord $b) => $a->box->minX <=> $b->box->minX);
            $rows[] = new Row($sorted, $line->confidence);
        }

        return $rows;
    }

    public static function classify(Row $row): Row
    {
        $text = $row->text();

        if (preg_match(self::HEADER_REGEX, $text, $m)) {
            $name = mb_strtolower($m[1]);
            $number = isset($m[2]) && $m[2] !== '' ? (int) $m[2] : null;
            $hasColon = isset($m[3]) && $m[3] !== '';
            $repeatNote = (isset($m[4]) && $m[4] !== '') ? '×'.preg_replace('/[^0-9]/', '', $m[4]) : null;
            $short = in_array($name, ['v', 'ch', 'cho'], true);
            $consumed = strlen($m[0]);
            $pos = 0;
            $restWords = [];
            foreach ($row->words as $w) {
                if ($pos + mb_strlen($w->text) > $consumed) {
                    $restWords[] = $w;
                }
                $pos += mb_strlen($w->text) + 1;
            }
            $restTokens = array_map(fn (ScanWord $w) => [self::token($w->text), $w], $restWords);
            $restIsChords = count(array_filter($restTokens, fn ($pair) => $pair[0] instanceof WordToken)) === 0;
            $restHasChord = count(array_filter($restTokens, fn ($pair) => $pair[0] instanceof ChordToken)) > 0;
            if ($restIsChords && (! $short || $number !== null || $hasColon || $restHasChord)) {
                [$kind, $label] = self::sectionKind($name);
                $chords = [];
                foreach ($restTokens as [$tok, $w]) {
                    if ($tok instanceof ChordToken) {
                        $chords[] = new PlacedChord($tok->name, $w->box->minX, $tok->note);
                    }
                }

                return $row->withKind(new HeaderRowKind($kind, $label, $number, $repeatNote, $chords));
            }
        }

        $tokens = array_map(fn (ScanWord $w) => [self::token($w->text), $w], $row->words);
        $chordCount = count(array_filter($tokens, fn ($p) => $p[0] instanceof ChordToken));
        if ($chordCount >= 2) {
            $tokens = array_map(function ($pair) {
                [$t, $w] = $pair;

                return $t instanceof WordToken ? [self::barToken($w->text), $w] : $pair;
            }, $tokens);
        }
        $counted = array_filter($tokens, fn ($p) => ! ($p[0] instanceof BarToken));
        $chords = [];
        foreach ($tokens as [$tok, $w]) {
            if ($tok instanceof ChordToken) {
                $chords[] = new PlacedChord($tok->name, $w->box->minX, $tok->note);
            }
        }

        $chordNum = count($chords);
        $countedNum = count($counted);
        if ($chordNum > 0 && $chordNum >= 0.75 * $countedNum) {
            return $row->withKind(new ChordsRowKind($chords));
        }
        if (self::graphemeCount($text) < 48 && preg_match(self::META_REGEX, $text)) {
            return $row->withKind(new MetaRowKind);
        }

        return $row->withKind(new LyricRowKind);
    }

    // ─── Sheet furniture ──────────────────────────────────────────────────────

    public static function isDiagramWord(string $text): bool
    {
        return (bool) preg_match(self::DIAGRAM_REGEX, $text) && ! preg_match(self::REPEAT_MARK_REGEX, $text);
    }

    /** @var list<string> */
    private const ARROWS = ['↓', '↑', '⇩', '⇧', '⬇', '⬆', '🡓', '🡑'];

    public static function isArrowWord(string $text): bool
    {
        if ($text === '') {
            return false;
        }
        preg_match_all('/./su', $text, $m);
        foreach ($m[0] as $char) {
            if (! in_array($char, self::ARROWS, true)) {
                return false;
            }
        }

        return true;
    }

    /** @param list<Row> $rows
     * @return list<Row>
     */
    public static function sheetFurniture(array $rows): array
    {
        $isDiagram = function (Row $row): bool {
            $words = array_map(fn (ScanWord $w) => $w->text, $row->readWords());

            return ! empty($words)
                && count(array_filter($words, fn ($w) => ! self::isDiagramWord($w))) === 0
                && (count($words) >= 3 || count(array_filter($words, fn ($w) => mb_strlen($w) >= 3)) > 0);
        };

        $near = fn (Row $a, Row $b): bool => abs($a->box()->midY - $b->box()->midY) < max($a->box()->height, $b->box()->height) * 2.5;

        $isLegend = function (int $i) use ($rows): bool {
            if ($i >= 4) {
                return false;
            }
            $k = $rows[$i]->kind;
            if (! ($k instanceof ChordsRowKind)) {
                return false;
            }
            $chords = $k->chords;
            if ($i + 1 >= count($rows) || ! ($rows[$i + 1]->kind instanceof LyricRowKind)) {
                return false;
            }
            if ($rows[$i]->box()->maxY > $rows[$i + 1]->box()->minY + $rows[$i + 1]->box()->height * 0.3) {
                return false;
            }
            $end = $rows[$i + 1]->box()->maxX;

            return count(array_filter($chords, fn (PlacedChord $c) => $c->x <= $end)) === 0;
        };

        $out = [];
        foreach ($rows as $i => $row) {
            if ($isDiagram($row) || $isLegend($i)) {
                continue;
            }

            if ($row->kind instanceof ChordsRowKind) {
                $prev = $i > 0 && $isDiagram($rows[$i - 1]) && $near($rows[$i - 1], $row);
                $next = $i + 1 < count($rows) && $isDiagram($rows[$i + 1]) && $near($row, $rows[$i + 1]);
                if ($prev || $next) {
                    continue;
                }
            }

            $arrowWords = array_values(array_filter($row->words, fn (ScanWord $w) => self::isArrowWord($w->text)));
            if (empty($arrowWords) || ! (count($arrowWords) === count($row->words) || count($arrowWords) >= 3)) {
                $out[] = $row;

                continue;
            }

            $rest = array_values(array_filter($row->words, fn (ScanWord $w) => ! self::isArrowWord($w->text)));
            if (! empty($rest)) {
                $out[] = self::classify($row->withWords($rest));
            }

            $pattern = implode(' ', array_map(fn (ScanWord $w) => $w->text, $arrowWords));
            $last = ! empty($out) ? $out[count($out) - 1] : null;
            $before = ($last !== null && $last->kind instanceof StrumRowKind) ? $last->kind->pattern : null;
            if ($last !== null && $before !== null && $near($last, $row)) {
                $out[count($out) - 1] = $last->withWords(
                    [...$last->words, ...$arrowWords],
                    new StrumRowKind("$before $pattern"),
                );
            } else {
                $out[] = new Row($arrowWords, $row->confidence, new StrumRowKind($pattern));
            }
        }

        return $out;
    }

    // ─── Inline separation ────────────────────────────────────────────────────

    /** @param list<Row> $rows
     * @return list<Row>
     */
    public static function separateInline(array $rows): array
    {
        $out = [];
        foreach ($rows as $row) {
            $h = $row->kind instanceof HeaderRowKind ? $row->kind : null;
            if ($h !== null && $h->kind !== SectionKind::Music && ! empty($h->chords)) {
                $chordXs = array_map(fn (PlacedChord $c) => $c->x, $h->chords);
                $chordWords = array_values(array_filter($row->words, fn (ScanWord $w) => in_array($w->box->minX, $chordXs, false)));
                $otherWords = array_values(array_filter($row->words, fn (ScanWord $w) => ! in_array($w->box, array_map(fn (ScanWord $cw) => $cw->box, $chordWords), false)));
                $out[] = $row->withWords($otherWords, new HeaderRowKind($h->kind, $h->label, $h->number, $h->note, []));
                $out[] = $row->withWords($chordWords, new ChordsRowKind($h->chords));

                continue;
            }

            if (! ($row->kind instanceof LyricRowKind) || ! self::isMyanmar($row->text())) {
                $out[] = $row;

                continue;
            }

            $words = $row->words;
            $headerRow = null;
            $first = ! empty($words) ? $words[0] : null;
            if ($first !== null && ! self::isMyanmar($first->text) && count($words) > 1) {
                $lbl = self::inlineLabel($first->text);
                if ($lbl !== null) {
                    $headerRow = new Row([$first], $row->confidence, new HeaderRowKind($lbl->kind, $lbl->label, $lbl->number, null, []));
                    $words = array_slice($words, 1);
                }
            }

            $chordWords = array_values(array_filter($words, fn (ScanWord $w) => ! self::isMyanmar($w->text) && self::token($w->text) instanceof ChordToken));
            $lyricWords = array_values(array_filter($words, function (ScanWord $w) use ($chordWords) {
                foreach ($chordWords as $cw) {
                    if ($cw->box === $w->box && $cw->text === $w->text) {
                        return false;
                    }
                }

                return true;
            }));

            if ($headerRow === null && empty($chordWords)) {
                $out[] = $row;

                continue;
            }
            $hasMyanmarLyric = count(array_filter($lyricWords, fn (ScanWord $w) => self::isMyanmar($w->text))) > 0;
            if (! $hasMyanmarLyric) {
                $out[] = $row;

                continue;
            }

            $lyricRow = $row->withWords($lyricWords, new LyricRowKind);

            $aboveIndex = null;
            if (! empty($out)) {
                $last = $out[count($out) - 1];
                if ($last->kind instanceof ChordsRowKind) {
                    $gap = $lyricRow->box()->minY - $last->box()->maxY;
                    if ($gap < max($last->box()->height, $lyricRow->box()->height) * 1.6) {
                        $aboveIndex = count($out) - 1;
                    }
                }
            }

            if ($headerRow !== null) {
                if ($aboveIndex !== null) {
                    array_splice($out, $aboveIndex, 0, [$headerRow]);
                    $aboveIndex++;
                } else {
                    $out[] = $headerRow;
                }
            }

            if (! empty($chordWords)) {
                $placed = [];
                foreach ($chordWords as $w) {
                    $tok = self::token($w->text);
                    if ($tok instanceof ChordToken) {
                        $placed[] = new PlacedChord($tok->name, $w->box->minX, $tok->note);
                    }
                }
                $insertIdx = $aboveIndex !== null ? ($headerRow === null ? $aboveIndex : $aboveIndex) : null;
                if ($insertIdx !== null) {
                    $existing = $out[$insertIdx]->kind instanceof ChordsRowKind ? $out[$insertIdx]->kind->chords : null;
                    if ($existing !== null) {
                        $merged = [...$existing, ...$placed];
                        usort($merged, fn (PlacedChord $a, PlacedChord $b) => $a->x <=> $b->x);
                        $out[$insertIdx] = $out[$insertIdx]->withWords(
                            [...$out[$insertIdx]->words, ...$chordWords],
                            new ChordsRowKind($merged),
                        );
                    } else {
                        array_splice($out, $insertIdx, 0, [new Row($chordWords, $row->confidence, new ChordsRowKind($placed))]);
                    }
                } else {
                    $out[] = new Row($chordWords, $row->confidence, new ChordsRowKind($placed));
                }
            }

            $out[] = $lyricRow;
        }

        return $out;
    }

    // ─── Title / credits ──────────────────────────────────────────────────────

    private static array $singerWords = [
        'တေးဆိုရှင်', 'တေးဆို', 'ဆိုသူ', 'တေးသံရှင်', 'သီဆို', 'အဆို',
        'sung by', 'singer', 'vocals', 'vocal', 'artist', 'by',
    ];

    private static array $writerWords = [
        'တေးရေးဆရာ', 'တေးရေး', 'ရေးစပ်', 'ရေးသူ', 'သံစဉ်', 'စာသား', 'ဂီတ', 'တီးလုံး',
        'written by', 'words by', 'lyrics', 'lyric', 'music', 'composed by', 'composer',
        'arranged by', 'arr.', 'chords by', 'original', 'cover',
    ];

    public static function credit(string $text): ?Credit
    {
        $lower = mb_strtolower($text);
        $source = mb_strlen($lower) === mb_strlen($text) ? $text : $lower;

        $hits = [];
        foreach ([[self::$singerWords, true], [self::$writerWords, false]] as [$wordList, $singer]) {
            $sorted = $wordList;
            usort($sorted, fn ($a, $b) => mb_strlen($b) - mb_strlen($a));
            foreach ($sorted as $w) {
                $from = 0;
                while (true) {
                    $at = mb_strpos($lower, $w, $from);
                    if ($at === false) {
                        break;
                    }
                    $wLen = mb_strlen($w);
                    $rStart = $at;
                    $rEnd = $at + $wLen - 1;
                    $isLatin = ord($w[0]) < 128;
                    $before = $at > 0 ? mb_substr($lower, $at - 1, 1) : null;
                    $afterChar = ($at + $wLen < mb_strlen($lower)) ? mb_substr($lower, $at + $wLen, 1) : null;
                    if ($isLatin) {
                        $whole = ($before === null || ! preg_match('/[a-z]/u', $before))
                            && ($afterChar === null || ! preg_match('/[a-z]/u', $afterChar));
                    } else {
                        $whole = $afterChar === null || (bool) preg_match('/[ \-–—:=၊။\/|,()\s]/u', $afterChar);
                    }
                    $overlaps = false;
                    foreach ($hits as $h) {
                        if ($h['start'] <= $rEnd && $rStart <= $h['end']) {
                            $overlaps = true;
                            break;
                        }
                    }
                    if ($whole && ! $overlaps) {
                        $hits[] = ['start' => $rStart, 'end' => $rEnd, 'singer' => $singer, 'wLen' => $wLen];
                    }
                    $from = $at + 1;
                }
            }
        }

        if (empty($hits)) {
            return null;
        }

        usort($hits, fn ($a, $b) => $a['start'] - $b['start']);
        $leadingLatin = mb_strlen(trim(mb_substr($lower, 0, $hits[0]['start']), " \t")) === 0;
        $allLatin = count(array_filter($hits, fn ($h) => ord(mb_substr($lower, $h['start'], 1)) >= 128)) === 0;
        if ($allLatin && ! $leadingLatin) {
            return null;
        }

        $singer = null;
        $writer = null;
        $hits = array_values($hits);
        foreach ($hits as $idx => $hit) {
            $end = isset($hits[$idx + 1]) ? $hits[$idx + 1]['start'] : mb_strlen($source);
            $name = mb_substr($source, $hit['start'] + $hit['wLen'], $end - $hit['start'] - $hit['wLen']);
            $name = preg_replace('/^[ \-–—:=၊။\/|,()\[\]\s]+|[ \-–—:=၊။\/|,()\[\]\s]+$/u', '', $name) ?? $name;
            if ($name === '') {
                continue;
            }
            if ($hit['singer']) {
                if ($singer === null) {
                    $singer = $name;
                }
            } else {
                if ($writer === null) {
                    $writer = $name;
                }
            }
        }

        return new Credit($singer, $writer);
    }

    public static function cleanTitle(string $text): string
    {
        $t = trim($text, " \t");
        if (preg_match(self::TITLE_LABEL_REGEX, $t, $m)) {
            $t = mb_substr($t, mb_strlen($m[0]));
        }
        if (preg_match(self::TIME_SIG_REGEX, $t, $m) && mb_strlen($m[0]) < mb_strlen($t)) {
            $t = mb_substr($t, mb_strlen($m[0]));
        }
        $t = preg_replace('/^[\x{201C}\x{201D}\x{201E}\x{2018}\x{2019}\x{201A}\x{300C}\x{300D}\x{300E}\x{300F}\x{AB}\x{BB}"\'*\s]+|[\x{201C}\x{201D}\x{201E}\x{2018}\x{2019}\x{201A}\x{300C}\x{300D}\x{300E}\x{300F}\x{AB}\x{BB}"\'*\s]+$/u', '', $t) ?? $t;
        $t = preg_replace('/^[.\x{104B}]+|[.\x{104B}]+$/u', '', $t) ?? $t;

        return $t;
    }

    public static function withoutMeta(Row $row): Row
    {
        $kept = [];
        $skipValue = false;
        foreach ($row->words as $w) {
            if ($skipValue) {
                $skipValue = false;

                continue;
            }
            if (preg_match(self::META_WORD_REGEX, $w->text, $m)) {
                if ($m[2] === '') {
                    $skipValue = true;
                }

                continue;
            }
            if (preg_match(self::META_VALUE_REGEX, $w->text)) {
                continue;
            }
            $kept[] = $w;
        }

        return $row->withWords($kept, new LyricRowKind);
    }

    // ─── Myanmar helpers ──────────────────────────────────────────────────────

    public static function isMyanmar(string $s): bool
    {
        return (bool) preg_match('/[\x{1000}-\x{109F}]/u', $s);
    }

    public static function isWordLike(string $s): bool
    {
        return (bool) preg_match(self::WORD_LIKE_REGEX, $s);
    }

    public static function isReadable(Row $row, bool $readsMyanmar): bool
    {
        $readWords = $row->readWords();
        if (count($readWords) * 2 <= count($row->words)) {
            return false;
        }
        $chars = preg_replace('/\s/', '', $row->readText());
        if ($chars === '' || $chars === null) {
            return false;
        }
        if (self::isMyanmar($row->text())) {
            return $readsMyanmar;
        }
        if ($row->confidence < 0.45) {
            return false;
        }
        $words = array_map(fn (ScanWord $w) => $w->text, $readWords);
        $wordLike = count(array_filter($words, fn ($w) => self::isWordLike($w)));
        $vowels = preg_match_all('/[aeiouyAEIOUY]/u', $chars);
        $charLen = mb_strlen($chars);

        return $wordLike >= 0.75 * count($words) && $vowels * 6 >= $charLen;
    }

    /** @return list<string> */
    public static function graphemes(string $text): array
    {
        if ($text === '') {
            return [];
        }
        preg_match_all('/./su', $text, $m);
        $chars = $m[0];
        $out = [];
        $current = '';
        foreach ($chars as $char) {
            $cp = mb_ord($char, 'UTF-8');
            if ($current !== '' && self::extendsCluster($cp)) {
                $current .= $char;
            } else {
                if ($current !== '') {
                    $out[] = $current;
                }
                $current = $char;
            }
        }
        if ($current !== '') {
            $out[] = $current;
        }

        return $out;
    }

    public static function graphemeCount(string $text): int
    {
        return count(self::graphemes($text));
    }

    private static function extendsCluster(int $cp): bool
    {
        if ($cp === 0x200C || $cp === 0x200D) {
            return true;
        }
        $category = \IntlChar::charType($cp);

        return match ($category) {
            \IntlChar::CHAR_CATEGORY_NON_SPACING_MARK,
            \IntlChar::CHAR_CATEGORY_ENCLOSING_MARK => true,
            \IntlChar::CHAR_CATEGORY_COMBINING_SPACING_MARK => ! in_array($cp, self::STANDALONE_SPACING_MARKS, true),
            default => false,
        };
    }

    /** @return list<int> */
    public static function myanmarSyllableStarts(string $text): array
    {
        return self::myanmarSyllableStartsFromGraphemes(self::graphemes($text));
    }

    /** @param list<string> $chars
     * @return list<int>
     */
    private static function myanmarSyllableStartsFromGraphemes(array $chars): array
    {
        $starts = [];
        foreach ($chars as $i => $grapheme) {
            preg_match_all('/./su', $grapheme, $gm);
            $gChars = $gm[0];
            $first = mb_ord($gChars[0], 'UTF-8');
            $inRange = ($first >= 0x1000 && $first <= 0x102A) || ($first >= 0x1040 && $first <= 0x1049) || $first === 0x103F;
            if (! $inRange) {
                continue;
            }
            $hasKilled = count(array_filter($gChars, fn ($c) => mb_ord($c, 'UTF-8') === 0x103A)) > 0;
            if ($hasKilled) {
                continue;
            }
            if ($i > 0) {
                preg_match_all('/./su', $chars[$i - 1], $pm);
                $prevChars = $pm[0];
                $lastCp = mb_ord($prevChars[count($prevChars) - 1], 'UTF-8');
                if ($lastCp === 0x1039) {
                    continue;
                }
            }
            $starts[] = $i;
        }

        return $starts;
    }

    // ─── Chord placement ─────────────────────────────────────────────────────

    /**
     * @param  list<PlacedChord>  $chords
     */
    public static function merge(array $chords, Row $lyric): string
    {
        $words = $lyric->readWords();
        $chars = [];
        $starts = [];
        foreach ($words as $k => $w) {
            if ($k > 0) {
                $chars[] = ' ';
            }
            $starts[$k] = count($chars);
            foreach (self::graphemes($w->text) as $g) {
                $chars[] = $g;
            }
        }

        $inserts = [];
        foreach ($chords as $n => $c) {
            $k = -1;
            foreach ($words as $ki => $w) {
                if ($c->x < $w->box->maxX) {
                    $k = $ki;
                    break;
                }
            }
            if ($k >= 0) {
                $w = $words[$k];
                $frac = $w->box->width > 0.0001 ? ($c->x - $w->box->minX) / $w->box->width : 0.0;
                $count = self::graphemeCount($w->text);
                $within = $frac <= 0.34 ? 0 : min($count - 1, (int) round($frac * $count));
                if ($within > 0 && self::isMyanmar($w->text)) {
                    $syllables = self::myanmarSyllableStarts($w->text);
                    if (! empty($syllables)) {
                        $best = $syllables[0];
                        $bestDiff = abs($syllables[0] - $within);
                        foreach ($syllables as $s) {
                            if (abs($s - $within) < $bestDiff) {
                                $best = $s;
                                $bestDiff = abs($s - $within);
                            }
                        }
                        $within = $best;
                    }
                }
                $inserts[] = [$starts[$k] + $within, $n, "[{$c->name}]"];
            } else {
                $inserts[] = [count($chars), $n, " [{$c->name}]"];
            }
        }

        usort($inserts, function ($a, $b) {
            if ($a[0] !== $b[0]) {
                return $b[0] - $a[0];
            }

            return $b[1] - $a[1];
        });

        foreach ($inserts as [$offset, , $tag]) {
            array_splice($chars, $offset, 0, [$tag]);
        }

        return trim(implode('', $chars), " \t");
    }

    /**
     * @param  list<PlacedChord>  $chords
     */
    public static function chordsOnly(array $chords, float $margin, float $width, ?float $lyricEnd = null): string
    {
        $column = fn (float $x): int => (int) ((($x - $margin) / max($width, 0.0001)) * self::COLUMNS);
        $out = '';
        $col = 0;
        $sorted = $chords;
        usort($sorted, fn (PlacedChord $a, PlacedChord $b) => $a->x <=> $b->x);
        foreach ($sorted as $c) {
            $target = max($col, $column($c->x));
            $out .= str_repeat(' ', $target - $col)."[{$c->name}]";
            $col = $target;
        }
        if ($lyricEnd !== null && $column($lyricEnd) > $col) {
            $out .= str_repeat(' ', $column($lyricEnd) - $col);
        }

        return $out;
    }

    public static function words(string $line): string
    {
        $segs = ChordLine::parse($line);
        $result = '';
        foreach ($segs as $seg) {
            $result .= $seg['text'].($seg['trailingSpace'] ? ' ' : '');
        }

        return trim($result, " \t");
    }

    // ─── reword / placeChords ─────────────────────────────────────────────────

    public static function reword(string $line, string $newWords): string
    {
        [$chords, $plain] = self::chordColumns($line);
        $new = self::graphemes(trim($newWords, " \t"));
        if (empty($chords)) {
            return implode('', $new);
        }
        if (empty($new)) {
            return implode(' ', array_map(fn ($c) => "[{$c['name']}]", $chords));
        }

        $old = self::wordsOf($plain);
        $now = self::wordsOf($new);
        $anchors = self::commonWords(
            array_map(fn ($w) => $w['word'], $old),
            array_map(fn ($w) => $w['word'], $now),
        );
        $oldEnd = 0;
        for ($i = count($plain) - 1; $i >= 0; $i--) {
            if ($plain[$i] !== ' ') {
                $oldEnd = $i + 1;
                break;
            }
        }

        $placed = [];
        foreach ($chords as $c) {
            $at = count($new);
            if ($c['col'] < $oldEnd && ! empty($now)) {
                $w = 0;
                for ($wi = count($old) - 1; $wi >= 0; $wi--) {
                    if ($old[$wi]['start'] <= $c['col']) {
                        $w = $wi;
                        break;
                    }
                }
                $inWord = $c['col'] - (empty($old) ? 0 : $old[$w]['start']);
                $prev = null;
                foreach (array_reverse($anchors) as $a) {
                    if ($a[0] <= $w) {
                        $prev = $a;
                        break;
                    }
                }
                $next = null;
                foreach ($anchors as $a) {
                    if ($a[0] > $w) {
                        $next = $a;
                        break;
                    }
                }
                if ($prev !== null && $prev[0] === $w) {
                    $word = $now[$prev[1]];
                    $at = $word['start'] + min($inWord, max(0, self::graphemeCount($word['word']) - 1));
                } else {
                    $lo = $prev !== null ? [$prev[0], $prev[1]] : [-1, -1];
                    $hi = $next !== null ? [$next[0], $next[1]] : [count($old), count($now)];
                    $oldGap = $hi[0] - $lo[0] - 1;
                    $newGap = $hi[1] - $lo[1] - 1;
                    if ($newGap <= 0) {
                        $at = $next !== null ? $now[$next[1]]['start'] : count($new);
                    } else {
                        $position = ($w - $lo[0] - 1) / max(1, $oldGap - 1);
                        $target = $lo[1] + 1 + (int) round($position * ($newGap - 1));
                        $target = min(max(0, $target), count($now) - 1);
                        $word = $now[$target];
                        $at = $word['start'] + ($oldGap === $newGap ? min($inWord, max(0, self::graphemeCount($word['word']) - 1)) : 0);
                    }
                }
            }
            if (! empty($placed) && $at < $placed[count($placed) - 1]['at']) {
                $at = $placed[count($placed) - 1]['at'];
            }
            $placed[] = ['at' => $at, 'name' => $c['name']];
        }

        $out = $new;
        foreach (array_reverse($placed) as $p) {
            $at = min($p['at'], count($out));
            array_splice($out, $at, 0, [$at >= count($new) ? " [{$p['name']}]" : "[{$p['name']}]"]);
        }

        return implode('', $out);
    }

    public static function placeChords(string $line, string $lyric): string
    {
        [$chords, $plain] = self::chordColumns($line);
        $col = count($plain);
        $text = self::graphemes(trim($lyric, " \t"));
        if (empty($text)) {
            return $line;
        }
        $wordStarts = [];
        foreach ($text as $i => $c) {
            if ($i === 0 || ($text[$i - 1] === ' ' && $c !== ' ')) {
                $wordStarts[] = $i;
            }
        }
        $last = ! empty($chords) ? $chords[count($chords) - 1]['col'] : 0;
        $extent = count($chords) === 0 ? 1.0 : (
            str_ends_with($line, ' ') ? (float) $col :
            (count($chords) > 1 ? $last + $last / max(1, count($chords) - 1) : $last * 2)
        );
        $offsets = [];
        foreach ($chords as $c) {
            $target = (int) (($extent > 0 ? $c['col'] / $extent : 0.0) * count($text));
            $best = $wordStarts[0] ?? 0;
            $bestDiff = abs($best - $target);
            foreach ($wordStarts as $ws) {
                if (abs($ws - $target) < $bestDiff) {
                    $best = $ws;
                    $bestDiff = abs($ws - $target);
                }
            }
            if (! empty($offsets) && $best <= $offsets[count($offsets) - 1]) {
                $best = min(count($text), max($offsets[count($offsets) - 1] + 1, $target));
            }
            $offsets[] = $best;
        }

        $out = $text;
        foreach (array_reverse(array_keys($chords)) as $k) {
            $at = min($offsets[$k], count($out));
            $tag = $at === count($text) ? " [{$chords[$k]['name']}]" : "[{$chords[$k]['name']}]";
            array_splice($out, $at, 0, [$tag]);
        }

        return implode('', $out);
    }

    // ─── Key guessing ─────────────────────────────────────────────────────────

    /** @param list<string> $chords */
    public static function guessKey(array $chords): string
    {
        $parts = [];
        foreach ($chords as $c) {
            $p = self::parseChordParts($c);
            if ($p !== null) {
                $parts[] = $p;
            }
        }
        if (empty($parts)) {
            return 'C';
        }
        $first = $parts[0];
        $last = $parts[count($parts) - 1];
        $degrees = [[0, false], [2, true], [4, true], [5, false], [7, false], [9, true]];
        $bestTonic = 0;
        $bestScore = -1;
        for ($tonic = 0; $tonic < 12; $tonic++) {
            $score = 0;
            foreach ($parts as $p) {
                foreach ($degrees as [$d, $minor]) {
                    if (($d + $tonic) % 12 === $p[0] && $minor === $p[1]) {
                        $score += 2;
                        break;
                    }
                }
            }
            if ($first[0] === $tonic && ! $first[1]) {
                $score += 3;
            }
            if ($last[0] === $tonic && ! $last[1]) {
                $score += 3;
            }
            if ($score > $bestScore) {
                $bestTonic = $tonic;
                $bestScore = $score;
            }
        }
        $relMinor = ($bestTonic + 9) % 12;
        if ($first[1] && $first[0] === $relMinor && $last[0] === $relMinor) {
            return ChordTheory::NAMES[$relMinor].'m';
        }

        return ChordTheory::NAMES[$bestTonic];
    }

    /** @return array{int,bool}|null [root, isMinor] */
    private static function parseChordParts(string $c): ?array
    {
        $first = mb_substr($c, 0, 1);
        if (! preg_match('/^[A-G]$/', $first)) {
            return null;
        }
        $root = $first;
        $rest = mb_substr($c, 1);
        if ($rest !== '' && ($rest[0] === '#' || $rest[0] === 'b')) {
            $root .= $rest[0];
            $rest = mb_substr($rest, 1);
        }
        $i = ChordTheory::INDEX[$root] ?? null;
        if ($i === null) {
            return null;
        }
        $quality = explode('/', $rest)[0];
        $isMinor = str_starts_with($quality, 'm') && ! str_starts_with($quality, 'maj');

        return [$i, $isMinor];
    }

    // ─── Inline label ─────────────────────────────────────────────────────────

    public static function inlineLabel(string $text): ?InlineLabel
    {
        if (! preg_match(self::INLINE_LABEL_REGEX, $text, $m)) {
            return null;
        }
        $name = mb_strtolower($m[1]);
        $number = isset($m[2]) && $m[2] !== '' ? (int) $m[2] : null;
        if (in_array($name, ['v', 'c', 'r', 'b'], true) && $number === null) {
            return null;
        }
        $canonical = ['c' => 'chorus', 'r' => 'refrain', 'ref' => 'refrain', 'b' => 'bridge', 'int' => 'intro', 'pre' => 'pre-chorus'][$name] ?? $name;
        [$kind, $label] = self::sectionKind($canonical);

        return new InlineLabel($kind, $label, $number);
    }

    // ─── Section kind mapping ─────────────────────────────────────────────────

    /** @return array{SectionKind, ?string} */
    private static function sectionKind(string $name): array
    {
        return match ($name) {
            'intro' => [SectionKind::Music, 'INTRO'],
            'verse', 'v' => [SectionKind::Verse, null],
            'chorus', 'ch', 'cho', 'refrain', 'hook' => [SectionKind::Chorus, 'CHORUS'],
            'bridge' => [SectionKind::Bridge, 'BRIDGE'],
            'outro', 'ending', 'coda' => [SectionKind::Music, 'OUTRO'],
            'tag' => [SectionKind::Music, 'TAG'],
            default => str_starts_with($name, 'pre')
                ? [SectionKind::Bridge, 'PRE-CHORUS']
                : [SectionKind::Music, $name === 'solo' ? 'SOLO' : 'MUSIC'],
        };
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * @return array{list<array{col:int,name:string}>, list<string>}
     */
    private static function chordColumns(string $line): array
    {
        $chords = [];
        $plain = [];
        $i = 0;
        $len = strlen($line);
        while ($i < $len) {
            $close = $line[$i] === '[' ? strpos($line, ']', $i) : false;
            if ($line[$i] === '[' && $close !== false) {
                $chords[] = ['col' => count($plain), 'name' => substr($line, $i + 1, (int) $close - $i - 1)];
                $i = (int) $close + 1;
            } else {
                $cp = mb_ord(mb_substr($line, $i, 1));
                $charWidth = strlen(mb_substr($line, $i, 1));
                $piece = mb_substr($line, $i, 1);
                if (! empty($plain) && self::extendsCluster($cp)) {
                    $plain[count($plain) - 1] .= $piece;
                } else {
                    $plain[] = $piece;
                }
                $i += $charWidth;
            }
        }

        return [$chords, $plain];
    }

    /**
     * @param  list<string>  $text
     * @return list<array{start:int,word:string}>
     */
    private static function wordsOf(array $text): array
    {
        $result = [];
        $n = count($text);
        for ($i = 0; $i < $n; $i++) {
            if ($text[$i] === ' ') {
                continue;
            }
            if ($i > 0 && $text[$i - 1] !== ' ') {
                continue;
            }
            $end = $n;
            for ($j = $i; $j < $n; $j++) {
                if ($text[$j] === ' ') {
                    $end = $j;
                    break;
                }
            }
            $result[] = ['start' => $i, 'word' => implode('', array_slice($text, $i, $end - $i))];
        }

        return $result;
    }

    /**
     * @param  list<string>  $a
     * @param  list<string>  $b
     * @return list<array{int,int}>
     */
    private static function commonWords(array $a, array $b): array
    {
        if (empty($a) || empty($b)) {
            return [];
        }
        $n = count($a);
        $m = count($b);
        $len = array_fill(0, $n + 1, array_fill(0, $m + 1, 0));
        for ($i = $n - 1; $i >= 0; $i--) {
            for ($j = $m - 1; $j >= 0; $j--) {
                $len[$i][$j] = $a[$i] === $b[$j]
                    ? $len[$i + 1][$j + 1] + 1
                    : max($len[$i + 1][$j], $len[$i][$j + 1]);
            }
        }
        $pairs = [];
        $i = 0;
        $j = 0;
        while ($i < $n && $j < $m) {
            if ($a[$i] === $b[$j]) {
                $pairs[] = [$i, $j];
                $i++;
                $j++;
            } elseif ($len[$i + 1][$j] >= $len[$i][$j + 1]) {
                $i++;
            } else {
                $j++;
            }
        }

        return $pairs;
    }
}

// ─── Builder (internal state machine) ────────────────────────────────────────

final class SheetBuilder
{
    private string $title = '';

    private string $artist = '';

    private ?string $key = null;

    private ?int $tempo = null;

    private ?int $capo = null;

    /** @var list<array<string,mixed>> */
    private array $sections = [];

    /** @var list<Box|null> */
    private array $sectionBoxes = [];

    /** @var list<array{sectionId:string,line:int}> */
    private array $needs = [];

    private int $verseCount = 0;

    /** @var list<string> */
    private array $lowConfidence = [];

    /** @var array<string,string> */
    private array $notes = [];

    private int $readableLyrics = 0;

    private bool $sawMyanmar = false;

    private ?string $songStrum = null;

    private ?string $pendingStrum = null;

    public function __construct(
        private readonly bool $readsMyanmar,
        private readonly float $margin,
        private readonly float $width,
    ) {}

    public function readMeta(Row $row): void
    {
        $text = $row->text();
        if ($this->key === null && preg_match('/key\s*(?:of)?\s*[:=\-]?\s*([A-Ga-g][#b]?m?)\b/iu', $text, $m)) {
            $k = $m[1];
            $this->key = mb_strtoupper(mb_substr($k, 0, 1)).mb_substr($k, 1);
        }
        if ($this->capo === null && preg_match('/capo\s*[:=\-]?\s*(\d{1,2})/iu', $text, $m)) {
            $this->capo = (int) $m[1];
        }
        if ($this->tempo === null && preg_match('/(\d{2,3})\s*bpm|tempo\s*[:=\-]?\s*(\d{2,3})/iu', $text, $m)) {
            $this->tempo = (int) ($m[1] !== '' ? $m[1] : $m[2]);
        }
    }

    /** @param list<Row> $rows */
    public function readPreamble(array $rows): void
    {
        $candidates = [];
        $writer = null;
        foreach ($rows as $row) {
            if ($row->kind instanceof StrumRowKind) {
                $this->songStrum = $row->kind->pattern;

                continue;
            }
            if ($row->kind instanceof MetaRowKind) {
                $this->readMeta($row);
                $rest = ChordSheetParser::withoutMeta($row);
                if (! empty($rest->words)) {
                    $candidates[] = $rest;
                }

                continue;
            }
            $credit = ChordSheetParser::credit($row->readText());
            if ($credit !== null) {
                if ($this->artist === '' && $credit->singer !== null) {
                    $this->artist = $credit->singer;
                }
                if ($writer === null) {
                    $writer = $credit->writer;
                }
                if (ChordSheetParser::isMyanmar($row->text())) {
                    $this->sawMyanmar = true;
                }

                continue;
            }
            $r = $row;
            if (count(array_filter($r->words, fn (ScanWord $w) => ! ChordSheetParser::isDiagramWord($w->text))) > 0) {
                $r = $r->withWords(array_values(array_filter($r->words, fn (ScanWord $w) => ! ChordSheetParser::isDiagramWord($w->text))));
            }
            $candidates[] = $r;
        }

        $readable = array_values(array_filter($candidates, fn (Row $r) => ChordSheetParser::isReadable($r, $this->readsMyanmar) && ChordSheetParser::cleanTitle($r->readText()) !== ''));
        foreach ($candidates as $r) {
            if (ChordSheetParser::isMyanmar($r->text())) {
                $this->sawMyanmar = true;
            }
        }

        if (! empty($readable)) {
            $best = $readable[0];
            $bestScore = $this->titleScore($best, $readable);
            foreach ($readable as $r) {
                $score = $this->titleScore($r, $readable);
                if ($score > $bestScore) {
                    $best = $r;
                    $bestScore = $score;
                }
            }

            $text = ChordSheetParser::cleanTitle($best->readText());
            if ($this->artist === '' && preg_match('/^(.+?)\s+[-–—]\s+(.+)$/u', $text, $m)) {
                $text = $m[1];
                $this->artist = $m[2];
            }
            $this->title = $text;

            if ($this->artist === '') {
                $bi = array_search($best, $readable, true);
                if ($bi !== false && $bi + 1 < count($readable)) {
                    $next = $readable[$bi + 1];
                    if (ChordSheetParser::graphemeCount($next->readText()) <= 40 && $next->box()->height <= $best->box()->height) {
                        $this->artist = ChordSheetParser::cleanTitle($next->readText());
                    }
                }
            }
        }

        if ($this->artist === '' && $writer !== null) {
            $this->artist = $writer;
        }
    }

    private function titleScore(Row $row, array $rows): float
    {
        $tallest = 0.0;
        foreach ($rows as $r) {
            if ($r->box()->height > $tallest) {
                $tallest = $r->box()->height;
            }
        }
        $score = $row->box()->height / max($tallest, 0.0001);
        $center = $this->margin + $this->width / 2.0;
        if (abs($row->box()->midX - $center) < $this->width * 0.12) {
            $score += 0.15;
        }
        if (! empty($rows) && $row->box() == $rows[0]->box()) {
            $score += 0.1;
        }
        if (ChordSheetParser::graphemeCount(ChordSheetParser::cleanTitle($row->readText())) > 32) {
            $score -= 0.3;
        }

        return $score;
    }

    private function start(SectionKind $kind, ?string $label, ?int $number = null, ?string $note = null): void
    {
        $l = $label ?? '';
        if ($kind === SectionKind::Verse) {
            $this->verseCount = $number ?? ($this->verseCount + 1);
            $l = "VERSE {$this->verseCount}";
        }
        $this->sections[] = [
            'id' => (string) Str::uuid(),
            'kind' => $kind->value,
            'label' => $l,
            'note' => $note,
            'strum' => $this->pendingStrum,
            'lines' => [],
            'bars' => [],
            'flow' => [],
        ];
        $this->pendingStrum = null;
        $this->sectionBoxes[] = null;
    }

    private function lyricSection(): void
    {
        $last = ! empty($this->sections) ? $this->sections[count($this->sections) - 1] : null;
        if ($last === null || $last['kind'] === SectionKind::Music->value) {
            $this->start(SectionKind::Verse, null);
        }
    }

    private function track(Row $row): void
    {
        if (empty($this->sectionBoxes)) {
            return;
        }
        $i = count($this->sectionBoxes) - 1;
        $this->sectionBoxes[$i] = $this->sectionBoxes[$i] !== null
            ? $this->sectionBoxes[$i]->union($row->box())
            : $row->box();
    }

    /** @param list<PlacedChord> $chords */
    private function note(array $chords, float $confidence): void
    {
        foreach ($chords as $c) {
            if ($c->note !== null) {
                $this->notes[$c->name] = $c->note;
            }
            if (($c->note !== null || $confidence < 0.5) && ! in_array($c->name, $this->lowConfidence, true)) {
                $this->lowConfidence[] = $c->name;
            }
        }
    }

    private function update(callable $transform): void
    {
        $i = count($this->sections) - 1;
        $this->sections[$i] = $transform($this->sections[$i]);
    }

    private function addLine(string $text, bool $missingLyrics): void
    {
        $this->update(function (array $s) use ($text) {
            $s['lines'][] = $text;

            return $s;
        });
        if ($missingLyrics) {
            $last = $this->sections[count($this->sections) - 1];
            $this->needs[] = ['sectionId' => $last['id'], 'line' => count($last['lines']) - 1];
        }
    }

    /** @param list<Row> $rows */
    public function readBody(array $rows): void
    {
        $i = 0;
        $n = count($rows);
        while ($i < $n) {
            $row = $rows[$i];
            $next = $i + 1 < $n ? $rows[$i + 1] : null;
            $gapBelow = $next !== null ? $next->box()->minY - $row->box()->maxY : INF;

            $kind = $row->kind;

            if ($kind instanceof HeaderRowKind) {
                $this->start($kind->kind, $kind->label, $kind->number, $kind->note);
                $this->track($row);
                if (! empty($kind->chords)) {
                    $this->update(function (array $s) use ($kind) {
                        foreach ($kind->chords as $c) {
                            $s['bars'][] = $c->name;
                        }

                        return $s;
                    });
                    $this->note($kind->chords, $row->confidence);
                }
            } elseif ($kind instanceof ChordsRowKind) {
                $chords = $kind->chords;
                $this->note($chords, $row->confidence);
                $text = trim($row->text(), " \t");
                $instrumental = (str_starts_with($text, '(') && str_ends_with($text, ')'))
                    || str_contains($text, '|');
                $lyricBelow = null;
                if (! $instrumental && $next !== null && $next->kind instanceof LyricRowKind) {
                    if ($gapBelow < max($row->box()->height, $next->box()->height) * 1.6) {
                        $lyricBelow = $next;
                    }
                }
                if ($lyricBelow !== null) {
                    $this->lyricSection();
                    $this->track($row);
                    $this->track($lyricBelow);
                    if (ChordSheetParser::isMyanmar($lyricBelow->text())) {
                        $this->sawMyanmar = true;
                    }
                    if (ChordSheetParser::isReadable($lyricBelow, $this->readsMyanmar)) {
                        $this->readableLyrics++;
                        $this->addLine(ChordSheetParser::merge($chords, $lyricBelow), false);
                    } else {
                        $this->addLine(ChordSheetParser::chordsOnly($chords, $this->margin, $this->width, $lyricBelow->box()->maxX), true);
                    }
                    $i += 2;

                    continue;
                }
                $skippedLine = ! $this->readsMyanmar && $gapBelow > $row->box()->height * 1.2 && $gapBelow !== INF;
                if (empty($this->sections)) {
                    $this->start($skippedLine ? SectionKind::Verse : SectionKind::Music, $skippedLine ? null : 'INTRO');
                }
                $this->track($row);
                $last = $this->sections[count($this->sections) - 1];
                if ($last['kind'] === SectionKind::Music->value && ! $skippedLine) {
                    $this->update(function (array $s) use ($chords) {
                        foreach ($chords as $c) {
                            $s['bars'][] = $c->name;
                        }

                        return $s;
                    });
                } else {
                    $this->lyricSection();
                    $this->addLine(ChordSheetParser::chordsOnly($chords, $this->margin, $this->width), ! $this->readsMyanmar);
                }
            } elseif ($kind instanceof LyricRowKind) {
                $readable = ChordSheetParser::isReadable($row, $this->readsMyanmar);
                $gapAbove = $i > 0 ? $row->box()->minY - $rows[$i - 1]->box()->maxY : INF;
                if (! $readable && $i > 0 && $gapAbove < $row->box()->height * 0.3 && $row->box()->height < $rows[$i - 1]->box()->height * 0.7) {
                    $this->track($row);
                } elseif (! $readable && $gapAbove > $row->box()->height * 2.5 && $gapBelow > $row->box()->height * 2.5) {
                    // Isolated unread ink — skip.
                } else {
                    $this->lyricSection();
                    $this->track($row);
                    if (ChordSheetParser::isMyanmar($row->text())) {
                        $this->sawMyanmar = true;
                    }
                    if ($readable) {
                        $this->readableLyrics++;
                        $this->addLine($row->readText(), false);
                    } else {
                        $this->addLine('', true);
                    }
                }
            } elseif ($kind instanceof MetaRowKind) {
                $this->readMeta($row);
            } elseif ($kind instanceof StrumRowKind) {
                $last = ! empty($this->sections) ? $this->sections[count($this->sections) - 1] : null;
                if ($last !== null && empty($last['lines']) && empty($last['bars'])) {
                    $this->update(fn (array $s) => [...$s, 'strum' => $kind->pattern]);
                } elseif (empty($this->sections)) {
                    $this->songStrum ??= $kind->pattern;
                } else {
                    $this->pendingStrum = $kind->pattern;
                }
            }

            $i++;
        }
    }

    public function result(): ParsedSheet
    {
        $kept = array_values(array_filter($this->sections, fn (array $s) => ! empty($s['lines']) || ! empty($s['bars'])));

        if ($this->songStrum !== null) {
            $strum = $this->songStrum;
            $kept = array_map(function (array $s) use ($strum) {
                if ($s['strum'] === null && $s['kind'] !== SectionKind::Music->value) {
                    $s['strum'] = $strum;
                }

                return $s;
            }, $kept);
        }

        $kept = array_map(function (array $s) {
            if ($s['kind'] === SectionKind::Music->value && $s['note'] === null && ! empty($s['bars'])) {
                $s['note'] = count($s['bars']).' bars';
            }

            return $s;
        }, $kept);

        $keptIds = array_flip(array_map(fn (array $s) => $s['id'], $kept));

        $allChords = [];
        foreach ($kept as $s) {
            foreach ($s['bars'] as $c) {
                $allChords[] = $c;
            }
            foreach ($s['lines'] as $l) {
                foreach (ChordLine::chords($l) as $c) {
                    $allChords[] = $c;
                }
            }
        }
        $key = $this->key ?? ChordSheetParser::guessKey($allChords);

        $isMyanmar = $this->sawMyanmar || (! empty($this->needs) && count($this->needs) >= $this->readableLyrics);

        $song = [
            'id' => (string) Str::uuid(),
            'title' => $this->title,
            'artist' => $this->artist,
            'key' => $key,
            'tempo' => $this->tempo ?? 80,
            'sections' => $kept,
            'isMyanmar' => $isMyanmar,
            'myNote' => ($this->capo !== null && $this->capo > 0) ? "Capo {$this->capo}" : null,
        ];

        $regions = [];
        foreach ($this->sections as $idx => $s) {
            if (! isset($keptIds[$s['id']])) {
                continue;
            }
            $box = $this->sectionBoxes[$idx] ?? null;
            if ($box === null || $box->minY >= 1.0) {
                continue;
            }
            $regions[] = [
                'kind' => $s['kind'],
                'x' => $box->minX,
                'y' => $box->minY,
                'width' => $box->width,
                'height' => min($box->height, 1.0 - $box->minY),
            ];
        }

        $needs = array_values(array_filter($this->needs, fn (array $ref) => isset($keptIds[$ref['sectionId']])));

        return new ParsedSheet($song, array_slice($this->lowConfidence, 0, 5), $this->notes, $needs, $regions);
    }
}
