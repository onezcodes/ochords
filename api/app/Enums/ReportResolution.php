<?php

namespace App\Enums;

/**
 * How a report was closed (`ochords:reports:resolve`).
 */
enum ReportResolution: string
{
    case Dismissed = 'dismissed';
    case Hidden = 'hidden';
    case Fixed = 'fixed';
}
