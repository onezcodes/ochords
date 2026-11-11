<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\Songs\ExploreRequest;
use App\Http\Resources\CommunitySheetResource;
use App\Services\SongService;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

/** Public community sheets — open to guests too. */
class ExploreController extends Controller
{
    public function __construct(private readonly SongService $songs) {}

    public function index(ExploreRequest $request): AnonymousResourceCollection
    {
        return CommunitySheetResource::collection($this->songs->explore($request->input('q')));
    }

    public function popular(): AnonymousResourceCollection
    {
        return CommunitySheetResource::collection($this->songs->popular());
    }
}
