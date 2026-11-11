<?php

namespace App\Services\Social;

/**
 * Verifies an identity token from Sign in with Apple or Google and says who it is for.
 */
interface SocialTokenVerifier
{
    /**
     * @throws InvalidSocialTokenException
     */
    public function verifyApple(string $identityToken): SocialIdentity;

    /**
     * @throws InvalidSocialTokenException
     * @throws SocialSignInUnavailableException when no Google client ID is configured
     */
    public function verifyGoogle(string $idToken): SocialIdentity;
}
