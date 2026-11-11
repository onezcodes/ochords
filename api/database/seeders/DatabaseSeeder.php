<?php

namespace Database\Seeders;

use Illuminate\Database\Seeder;

class DatabaseSeeder extends Seeder
{
    public function run(): void
    {
        // The learning path is content every environment needs.
        $this->call(LearnPathSeeder::class);

        if (app()->isProduction()) {
            return;
        }
        $this->call(DemoSeeder::class);
    }
}
