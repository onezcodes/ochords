<?php

namespace App\Mail;

use Illuminate\Bus\Queueable;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Mail\Mailable;
use Illuminate\Mail\Mailables\Content;
use Illuminate\Mail\Mailables\Envelope;
use Illuminate\Queue\SerializesModels;

/**
 * The "reset your password" email. The link opens the app, which posts the token back with the
 * new password; the email address is never put in the link.
 */
class ResetPasswordMail extends Mailable implements ShouldQueue
{
    use Queueable, SerializesModels;

    public function __construct(public readonly string $token) {}

    public function envelope(): Envelope
    {
        return new Envelope(subject: 'Reset your O Chords password');
    }

    public function content(): Content
    {
        return new Content(
            text: 'mail.reset-password',
            with: [
                'link' => 'ochords://reset-password?token='.$this->token,
                'minutes' => (int) config('ochords.password_reset_minutes'),
            ],
        );
    }
}
