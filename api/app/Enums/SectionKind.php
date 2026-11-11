<?php

namespace App\Enums;

enum SectionKind: string
{
    case Verse = 'verse';
    case Chorus = 'chorus';
    case Music = 'music';
    case Flow = 'flow';
    case Bridge = 'bridge';
    case Intro = 'intro';
}
