/**
 * Docusaurus plugin that rewrites the <link rel="canonical"> on versioned and
 * `next` docs pages to point at the current (unversioned) /docs/<path>. Old
 * numbered snapshots and the unreleased dev docs otherwise self-canonicalize
 * and compete with the live docs for ranking. The sitemap already excludes
 * these pages; this consolidates the remaining crawl/canonical signal onto the
 * current version. Only rewrites when the current page actually exists in the
 * build, so pages with no current equivalent keep their self-canonical.
 */

const fs = require('fs');
const path = require('path');

const SITE = 'https://orm.st';

module.exports = function versionedDocsCanonical() {
  return {
    name: 'versioned-docs-canonical',
    async postBuild({ outDir }) {
      const docsDir = path.join(outDir, 'docs');
      if (!fs.existsSync(docsDir)) return;

      // Fold numbered snapshots (1.2.3) and the dev docs (`next`) onto current.
      const segments = fs.readdirSync(docsDir).filter((name) => {
        const full = path.join(docsDir, name);
        return (
          fs.statSync(full).isDirectory() &&
          (name === 'next' || /^\d+\.\d+\.\d+$/.test(name))
        );
      });

      const walk = (dir, seg) => {
        for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
          const full = path.join(dir, entry.name);
          if (entry.isDirectory()) {
            walk(full, seg);
            continue;
          }
          if (entry.name !== 'index.html') continue;

          const rel = path
            .relative(path.join(docsDir, seg), path.dirname(full))
            .split(path.sep)
            .filter(Boolean)
            .join('/');

          // Only fold onto a page that exists in the current (unversioned) docs.
          const currentPage = path.join(docsDir, rel, 'index.html');
          if (!fs.existsSync(currentPage)) continue;

          const target = rel ? `${SITE}/docs/${rel}` : `${SITE}/docs/`;
          const html = fs.readFileSync(full, 'utf-8');
          const next = html.replace(
            /(<link\b[^>]*\brel="canonical"\s+href=")[^"]*(")/,
            `$1${target}$2`,
          );
          if (next !== html) fs.writeFileSync(full, next);
        }
      };

      for (const seg of segments) walk(path.join(docsDir, seg), seg);
    },
  };
};
