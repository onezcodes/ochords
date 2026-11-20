<?php

namespace App\Filament\Resources\Feedback\Tables;

use App\Enums\AdminPermission;
use App\Enums\FeedbackKind;
use App\Models\Feedback;
use App\Models\User;
use App\Services\AdminAuditService;
use Filament\Actions\Action;
use Filament\Actions\BulkAction;
use Filament\Actions\BulkActionGroup;
use Filament\Actions\ViewAction;
use Filament\Tables\Columns\IconColumn;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Filters\TernaryFilter;
use Filament\Tables\Table;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Collection;

class FeedbackTable
{
    public static function configure(Table $table): Table
    {
        return $table
            ->modifyQueryUsing(fn (Builder $query): Builder => $query->with('user'))
            ->defaultSort('created_at', 'desc')
            ->columns([
                TextColumn::make('id')->label('ID')->sortable(),
                TextColumn::make('kind')->badge()->formatStateUsing(fn (FeedbackKind $state): string => $state->value),
                TextColumn::make('message')->limit(60)->wrap(),
                TextColumn::make('user.handle')->label('User')->placeholder('guest'),
                IconColumn::make('reviewed_at')->label('Reviewed')->boolean()
                    ->getStateUsing(fn (Feedback $record): bool => $record->reviewed_at !== null),
                TextColumn::make('created_at')->dateTime()->sortable(),
            ])
            ->filters([
                TernaryFilter::make('reviewed')
                    ->label('Reviewed')
                    ->nullable()
                    ->trueLabel('Reviewed')
                    ->falseLabel('Unreviewed')
                    ->queries(
                        true: fn (Builder $query) => $query->whereNotNull('reviewed_at'),
                        false: fn (Builder $query) => $query->whereNull('reviewed_at'),
                        blank: fn (Builder $query) => $query,
                    )
                    ->default(false),
                SelectFilter::make('kind')->options(collect(FeedbackKind::cases())->mapWithKeys(
                    fn (FeedbackKind $k) => [$k->value => $k->value]
                )->all()),
            ])
            ->recordActions([
                ViewAction::make(),
                Action::make('markReviewed')
                    ->label('Mark reviewed')
                    ->icon('heroicon-o-check')
                    ->visible(fn (Feedback $record): bool => $record->reviewed_at === null && self::canReview())
                    ->action(function (Feedback $record, AdminAuditService $audit): void {
                        $record->forceFill(['reviewed_at' => now()])->save();
                        $audit->log('feedback.reviewed', $record);
                    }),
                Action::make('markUnreviewed')
                    ->label('Mark unreviewed')
                    ->color('gray')
                    ->visible(fn (Feedback $record): bool => $record->reviewed_at !== null && self::canReview())
                    ->action(fn (Feedback $record) => $record->forceFill(['reviewed_at' => null])->save()),
            ])
            ->toolbarActions([
                BulkActionGroup::make([
                    BulkAction::make('markReviewed')
                        ->label('Mark reviewed')
                        ->icon('heroicon-o-check')
                        ->visible(fn (): bool => self::canReview())
                        ->action(function (Collection $records, AdminAuditService $audit): void {
                            $records->each(function (Feedback $feedback) use ($audit): void {
                                if ($feedback->reviewed_at === null) {
                                    $feedback->forceFill(['reviewed_at' => now()])->save();
                                    $audit->log('feedback.reviewed', $feedback);
                                }
                            });
                        }),
                ]),
            ]);
    }

    private static function canReview(): bool
    {
        $actor = auth()->user();

        return $actor instanceof User && ($actor->isSuperAdmin() || $actor->can(AdminPermission::FeedbackReview->value));
    }
}
