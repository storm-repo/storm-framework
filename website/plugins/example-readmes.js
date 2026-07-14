const {marked} = require('marked');

// Build-time fetch of the example-project READMEs, rendered inline at
// /examples/<slug> so visitors stay in the orm.st funnel (tutorials ->
// examples -> getting started) and the content is indexable on our domain.
// The READMEs stay canonical in their repos; the site rebuilds on every docs
// push and release, which keeps the rendered copies fresh without any manual
// syncing. Each page shows the clone command and a GitHub link up front.

const GITHUB_ORG = 'storm-orm';

const EXAMPLES = [
  {
    slug: 'kotlin-ktor',
    repo: 'storm-example-kotlin-ktor',
    title: 'Storm Movies · Kotlin + Ktor',
    description:
      'A server-rendered movie browser on Ktor 3 with the Storm plugin: ' +
      'automatic repository registration, service wiring with ktor-server-di, ' +
      'coroutine-native transactions, kotlinx.serialization for the JSON ' +
      'APIs, and Playwright-driven interface tests.',
    chips: ['Kotlin', 'Ktor 3', 'PostgreSQL'],
  },
  {
    slug: 'kotlin-spring-boot',
    repo: 'storm-example-kotlin-spring-boot-4',
    title: 'Storm Movies · Kotlin + Spring Boot 4',
    description:
      'The same movie browser on Spring Boot 4 with immutable data-class ' +
      'entities, metamodel-based queries, coroutine-native transactions, and ' +
      'schema validation. PostgreSQL with Flyway migrations; repository tests ' +
      'on H2 with storm-test.',
    chips: ['Kotlin', 'Spring Boot 4', 'PostgreSQL'],
  },
  {
    slug: 'java-spring-boot',
    repo: 'storm-example-java-spring-boot-4',
    title: 'Storm Movies · Java + Spring Boot 4',
    description:
      'The Java flavor on Java 21: immutable record entities, ' +
      'metamodel-based queries, Spring-managed transactions, and schema ' +
      'validation. No JPA, no proxies, no persistence context.',
    chips: ['Java 21', 'Spring Boot 4', 'PostgreSQL'],
  },
  {
    slug: 'kotlin-spring-boot-graalvm',
    repo: 'storm-example-kotlin-spring-boot-4-graalvm',
    title: 'Storm Movies · Spring Boot 4 · GraalVM',
    description:
      'The Spring Boot movie browser compiled to a GraalVM native image: ' +
      'entities and scanned repositories registered automatically through ' +
      "Storm's Spring AOT hints, startup around a quarter of a second, and " +
      'the full Playwright suite running against the native binary.',
    chips: ['Kotlin', 'Spring Boot 4', 'GraalVM'],
  },
  {
    slug: 'kotlin-ktor-graalvm',
    repo: 'storm-example-kotlin-ktor-graalvm',
    title: 'Storm Movies · Ktor · GraalVM',
    description:
      'The Ktor movie browser as a native image: storm-core ships a GraalVM ' +
      'feature that registers entities and repositories from the compile-time ' +
      'type index, so the data layer needs no native configuration at all.',
    chips: ['Kotlin', 'Ktor 3', 'GraalVM'],
  },
];

async function fetchReadme(repo) {
  const url = `https://raw.githubusercontent.com/${GITHUB_ORG}/${repo}/main/README.md`;
  let lastError;
  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      const response = await fetch(url);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status} for ${url}`);
      }
      return await response.text();
    } catch (error) {
      lastError = error;
      await new Promise((resolve) => setTimeout(resolve, attempt * 1000));
    }
  }
  throw new Error(
    `example-readmes: failed to fetch ${url} after 3 attempts: ${lastError}`
  );
}

function renderReadme(markdown, repo) {
  let html = marked.parse(markdown, {gfm: true});
  // The page hero owns the title; drop the README's own <h1>.
  html = html.replace(/<h1[^>]*>[\s\S]*?<\/h1>\s*/, '');
  // Repo-relative links and images only resolve on GitHub; point them there.
  html = html.replace(
    /(href|src)="(?!https?:\/\/|#|mailto:)([^"]+)"/g,
    (match, attr, target) => {
      const path = target.replace(/^\.?\//, '');
      return attr === 'src'
        ? `src="https://raw.githubusercontent.com/${GITHUB_ORG}/${repo}/main/${path}"`
        : `href="https://github.com/${GITHUB_ORG}/${repo}/blob/main/${path}"`;
    }
  );
  return html;
}

module.exports = function exampleReadmesPlugin() {
  return {
    name: 'example-readmes',

    async loadContent() {
      const readmes = await Promise.all(
        EXAMPLES.map((example) => fetchReadme(example.repo))
      );
      return EXAMPLES.map((example, index) => ({
        ...example,
        html: renderReadme(readmes[index], example.repo),
      }));
    },

    async contentLoaded({content, actions}) {
      for (const example of content) {
        const dataPath = await actions.createData(
          `example-${example.slug}.json`,
          JSON.stringify(example)
        );
        actions.addRoute({
          path: `/examples/${example.slug}`,
          component: '@site/src/components/examples/ExampleReadmePage.js',
          modules: {example: dataPath},
          exact: true,
        });
      }
    },
  };
};
