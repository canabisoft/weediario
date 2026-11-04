// Passo depois do `ng build` (Dockerfile): gera o sitemap.xml das páginas pré-renderizadas e a CSP com o hash do
// importmap inline que o Angular escreve para a verificação de integridade (SRI) dos chunks.
//
//   node scripts/postbuild.mjs
//
// O build falha se aparecer qualquer outro <script> inline executável: a CSP continua sem 'unsafe-inline'.
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const SITE_URL = 'https://weediario.com';
const DIST = 'dist/frontend';
const BROWSER = join(DIST, 'browser');
const CSP_TEMPLATE = 'nginx/security-headers.conf';
const CSP_OUT = join(DIST, 'nginx', 'security-headers.conf');
// Tipos que o navegador não executa: dados, não código (JSON-LD e o estado do Angular).
const DATA_TYPES = new Set(['application/ld+json', 'application/json']);

function htmlFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? htmlFiles(path) : name.endsWith('.html') ? [path] : [];
  });
}

// Sitemap: só o que o Angular pré-renderizou, ou seja, as páginas públicas (app.routes.server.ts). Nada de usuário.
const { routes } = JSON.parse(readFileSync(join(DIST, 'prerendered-routes.json'), 'utf8'));
const lastmod = new Date().toISOString().slice(0, 10);
const urls = Object.keys(routes)
  .sort()
  .map(
    (route) =>
      `  <url>\n    <loc>${SITE_URL}${route}</loc>\n    <lastmod>${lastmod}</lastmod>\n  </url>`,
  );
writeFileSync(
  join(BROWSER, 'sitemap.xml'),
  `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls.join('\n')}\n</urlset>\n`,
);

// CSP: hash de cada importmap inline distinto; qualquer outro script inline executável derruba o build.
const hashes = new Set();
for (const file of htmlFiles(BROWSER)) {
  const html = readFileSync(file, 'utf8');
  for (const [, attributes, body] of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/g)) {
    if (/\bsrc=/.test(attributes)) {
      continue;
    }
    const type = /\btype="([^"]*)"/.exec(attributes)?.[1] ?? '';
    if (type === 'importmap') {
      hashes.add(`'sha256-${createHash('sha256').update(body, 'utf8').digest('base64')}'`);
    } else if (!DATA_TYPES.has(type)) {
      console.error(
        `Script inline executável em ${file}: a CSP não tem 'unsafe-inline'. Mova-o para um arquivo .js.`,
      );
      process.exit(1);
    }
  }
}
const template = readFileSync(CSP_TEMPLATE, 'utf8');
if (!template.includes("script-src 'self';")) {
  console.error(`${CSP_TEMPLATE} não tem "script-src 'self';" para receber os hashes.`);
  process.exit(1);
}
mkdirSync(join(DIST, 'nginx'), { recursive: true });
writeFileSync(
  CSP_OUT,
  template.replace("script-src 'self';", `script-src 'self' ${[...hashes].join(' ')};`),
);

console.log(`sitemap.xml: ${urls.length} páginas · CSP: ${hashes.size} hash(es) de importmap`);
