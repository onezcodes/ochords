<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\Community\SaveSetlistRequest;
use App\Http\Resources\SetlistResource;
use App\Models\Setlist;
use App\Models\User;
use App\Services\CommunityService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\Gate;

class SetlistController extends Controller
{
    public function __construct(private readonly CommunityService $community) {}

    public function index(Request $request): AnonymousResourceCollection
    {
        return SetlistResource::collection($this->community->setlists($this->user($request)));
    }

    /** Create (201) or update (200) by the app's setlist ID. */
    public function update(SaveSetlistRequest $request, string $id): JsonResponse
    {
        $existing = Setlist::query()->find($id);
        if ($existing) {
            Gate::authorize('update', $existing);
        }
        /** @var array{name: string, note?: ?string, songIDs: list<string>} $data */
        $data = $request->validated();
        $setlist = $this->community->saveSetlist($this->user($request), $existing, $id, $data);

        return (new SetlistResource($setlist))->response()->setStatusCode($existing ? Response::HTTP_OK : Response::HTTP_CREATED);
    }

    public function destroy(Setlist $setlist): Response
    {
        Gate::authorize('delete', $setlist);
        $this->community->deleteSetlist($setlist);

        return response()->noContent();
    }

    private function user(Request $request): User
    {
        /** @var User */
        return $request->user();
    }
}
