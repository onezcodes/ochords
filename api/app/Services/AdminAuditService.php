<?php

namespace App\Services;

use App\Models\AdminAuditLog;
use App\Models\User;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Request;

/**
 * Records staff actions for the admin panel. Never pass emails, passwords, or encrypted free text in $meta.
 */
class AdminAuditService
{
    /**
     * @param  array<string, mixed>  $meta
     */
    public function log(string $action, ?Model $subject = null, array $meta = [], ?User $actor = null): void
    {
        $actor ??= Auth::user() instanceof User ? Auth::user() : null;

        AdminAuditLog::query()->create([
            'actor_id' => $actor?->id,
            'action' => $action,
            'subject_type' => $subject ? $subject::class : null,
            'subject_id' => $subject?->getKey() !== null ? (string) $subject->getKey() : null,
            'meta' => $meta === [] ? null : $meta,
            'ip' => Request::ip(),
        ]);
    }
}
