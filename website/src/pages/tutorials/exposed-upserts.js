import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: upserts and batching, Exposed vs Storm. A parity/knowledge-mapping
// page: both libraries have native, dialect-aware upserts. Exposed facts
// verified against the DSL CRUD docs (upsert with onUpdate/onUpdateExclude,
// batchInsert and the rewriteBatchedInserts caveat).

const TITLE = 'Upserts and Batching: Exposed vs Storm';
const DESC =
  'Both libraries generate native, dialect-aware upserts, so this page mostly ' +
  'maps knowledge. The differences: Exposed offers finer control over the ' +
  'update clause, Storm works at the entity level and returns hydrated values.';

const CODE_EXPOSED_UPSERT = [
  C('// Statement-level upsert, with fine-grained control when needed\n'),
  F('transaction'), P(' {\n'),
  P('    '), T('Users'), P('.'), F('upsert'), P(' {\n'),
  P('        it[email] = '), S('"alice@example.com"'), P('\n'),
  P('        it[name] = '), S('"Alice"'), P('\n'),
  P('    }\n\n'),
  P('    '), C('// control which columns the update branch touches\n'),
  P('    '), T('Users'), P('.'), F('upsert'), P('(onUpdateExclude = '), F('listOf'), P('('), T('Users'), P('.createdAt)) {\n'),
  P('        it[email] = '), S('"alice@example.com"'), P('\n'),
  P('        it[name] = '), S('"Alice"'), P('\n'),
  P('    }\n'),
  P('}'),
].join('');

const CODE_EXPOSED_BATCH = [
  C('// Batch insert from a list\n'),
  F('transaction'), P(' {\n'),
  P('    '), T('Users'), P('.'), F('batchInsert'), P('(imported) { row ->\n'),
  P('        '), K('this'), P('['), T('Users'), P('.email] = row.email\n'),
  P('        '), K('this'), P('['), T('Users'), P('.name] = row.name\n'),
  P('    }\n'),
  P('}\n'),
  C('// note: becomes separate INSERT statements unless the JDBC driver\n'),
  C('// is configured with rewriteBatchedInserts=true'),
].join('');

const CODE_STORM_UPSERT = [
  C('// Entity-level upsert: pass the value, get the persisted value back\n'),
  K('val '), P('user = orm '), K('upsert '), T('User'), P('(\n'),
  P('    email = '), S('"alice@example.com"'), P(',\n'),
  P('    name = '), S('"Alice"'), P(',\n'),
  P('    city = city,\n'),
  P(')\n'),
  C('// user.id is populated whether the row was inserted or updated'),
].join('');

const SQL_STORM_UPSERT = [
  QC('-- PostgreSQL dialect'), '\n',
  QK('INSERT INTO'), ' "user" (email, name, city_id) ', QK('VALUES'), ' (', QQ('?'), ', ', QQ('?'), ', ', QQ('?'), ')\n',
  QK('ON CONFLICT'), ' (email) ', QK('DO UPDATE'), ' ', QK('SET'), ' name = excluded.name, city_id = excluded.city_id\n\n',
  QC('-- MySQL/MariaDB: ON DUPLICATE KEY UPDATE; Oracle, SQL Server, H2: MERGE'),
].join('');

const CODE_STORM_BATCH = [
  C('// Lists batch through JDBC batching, one statement shape\n'),
  K('val '), P('users = imported.'), F('map'), P(' { row ->\n'),
  P('    '), T('User'), P('(email = row.email, name = row.name, city = '), F('resolveCity'), P('(row.city))\n'),
  P('}\n\n'),
  P('orm '), K('upsert'), P(' users   '), C('// batched upsert\n'),
  P('orm '), K('insert'), P(' users   '), C('// batched insert, returns hydrated entities'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Exposed vs Storm: upserts</div>
  <h1>Upserts and <span class="grad">batching</span></h1>
  <p class="dek">Good news first: both libraries generate the database's native upsert syntax, dialect-aware and atomic. This page is mostly a translation guide, with two differences worth knowing on each side.</p>
  <div class="meta"><span>Series · Exposed to Storm</span><span>4 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>Idempotent writes: a webhook that may deliver twice, an import that synchronizes an external source. Insert if missing, update if present, in one atomic statement, and do it for lists as well as single records.</p>

  <h2><span class="hno">02</span>The Exposed way</h2>
  <p>Exposed has first-class upsert support with notably fine-grained control over the update branch, a genuine strength:</p>
  ${editor({file: 'ImportService.kt', tag: 'Kotlin · Exposed', code: CODE_EXPOSED_UPSERT})}
  <p><code>onUpdate</code>, <code>onUpdateExclude</code>, and an optional <code>where</code> clause let you shape exactly what a conflict does, which Storm does not expose at that granularity. Batching is a separate builder:</p>
  ${editor({file: 'SyncJob.kt', tag: 'Kotlin · Exposed', code: CODE_EXPOSED_BATCH})}

  <h2><span class="hno">03</span>The Storm way</h2>
  <p>Storm's upsert works at the entity level: you pass values, conflict detection rides on the table's unique constraints, and you get the persisted entity back with its database-generated fields:</p>
  ${editor({file: 'ImportService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_UPSERT, sql: SQL_STORM_UPSERT})}
  <p>Batches take the same shape as single writes, a list instead of a value, and combine JDBC batching with the native upsert syntax:</p>
  ${editor({file: 'SyncJob.kt', tag: 'Kotlin · Storm', code: CODE_STORM_BATCH})}

  <h2><span class="hno">04</span>The translation table</h2>
  <table class="cmp">
    <tr><th>Exposed</th><th>Storm</th></tr>
    <tr><td><code>Users.upsert { it[col] = value }</code></td><td><code>orm upsert User(...)</code></td></tr>
    <tr><td>Returns affected-row count</td><td>Returns the entity, generated id included</td></tr>
    <tr><td><code>onUpdate</code> / <code>onUpdateExclude</code> / <code>where</code>: fine-grained conflict control</td><td>Update branch derived from the entity; less control, less to specify</td></tr>
    <tr><td><code>batchInsert(list) { ... }</code>; separate statements unless <code>rewriteBatchedInserts</code></td><td><code>orm upsert list</code> / <code>orm insert list</code>, JDBC-batched</td></tr>
  </table>
  <p>Rule of thumb for the migration: if your upserts are entity-shaped, the Storm version is shorter and returns richer results. If you relied on <code>onUpdate</code> to make conflicts do something other than "overwrite with the new values", check <a class="tlink" href="/docs/upserts">the upsert docs</a> for what Storm's model covers before porting.</p>

  <h2><span class="hno">05</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/upserts">Upserts</a>
    <a href="/docs/batch-streaming">Batch and Streaming</a>
    <a href="/docs/dialects">Dialects</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" target="_blank" rel="noopener" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function ExposedUpsertsTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="exposed-upserts" body={BODY} />;
}
