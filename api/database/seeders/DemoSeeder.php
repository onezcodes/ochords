<?php

namespace Database\Seeders;

use App\Enums\NotificationCategory;
use App\Enums\SongSource;
use App\Models\AppNotification;
use App\Models\PracticeDay;
use App\Models\Setlist;
use App\Models\Song;
use App\Models\User;
use Illuminate\Database\Seeder;

/**
 * Synthetic demo content matching the iOS app's MockData, for local development only.
 * Sign in as demo@ochords.test with DEMO_PASSWORD (default "Chords2026").
 */
class DemoSeeder extends Seeder
{
    /** Chords of the original key (C) and their simplified-version equivalents (G). */
    private const TO_G = ['C' => 'G', 'G/B' => 'D/F#', 'Am' => 'Em', 'F' => 'C', 'G' => 'D'];

    public function run(): void
    {
        $password = (string) config('ochords.demo_password');

        $me = User::create(['name' => '[Your name]', 'handle' => 'you', 'email' => 'demo@ochords.test', 'password' => $password, 'city' => 'Yangon']);
        $me->forceFill(['points' => 1240, 'groove_days' => 7, 'best_groove' => 12, 'daily_goal_minutes' => 10, 'rest_notes' => 1, 'songs_learned' => 3, 'lessons_done' => 14])->save();

        $uploader = User::create(['name' => '[username]', 'handle' => 'uploader', 'email' => 'uploader@ochords.test', 'password' => $password, 'city' => '[City]']);
        $uploader->forceFill(['points' => 3400])->save();
        $fans = collect(range(1, 3))->map(fn (int $i) => User::create([
            'name' => "[Player {$i}]", 'handle' => "player{$i}", 'email' => "player{$i}@ochords.test", 'password' => $password,
        ]));
        $uploader->followers()->attach($fans->pluck('id')->push($me->id));

        $this->seedSongs($me, $uploader, $fans->pluck('id')->all());
        $this->seedSetlists($me);
        $this->seedNotifications($me);
        $this->seedPractice($me);
        $me->completedFrets()->attach(['f1' => ['stars' => 3], 'f2' => ['stars' => 3]]);
    }

    /** @param list<int> $fanIds */
    private function seedSongs(User $me, User $uploader, array $fanIds): void
    {
        $original = $this->song($me, 'sinsarpar', 'စဉ်းစားပါ', 'မေ', 'C', $this->sinSarParSections(), [
            'tempo' => 76, 'is_public' => true, 'difficulty' => 'Intermediate', 'plays' => 860,
            'my_note' => 'Slow down on the chorus — let G/B ring.',
        ]);
        $original->savers()->attach(array_slice($fanIds, 0, 2));

        $easy = $this->song($uploader, 'sinsarpar-easy', 'စဉ်းစားပါ', 'မေ', 'G', $this->transposed($this->sinSarParSections()), [
            'tempo' => 76, 'is_public' => true, 'source' => SongSource::Community, 'version_name' => 'Simplified chords',
            'plays' => 1200, 'comments_count' => 7,
        ]);
        $easy->savers()->attach(array_fill_keys([...$fanIds, $me->id], ['rating' => 5]));

        $placeholders = [
            ['c', 'D', true, 'Capo 2', 'Beginner', 340],
            ['d', 'Am', false, 'Fingerstyle', 'Intermediate', 210],
            ['e', 'E', false, '4 chords', 'Beginner', 640],
            ['f', 'F', true, 'Barre chords', 'Advanced', 95],
        ];
        foreach ($placeholders as [$id, $key, $myanmar, $version, $difficulty, $plays]) {
            $this->song($uploader, $id, '[Song title]', '[Artist]', $key, $this->placeholderSections($key), [
                'is_public' => true, 'is_myanmar' => $myanmar, 'source' => SongSource::Community,
                'version_name' => $version, 'difficulty' => $difficulty, 'plays' => $plays,
            ]);
        }
    }

    /**
     * @param  list<array<string, mixed>>  $sections
     * @param  array<string, mixed>  $extra
     */
    private function song(User $owner, string $id, string $title, string $artist, string $key, array $sections, array $extra): Song
    {
        $song = new Song(['id' => $id, 'title' => $title, 'artist' => $artist, 'key' => $key, 'sections' => $sections, 'added_at' => now()]);
        // $extra wins over the defaults (array + keeps the left-hand keys).
        $song->forceFill($extra + ['user_id' => $owner->id, 'is_myanmar' => true, 'source' => SongSource::Imported])->save();

        return $song;
    }

    private function seedSetlists(User $me): void
    {
        foreach ([
            ['s1', '[Setlist name]', 'Sunday set', '[Date]', ['sinsarpar', 'sinsarpar-easy', 'c', 'd']],
            ['s2', '[Setlist name]', 'Acoustic night', null, ['e', 'f']],
        ] as [$id, $name, $note, $date, $songIds]) {
            $setlist = new Setlist(compact('id', 'name', 'note', 'date'));
            $setlist->user_id = $me->id;
            $setlist->save();
            $setlist->syncSongIds($songIds);
        }
    }

    private function seedNotifications(User $me): void
    {
        $rows = [
            [NotificationCategory::Community, '[Aung]', 'saved your sheet စဉ်းစားပါ. 24 players have saved it now.', 'heart.fill', 5, true],
            [NotificationCategory::Community, '[Thiri]', 'suggested a chord fix in Verse 2, bar 3: G → G/B.', 'pencil', 60, true],
            [NotificationCategory::Learning, 'Your groove is at risk.', '10 minutes today keeps day 8 going.', 'waveform', 120, true],
            [NotificationCategory::Community, '[Band leader]', 'invited you to a Jam room · Sunday set (8 songs).', 'music.note', 60 * 24, false],
            [NotificationCategory::Learning, 'New tier!', 'You’re now a Chord Keeper · +40 O Points.', 'shield.fill', 60 * 24 * 2, false],
            [NotificationCategory::System, 'Report update:', 'the sheet you reported was removed. Thanks for keeping O Chords tidy.', 'checkmark', 60 * 24 * 3, false],
            [NotificationCategory::Learning, 'Chord Rush:', 'new best score — 180 → 205.', 'bolt.fill', 60 * 24 * 4, false],
        ];
        foreach ($rows as [$category, $title, $body, $image, $minutesAgo, $unread]) {
            $notification = new AppNotification([
                'category' => $category, 'title' => $title, 'body' => $body, 'system_image' => $image,
                'read_at' => $unread ? null : now(),
            ]);
            $notification->user_id = $me->id;
            $notification->created_at = now()->subMinutes($minutesAgo);
            $notification->save();
        }
    }

    private function seedPractice(User $me): void
    {
        foreach ([12, 8, 15, 10, 4, 11, 6] as $i => $minutes) {
            PracticeDay::create(['user_id' => $me->id, 'day' => today()->subDays(6 - $i), 'minutes' => $minutes]);
        }
    }

    /** @return list<array<string, mixed>> */
    private function sinSarParSections(): array
    {
        return [
            $this->section('music', 'INTRO', note: '4 bars', bars: ['C', 'G/B', 'Am', 'F']),
            $this->section('verse', 'VERSE 1', lines: [
                '[C]ငါ မကြားချင် ခဲ့သော စကား [G/B]မသိချင် ခဲ့ဆုံး [Am]အကြောင်းအရာ နင်ဟာ [F]ရက်စက်စွာ',
                'ပြောခဲ့ပြီ အခုတော့ ငါ့မှာ [C]အိပ်မက်တွေ ပြိုလွင့်ခဲ့ [G/B]ဆောက်တည်ရာမဲ့ [Am]မျက်ရည်',
                '[F]နေနိုင်လွန်းလိုက်တာ မင်းတစက်တောင် လှည့်မကြည့်တော့တာ',
            ]),
            $this->section('chorus', 'CHORUS', lines: [
                '[C]လမ်းခွဲဖို့အတွက် မင်းဘက်က [G/B]အကြောင်းပြချက်တို့ သိပ်လွယ်ပ [Am]ငါ့ဘက်ကို',
                'ထည့် မစဉ်းစား [F]အတူတူ [C]ထားရှိခဲ့တဲ့ ကတိတို့က [G/B]အချည်းနှီး မဖြစ်ဖို့ ညှိလို့မရ',
                '[Am]ငါ့ဘက်က အားလုံး ရှုံး[F]နေတာ ဖေး ရေး',
            ]),
            $this->section('verse', 'VERSE 2', lines: [
                '[C]နည်းနည်း စဉ်းစားပေးဦး [G/B]ချစ်သူရာ တို့နှစ်ဦး [Am]ပါတ်သက်ခဲ့တဲ့ နေ့ရက်များ [F]မခွဲဘူး',
                'အမြဲတမ်း ပြောတဲ့စကား [C]ချစ်ခဲ့တုန်းက အကောင်းဆုံး [G/B]အကြောင်းအရာများ',
                'ပြန်ပြီး[Am]လွမ်းတတ်ဖို့ [G]နင်သေချာ စဉ်း[F]စား',
            ]),
            $this->section('music', 'MUSIC', note: 'repeat ×2', bars: ['C', 'G/B', 'Am', 'F']),
            $this->section('verse', 'VERSE 3', lines: [
                'မိုးလင်းထိ [C]ကားလျှောက်မောင်းပြီး သီချင်းအော်ဟစ် ဆိုခဲ့တာတွေ',
                'ဝမ်းနည်းတိုင်း [G/B]ငါ့ရင်ခွင်မှာ ကလေးလို တိုးငိုခဲ့တာတွေ မင်း… [Am]မေ့ပြီး…',
                'ငါမသိ [F]ဖူးခဲ့တဲ့ သူစိမ်းတယောက်လို လှည့်တောင် မကြည့်',
            ]),
            $this->section('flow', 'THEN PLAY', note: 'ends with fade out', flow: [
                ['kind' => 'chorus', 'label' => 'Chorus'], ['kind' => 'verse', 'label' => 'Verse 2'],
                ['kind' => 'music', 'label' => 'Music'], ['kind' => 'chorus', 'label' => 'Chorus ×2'],
                ['kind' => 'music', 'label' => 'Music · fade out'],
            ]),
        ];
    }

    /** @return list<array<string, mixed>> */
    private function placeholderSections(string $key): array
    {
        return [
            $this->section('verse', 'VERSE 1', lines: ["[{$key}][Lyrics line one]", "[{$key}][Lyrics line two]"]),
            $this->section('chorus', 'CHORUS', lines: ["[{$key}][Chorus line]"]),
        ];
    }

    /**
     * @param  list<array<string, mixed>>  $sections
     * @return list<array<string, mixed>>
     */
    private function transposed(array $sections): array
    {
        $chord = fn (string $c) => self::TO_G[$c] ?? $c;

        return array_map(function (array $s) use ($chord) {
            $s['id'] = $s['id'].'-g';
            $s['bars'] = array_map($chord, $s['bars']);
            $s['lines'] = array_map(fn (string $line) => preg_replace_callback(
                '/\[([^\]]+)\]/u',
                fn (array $m) => '['.$chord($m[1]).']',
                $line,
            ), $s['lines']);

            return $s;
        }, $sections);
    }

    /**
     * @param  list<string>  $lines
     * @param  list<string>  $bars
     * @param  list<array{kind: string, label: string}>  $flow
     * @return array<string, mixed>
     */
    private function section(string $kind, string $label, ?string $note = null, array $lines = [], array $bars = [], array $flow = []): array
    {
        $id = strtolower(str_replace(' ', '-', $label));

        return compact('id', 'kind', 'label', 'note', 'lines', 'bars', 'flow');
    }
}
