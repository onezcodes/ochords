<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Optional app-level IP allowlist for /admin when OCHORDS_ADMIN_ALLOWED_IPS is set.
 * Prefer enforcing this at the reverse proxy as well.
 */
class EnsureAdminIpAllowed
{
    public function handle(Request $request, Closure $next): Response
    {
        /** @var list<string> $allowed */
        $allowed = config('ochords.admin.allowed_ips', []);
        if ($allowed === []) {
            return $next($request);
        }

        $ip = (string) $request->ip();
        foreach ($allowed as $rule) {
            if ($this->matches($ip, $rule)) {
                return $next($request);
            }
        }

        abort(403, 'Admin access is not allowed from this network.');
    }

    private function matches(string $ip, string $rule): bool
    {
        if (! str_contains($rule, '/')) {
            return $ip === $rule;
        }

        [$subnet, $bits] = explode('/', $rule, 2);
        if (filter_var($ip, FILTER_VALIDATE_IP, FILTER_FLAG_IPV4) === false
            || filter_var($subnet, FILTER_VALIDATE_IP, FILTER_FLAG_IPV4) === false) {
            return false;
        }

        $mask = -1 << (32 - (int) $bits);
        $ipLong = ip2long($ip) & $mask;
        $subnetLong = ip2long($subnet) & $mask;

        return $ipLong === $subnetLong;
    }
}
