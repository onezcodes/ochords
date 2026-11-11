<?php

namespace App\Services\Social;

use App\Enums\SocialProvider;

/**
 * What a verified identity token says about the person: the provider's stable subject ID and,
 * when the provider shared one, their email (Apple's private relay addresses included).
 */
final readonly class SocialIdentity
{
    public function __construct(
        public SocialProvider $provider,
        public string $subject,
        public ?string $email,
        public bool $emailVerified,
    ) {}
}
