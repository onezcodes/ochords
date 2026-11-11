<?php

namespace App\Http\Requests\Songs;

use Illuminate\Foundation\Http\FormRequest;

class StoreScanRequest extends FormRequest
{
    private const MAX_SIDE = 6000;

    /** @return array<string, mixed> */
    public function rules(): array
    {
        return [
            'image' => [
                'required', 'file', 'mimes:jpeg,png',
                'max:'.(int) config('ochords.scan.max_kilobytes'),
                'dimensions:max_width='.self::MAX_SIDE.',max_height='.self::MAX_SIDE,
            ],
        ];
    }
}
