<?php

namespace App\Enums;

/**
 * Built-in staff roles for /admin. Custom roles can be created in the panel;
 * these are seeded and used by artisan grant/revoke.
 */
enum AdminRole: string
{
    case SuperAdmin = 'super_admin';
    case Admin = 'admin';
    case Moderator = 'moderator';
    case Support = 'support';

    /** @return list<string> */
    public static function values(): array
    {
        return array_column(self::cases(), 'value');
    }

    /** Roles that unlock Filament /admin (any one is enough). */
    public static function panelRoles(): array
    {
        return self::values();
    }
}
