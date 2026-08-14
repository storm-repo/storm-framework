#!/usr/bin/env node
//
// Link checker for the built site (website/build).
//
// Docusaurus already fails the build on a broken Markdown link (onBrokenLinks:
// 'throw'), but that check only sees links it routed itself. The hand-built
// pages — the landing page, /quickstart, /comparison, /benchmarks, the
// tutorials and the blog — are rendered from raw HTML strings, so their links
// are invisible to it. This walks the emitted HTML instead, which sees every
// link on every page regardless of how it got there.
//
// Usage:
//   node scripts/check-links.mjs              # internal links + fragments
//   node scripts/check-links.mjs --external   # also probe external URLs
//
// Exits non-zero when anything is broken.

import {readFileSync, readdirSync, statSync, existsSync} from 'node:fs';
import {join, resolve, dirname, posix} from 'node:path';

const BUILD = resolve(process.cwd(), 'build');
const CHECK_EXTERNAL = process.argv.includes('--external');

if (!existsSync(BUILD)) {
  console.error('No build/ directory. Run `npm run build` first.');
  process.exit(1);
}

// Frozen release snapshots are near-identical copies of the live docs; their
// internal links are already validated by the Docusaurus build, and walking all
// fourteen of them multiplies the run for no new information.
const VERSIONED_DOCS = /^docs\/\d+\.\d+\.\d+\//;
// Generated API trees (Javadoc/KDoc). They are copied in by the deploy workflow
// and are absent from a local build, so their links are not ours to validate.
const GENERATED = /^api\//;

/** Every .html file in the build, as build-relative posix paths. */
function htmlFiles(dir, base = '') {
  const out = [];
  for (const name of readdirSync(dir)) {
    const abs = join(dir, name);
    const rel = base ? posix.join(base, name) : name;
    if (statSync(abs).isDirectory()) {
      if (GENERATED.test(rel + '/')) continue;
      out.push(...htmlFiles(abs, rel));
    } else if (name.endsWith('.html')) {
      out.push(rel);
    }
  }
  return out;
}

const pages = htmlFiles(BUILD).filter(
  (p) => !VERSIONED_DOCS.test(p) && !GENERATED.test(p)
);

/** Anchor ids available on a built page, cached per file. */
const idCache = new Map();
function idsOf(relFile) {
  if (idCache.has(relFile)) return idCache.get(relFile);
  let ids = new Set();
  const abs = join(BUILD, relFile);
  if (existsSync(abs)) {
    const html = readFileSync(abs, 'utf8');
    for (const m of html.matchAll(/\sid="([^"]+)"/g)) ids.add(m[1]);
    for (const m of html.matchAll(/\sname="([^"]+)"/g)) ids.add(m[1]);
  }
  idCache.set(relFile, ids);
  return ids;
}

/**
 * Maps a site path to the built file that serves it. Docusaurus emits either
 * `<path>.html` or `<path>/index.html`; static assets are served verbatim.
 */
function resolveTarget(pathname) {
  const clean = pathname.replace(/^\/+/, '') || 'index.html';
  const candidates = clean.endsWith('.html')
    ? [clean]
    : clean.endsWith('/')
      ? [clean + 'index.html']
      : [clean, clean + '/index.html', clean + '.html'];
  for (const c of candidates) {
    const abs = join(BUILD, c);
    if (existsSync(abs) && statSync(abs).isFile()) return c;
  }
  return null;
}

const broken = [];
const externals = new Map(); // url -> Set(pages)

for (const page of pages) {
  const html = readFileSync(join(BUILD, page), 'utf8');
  const pageDir = dirname('/' + page);
  const seen = new Set();

  // Connection hints (preconnect / dns-prefetch) name an origin to warm up, not
  // a document to fetch. Probing them reports a 404 for a tag that is doing
  // exactly its job, so drop those tags before extracting links.
  const linkable = html.replace(
    /<link\b[^>]*\brel="(?:preconnect|dns-prefetch)"[^>]*>/gi,
    ''
  );

  for (const m of linkable.matchAll(/\s(?:href|src)="([^"]*)"/g)) {
    const raw = m[1].trim();
    if (!raw || seen.has(raw)) continue;
    seen.add(raw);

    if (/^(mailto:|tel:|javascript:|data:)/i.test(raw)) continue;
    if (raw.startsWith('#')) {
      // Same-page fragment.
      const id = decodeURIComponent(raw.slice(1));
      if (id && !idsOf(page).has(id)) {
        broken.push(`${page}  ->  ${raw}  (no such id on this page)`);
      }
      continue;
    }
    if (/^https?:\/\//i.test(raw) || raw.startsWith('//')) {
      const url = raw.startsWith('//') ? 'https:' + raw : raw;
      // orm.st absolute links are our own pages; check them as internal.
      const self = url.match(/^https?:\/\/orm\.st(\/.*)?$/i);
      if (self) {
        const [p, frag] = (self[1] || '/').split('#');
        checkInternal(page, p, frag, raw);
      } else {
        if (!externals.has(url)) externals.set(url, new Set());
        externals.get(url).add(page);
      }
      continue;
    }

    // Root-relative or document-relative.
    const [pathPart, frag] = raw.split('#');
    const pathname = pathPart.startsWith('/')
      ? pathPart
      : posix.normalize(posix.join(pageDir, pathPart || '.'));
    checkInternal(page, pathname, frag, raw);
  }
}

function checkInternal(page, pathname, frag, raw) {
  // The Javadoc/KDoc trees under /api are generated by the deploy workflow and
  // copied into the build there, so they are legitimately missing from a local
  // `npm run build`. Their absence is not a broken link in this repository.
  if (pathname.replace(/^\/+/, '').startsWith('api/')) return;
  const target = resolveTarget(pathname.split('?')[0]);
  if (!target) {
    broken.push(`${page}  ->  ${raw}  (no such page or asset)`);
    return;
  }
  if (frag && target.endsWith('.html')) {
    const id = decodeURIComponent(frag);
    if (!idsOf(target).has(id)) {
      broken.push(`${page}  ->  ${raw}  (page exists, #${id} does not)`);
    }
  }
}

console.log(
  `Checked ${pages.length} pages · ${externals.size} distinct external URLs`
);

if (CHECK_EXTERNAL) {
  const urls = [...externals.keys()];
  // Deliberately low: the bulk of the external set is github.com, which starts
  // returning 429 to an unauthenticated client well before this is a bottleneck.
  const CONCURRENCY = 4;
  let cursor = 0;
  const failures = [];

  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  async function probe(url) {
    // "Edit this page" targets are write-action endpoints: they require auth
    // and answer 404 for a signed-out client even when the file exists. Probe
    // the equivalent blob view instead, which validates the thing that
    // actually matters — that the file is really at that path.
    const target = url.replace(
      /^(https:\/\/github\.com\/[^/]+\/[^/]+)\/edit\//,
      '$1/blob/'
    );
    // HEAD first (cheap); some hosts answer 403/405 to HEAD but serve GET, so
    // fall back rather than reporting a link that works in a browser.
    for (const method of ['HEAD', 'GET']) {
      for (let attempt = 0; attempt < 3; attempt++) {
        try {
          const res = await fetch(target, {
            method,
            redirect: 'follow',
            headers: {'user-agent': 'storm-docs-link-check'},
            signal: AbortSignal.timeout(20000),
          });
          if (res.ok) return null;
          // Rate limiting is a property of the checker, not of the link.
          if (res.status === 429 || res.status === 503) {
            await sleep(2000 * (attempt + 1));
            continue;
          }
          if (method === 'GET') return `HTTP ${res.status}`;
          break;
        } catch (e) {
          if (method === 'GET' && attempt === 2) {
            return e.name === 'TimeoutError' ? 'timeout' : e.message;
          }
          await sleep(1000 * (attempt + 1));
        }
      }
    }
    return 'unreachable';
  }

  await Promise.all(
    Array.from({length: CONCURRENCY}, async () => {
      while (cursor < urls.length) {
        const url = urls[cursor++];
        const problem = await probe(url);
        if (problem) {
          const from = [...externals.get(url)].slice(0, 3).join(', ');
          failures.push(`${url}  (${problem})  from: ${from}`);
        }
      }
    })
  );

  if (failures.length) {
    console.error(`\n${failures.length} external link(s) failed:`);
    for (const f of failures.sort()) console.error('  ' + f);
  } else {
    console.log(`All ${urls.length} external URLs responded OK.`);
  }
  broken.push(...failures.map((f) => 'external: ' + f));
}

if (broken.length) {
  console.error(`\n${broken.length} broken link(s):`);
  for (const b of broken.sort()) console.error('  ' + b);
  process.exit(1);
}

console.log('No broken links.');
