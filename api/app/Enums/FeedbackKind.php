<?php

namespace App\Enums;

/**
 * Feedback topics offered on the app's Send feedback screen.
 */
enum FeedbackKind: string
{
    case Broken = 'broken';
    case WrongChords = 'wrong_chords';
    case Idea = 'idea';
    case Other = 'other';
}
