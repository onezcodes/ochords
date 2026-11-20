import './styles.css';

/** Toggle .is-scrolled on the nav when the page is scrolled past 8px. */
export function setupNavScroll(nav, win = window) {
  if (!nav) return;
  const onScroll = () => nav.classList.toggle('is-scrolled', win.scrollY > 8);
  onScroll();
  win.addEventListener('scroll', onScroll, { passive: true });
  return onScroll;
}

/** Wire the hamburger button to open/close the mobile nav. */
export function setupMobileMenu(nav, toggle) {
  if (!toggle || !nav) return;
  toggle.addEventListener('click', () => {
    const open = nav.classList.toggle('is-open');
    toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
  });
}

/**
 * Reveal-on-scroll for `.reveal` elements.
 * Falls back to immediately showing them when IntersectionObserver is unavailable.
 */
export function initReveal(root = document, win = window) {
  const reveals = root.querySelectorAll('.reveal');
  if (reveals.length && 'IntersectionObserver' in win) {
    const io = new win.IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            io.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.16, rootMargin: '0px 0px -8% 0px' },
    );
    reveals.forEach((el) => io.observe(el));
  } else {
    reveals.forEach((el) => el.classList.add('is-visible'));
  }
}

/** Apply store URLs from API JSON onto `[data-store]` buttons. */
export function applyStoreLinks(data, root = document) {
  if (!data || typeof data !== 'object') return;
  const apply = (key, label) => {
    const url = typeof data[key] === 'string' ? data[key].trim() : '';
    if (!url) return;
    root.querySelectorAll(`[data-store="${key === 'appStore' ? 'apple' : 'google'}"]`).forEach((a) => {
      a.setAttribute('href', url);
      a.removeAttribute('aria-disabled');
      a.setAttribute('rel', 'noopener noreferrer');
      a.setAttribute('target', '_blank');
      const eye = a.querySelector('[data-store-eyebrow]');
      if (eye) eye.textContent = label;
    });
  };
  apply('appStore', 'Download on the');
  apply('playStore', 'Get it on');
}

/** Fetch live store URLs and apply them to the page. */
export function loadStoreLinks(fetchImpl = fetch, root = document) {
  return fetchImpl('/marketing/store-links.json', { credentials: 'omit' })
    .then((r) => (r.ok ? r.json() : null))
    .then((data) => {
      applyStoreLinks(data, root);
    })
    .catch(() => {});
}

/** Bootstrap all marketing-page interactions. */
export function init(doc = document, win = window) {
  const nav = doc.querySelector('.nav');
  const toggle = doc.querySelector('.nav-toggle');
  setupNavScroll(nav, win);
  setupMobileMenu(nav, toggle);
  initReveal(doc, win);
  loadStoreLinks(typeof win.fetch === 'function' ? win.fetch.bind(win) : fetch, doc);
}

// Live site entry: skip auto-init under Vitest so unit tests control the DOM.
if (import.meta.env.MODE !== 'test') {
  init();
}
