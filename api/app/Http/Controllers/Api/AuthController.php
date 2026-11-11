<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\Auth\AppleSignInRequest;
use App\Http\Requests\Auth\DeleteAccountRequest;
use App\Http\Requests\Auth\ForgotPasswordRequest;
use App\Http\Requests\Auth\GoogleSignInRequest;
use App\Http\Requests\Auth\ResetPasswordRequest;
use App\Http\Requests\Auth\SignInRequest;
use App\Http\Requests\Auth\SignUpRequest;
use App\Http\Resources\ProfileResource;
use App\Models\User;
use App\Services\AuthService;
use App\Services\ExportService;
use App\Services\ProfileService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;

class AuthController extends Controller
{
    public function __construct(
        private readonly AuthService $auth,
        private readonly ProfileService $profiles,
        private readonly ExportService $exports,
    ) {}

    public function signUp(SignUpRequest $request): JsonResponse
    {
        /** @var array{name: string, email: string, password: string, device_name?: ?string} $data */
        $data = $request->validated();

        return $this->session($this->auth->signUp($data), Response::HTTP_CREATED);
    }

    public function signIn(SignInRequest $request): JsonResponse
    {
        /** @var array{email: string, password: string, device_name?: ?string} $data */
        $data = $request->validated();

        return $this->session($this->auth->signIn($data), Response::HTTP_OK);
    }

    /** Sign in with Apple: 201 when the account was just created, 200 otherwise. */
    public function apple(AppleSignInRequest $request): JsonResponse
    {
        $session = $this->auth->signInWithApple(
            $request->string('identityToken')->toString(),
            $request->input('name'),
            $request->input('device_name'),
        );

        return $this->session($session, $session['created'] ? Response::HTTP_CREATED : Response::HTTP_OK);
    }

    public function google(GoogleSignInRequest $request): JsonResponse
    {
        $session = $this->auth->signInWithGoogle($request->string('idToken')->toString(), $request->input('device_name'));

        return $this->session($session, $session['created'] ? Response::HTTP_CREATED : Response::HTTP_OK);
    }

    public function signOut(Request $request): Response
    {
        $this->auth->signOut($this->user($request));

        return response()->noContent();
    }

    public function forgotPassword(ForgotPasswordRequest $request): JsonResponse
    {
        $this->auth->sendResetLink($request->string('email')->toString());

        return response()->json(['message' => 'If an account exists for that address, a reset link is on its way.'], Response::HTTP_ACCEPTED);
    }

    public function resetPassword(ResetPasswordRequest $request): Response
    {
        $this->auth->resetPassword(
            $request->string('email')->toString(),
            $request->string('token')->toString(),
            $request->string('password')->toString(),
        );

        return response()->noContent();
    }

    public function me(Request $request): ProfileResource
    {
        return new ProfileResource($this->profiles->withWeek($this->user($request)));
    }

    /** Data-access request (PDPO): everything the account holds, as one JSON document. */
    public function export(Request $request): JsonResponse
    {
        return response()->json($this->exports->export($this->user($request), $request));
    }

    public function deleteAccount(DeleteAccountRequest $request): JsonResponse
    {
        $this->auth->requestDeletion($this->user($request), $request->input('reason'));

        return response()->json([
            'message' => 'Your account is scheduled for deletion.',
            'graceDays' => (int) config('ochords.account_deletion_grace_days'),
        ], Response::HTTP_ACCEPTED);
    }

    /** @param array{token: string, user: User, created?: bool} $session */
    private function session(array $session, int $status): JsonResponse
    {
        return response()->json([
            'token' => $session['token'],
            'profile' => new ProfileResource($this->profiles->withWeek($session['user'])),
        ], $status);
    }

    private function user(Request $request): User
    {
        /** @var User */
        return $request->user();
    }
}
