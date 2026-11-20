<?php

namespace App\Services\SheetParsing;

final class ChordLine
{
    /** @return list<string> */
    public static function chords(string $line): array
    {
        $out = [];
        $i = 0;
        $len = strlen($line);
        while ($i < $len) {
            $open = strpos($line, '[', $i);
            if ($open === false) {
                break;
            }
            $close = strpos($line, ']', $open);
            if ($close === false) {
                break;
            }
            $out[] = substr($line, $open + 1, $close - $open - 1);
            $i = $close + 1;
        }

        return $out;
    }

    /** @return list<array{chord:?string,text:string,trailingSpace:bool}> */
    public static function parse(string $line): array
    {
        $segs = [];
        $pending = null;
        $buffer = '';

        $flushText = function () use (&$segs, &$pending, &$buffer) {
            if ($buffer === '') {
                return;
            }
            $text = $buffer;
            if ($text[0] === ' ' && ! empty($segs)) {
                $last = array_pop($segs);
                $segs[] = ['chord' => $last['chord'], 'text' => $last['text'], 'trailingSpace' => true];
            }
            $words = array_values(array_filter(preg_split('/\s+/', $text) ?: [], fn ($s) => $s !== ''));
            $endSpace = $text !== '' && ctype_space($text[strlen($text) - 1]);
            foreach ($words as $n => $w) {
                $segs[] = [
                    'chord' => $n === 0 ? $pending : null,
                    'text' => $w,
                    'trailingSpace' => $n < count($words) - 1 || $endSpace,
                ];
                if ($n === 0) {
                    $pending = null;
                }
            }
            $buffer = '';
        };

        $i = 0;
        $len = strlen($line);
        while ($i < $len) {
            $c = $line[$i];
            $close = $c === '[' ? strpos($line, ']', $i) : false;
            if ($c === '[' && $close !== false) {
                $flushText();
                if ($pending !== null) {
                    $segs[] = ['chord' => $pending, 'text' => '', 'trailingSpace' => true];
                }
                $pending = substr($line, $i + 1, (int) $close - $i - 1);
                $i = (int) $close + 1;
            } else {
                $buffer .= $c;
                $i++;
            }
        }
        $flushText();
        if ($pending !== null) {
            $segs[] = ['chord' => $pending, 'text' => '', 'trailingSpace' => false];
        }

        return $segs;
    }
}
