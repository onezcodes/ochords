<?php

namespace App\Models;

use App\Enums\Tier;
use Database\Factories\UserFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Hidden;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Casts\Attribute;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\Crypt;
use Laravel\Sanctum\HasApiTokens;

/**
 * @property int $id
 * @property string $name
 * @property string $handle
 * @property string $email
 * @property string $email_hash
 * @property ?string $city
 * @property int $points
 * @property int $groove_days
 * @property int $best_groove
 * @property int $daily_goal_minutes
 * @property int $rest_notes
 * @property int $songs_learned
 * @property int $lessons_done
 * @property ?Carbon $deletion_requested_at
 * @property ?string $deletion_reason
 * @property ?string $apple_id
 * @property ?string $google_id
 * @property ?Carbon $pro_until
 * @property ?string $pro_original_transaction_id
 */
#[Fillable(['name', 'handle', 'email', 'password', 'city'])]
#[Hidden(['password', 'email_hash'])]
class User extends Authenticatable
{
    /** @use HasFactory<UserFactory> */
    use HasApiTokens, HasFactory, Notifiable;

    /** Same defaults as the table, so a just-created user can be shown without reloading. */
    protected $attributes = [
        'city' => null,
        'points' => 0,
        'groove_days' => 0,
        'best_groove' => 0,
        'daily_goal_minutes' => 10,
        'rest_notes' => 0,
        'songs_learned' => 0,
        'lessons_done' => 0,
        'deletion_requested_at' => null,
        'apple_id' => null,
        'google_id' => null,
        'pro_until' => null,
        'pro_original_transaction_id' => null,
    ];

    protected function casts(): array
    {
        return [
            'email' => 'encrypted',
            'city' => 'encrypted',
            'deletion_reason' => 'encrypted',
            'deletion_requested_at' => 'datetime',
            'pro_until' => 'datetime',
            'password' => 'hashed',
        ];
    }

    /** Lookup key for an email: HMAC so the stored value can't be reversed or matched without the key. */
    public static function hashEmail(string $email): string
    {
        return hash_hmac('sha256', mb_strtolower(trim($email)), (string) config('ochords.lookup_key'));
    }

    /** @param Builder<User> $query */
    public function scopeWhereEmail(Builder $query, string $email): void
    {
        $query->where('email_hash', self::hashEmail($email));
    }

    /**
     * Keeps the lookup hash in step with the email; encrypts the same way as the `encrypted` cast.
     *
     * @return Attribute<never, string>
     */
    protected function email(): Attribute
    {
        return Attribute::make(
            set: fn (string $value) => [
                'email' => Crypt::encryptString(trim($value)),
                'email_hash' => self::hashEmail($value),
            ],
        );
    }

    public function tier(): Tier
    {
        return Tier::forPoints($this->points);
    }

    /** O Chords Pro, as of the last subscription sync from the app. */
    public function isPro(): bool
    {
        return $this->pro_until !== null && $this->pro_until->isFuture();
    }

    /** @return HasMany<Song, $this> */
    public function songs(): HasMany
    {
        return $this->hasMany(Song::class);
    }

    /**
     * Other players' sheets this user saved.
     *
     * @return BelongsToMany<Song, $this>
     */
    public function savedSongs(): BelongsToMany
    {
        return $this->belongsToMany(Song::class)->withPivot('rating')->withTimestamps();
    }

    /** @return HasMany<Setlist, $this> */
    public function setlists(): HasMany
    {
        return $this->hasMany(Setlist::class);
    }

    /** @return HasMany<AppNotification, $this> */
    public function appNotifications(): HasMany
    {
        return $this->hasMany(AppNotification::class);
    }

    /** @return HasMany<PracticeDay, $this> */
    public function practiceDays(): HasMany
    {
        return $this->hasMany(PracticeDay::class);
    }

    /** @return BelongsToMany<User, $this> */
    public function followers(): BelongsToMany
    {
        return $this->belongsToMany(self::class, 'follows', 'followee_id', 'follower_id')->withTimestamps();
    }

    /** @return HasMany<Report, $this> */
    public function reports(): HasMany
    {
        return $this->hasMany(Report::class);
    }

    /** @return HasMany<Feedback, $this> */
    public function feedback(): HasMany
    {
        return $this->hasMany(Feedback::class);
    }

    /** @return BelongsToMany<LearnFret, $this> */
    public function completedFrets(): BelongsToMany
    {
        return $this->belongsToMany(LearnFret::class, 'fret_progress')->withPivot('stars')->withTimestamps();
    }
}
