<?php

namespace App\Http\Resources;

use App\Models\LessonQuestion;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * The app's `LessonQuestion`. The answer is included because lessons are checked on the phone,
 * which also lets them work offline.
 *
 * @mixin LessonQuestion
 */
class LessonQuestionResource extends JsonResource
{
    /** @return array<string, mixed> */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'kind' => $this->kind,
            'prompt' => $this->prompt,
            'detail' => $this->detail,
            'options' => $this->options,
            'answer' => $this->answer,
        ];
    }
}
