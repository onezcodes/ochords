<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Resources\LearnPositionResource;
use App\Http\Resources\LessonQuestionResource;
use App\Models\LearnFret;
use App\Models\User;
use App\Services\LearnService;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use Illuminate\Support\Facades\Auth;

/** The learning path — open to guests, with progress when signed in. */
class LearnController extends Controller
{
    public function __construct(private readonly LearnService $learn) {}

    public function path(): AnonymousResourceCollection
    {
        /** @var ?User $user */
        $user = Auth::guard('sanctum')->user();

        return LearnPositionResource::collection($this->learn->path($user));
    }

    public function questions(LearnFret $fret): AnonymousResourceCollection
    {
        return LessonQuestionResource::collection($this->learn->questions($fret));
    }
}
