<?php

namespace App\Services;

use App\Mail\ResetPasswordMail;
use App\Models\User;
use App\Services\Social\InvalidSocialTokenException;
use App\Services\Social\SocialIdentity;
use App\Services\Social\SocialTokenVerifier;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Mail;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

/**
 * Accounts and API tokens. Logs record events and user IDs only — never emails or passwords.
 */
class AuthService
{
    private const DEFAULT_DEVICE = 'O Chords app';

    /** Name for an account created from an Apple/Google token that carried none. */
    private const DEFAULT_NAME = 'Guitarist';

    public function __construct(private readonly SocialTokenVerifier $social) {}

    /**
     * @param  array{name: string, email: string, password: string, device_name?: ?string}  $data
     * @return array{token: string, user: User}
     */
    public function signUp(array $data): array
    {
        if (User::query()->whereEmail($data['email'])->exists()) {
            throw ValidationException::withMessages(['email' => 'An account with this email already exists.']);
        }

        $user = User::create([
            'name' => $data['name'],
            'handle' => $this->uniqueHandle($data['name']),
            'email' => $data['email'],
            'password' => $data['password'],
        ]);
        Log::info('auth.sign_up', ['user_id' => $user->id]);

        return ['token' => $this->issueToken($user, $data['device_name'] ?? null), 'user' => $user];
    }

    /**
     * Signing in also cancels a pending account deletion, as the app promises.
     *
     * @param  array{email: string, password: string, device_name?: ?string}  $data
     * @return array{token: string, user: User}
     */
    public function signIn(array $data): array
    {
        $user = User::query()->whereEmail($data['email'])->first();

        if (! $user || ! Hash::check($data['password'], $user->password)) {
            Log::notice('auth.sign_in.failed');
            throw ValidationException::withMessages(['email' => 'That email and password don’t match.']);
        }

        $this->cancelPendingDeletion($user);
        Log::info('auth.sign_in', ['user_id' => $user->id]);

        return ['token' => $this->issueToken($user, $data['device_name'] ?? null), 'user' => $user];
    }

    /**
     * Sign in with Apple: the app's identity token is verified, then matched to an account.
     *
     * @return array{token: string, user: User, created: bool}
     */
    public function signInWithApple(string $identityToken, ?string $name, ?string $device): array
    {
        return $this->socialSession($this->social->verifyApple($identityToken), $name, $device);
    }

    /** @return array{token: string, user: User, created: bool} */
    public function signInWithGoogle(string $idToken, ?string $device): array
    {
        return $this->socialSession($this->social->verifyGoogle($idToken), null, $device);
    }

    /**
     * Matches by the provider's subject ID first; then by verified email (and links the account);
     * otherwise creates an account with a random password. Like sign-in, cancels a pending deletion.
     *
     * @return array{token: string, user: User, created: bool}
     */
    private function socialSession(SocialIdentity $identity, ?string $name, ?string $device): array
    {
        $column = $identity->provider->column();
        $linkedBy = 'subject';
        $user = User::query()->where($column, $identity->subject)->first();

        if (! $user && $identity->email !== null && $identity->emailVerified) {
            $user = User::query()->whereEmail($identity->email)->first();
            if ($user) {
                $existing = $user->getAttribute($column);
                if ($existing !== null && $existing !== $identity->subject) {
                    Log::notice('auth.social.subject_mismatch', ['provider' => $identity->provider->value, 'user_id' => $user->id]);
                    throw new InvalidSocialTokenException('This '.ucfirst($identity->provider->value).' account doesn’t match the one linked to that email.');
                }
                $user->forceFill([$column => $identity->subject])->save();
                $linkedBy = 'email';
            }
        }

        if (! $user) {
            // Without a verified email there is nothing safe to link or create an account with.
            if ($identity->email === null || ! $identity->emailVerified) {
                throw ValidationException::withMessages(['email' => ucfirst($identity->provider->value).' didn’t share a verified email address for this account.']);
            }
            $name = trim((string) $name) ?: self::DEFAULT_NAME;
            $user = User::create([
                'name' => $name,
                'handle' => $this->uniqueHandle($name),
                'email' => $identity->email,
                'password' => Str::random(48),
            ]);
            $user->forceFill([$column => $identity->subject])->save();
            $linkedBy = 'created';
        }

        $this->cancelPendingDeletion($user);
        Log::info('auth.social.sign_in', ['provider' => $identity->provider->value, 'user_id' => $user->id, 'linked_by' => $linkedBy]);

        return ['token' => $this->issueToken($user, $device), 'user' => $user, 'created' => $linkedBy === 'created'];
    }

    private function cancelPendingDeletion(User $user): void
    {
        if ($user->deletion_requested_at !== null) {
            $user->forceFill(['deletion_requested_at' => null, 'deletion_reason' => null])->save();
            Log::info('auth.deletion_cancelled', ['user_id' => $user->id]);
        }
    }

    public function signOut(User $user): void
    {
        // Bearer tokens only (no cookie sessions), so the current token is always a stored one.
        $user->currentAccessToken()->delete();
        Log::info('auth.sign_out', ['user_id' => $user->id]);
    }

    /** Always succeeds from the caller's view, so the endpoint can't be used to find out who has an account. */
    public function sendResetLink(string $email): void
    {
        $user = User::query()->whereEmail($email)->first();
        if (! $user) {
            return;
        }

        $token = Str::random(64);
        DB::table('password_reset_tokens')->updateOrInsert(
            ['email_hash' => $user->email_hash],
            ['token' => Hash::make($token), 'created_at' => now()],
        );
        Mail::to($user->email)->queue(new ResetPasswordMail($token));
        Log::info('auth.reset_link_sent', ['user_id' => $user->id]);
    }

    public function resetPassword(string $email, string $token, string $password): void
    {
        $hash = User::hashEmail($email);
        $row = DB::table('password_reset_tokens')->where('email_hash', $hash)->first();
        $expired = $row && now()->subMinutes((int) config('ochords.password_reset_minutes'))->gt($row->created_at);

        if (! $row || $expired || ! Hash::check($token, $row->token)) {
            throw ValidationException::withMessages(['token' => 'This reset link is invalid or has expired.']);
        }

        $user = User::query()->where('email_hash', $hash)->firstOrFail();
        $user->forceFill(['password' => $password])->save();
        // A new password signs every device out.
        $user->tokens()->delete();
        DB::table('password_reset_tokens')->where('email_hash', $hash)->delete();
        Log::info('auth.password_reset', ['user_id' => $user->id]);
    }

    /** Signs the user out everywhere; the data is purged after the grace period (see purgeDeletedAccounts). */
    public function requestDeletion(User $user, ?string $reason): void
    {
        $user->forceFill(['deletion_requested_at' => now(), 'deletion_reason' => $reason])->save();
        $user->tokens()->delete();
        Log::info('auth.deletion_requested', ['user_id' => $user->id]);
    }

    /** @return int how many accounts were deleted */
    public function purgeDeletedAccounts(): int
    {
        $cutoff = now()->subDays((int) config('ochords.account_deletion_grace_days'));
        $count = 0;

        User::query()->where('deletion_requested_at', '<=', $cutoff)->each(function (User $user) use (&$count) {
            $user->tokens()->delete();
            $user->delete();
            $count++;
        });
        Log::info('auth.accounts_purged', ['count' => $count]);

        return $count;
    }

    private function issueToken(User $user, ?string $device): string
    {
        return $user->createToken($device ?: self::DEFAULT_DEVICE)->plainTextToken;
    }

    private function uniqueHandle(string $name): string
    {
        $base = Str::slug($name, '') ?: 'player';
        $handle = Str::limit($base, 20, '');
        while (User::query()->where('handle', $handle)->exists()) {
            $handle = Str::limit($base, 15, '').random_int(1000, 99999);
        }

        return $handle;
    }
}
