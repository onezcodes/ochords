<?php

namespace App\Http\Requests\Community;

use App\Enums\FeedbackKind;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class StoreFeedbackRequest extends FormRequest
{
    /** @return array<string, mixed> */
    public function rules(): array
    {
        return [
            'kind' => ['required', Rule::enum(FeedbackKind::class)],
            // The app enables Send at 5 characters.
            'message' => ['required', 'string', 'min:5', 'max:2000'],
        ];
    }
}
