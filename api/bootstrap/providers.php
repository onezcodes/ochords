<?php

use App\Providers\AppServiceProvider;
use App\Providers\Filament\AdminPanelProvider;

return [
    AppServiceProvider::class,
    AdminPanelProvider::class,
    // Telescope is require-dev; AppServiceProvider registers it only when the package is installed.
];
