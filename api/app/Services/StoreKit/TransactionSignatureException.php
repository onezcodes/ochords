<?php

namespace App\Services\StoreKit;

use RuntimeException;

/** Well-formed, but not signed by Apple (bad chain or signature) — a 401. */
class TransactionSignatureException extends RuntimeException {}
