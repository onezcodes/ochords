import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  applyStoreLinks,
  initReveal,
  loadStoreLinks,
  setupMobileMenu,
  setupNavScroll,
} from './main.js';

function storeButtonsHtml() {
  return `
    <a class="store" data-store="apple" href="#download" aria-disabled="true">
      <span>
        <small data-store-eyebrow>Coming soon on the</small>
        <strong>App Store</strong>
      </span>
    </a>
    <a class="store" data-store="google" href="#download" aria-disabled="true">
      <span>
        <small data-store-eyebrow>Coming soon on</small>
        <strong>Google Play</strong>
      </span>
    </a>
  `;
}

describe('setupMobileMenu', () => {
  let nav;
  let toggle;

  beforeEach(() => {
    document.body.innerHTML = `
      <header class="nav"></header>
      <button class="nav-toggle" type="button" aria-expanded="false"></button>
    `;
    nav = document.querySelector('.nav');
    toggle = document.querySelector('.nav-toggle');
    setupMobileMenu(nav, toggle);
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('toggles is-open on .nav and aria-expanded on the button', () => {
    expect(nav.classList.contains('is-open')).toBe(false);
    expect(toggle.getAttribute('aria-expanded')).toBe('false');

    toggle.click();
    expect(nav.classList.contains('is-open')).toBe(true);
    expect(toggle.getAttribute('aria-expanded')).toBe('true');

    toggle.click();
    expect(nav.classList.contains('is-open')).toBe(false);
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
  });
});

describe('setupNavScroll', () => {
  let nav;
  let win;

  beforeEach(() => {
    document.body.innerHTML = `<header class="nav"></header>`;
    nav = document.querySelector('.nav');
    win = {
      scrollY: 0,
      addEventListener: vi.fn(),
    };
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('adds is-scrolled when scrollY > 8', () => {
    setupNavScroll(nav, win);
    expect(nav.classList.contains('is-scrolled')).toBe(false);

    const onScroll = win.addEventListener.mock.calls.find(([type]) => type === 'scroll')?.[1];
    expect(onScroll).toBeTypeOf('function');

    win.scrollY = 9;
    onScroll();
    expect(nav.classList.contains('is-scrolled')).toBe(true);
  });

  it('removes is-scrolled when scrollY is 8 or less', () => {
    win.scrollY = 20;
    setupNavScroll(nav, win);
    expect(nav.classList.contains('is-scrolled')).toBe(true);

    const onScroll = win.addEventListener.mock.calls.find(([type]) => type === 'scroll')?.[1];
    win.scrollY = 8;
    onScroll();
    expect(nav.classList.contains('is-scrolled')).toBe(false);
  });
});

describe('applyStoreLinks / loadStoreLinks', () => {
  beforeEach(() => {
    document.body.innerHTML = storeButtonsHtml();
  });

  afterEach(() => {
    document.body.innerHTML = '';
    vi.restoreAllMocks();
  });

  function apple() {
    return document.querySelector('[data-store="apple"]');
  }

  function google() {
    return document.querySelector('[data-store="google"]');
  }

  it('applies both store URLs: href, target, and removes aria-disabled', () => {
    applyStoreLinks(
      {
        appStore: 'https://apps.apple.com/app/ochords',
        playStore: 'https://play.google.com/store/apps/details?id=com.ochords',
      },
      document,
    );

    expect(apple().getAttribute('href')).toBe('https://apps.apple.com/app/ochords');
    expect(apple().hasAttribute('aria-disabled')).toBe(false);
    expect(apple().getAttribute('target')).toBe('_blank');
    expect(apple().getAttribute('rel')).toBe('noopener noreferrer');
    expect(apple().querySelector('[data-store-eyebrow]').textContent).toBe('Download on the');

    expect(google().getAttribute('href')).toBe(
      'https://play.google.com/store/apps/details?id=com.ochords',
    );
    expect(google().hasAttribute('aria-disabled')).toBe(false);
    expect(google().getAttribute('target')).toBe('_blank');
    expect(google().querySelector('[data-store-eyebrow]').textContent).toBe('Get it on');
  });

  it('leaves buttons unchanged for empty, invalid, or null data', () => {
    const before = {
      appleHref: apple().getAttribute('href'),
      googleHref: google().getAttribute('href'),
      appleDisabled: apple().getAttribute('aria-disabled'),
      googleDisabled: google().getAttribute('aria-disabled'),
    };

    applyStoreLinks(null, document);
    applyStoreLinks({}, document);
    applyStoreLinks({ appStore: '   ', playStore: 42 }, document);
    applyStoreLinks('not-an-object', document);

    expect(apple().getAttribute('href')).toBe(before.appleHref);
    expect(google().getAttribute('href')).toBe(before.googleHref);
    expect(apple().getAttribute('aria-disabled')).toBe(before.appleDisabled);
    expect(google().getAttribute('aria-disabled')).toBe(before.googleDisabled);
  });

  it('updates Apple only when playStore is missing', () => {
    applyStoreLinks({ appStore: 'https://apps.apple.com/app/ochords' }, document);

    expect(apple().getAttribute('href')).toBe('https://apps.apple.com/app/ochords');
    expect(apple().hasAttribute('aria-disabled')).toBe(false);
    expect(apple().getAttribute('target')).toBe('_blank');

    expect(google().getAttribute('href')).toBe('#download');
    expect(google().getAttribute('aria-disabled')).toBe('true');
    expect(google().hasAttribute('target')).toBe(false);
  });

  it('updates Google only when appStore is missing', () => {
    applyStoreLinks(
      { playStore: 'https://play.google.com/store/apps/details?id=com.ochords' },
      document,
    );

    expect(google().getAttribute('href')).toBe(
      'https://play.google.com/store/apps/details?id=com.ochords',
    );
    expect(google().hasAttribute('aria-disabled')).toBe(false);
    expect(google().getAttribute('target')).toBe('_blank');

    expect(apple().getAttribute('href')).toBe('#download');
    expect(apple().getAttribute('aria-disabled')).toBe('true');
    expect(apple().hasAttribute('target')).toBe(false);
  });

  it('applies URLs from a successful fetch', async () => {
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        appStore: 'https://apps.apple.com/app/ochords',
        playStore: 'https://play.google.com/store/apps/details?id=com.ochords',
      }),
    });

    await loadStoreLinks(fetchImpl, document);

    expect(fetchImpl).toHaveBeenCalledWith('/marketing/store-links.json', {
      credentials: 'omit',
    });
    expect(apple().getAttribute('href')).toBe('https://apps.apple.com/app/ochords');
    expect(google().getAttribute('href')).toBe(
      'https://play.google.com/store/apps/details?id=com.ochords',
    );
    expect(apple().hasAttribute('aria-disabled')).toBe(false);
    expect(google().hasAttribute('aria-disabled')).toBe(false);
    expect(apple().getAttribute('target')).toBe('_blank');
    expect(google().getAttribute('target')).toBe('_blank');
  });

  it('leaves buttons unchanged when fetch fails or returns non-ok', async () => {
    await loadStoreLinks(vi.fn().mockRejectedValue(new Error('network')), document);
    expect(apple().getAttribute('href')).toBe('#download');
    expect(apple().getAttribute('aria-disabled')).toBe('true');

    document.body.innerHTML = storeButtonsHtml();
    await loadStoreLinks(
      vi.fn().mockResolvedValue({ ok: false, json: async () => ({}) }),
      document,
    );
    expect(google().getAttribute('href')).toBe('#download');
    expect(google().getAttribute('aria-disabled')).toBe('true');
  });
});

describe('initReveal', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('marks .reveal elements is-visible immediately when IntersectionObserver is missing', () => {
    document.body.innerHTML = `
      <div class="reveal"></div>
      <div class="reveal"></div>
    `;
    const win = {};
    initReveal(document, win);

    document.querySelectorAll('.reveal').forEach((el) => {
      expect(el.classList.contains('is-visible')).toBe(true);
    });
  });
});
