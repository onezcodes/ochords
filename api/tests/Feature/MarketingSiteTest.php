<?php

namespace Tests\Feature;

use Tests\TestCase;

class MarketingSiteTest extends TestCase
{
    public function test_home_serves_marketing_html(): void
    {
        $this->assertFileExists(public_path('marketing/index.html'));

        $response = $this->get('/');
        $response->assertOk();
        $response->assertHeader('Content-Type', 'text/html; charset=UTF-8');

        $html = file_get_contents(public_path('marketing/index.html'));
        $this->assertNotFalse($html);
        $this->assertStringContainsString('O Chords', $html);
        $this->assertStringContainsString('Chord sheets that keep up with you', $html);
    }

    public function test_privacy_and_support_pages(): void
    {
        $this->get('/privacy')->assertOk();
        $this->get('/support')->assertOk();

        $this->assertFileExists(public_path('marketing/privacy.html'));
        $this->assertFileExists(public_path('marketing/support.html'));
        $this->assertStringContainsString('Privacy', (string) file_get_contents(public_path('marketing/privacy.html')));
        $this->assertStringContainsString('Support', (string) file_get_contents(public_path('marketing/support.html')));
    }

    public function test_marketing_brand_assets_are_built(): void
    {
        $this->assertFileExists(public_path('marketing/brand/o-chords-mark.svg'));
        $this->assertFileExists(public_path('marketing/assets'));
    }

    public function test_sitemap_is_served(): void
    {
        $this->assertFileExists(public_path('marketing/sitemap.xml'));
        $this->get('/sitemap.xml')->assertOk();
    }

    public function test_store_links_json_exposes_configured_urls(): void
    {
        config([
            'ochords.marketing.app_store_url' => 'https://apps.apple.com/app/id123',
            'ochords.marketing.play_store_url' => '',
        ]);

        $this->getJson('/marketing/store-links.json')
            ->assertOk()
            ->assertJsonPath('appStore', 'https://apps.apple.com/app/id123')
            ->assertJsonPath('playStore', '');
    }
}
