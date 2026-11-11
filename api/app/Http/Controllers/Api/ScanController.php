<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\Songs\StoreScanRequest;
use App\Http\Resources\ScanResource;
use App\Models\User;
use App\Services\ScanService;

/**
 * Reads a sheet photo the phone can't (Myanmar lyrics). Synchronous on purpose: the app waits
 * on the reading screen, and the photo must not be stored for a queued job.
 */
class ScanController extends Controller
{
    public function __construct(private readonly ScanService $scans) {}

    public function store(StoreScanRequest $request): ScanResource
    {
        /** @var User $user */
        $user = $request->user();
        $image = (string) $request->file('image')?->get();

        return new ScanResource([
            'engine' => $this->scans->engineName(),
            'lines' => $this->scans->read($user, $image),
        ]);
    }
}
