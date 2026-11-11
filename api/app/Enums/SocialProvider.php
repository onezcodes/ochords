<?php

namespace App\Enums;

/**
 * Identity providers for token-exchange sign-in. The column holds the provider's subject ID.
 */
enum SocialProvider: string
{
    case Apple = 'apple';
    case Google = 'google';

    public function column(): string
    {
        return match ($this) {
            self::Apple => 'apple_id',
            self::Google => 'google_id',
        };
    }
}
