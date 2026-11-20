<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/** Shortens idle session lifetime for Filament /admin relative to the global SESSION_LIFETIME. */
class SetAdminSessionLifetime
{
    public function handle(Request $request, Closure $next): Response
    {
        $minutes = (int) config('ochords.admin.session_lifetime_minutes', 60);
        if ($minutes > 0) {
            config(['session.lifetime' => $minutes]);
        }

        return $next($request);
    }
}
