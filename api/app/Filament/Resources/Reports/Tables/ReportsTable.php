<?php

namespace App\Filament\Resources\Reports\Tables;

use App\Enums\AdminPermission;
use App\Enums\ReportReason;
use App\Enums\ReportResolution;
use App\Models\Report;
use App\Models\User;
use App\Services\ModerationService;
use Filament\Actions\Action;
use Filament\Actions\BulkAction;
use Filament\Actions\BulkActionGroup;
use Filament\Actions\ViewAction;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Table;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Collection;
use Illuminate\Support\Facades\DB;

class ReportsTable
{
    public static function configure(Table $table): Table
    {
        return $table
            ->modifyQueryUsing(function (Builder $query): Builder {
                $perSong = Report::query()->open()
                    ->select('song_id', DB::raw('count(*) as total'))
                    ->groupBy('song_id');

                return $query
                    ->with([
                        'song.owner',
                        'reporter' => fn ($q) => $q->withCount('reports'),
                    ])
                    ->leftJoinSub($perSong, 'song_report_totals', fn ($join) => $join->on('reports.song_id', '=', 'song_report_totals.song_id'))
                    ->addSelect('reports.*', DB::raw('COALESCE(song_report_totals.total, 0) as song_reports_count'));
            })
            ->defaultSort('created_at')
            ->columns([
                TextColumn::make('id')->label('ID')->sortable(),
                TextColumn::make('song.title')->label('Sheet')->searchable()->placeholder('—'),
                TextColumn::make('song_id')->label('Song ID')->toggleable(isToggledHiddenByDefault: true)->copyable(),
                TextColumn::make('reason')->badge()->formatStateUsing(fn (ReportReason $state): string => $state->value),
                TextColumn::make('reporter.handle')->label('Reporter')->placeholder('—'),
                TextColumn::make('reporter.reports_count')
                    ->label('Reporter total')
                    ->numeric()
                    ->alignEnd()
                    ->placeholder('—')
                    ->toggleable(),
                TextColumn::make('song_reports_count')->label('Open on song')->numeric()->alignEnd(),
                TextColumn::make('resolution')->badge()->placeholder('open'),
                TextColumn::make('created_at')->dateTime()->sortable(),
                TextColumn::make('resolved_at')->dateTime()->placeholder('—')->toggleable(),
            ])
            ->filters([
                SelectFilter::make('status')
                    ->options([
                        'open' => 'Open',
                        'resolved' => 'Resolved',
                    ])
                    ->default('open')
                    ->query(function (Builder $query, array $data): Builder {
                        return match ($data['value'] ?? null) {
                            'resolved' => $query->whereNotNull('resolved_at'),
                            'open', null => $query->whereNull('resolved_at'),
                            default => $query,
                        };
                    }),
                SelectFilter::make('reason')->options(collect(ReportReason::cases())->mapWithKeys(
                    fn (ReportReason $r) => [$r->value => $r->value]
                )->all()),
            ])
            ->recordActions([
                ViewAction::make(),
                Action::make('dismiss')
                    ->label('Dismiss')
                    ->color('gray')
                    ->requiresConfirmation()
                    ->visible(fn (Report $record): bool => $record->resolved_at === null && self::canResolve())
                    ->action(fn (Report $record, ModerationService $moderation) => $moderation->resolve($record, ReportResolution::Dismissed)),
                Action::make('fixed')
                    ->label('Fixed')
                    ->color('success')
                    ->requiresConfirmation()
                    ->visible(fn (Report $record): bool => $record->resolved_at === null && self::canResolve())
                    ->action(fn (Report $record, ModerationService $moderation) => $moderation->resolve($record, ReportResolution::Fixed)),
                Action::make('hide')
                    ->label('Hide sheet')
                    ->color('danger')
                    ->requiresConfirmation()
                    ->visible(fn (Report $record): bool => $record->resolved_at === null && self::canResolve())
                    ->action(fn (Report $record, ModerationService $moderation) => $moderation->resolve($record, ReportResolution::Hidden)),
            ])
            ->toolbarActions([
                BulkActionGroup::make([
                    BulkAction::make('hideSelected')
                        ->label('Hide sheets')
                        ->color('danger')
                        ->requiresConfirmation()
                        ->visible(fn (): bool => self::canResolve())
                        ->action(function (Collection $records, ModerationService $moderation): void {
                            $records->each(function (Report $report) use ($moderation): void {
                                if ($report->resolved_at === null) {
                                    $moderation->resolve($report, ReportResolution::Hidden);
                                }
                            });
                        }),
                ]),
            ]);
    }

    private static function canResolve(): bool
    {
        $actor = auth()->user();

        return $actor instanceof User && ($actor->isSuperAdmin() || $actor->can(AdminPermission::ReportsResolve->value));
    }
}
