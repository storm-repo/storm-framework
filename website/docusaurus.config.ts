import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const stormVersion = require('./plugins/read-storm-version');
const remarkStormVersion = require('./plugins/remark-storm-version');
const staticVersionReplace = require('./plugins/static-version-replace');
const exampleReadmes = require('./plugins/example-readmes');
const versionedDocsCanonical = require('./plugins/versioned-docs-canonical');

const config: Config = {
  title: 'Storm Framework',
  tagline: 'A modern, high-performance ORM for Kotlin 2.0+ and Java 21+',
  favicon: 'img/storm-dark.png',

  url: 'https://orm.st',
  baseUrl: '/',

  organizationName: 'storm-orm',
  projectName: 'storm-framework',

  onBrokenLinks: 'warn',

  // Exposed to client-side page components (e.g. the hand-built /quickstart
  // page) so install snippets can render the resolved release version. The
  // static-version-replace plugin only rewrites static files, not src/pages
  // output, so pages read the version from here instead of @@STORM_VERSION@@.
  customFields: {
    stormVersion,
  },

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  plugins: [
    [staticVersionReplace, { version: stormVersion }],
    // Point versioned and `next` docs canonicals at the current /docs/ page so
    // old snapshots do not compete with the live docs (see the plugin).
    versionedDocsCanonical,
    // Renders the example-project READMEs inline at /examples/<slug>,
    // fetched from GitHub at build time (see plugins/example-readmes.js).
    exampleReadmes,
    // Redirect the old root-level doc URLs (which used to serve at `/`) to their
    // new `/docs/...` homes, so existing links, bookmarks, and SEO keep working
    // now that the landing page owns `/`.
    [
      '@docusaurus/plugin-client-redirects',
      {
        createRedirects(existingPath: string) {
          // Every doc now lives under `/docs/`; map `/docs/x` <- `/x`.
          // The docs index (`/docs/`) is intentionally NOT redirected from `/`,
          // because `/` is the landing page.
          if (existingPath.startsWith('/docs/')) {
            const rest = existingPath.slice('/docs/'.length);
            // Do not shadow real top-level pages that live under src/pages
            // (e.g. /comparison, /quickstart) with a /docs redirect.
            const reserved = new Set(['comparison', 'quickstart']);
            if (rest && !reserved.has(rest)) return ['/' + rest];
          }
          return undefined;
        },
      },
    ],
    // Privacy-friendly analytics (Plausible). Injects the site-specific
    // loader plus the init snippet into <head> on every page.
    function plausibleAnalyticsPlugin() {
      return {
        name: 'plausible-analytics',
        injectHtmlTags() {
          return {
            headTags: [
              {
                tagName: 'script',
                attributes: {
                  async: true,
                  src: 'https://plausible.io/js/pa-tkVl2-A9dQBBULkKCv59f.js',
                },
              },
              {
                tagName: 'script',
                innerHTML:
                  "window.plausible=window.plausible||function(){(plausible.q=plausible.q||[]).push(arguments)},plausible.init=plausible.init||function(i){plausible.o=i||{}};plausible.init()",
              },
            ],
          };
        },
      };
    },
  ],

  presets: [
    [
      'classic',
      {
        docs: {
          path: '../docs',
          // Docs are namespaced under `/docs` so the landing page
          // (src/pages/index.js) owns `/`. Keep this as `/docs`: reverting to `/`
          // would let the docs reclaim the homepage and hide the landing.
          // Doc versioning (`docusaurus docs:version`) only writes to
          // versioned_docs/ — it never touches src/pages — so the front page
          // is safe across doc generation.
          routeBasePath: '/docs',
          sidebarPath: './sidebars.ts',
          versions: {
            current: {
              label: 'Next',
              path: 'next',
            },
          },
          editUrl: 'https://github.com/storm-orm/storm-framework/edit/main/docs/',
          beforeDefaultRemarkPlugins: [
            [remarkStormVersion, { version: stormVersion }],
          ],
        },
        sitemap: {
          // Only advertise the current (latest) docs version to crawlers.
          // Old numbered versions and the unreleased `/docs/next` dev docs are
          // near-duplicate content that splits ranking signal across ~10 copies
          // of each page. They stay reachable via the version dropdown; we just
          // keep them out of the sitemap so Google consolidates on the canonical
          // (unversioned) `/docs/*` pages.
          createSitemapItems: async (params) => {
            const {defaultCreateSitemapItems, ...rest} = params;
            const items = await defaultCreateSitemapItems(rest);
            const filtered = items.filter(
              (item) =>
                !item.url.includes('/docs/next') &&
                !/\/docs\/\d+\.\d+\.\d+(\/|$)/.test(item.url),
            );
            // The AI-facing docs indexes are static files, so they are not in
            // the route table the sitemap is built from; add them explicitly.
            filtered.push(
              {url: 'https://orm.st/llms.txt', changefreq: 'weekly', priority: 0.5},
              {url: 'https://orm.st/llms-full.txt', changefreq: 'weekly', priority: 0.5},
            );
            return filtered;
          },
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    // TODO(campaign): replace with a purpose-built 1200x630 social card.
    image: 'img/storm.png',
    navbar: {
      title: 'Storm',
      logo: {
        alt: 'Storm Logo',
        src: 'img/storm-dark.png',
        srcDark: 'img/storm-light.png',
      },
      items: [
        {
          type: 'docsVersionDropdown',
          position: 'right',
        },
        {
          to: '/tutorials/',
          label: 'Tutorials',
          position: 'left',
        },
        {
          to: '/examples/',
          label: 'Examples',
          position: 'left',
        },
        {
          to: '/comparison',
          label: 'Comparison',
          position: 'left',
        },
        {
          to: '/blog/',
          label: 'Blog',
          position: 'left',
        },
        {
          type: 'docSidebar',
          sidebarId: 'docs',
          position: 'left',
          label: 'Documentation',
        },
        {
          href: 'https://github.com/orgs/storm-orm/repositories',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {label: 'Get Started', to: '/quickstart'},
            {label: 'Entities', to: '/docs/entities'},
            {label: 'Queries', to: '/docs/queries'},
          ],
        },
        {
          title: 'More',
          items: [
            {label: 'Tutorials', to: '/tutorials/'},
            {label: 'Example Projects', to: '/examples/'},
            {label: 'Blog', to: '/blog/'},
            {label: 'GitHub', href: 'https://github.com/storm-orm/storm-framework'},
            {label: 'Discussions', href: 'https://github.com/storm-orm/storm-framework/discussions'},
            {label: 'Maven Central', href: 'https://central.sonatype.com/namespace/st.orm'},
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Storm Contributors.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java', 'kotlin', 'groovy'],
    },
    colorMode: {
      defaultMode: 'light',
      disableSwitch: false,
      respectPrefersColorScheme: true,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
