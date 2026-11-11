<?php

namespace Tests\Feature;

use App\Mail\ResetPasswordMail;
use App\Models\Song;
use App\Models\User;
use Database\Factories\UserFactory;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Mail;
use Tests\TestCase;

class AuthTest extends TestCase
{
    use RefreshDatabase;

    public function test_sign_up_returns_a_token_and_the_apps_profile_shape(): void
    {
        $this->postJson('/api/v1/auth/sign-up', [
            'name' => 'Aung Aung', 'email' => 'aung@example.com', 'password' => 'Guitar123',
        ])
            ->assertCreated()
            ->assertJsonStructure(['token', 'profile' => [
                'user' => ['id', 'name', 'handle', 'tier', 'sheets', 'followers', 'city'],
                'email', 'points', 'grooveDays', 'bestGroove', 'dailyGoalMinutes', 'todayMinutes',
                'week', 'restNotes', 'songsLearned', 'lessonsDone',
            ]])
            ->assertJsonPath('profile.email', 'aung@example.com')
            ->assertJsonPath('profile.user.tier', 1)
            ->assertJsonCount(7, 'profile.week');
    }

    public function test_email_is_encrypted_at_rest_and_found_by_its_hash(): void
    {
        $this->postJson('/api/v1/auth/sign-up', ['name' => 'A', 'email' => 'Secret@Example.com', 'password' => 'Guitar123'])->assertCreated();

        $row = DB::table('users')->first();
        $this->assertNotNull($row);
        $this->assertStringNotContainsString('example.com', $row->email);
        $this->assertSame(User::hashEmail('secret@example.com'), $row->email_hash);
        $this->assertSame('Secret@Example.com', User::query()->firstOrFail()->email);
    }

    public function test_sign_up_rejects_a_taken_email_and_weak_passwords(): void
    {
        User::factory()->create(['email' => 'taken@example.com']);

        $this->postJson('/api/v1/auth/sign-up', ['name' => 'A', 'email' => 'TAKEN@example.com', 'password' => 'Guitar123'])
            ->assertUnprocessable()->assertJsonValidationErrors('email');
        $this->postJson('/api/v1/auth/sign-up', ['name' => 'A', 'email' => 'new@example.com', 'password' => 'guitar'])
            ->assertUnprocessable()->assertJsonValidationErrors('password');
    }

    public function test_sign_in_with_wrong_password_fails_without_saying_which_part_was_wrong(): void
    {
        User::factory()->create(['email' => 'me@example.com']);

        $this->postJson('/api/v1/auth/sign-in', ['email' => 'me@example.com', 'password' => 'Wrong1234'])
            ->assertUnprocessable()
            ->assertJsonPath('errors.email.0', 'That email and password don’t match.');
        $this->postJson('/api/v1/auth/sign-in', ['email' => 'nobody@example.com', 'password' => 'Wrong1234'])
            ->assertUnprocessable()
            ->assertJsonPath('errors.email.0', 'That email and password don’t match.');
    }

    public function test_token_works_until_sign_out(): void
    {
        User::factory()->create(['email' => 'me@example.com']);
        $token = $this->postJson('/api/v1/auth/sign-in', ['email' => 'me@example.com', 'password' => UserFactory::PASSWORD])
            ->assertOk()->json('token');

        $this->withToken($token)->getJson('/api/v1/me')->assertOk()->assertJsonPath('email', 'me@example.com');
        $this->withToken($token)->postJson('/api/v1/auth/sign-out')->assertNoContent();

        $this->app['auth']->forgetGuards();
        $this->withToken($token)->getJson('/api/v1/me')->assertUnauthorized();
    }

    public function test_protected_endpoints_return_json_401_without_a_token(): void
    {
        $this->getJson('/api/v1/songs')->assertUnauthorized()->assertJsonPath('message', 'Unauthenticated.');
    }

    public function test_password_reset_flow(): void
    {
        Mail::fake();
        $user = User::factory()->create(['email' => 'me@example.com']);
        $user->createToken('old phone');

        $this->postJson('/api/v1/auth/forgot-password', ['email' => 'me@example.com'])->assertAccepted();
        $token = null;
        Mail::assertQueued(ResetPasswordMail::class, function (ResetPasswordMail $mail) use (&$token) {
            $token = $mail->token;

            return $mail->hasTo('me@example.com');
        });

        $this->postJson('/api/v1/auth/reset-password', ['email' => 'me@example.com', 'token' => $token, 'password' => 'NewGuitar9'])
            ->assertNoContent();
        $this->assertSame(0, $user->tokens()->count());
        $this->postJson('/api/v1/auth/sign-in', ['email' => 'me@example.com', 'password' => 'NewGuitar9'])->assertOk();
        // The link only works once.
        $this->postJson('/api/v1/auth/reset-password', ['email' => 'me@example.com', 'token' => $token, 'password' => 'Another99'])
            ->assertUnprocessable();
    }

    public function test_forgot_password_does_not_reveal_whether_an_account_exists(): void
    {
        Mail::fake();

        $this->postJson('/api/v1/auth/forgot-password', ['email' => 'nobody@example.com'])->assertAccepted();
        Mail::assertNothingQueued();
    }

    public function test_reset_link_expires(): void
    {
        Mail::fake();
        User::factory()->create(['email' => 'me@example.com']);
        $this->postJson('/api/v1/auth/forgot-password', ['email' => 'me@example.com']);
        $token = null;
        Mail::assertQueued(ResetPasswordMail::class, function (ResetPasswordMail $mail) use (&$token) {
            $token = $mail->token;

            return true;
        });

        $this->travel(31)->minutes();
        $this->postJson('/api/v1/auth/reset-password', ['email' => 'me@example.com', 'token' => $token, 'password' => 'NewGuitar9'])
            ->assertUnprocessable()->assertJsonValidationErrors('token');
    }

    public function test_delete_account_signs_out_everywhere_and_signing_in_cancels_it(): void
    {
        $user = User::factory()->create(['email' => 'me@example.com']);
        $token = $user->createToken('phone')->plainTextToken;

        $this->withToken($token)->deleteJson('/api/v1/me', ['reason' => 'Taking a break'])
            ->assertAccepted()->assertJsonPath('graceDays', 30);
        $this->assertNotNull($user->fresh()?->deletion_requested_at);
        $this->assertSame(0, $user->tokens()->count());

        $this->postJson('/api/v1/auth/sign-in', ['email' => 'me@example.com', 'password' => UserFactory::PASSWORD])->assertOk();
        $this->assertNull($user->fresh()?->deletion_requested_at);
    }

    public function test_purge_deletes_accounts_after_the_grace_period_with_their_songs(): void
    {
        $leaving = User::factory()->create();
        $staying = User::factory()->create();
        Song::factory()->for($leaving, 'owner')->create();
        $leaving->forceFill(['deletion_requested_at' => now()->subDays(31)])->save();
        $staying->forceFill(['deletion_requested_at' => now()->subDays(5)])->save();

        $this->artisan('ochords:purge-deleted-accounts')->assertSuccessful();

        $this->assertModelMissing($leaving);
        $this->assertModelExists($staying);
        $this->assertSame(0, Song::query()->count());
    }

    public function test_sign_in_is_rate_limited(): void
    {
        for ($i = 0; $i < 10; $i++) {
            $this->postJson('/api/v1/auth/sign-in', ['email' => 'me@example.com', 'password' => 'Wrong1234']);
        }

        $this->postJson('/api/v1/auth/sign-in', ['email' => 'me@example.com', 'password' => 'Wrong1234'])->assertTooManyRequests();
    }
}
