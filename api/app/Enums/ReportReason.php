<?php

namespace App\Enums;

/**
 * Report reasons offered on the app's Report screen.
 */
enum ReportReason: string
{
    case WrongChords = 'wrong_chords';
    case Duplicate = 'duplicate';
    case Copyright = 'copyright';
    case Offensive = 'offensive';
    case Spam = 'spam';
    case Other = 'other';
}
