<?php

namespace App\Services\Social;

use RuntimeException;

/** The provider isn't configured on this server (no client ID) — a 503, not the player's fault. */
class SocialSignInUnavailableException extends RuntimeException {}
