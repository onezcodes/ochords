<?php

namespace App\Enums;

/**
 * Staff permissions for the Filament /admin panel (Spatie laravel-permission).
 * Naming: {area}.{ability} — policies call $user->can(...).
 */
enum AdminPermission: string
{
    // Reports
    case ReportsViewAny = 'reports.view_any';
    case ReportsView = 'reports.view';
    case ReportsResolve = 'reports.resolve';

    // Feedback
    case FeedbackViewAny = 'feedback.view_any';
    case FeedbackView = 'feedback.view';
    case FeedbackReview = 'feedback.review';

    // Sheets
    case SongsViewAny = 'songs.view_any';
    case SongsView = 'songs.view';
    case SongsCreate = 'songs.create';
    case SongsUpdate = 'songs.update';
    case SongsDelete = 'songs.delete';
    case SongsModerate = 'songs.moderate';

    // Players / staff users
    case UsersViewAny = 'users.view_any';
    case UsersView = 'users.view';
    case UsersUpdate = 'users.update';
    case UsersManageRoles = 'users.manage_roles';
    case UsersManagePro = 'users.manage_pro';

    // Learn CMS
    case LearnViewAny = 'learn.view_any';
    case LearnView = 'learn.view';
    case LearnCreate = 'learn.create';
    case LearnUpdate = 'learn.update';
    case LearnDelete = 'learn.delete';

    // RBAC management
    case RolesViewAny = 'roles.view_any';
    case RolesView = 'roles.view';
    case RolesCreate = 'roles.create';
    case RolesUpdate = 'roles.update';
    case RolesDelete = 'roles.delete';
    case PermissionsViewAny = 'permissions.view_any';
    case PermissionsView = 'permissions.view';

    /** @return list<string> */
    public static function values(): array
    {
        return array_column(self::cases(), 'value');
    }
}
