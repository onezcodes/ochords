<?php

namespace Tests\Unit;

use App\Services\SheetParsing\ChordTheory;
use Tests\TestCase;

class ChordTheoryTest extends TestCase
{
    public function test_names_cover_twelve_pitch_classes(): void
    {
        $this->assertCount(12, ChordTheory::NAMES);
        $this->assertSame(['C', 'C#', 'D', 'Eb', 'E', 'F', 'F#', 'G', 'Ab', 'A', 'Bb', 'B'], ChordTheory::NAMES);
    }

    public function test_index_maps_roots_and_enharmonic_aliases(): void
    {
        $this->assertSame(0, ChordTheory::INDEX['C']);
        $this->assertSame(1, ChordTheory::INDEX['C#']);
        $this->assertSame(1, ChordTheory::INDEX['Db']);
        $this->assertSame(3, ChordTheory::INDEX['D#']);
        $this->assertSame(3, ChordTheory::INDEX['Eb']);
        $this->assertSame(6, ChordTheory::INDEX['F#']);
        $this->assertSame(6, ChordTheory::INDEX['Gb']);
        $this->assertSame(10, ChordTheory::INDEX['A#']);
        $this->assertSame(10, ChordTheory::INDEX['Bb']);
        $this->assertSame(11, ChordTheory::INDEX['B']);
    }

    public function test_every_preferred_name_has_a_matching_index(): void
    {
        foreach (ChordTheory::NAMES as $i => $name) {
            $this->assertArrayHasKey($name, ChordTheory::INDEX);
            $this->assertSame($i, ChordTheory::INDEX[$name]);
        }
    }
}
