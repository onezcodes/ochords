<?php

namespace App\Console\Commands;

use App\Models\User;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Validator;

class SetAdminPasswordCommand extends Command
{
    protected $signature = 'ochords:admin:set-password {handle : Player handle} {password : New password (min 8)}';

    protected $description = 'Set a password so a staff user can sign in to /admin';

    public function handle(): int
    {
        $handle = (string) $this->argument('handle');
        $password = (string) $this->argument('password');
        $validator = Validator::make(
            ['password' => $password],
            ['password' => ['required', 'string', 'min:8']],
        );
        if ($validator->fails()) {
            $this->error($validator->errors()->first());

            return self::FAILURE;
        }
        $user = User::query()->where('handle', $handle)->first();
        if (! $user) {
            $this->error("No user with handle “{$handle}”.");

            return self::FAILURE;
        }
        // User casts password as hashed — pass the plaintext once.
        $user->forceFill(['password' => $password])->save();
        $this->info("Password updated for {$user->handle}.");

        return self::SUCCESS;
    }
}
