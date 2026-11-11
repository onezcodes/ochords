<?php

namespace App\Http\Controllers\Api;

use App\Enums\FeedbackKind;
use App\Enums\ReportReason;
use App\Http\Controllers\Controller;
use App\Http\Requests\Community\StoreFeedbackRequest;
use App\Http\Requests\Community\StoreReportRequest;
use App\Http\Resources\NotificationResource;
use App\Http\Resources\UserSummaryResource;
use App\Models\Song;
use App\Models\User;
use App\Services\CommunityService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Gate;

class CommunityController extends Controller
{
    public function __construct(private readonly CommunityService $community) {}

    public function notifications(Request $request): AnonymousResourceCollection
    {
        return NotificationResource::collection($this->community->notifications($this->user($request)));
    }

    public function readAllNotifications(Request $request): Response
    {
        $this->community->markAllRead($this->user($request));

        return response()->noContent();
    }

    public function player(User $user): UserSummaryResource
    {
        return new UserSummaryResource($this->community->player($user));
    }

    public function report(StoreReportRequest $request, Song $song): JsonResponse
    {
        Gate::authorize('view', $song);
        $report = $this->community->report(
            $this->user($request),
            $song,
            $request->enum('reason', ReportReason::class) ?? ReportReason::Other,
            $request->input('detail'),
        );

        return response()->json(['id' => (string) $report->id, 'status' => $report->status ?? 'received'], Response::HTTP_CREATED);
    }

    /** Works signed in or as a guest. */
    public function feedback(StoreFeedbackRequest $request): JsonResponse
    {
        /** @var ?User $user */
        $user = Auth::guard('sanctum')->user();
        $feedback = $this->community->feedback(
            $user,
            $request->enum('kind', FeedbackKind::class) ?? FeedbackKind::Other,
            $request->string('message')->toString(),
        );

        return response()->json(['id' => (string) $feedback->id], Response::HTTP_CREATED);
    }

    private function user(Request $request): User
    {
        /** @var User */
        return $request->user();
    }
}
