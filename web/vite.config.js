import { resolve } from 'node:path';
import { defineConfig } from 'vite';

export default defineConfig(({ command }) => {
  const prod = command === 'build';
  const base = prod ? '/marketing/' : '/';

  return {
    root: '.',
    base,
    publicDir: 'public',
    build: {
      outDir: '../api/public/marketing',
      emptyOutDir: true,
      rollupOptions: {
        input: {
          main: resolve(__dirname, 'index.html'),
          privacy: resolve(__dirname, 'privacy.html'),
          support: resolve(__dirname, 'support.html'),
        },
      },
    },
    server: {
      port: 5174,
      open: true,
    },
    plugins: [
      {
        name: 'ochords-html-paths',
        transformIndexHtml(html) {
          // Brand assets live under public/brand; in production they ship at /marketing/brand.
          let out = html.replaceAll('="/brand/', `="${base}brand/`);
          return out;
        },
      },
    ],
  };
});
