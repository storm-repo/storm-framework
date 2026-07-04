import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: testing the data layer with @StormTest and SqlCapture. First entry
// of "The Storm way" how-to series (no framework comparison, just the recipe).

const TITLE = 'Testing Your Data Layer with @StormTest';
const DESC =
  'One annotation gives you an in-memory database, schema scripts, and injected ' +
  'ORMTemplate and SqlCapture parameters. Assert results and the SQL that ' +
  'produced them, then run the same tests against PostgreSQL with Testcontainers.';

const CODE_BASIC = [
  A('@StormTest'), P('(scripts = ['), S('"/schema.sql"'), P(', '), S('"/data.sql"'), P('])\n'),
  K('class '), T('OwnerRepositoryTest'), P(' {\n\n'),
  P('    '), A('@Test'), P('\n'),
  P('    '), K('fun '), F('`finds owners by last name`'), P('(orm: '), T('ORMTemplate'), P(') {\n'),
  P('        '), K('val '), P('owners = orm.'), F('entity'), P('<'), T('Owner'), P('>().'), F('findAll'), P('('), T('Owner_'), P('.lastName '), K('eq'), P(' '), S('"Smith"'), P(')\n\n'),
  P('        owners.size '), K('shouldBe'), P(' '), N('2'), P('\n'),
  P('    }\n'),
  P('}'),
].join('');

const CODE_CAPTURE = [
  A('@Test'), P('\n'),
  K('fun '), F('`pet list loads owners in the same query`'), P('(orm: '), T('ORMTemplate'), P(', capture: '), T('SqlCapture'), P(') {\n'),
  P('    '), K('val '), P('pets = capture.'), F('execute'), P(' { orm.'), F('entity'), P('<'), T('Pet'), P('>().'), F('findAll'), P('() }\n\n'),
  P('    pets.'), F('forEach'), P(' { '), F('render'), P('(it.owner.lastName) }      '), C('// walk the graph freely\n'),
  P('    capture.'), F('count'), P('('), T('Operation'), P('.'), T('SELECT'), P(') '), K('shouldBe'), P(' '), N('1'), P('   '), C('// an added query fails the build\n'),
  P('    capture.'), F('count'), P('('), T('Operation'), P('.'), T('UPDATE'), P(') '), K('shouldBe'), P(' '), N('0'), P('   '), C('// reads stay reads\n'),
  P('}'),
].join('');

const CODE_CONTAINERS = [
  A('@StormTest'), P('(scripts = ['), S('"/schema-postgres.sql"'), P(', '), S('"/data.sql"'), P('])\n'),
  A('@Testcontainers'), P('\n'),
  K('class '), T('PostgresOwnerTest'), P(' {\n\n'),
  P('    '), K('companion object'), P(' {\n'),
  P('        '), A('@Container'), P('\n'),
  P('        '), K('val '), P('postgres = '), T('PostgreSQLContainer'), P('('), S('"postgres:latest"'), P(')\n\n'),
  P('        '), A('@JvmStatic'), P('\n'),
  P('        '), K('fun '), F('dataSource'), P('(): '), T('DataSource'), P(' = '), T('PGSimpleDataSource'), P('().'), F('apply'), P(' {\n'),
  P('            '), F('setUrl'), P('(postgres.jdbcUrl)\n'),
  P('            user = postgres.username\n'),
  P('            password = postgres.password\n'),
  P('        }\n'),
  P('    }\n\n'),
  P('    '), C('// the same tests now run against real PostgreSQL\n'),
  P('}'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Testing the data layer</div>
  <h1>Testing your data layer with <span class="grad">@StormTest</span></h1>
  <p class="dek">Data-layer tests usually start with a page of setup: a DataSource, schema scripts, wiring. Storm's test module reduces that to one annotation, and then lets you assert something most stacks cannot: the SQL itself.</p>
  <div class="meta"><span>Series · The Storm way</span><span>4 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>Test repository and query code against a real database schema, fast enough to run on every build, without booting a dependency-injection container or writing connection plumbing in every test class.</p>

  <h2><span class="hno">02</span>One annotation</h2>
  <p><code>@StormTest</code> creates an in-memory H2 database, runs your schema and data scripts, and injects test method parameters. No Spring context, no base class, no manual setup:</p>
  ${editor({file: 'OwnerRepositoryTest.kt', tag: 'Kotlin · storm-test', code: CODE_BASIC})}
  <p>Parameters resolve by type: <code>ORMTemplate</code>, <code>DataSource</code>, <code>SqlCapture</code>, or any type with a static <code>of(DataSource)</code> factory. Each test starts from the scripted state, and the whole cycle runs in milliseconds because nothing heavier than JUnit is involved.</p>

  <h2><span class="hno">03</span>Assert the SQL, not just the rows</h2>
  <p>A test that only checks results can pass while the query underneath quietly degrades. <code>SqlCapture</code> records every statement a block generates, with its operation type and bound parameters, so query shape becomes an assertion:</p>
  ${editor({file: 'PetQueryTest.kt', tag: 'Kotlin · storm-test', code: CODE_CAPTURE})}
  <p>This turns performance properties into regression tests: "no N+1", "this service only reads", "the batch runs one statement" all become one-line assertions that fail the build when violated.</p>

  <h2><span class="hno">04</span>The same tests against PostgreSQL</h2>
  <p>H2 keeps the loop fast, but dialect-specific behavior deserves the real database. <code>@StormTest</code> picks up a static <code>dataSource()</code> method, which is exactly the hook Testcontainers needs:</p>
  ${editor({file: 'PostgresOwnerTest.kt', tag: 'Kotlin · storm-test', code: CODE_CONTAINERS})}
  <p>Scripts and parameter injection work unchanged, so the fast H2 suite and the thorough PostgreSQL suite share their test code.</p>

  <h2><span class="hno">05</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/testing">Testing</a>
    <a href="/docs/sql-logging">SQL Logging</a>
    <a href="/docs/getting-started">Getting Started</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function TestingTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="testing" body={BODY} />;
}
