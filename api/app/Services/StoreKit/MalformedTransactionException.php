<?php

namespace App\Services\StoreKit;

use RuntimeException;

/** Not a StoreKit transaction JWS we can use (bad structure, missing claims, another app or product) — a 422. */
class MalformedTransactionException extends RuntimeException {}
