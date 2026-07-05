import React, {useEffect} from 'react';
import Head from '@docusaurus/Head';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import {
  TUT_CSS,
  navHtml,
  FOOT_HTML,
  editor,
  wireSqlToggles,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../components/tutorial/tutorialTheme';

// The 5-minute quickstart. Built in the landing/tutorial style (see
// tutorialTheme.js) rather than as a docs page so it deploys live immediately
// and is the target of every "Get started" button on the site. Kotlin-first;
// leads with the Storm CLI one-liner (AI-assisted setup), with the manual build
// right below. The install snippet renders the resolved release version, read
// from siteConfig.customFields.stormVersion (docusaurus.config.ts).

const TITLE = 'Quickstart · Your first Storm query in five minutes';
const DESC =
  'Set up Storm, define an entity as a plain Kotlin data class, and run a ' +
  'type-safe query. The whole path from empty project to first query, with the ' +
  'SQL it generates.';

function buildBody(version) {
  const cli = P('npx @storm-orm/cli init');

  const install =
    C('// build.gradle.kts\n') +
    F('plugins') + P(' {\n') +
    P('    ') + F('kotlin') + P('(') + S('"jvm"') + P(') version ') + S('"2.0.21"') + P('\n') +
    P('    ') + F('id') + P('(') + S('"com.google.devtools.ksp"') + P(') version ') + S('"2.0.21-1.0.28"') + P('\n') +
    P('}\n\n') +
    F('dependencies') + P(' {\n') +
    P('    ') + F('implementation') + P('(') + F('platform') + P('(') + S(`"st.orm:storm-bom:${version}"`) + P('))\n') +
    P('    ') + F('implementation') + P('(') + S('"st.orm:storm-kotlin"') + P(')\n') +
    P('    ') + F('runtimeOnly') + P('(') + S('"st.orm:storm-core"') + P(')\n') +
    P('    ') + F('runtimeOnly') + P('(') + S('"st.orm:storm-h2"') + P(')          ') + C('// zero-setup in-memory database\n') +
    P('    ') + F('runtimeOnly') + P('(') + S('"com.h2database:h2:2.2.224"') + P(')\n') +
    P('    ') + F('ksp') + P('(') + S('"st.orm:storm-metamodel-ksp"') + P(')\n') +
    P('    ') + F('kotlinCompilerPluginClasspath') + P('(') + S('"st.orm:storm-compiler-plugin-2.0"') + P(')\n') +
    P('}');

  const entity =
    C('// Movie.kt — a plain data class. This is the whole entity.\n') +
    K('data class ') + T('Movie') + P('(\n') +
    P('    ') + A('@PK') + P(' ') + K('val ') + P('id: ') + T('Int') + P(' = ') + N('0') + P(',\n') +
    P('    ') + K('val ') + P('title: ') + T('String') + P(',\n') +
    P('    ') + K('val ') + P('year: ') + T('Int') + P(',\n') +
    P('    ') + K('val ') + P('rating: ') + T('Double') + P(',\n') +
    P(') : ') + T('Entity') + P('<') + T('Int') + P('>');

  const schema =
    C('-- create the table with your migration tool, or run this once\n') +
    K('CREATE TABLE ') + P('movie (\n') +
    P('    id      ') + T('INT') + P(' ') + K('PRIMARY KEY AUTO_INCREMENT') + P(',\n') +
    P('    title   ') + T('VARCHAR') + P('(') + N('200') + P('),\n') +
    P('    year    ') + T('INT') + P(',\n') +
    P('    rating  ') + T('DOUBLE') + P('\n') +
    P(');');

  const query =
    C('// Open an ORM on any JDBC DataSource. Thread-safe; create it once.\n') +
    K('val ') + P('orm = dataSource.orm\n\n') +
    C('// A repository for Movie: every CRUD method included, nothing to implement.\n') +
    K('val ') + P('movies = orm.') + F('entity') + P('<') + T('Movie') + P('>()\n\n') +
    C('// Insert returns a copy with the generated id.\n') +
    K('val ') + P('saved = orm ') + K('insert ') + T('Movie') + P('(title = ') + S('"Dune: Part Two"') + P(', year = ') + N('2024') + P(', rating = ') + N('8.5') + P(')\n\n') +
    C('// One type-safe line. Movie_ is generated at compile time, so a typo fails to compile.\n') +
    K('val ') + P('recent = movies.') + F('findAll') + P('(Movie_.year ') + K('eq') + P(' ') + N('2024') + P(')');

  const querySql =
    QC('-- orm insert Movie(...)\n') +
    QK('INSERT INTO') + ' movie (title, year, rating) ' + QK('VALUES') + ' (' + QQ('?') + ', ' + QQ('?') + ', ' + QQ('?') + ')\n\n' +
    QC('-- movies.findAll(Movie_.year eq 2024)\n') +
    QK('SELECT') + ' m.id, m.title, m.year, m.rating\n' +
    QK('FROM') + ' movie m\n' +
    QK('WHERE') + ' m.year = ' + QQ('?');

  return `
${navHtml('')}

<div class="art">
  <div class="crumbs"><a href="/">Home</a><span class="sep">/</span>Quickstart</div>
  <h1>Your first query.<br><span class="grad">Five minutes.</span></h1>
  <p class="dek">Install Storm, define an entity as a plain Kotlin data class, and run a type-safe query. No persistence context, no proxies, no XML. Here is the whole path.</p>
  <div class="meta"><span>Kotlin</span><span>~5 min</span><span>JDK 21+</span></div>

  <h2><span class="hno">1</span>Set up</h2>
  <p>The fastest way in is the Storm CLI. It installs Storm-aware rules and skills for your AI coding assistant (Claude, Cursor, Copilot, Windsurf, Codex) and sets up a schema-aware MCP server, so the entities and queries it generates match your real schema.</p>
  ${editor({file: 'terminal', tag: 'shell', code: cli})}
  <p>Prefer to wire the build by hand? Add the Storm modules yourself. This is the full Kotlin set for a runnable project on an in-memory H2 database; the BOM keeps the versions aligned.</p>
  ${editor({file: 'build.gradle.kts', tag: 'Gradle · Kotlin DSL', code: install})}
  <p>The <code>ksp</code> dependency generates the type-safe metamodel (<code>Movie_</code>), and the compiler plugin makes SQL templates injection-safe by default. On a real database, swap <code>storm-h2</code> for your dialect and add its JDBC driver. See the <a class="tlink" href="/docs/installation">installation guide</a> for all options.</p>

  <h2><span class="hno">2</span>Define an entity</h2>
  <p>Entities are plain immutable data classes that implement <code>Entity&lt;ID&gt;</code>. Field names map to columns automatically (camelCase to snake_case). There is no base class to extend and no generated draft type to route through: the class you write is the object you get back.</p>
  ${editor({file: 'Movie.kt', tag: 'Kotlin', code: entity})}
  <p>Storm maps to an existing schema rather than creating one, so define the table with your migration tool (Flyway, Liquibase) or a one-off DDL script. Storm can verify at startup that your entities match it with <code>validateSchema()</code>.</p>
  ${editor({file: 'schema.sql', tag: 'SQL', code: schema})}

  <h2><span class="hno">3</span>Run a query</h2>
  <p>Open an <code>ORMTemplate</code> on your <code>DataSource</code>, ask for a repository, and query it. Toggle <b>Show SQL</b> to see exactly what Storm runs: one statement, fully parameterized, no surprises.</p>
  ${editor({file: 'Main.kt', tag: 'Kotlin', code: query, sql: querySql})}
  <p>That is the core loop. Insert, <code>findById</code>, <code>update</code>, and <code>remove</code> all come for free on the repository; add your own one-line queries whenever you need them.</p>

  <h2><span class="hno">4</span>Where to next</h2>
  <p>You have the whole shape of Storm in three steps. From here:</p>
  <div class="refs">
    <a href="/tutorials/build-a-rest-api">Build a REST API from scratch</a>
    <a href="/docs/first-query">First Query</a>
    <a href="/docs/entities">Entities</a>
    <a href="/examples/">Example apps</a>
    <a href="/comparison">How Storm compares</a>
  </div>

  <div class="cta">
    <a href="/tutorials/build-a-rest-api" class="btn primary">Build a real app →</a>
    <a href="/docs/" class="btn">Read the docs</a>
  </div>
</div>

${FOOT_HTML}
`;
}

export default function Quickstart() {
  const {siteConfig} = useDocusaurusContext();
  const version = siteConfig.customFields?.stormVersion || '0.0.0';
  useEffect(() => wireSqlToggles(), []);
  const url = 'https://orm.st/quickstart';
  return (
    <>
      <Head>
        <html lang="en" />
        <title>{TITLE}</title>
        <meta name="description" content={DESC} />
        <link rel="canonical" href={url} />
        <meta property="og:type" content="article" />
        <meta property="og:title" content={TITLE} />
        <meta property="og:description" content={DESC} />
        <meta name="twitter:title" content={TITLE} />
        <meta name="twitter:description" content={DESC} />
        <script type="application/ld+json">
          {JSON.stringify({
            '@context': 'https://schema.org',
            '@type': 'HowTo',
            name: 'Get started with Storm: your first query in five minutes',
            description: DESC,
            url,
            step: [
              {'@type': 'HowToStep', name: 'Set up', text: 'Use the Storm CLI, or add the Storm BOM and Kotlin modules to your build.'},
              {'@type': 'HowToStep', name: 'Define an entity', text: 'Write a plain Kotlin data class that implements Entity<ID>.'},
              {'@type': 'HowToStep', name: 'Run a query', text: 'Open an ORMTemplate on a DataSource and run a type-safe query.'},
            ],
          })}
        </script>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;700&display=swap"
          rel="stylesheet"
        />
      </Head>
      <style dangerouslySetInnerHTML={{__html: TUT_CSS}} />
      <div className="storm-tut" dangerouslySetInnerHTML={{__html: buildBody(version)}} />
    </>
  );
}
