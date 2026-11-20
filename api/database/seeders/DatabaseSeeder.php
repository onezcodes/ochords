<?php

namespace Database\Seeders;

use Illuminate\Database\Seeder;

class DatabaseSeeder extends Seeder
{
    public function run(): void
    {
        // Staff roles/permissions and the learning path are needed in every environment.
        $this->call(RolesAndPermissionsSeeder::class);
        $this->call(LearnPathSeeder::class);

        if (app()->isProduction()) {
            return;
        }
        $this->call(StaffSeeder::class);
        $this->call(DemoSeeder::class);
    }
}
