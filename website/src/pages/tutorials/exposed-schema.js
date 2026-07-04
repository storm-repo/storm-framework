import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: schema philosophies, Exposed vs Storm. A trade page where each
// side wins a different phase: Exposed generates DDL from table objects
// (great for prototypes and tests), Storm is schema-first and validates the
// mapping against the live database (validation.md, configuration.md).

const TITLE = 'Who Owns the Schema: Exposed vs Storm';
const DESC =
  'Exposed can generate the schema from your table objects, which is excellent ' +
  'for prototypes and tests. Storm is schema-first: the database owns the ' +
  'schema, migrations evolve it, and Storm validates that your model still matches.';

const CODE_EXPOSED_SCHEMA = [
  C('// The table objects can create the schema, a real convenience\n'),
  F('transaction'), P(' {\n'),
  P('    '), T('SchemaUtils'), P('.'), F('create'), P('('), T('Cities'), P(', '), T('Users'), P(')\n'),
  P('}'),
].join('');

const CODE_STORM_VALIDATE = [
  C('// The database owns the schema; Storm checks that the model matches\n'),
  K('val '), P('orm = dataSource.orm\n\n'),
  C('// Inspect programmatically ...\n'),
  K('val '), P('errors: '), T('List'), P('<'), T('String'), P('> = orm.'), F('validateSchema'), P('()\n\n'),
  C('// ... or fail fast at startup\n'),
  P('orm.'), F('validateSchemaOrThrow'), P('()'),
].join('');

const CODE_STORM_CONFIG = [
  C('# application.yml (Spring Boot starter)\n'),
  P('storm:\n'),
  P('  validation:\n'),
  P('    schema_mode: fail   '), C('# none | warn | fail\n'),
  P('    strict: true        '), C('# promote drift warnings to errors, useful in CI'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Exposed vs Storm: schema</div>
  <h1>Who owns <span class="grad">the schema</span></h1>
  <p class="dek">A genuine philosophical difference, with a winner on each side. Exposed can generate the schema from code, which is excellent early on. Storm assumes the database owns the schema and verifies that your model still tells the truth about it.</p>
  <div class="meta"><span>Series · Exposed to Storm</span><span>4 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>Two moments in a project's life: day one, when you want tables to exist with minimal fuss, and year two, when the schema is production data under migration control and the risk is drift between what the code believes and what the database is.</p>

  <h2><span class="hno">02</span>The Exposed way</h2>
  <p>Because Exposed's table objects fully describe columns and types, they can create the schema:</p>
  ${editor({file: 'Setup.kt', tag: 'Kotlin · Exposed', code: CODE_EXPOSED_SCHEMA})}
  <p>For prototypes, tests, and greenfield day one, this is genuinely great, and Storm has no equivalent. For evolution, Exposed 1.0 adds <code>MigrationUtils</code> (in the <code>exposed-migration</code> modules), which generates the statements a schema change requires; teams typically feed those into a migration tool such as Flyway or Liquibase. The table objects remain a second description of the schema, kept in sync with the migrations by discipline.</p>

  <h2><span class="hno">03</span>The Storm way</h2>
  <p>Storm starts from the other end: the database schema, evolved by your migration tool, is the source of truth, and entities are a typed view of it. What Storm adds is verification that the view is still accurate:</p>
  ${editor({file: 'Startup.kt', tag: 'Kotlin · Storm', code: CODE_STORM_VALIDATE})}
  <p>Validation checks entities and projections against the live schema: missing tables and columns, type incompatibilities, nullability mismatches, primary key mismatches, and missing constraints. With the Spring Boot starter it runs at startup, configured by property:</p>
  ${editor({file: 'application.yml', tag: 'YAML · Storm', code: CODE_STORM_CONFIG})}
  <p>With <code>strict: true</code> in CI, a migration that quietly diverges from the model fails the build instead of failing a query in production weeks later. For tests, <a class="tlink" href="/tutorials/testing">@StormTest</a> runs your real schema scripts, so the same migrations that shape production shape the test database.</p>

  <h2><span class="hno">04</span>The trade</h2>
  <table class="cmp">
    <tr><th></th><th>Exposed</th><th>Storm</th></tr>
    <tr><td>Creating the schema</td><td><code>SchemaUtils.create(...)</code> from table objects</td><td>Your migration tool; Storm does not generate DDL</td></tr>
    <tr><td>Evolving the schema</td><td><code>MigrationUtils</code> generates statements; a migration tool applies them</td><td>Migration tool; the model follows, verified</td></tr>
    <tr><td>Detecting drift</td><td>Discovered when a query fails</td><td><code>validateSchema()</code>, startup mode, strict CI mode</td></tr>
    <tr><td>Source of truth</td><td>The table objects, until migrations take over</td><td>The database, always</td></tr>
  </table>
  <p>If your project lives mostly in the prototype phase, Exposed's generation is the nicer workflow and this page happily concedes it. Storm's bet is that schemas spend most of their lives owned by migrations, and that the valuable tool in that long phase is the one that catches drift early.</p>

  <h2><span class="hno">05</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/validation">Validation</a>
    <a href="/docs/configuration">Configuration</a>
    <a href="/docs/testing">Testing</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function ExposedSchemaTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="exposed-schema" body={BODY} />;
}
