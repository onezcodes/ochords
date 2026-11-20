import './styles.css';

const nav = document.querySelector('.nav');
const toggle = document.querySelector('.nav-toggle');

if (nav) {
  const onScroll = () => nav.classList.toggle('is-scrolled', window.scrollY > 8);
  onScroll();
  window.addEventListener('scroll', onScroll, { passive: true });
}

if (toggle && nav) {
  toggle.addEventListener('click', () => {
    const open = nav.classList.toggle('is-open');
    toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
  });
}

const reveals = document.querySelectorAll('.reveal');
if (reveals.length && 'IntersectionObserver' in window) {
  const io = new IntersectionObserver(
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

/** Live store URLs from the API when configured (OCHORDS_APP_STORE_URL / OCHORDS_PLAY_STORE_URL). */
fetch('/marketing/store-links.json', { credentials: 'omit' })
  .then((r) => (r.ok ? r.json() : null))
  .then((data) => {
    if (!data || typeof data !== 'object') return;
    const apply = (key, label) => {
      const url = typeof data[key] === 'string' ? data[key].trim() : '';
      if (!url) return;
      document.querySelectorAll(`[data-store="${key === 'appStore' ? 'apple' : 'google'}"]`).forEach((a) => {
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
  })
  .catch(() => {});
