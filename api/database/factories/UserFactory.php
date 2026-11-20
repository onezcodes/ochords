<?php

namespace Database\Factories;

use App\Enums\AdminRole;
use App\Models\User;
use Database\Seeders\RolesAndPermissionsSeeder;
use Illuminate\Database\Eloquent\Factories\Factory;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Str;
use Spatie\Permission\Models\Role;

/**
 * @extends Factory<User>
 */
class UserFactory extends Factory
{
    public const PASSWORD = 'Password1';

    protected static ?string $password;

    /** @return array<string, mixed> */
    public function definition(): array
    {
        return [
            'name' => fake()->name(),
            'handle' => Str::lower(Str::random(12)),
            'email' => fake()->unique()->safeEmail(),
            'password' => static::$password ??= Hash::make(self::PASSWORD),
            'is_admin' => false,
        ];
    }

    public function admin(): static
    {
        return $this->state(fn () => ['is_admin' => true])
            ->afterCreating(function (User $user): void {
                if (! Role::query()->where('name', AdminRole::SuperAdmin->value)->exists()) {
                    (new RolesAndPermissionsSeeder)->run();
                }
                $user->assignRole(AdminRole::SuperAdmin->value);
            });
    }
}
