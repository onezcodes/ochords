<?php

namespace App\Enums;

enum NotificationCategory: string
{
    case Community = 'community';
    case Learning = 'learning';
    case System = 'system';
}
