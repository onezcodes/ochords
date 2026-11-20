<?php

namespace App\Filament\Resources\Users\Pages;

use App\Filament\Resources\Users\UserResource;
use App\Models\User;
use Filament\Resources\Pages\ListRecords;
use Illuminate\Database\Eloquent\Builder;

class ListUsers extends ListRecords
{
    protected static string $resource = UserResource::class;

    /** @param  Builder<User>  $query */
    protected function applySearchToTableQuery(Builder $query): Builder
    {
        $search = $this->getTableSearch();
        if (blank($search)) {
            return $query;
        }

        return $query->where(function (Builder $q) use ($search): void {
            $q->where('handle', 'like', "%{$search}%")
                ->orWhere('name', 'like', "%{$search}%");
            if (str_contains($search, '@')) {
                $q->orWhere('email_hash', User::hashEmail($search));
            }
        });
    }
}
