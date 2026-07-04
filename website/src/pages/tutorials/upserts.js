import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: upserts, JPA vs Storm.

const TITLE = 'Upserts Without the Workarounds: JPA vs Storm';
const DESC =
  'JPA has no upsert: the usual find-then-save pattern is two statements and one ' +
  'race condition. Storm generates the database\'s native upsert syntax, atomic ' +
  'and portable across PostgreSQL, MySQL, Oracle, SQL Server, SQLite and H2.';

const CODE_JPA_UPSERT = [
  C('// The common JPA workaround: check, then write. Two statements, one race.\n'),
  A('@Transactional'), P('\n'),
  K('fun '), F('importUser'), P('(email: '), T('String'), P(', name: '), T('String'), P(', city: '), T('City'), P(') {\n'),
  P('    '), K('val '), P('existing = userRepository.'), F('findByEmail'), P('(email)\n'),
  P('    '), K('if'), P(' (existing == '), K('null'), P(') {\n'),
  P('        userRepository.'), F('save'), P('('), T('User'), P('(email = email, name = name, city = city))\n'),
  P('    } '), K('else'), P(' {\n'),
  P('        existing.name = name\n'),
  P('        existing.city = city\n'),
  P('    }\n'),
  P('}'),
].join('');

const SQL_JPA_UPSERT = [
  QC('-- two statements with a gap between them'), '\n',
  QK('SELECT'), ' u.* ', QK('FROM'), ' user u ', QK('WHERE'), ' u.email = ', QQ('?'), '\n',
  QK('INSERT INTO'), ' user (email, name, city_id) ', QK('VALUES'), ' (', QQ('?'), ', ', QQ('?'), ', ', QQ('?'), ')\n\n',
  QC('-- two concurrent imports both see "no row" in the gap:'), '\n',
  QC('-- one insert succeeds, the other dies on the unique constraint'),
].join('');

const CODE_STORM_UPSERT = [
  C('// One statement, resolved by the database, safe under concurrency\n'),
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
  QK('ON CONFLICT'), ' (email) ', QK('DO UPDATE'), '\n',
  QK('SET'), ' name = excluded.name, city_id = excluded.city_id\n\n',
  QC('-- MySQL/MariaDB: INSERT ... ON DUPLICATE KEY UPDATE'), '\n',
  QC('-- Oracle, SQL Server, H2: MERGE INTO ...'),
].join('');

const CODE_STORM_BATCH = [
  C('// Synchronize an external source: insert the new, update the known\n'),
  K('val '), P('users = imported.'), F('map'), P(' { row ->\n'),
  P('    '), T('User'), P('(email = row.email, name = row.name, city = '), F('resolveCity'), P('(row.city))\n'),
  P('}\n\n'),
  P('orm '), K('upsert'), P(' users   '), C('// one batched statement, not a loop'),
].join('');

const CODE_STORM_TX = [
  C('// Upserts compose with transactions like any other write\n'),
  F('transaction'), P(' {\n'),
  P('    '), K('val '), P('city = orm '), K('insert '), T('City'), P('(name = '), S('"Sunnyvale"'), P(', population = '), N('161_884'), P(')\n'),
  P('    '), K('val '), P('user = orm '), K('upsert '), T('User'), P('(email = '), S('"alice@example.com"'), P(', name = '), S('"Alice"'), P(', city = city)\n'),
  P('}'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Upserts</div>
  <h1>Upserts without <span class="grad">the workarounds</span></h1>
  <p class="dek">Insert if missing, update if present: one of the most common write patterns, and one JPA never got a first-class answer for. Storm delegates it to the database, where it is atomic.</p>
  <div class="meta"><span>Series · JPA to Storm</span><span>5 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>A webhook delivers user records, possibly more than once. An import job synchronizes rows from an external system. In both cases the same logical record may arrive repeatedly, and the write must be idempotent: create the row the first time, update it every time after, and survive two deliveries arriving at once.</p>

  <h2><span class="hno">02</span>The JPA way</h2>
  <p>JPA has no upsert operation. <code>merge()</code> sounds like one but is not: it copies detached state into the session and still decides insert-or-update by looking first. So most codebases end up with this:</p>
  ${editor({file: 'ImportService.kt', tag: 'Kotlin · JPA', code: CODE_JPA_UPSERT, sql: SQL_JPA_UPSERT})}
  <p>The check and the write are separate statements, so two concurrent requests can both find nothing and both insert. One wins, the other throws <code>DataIntegrityViolationException</code>, and now you are writing retry logic or taking pessimistic locks for a write that the database could have resolved on its own. The alternative is a hand-written native query per database dialect.</p>

  <h2><span class="hno">03</span>The Storm way</h2>
  <p>Storm treats upsert as a first-class repository operation and generates the native conflict syntax of whatever database you are on:</p>
  ${editor({file: 'ImportService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_UPSERT, sql: SQL_STORM_UPSERT})}
  <p>The database resolves the conflict atomically in one statement: no race window, no retry logic, no application-level locking. Conflict detection rides on the table's unique constraints, in this case the unique index on <code>email</code>. The returned entity carries the database-generated id either way.</p>
  <div class="note">Upsert needs two things: the dialect module for your database on the classpath (for example <code>storm-postgresql</code>), and a primary key or unique constraint for the database to detect the conflict. On MySQL and MariaDB, any unique constraint can trigger the update branch, so be deliberate when a table has several. See <a href="/docs/upserts">Upserts</a> for the failure modes.</div>

  <h2><span class="hno">04</span>Batches</h2>
  <p>Synchronization jobs rarely process one record. Passing a list combines JDBC batching with the native upsert syntax, which is dramatically faster than a loop:</p>
  ${editor({file: 'SyncJob.kt', tag: 'Kotlin · Storm', code: CODE_STORM_BATCH})}

  <h2><span class="hno">05</span>Inside a transaction</h2>
  <p>Upserts participate in transactions like every other write, so multi-step imports stay atomic:</p>
  ${editor({file: 'ImportService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_TX})}

  <h2><span class="hno">06</span>Side by side</h2>
  <table class="cmp">
    <tr><th></th><th>JPA with Hibernate</th><th>Storm</th></tr>
    <tr><td>Upsert operation</td><td>None; find-then-save or a native query per dialect</td><td><code>orm upsert entity</code>, one statement</td></tr>
    <tr><td>Concurrency</td><td>Race window between check and write</td><td>Atomic; the database resolves the conflict</td></tr>
    <tr><td>Portability</td><td>Native SQL differs per database</td><td>Dialects generate <code>ON CONFLICT</code>, <code>ON DUPLICATE KEY</code>, or <code>MERGE</code></td></tr>
    <tr><td>Batches</td><td>Loop over single writes</td><td>One batched upsert statement</td></tr>
  </table>

  <h2><span class="hno">07</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/upserts">Upserts</a>
    <a href="/docs/dialects">Dialects</a>
    <a href="/docs/transactions">Transactions</a>
    <a href="/docs/repositories">Repositories</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function UpsertsTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="upserts" body={BODY} />;
}
