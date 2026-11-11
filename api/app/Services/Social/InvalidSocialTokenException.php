<?php

namespace App\Services\Social;

use RuntimeException;

/** The identity token could not be verified (bad signature, wrong audience, expired…) — a 401. */
class InvalidSocialTokenException extends RuntimeException {}
