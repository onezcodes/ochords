<?php

namespace App\Enums;

enum SongSource: string
{
    case Imported = 'imported';
    case Community = 'community';
    case Listened = 'listened';
    case Shared = 'shared';
}
