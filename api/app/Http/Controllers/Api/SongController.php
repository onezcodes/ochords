<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\Songs\SaveSongRequest;
use App\Http\Resources\SongResource;
use App\Models\Song;
use App\Models\User;
use App\Services\SongService;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\Gate;

class SongController extends Controller
{
    public function __construct(private readonly SongService $songs) {}

    /** The signed-in player's library. */
    public function index(Request $request): AnonymousResourceCollection
    {
        return SongResource::collection($this->songs->library($this->user($request)));
    }

    public function show(Song $song): SongResource
    {
        Gate::authorize('view', $song);

        return new SongResource($this->songs->withStats($song));
    }

    /** Create/update by the app's song ID, or save someone else's public sheet into the library. */
    public function update(SaveSongRequest $request, string $id): SongResource
    {
        $existing = Song::query()->find($id);
        if ($existing) {
            Gate::authorize('save', $existing);
        }

        return new SongResource($this->songs->save($this->user($request), $existing, $id, $request->validated()));
    }

    public function destroy(Request $request, Song $song): Response
    {
        Gate::authorize('remove', $song);
        $this->songs->remove($this->user($request), $song);

        return response()->noContent();
    }

    private function user(Request $request): User
    {
        /** @var User */
        return $request->user();
    }
}
