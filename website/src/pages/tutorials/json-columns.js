import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: JSON columns. Storm-way how-to; content verified against
// docs/json.md (@Json fields, complex types, JSON_OBJECTAGG aggregation).

const TITLE = 'JSON Columns Without the Fuss';
const DESC =
  'Map document-shaped fields inside relational entities with @Json: maps, ' +
  'lists, and structured objects serialize automatically, and JSON aggregation ' +
  'loads one-to-many relationships in a single query.';

const CODE_JSON_BASIC = [
  C('// @Json maps a field onto a JSON column\n'),
  K('data class '), T('User'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('email: '), T('String'), P(',\n'),
  P('    '), A('@Json'), P(' '), K('val '), P('preferences: '), T('Map'), P('<'), T('String'), P(', '), T('String'), P('>,\n'),
  P(') : '), T('Entity'), P('<'), T('Int'), P('>\n\n'),
  C('// Reads and writes serialize transparently\n'),
  K('val '), P('user = orm '), K('insert '), T('User'), P('(\n'),
  P('    email = '), S('"alice@example.com"'), P(',\n'),
  P('    preferences = '), F('mapOf'), P('('), S('"theme"'), P(' '), K('to'), P(' '), S('"dark"'), P(', '), S('"locale"'), P(' '), K('to'), P(' '), S('"en"'), P('),\n'),
  P(')'),
].join('');

const CODE_JSON_COMPLEX = [
  C('// Structured objects work too: shape without a table\n'),
  A('@Serializable'), P('   '), C('// for kotlinx.serialization; Jackson needs no annotation\n'),
  K('data class '), T('Address'), P('(\n'),
  P('    '), K('val '), P('street: '), T('String'), P(',\n'),
  P('    '), K('val '), P('city: '), T('String'), P(',\n'),
  P('    '), K('val '), P('postalCode: '), T('String'), P(',\n'),
  P(')\n\n'),
  K('data class '), T('User'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('email: '), T('String'), P(',\n'),
  P('    '), A('@Json'), P(' '), K('val '), P('address: '), T('Address'), P(',\n'),
  P(') : '), T('Entity'), P('<'), T('Int'), P('>'),
].join('');

const CODE_JSON_AGG = [
  C('// JSON aggregation: a one-to-many loaded in a single query\n'),
  K('data class '), T('RolesByUser'), P('(\n'),
  P('    '), K('val '), P('user: '), T('User'), P(',\n'),
  P('    '), A('@Json'), P(' '), K('val '), P('roles: '), T('List'), P('<'), T('Role'), P('>,\n'),
  P(')\n\n'),
  K('interface '), T('UserRepository'), P(' : '), T('EntityRepository'), P('<'), T('User'), P(', '), T('Int'), P('> {\n\n'),
  P('    '), K('fun '), F('getUserRoles'), P('(): '), T('List'), P('<'), T('RolesByUser'), P('> =\n'),
  P('        '), F('select'), P('<'), T('RolesByUser'), P(', _, _> { '), S('"${User::class}, JSON_OBJECTAGG(${Role::class})"'), P(' }\n'),
  P('            .'), F('innerJoin'), P('<'), T('UserRole'), P('>().'), F('on'), P('<'), T('User'), P('>()\n'),
  P('            .'), F('groupBy'), P('('), T('User_'), P('.id)\n'),
  P('            .resultList\n'),
  P('}'),
].join('');

const SQL_JSON_AGG = [
  QC('-- the related rows aggregate into a JSON array, one round trip'), '\n',
  QK('SELECT'), ' u.id, u.email, ', QK('JSON_OBJECTAGG'), '(r.id, r.name)\n',
  QK('FROM'), ' "user" u\n',
  QK('INNER JOIN'), ' user_role ur ', QK('ON'), ' ur.user_id = u.id\n',
  QK('INNER JOIN'), ' role r ', QK('ON'), ' ur.role_id = r.id\n',
  QK('GROUP BY'), ' u.id',
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>JSON columns</div>
  <h1>JSON columns without <span class="grad">the fuss</span></h1>
  <p class="dek">Some data is document-shaped: preferences, addresses, denormalized snapshots. Mark the field @Json and Storm serializes it into a JSON column on write and back into the typed field on read.</p>
  <div class="meta"><span>Series · The Storm way</span><span>4 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>Store user preferences and a structured address without inventing tables for them, and load a user's roles without a second query. Relational where it matters, documents where it helps.</p>

  <h2><span class="hno">02</span>Maps and collections</h2>
  <p>Add the JSON module for your serializer (<code>storm-kotlinx-serialization</code>, or <code>storm-jackson2</code>/<code>storm-jackson3</code>); Storm auto-detects it at runtime. Then annotate the field:</p>
  ${editor({file: 'User.kt', tag: 'Kotlin · Storm', code: CODE_JSON_BASIC})}

  <h2><span class="hno">03</span>Structured objects</h2>
  <p>JSON fields are not limited to maps. A domain object with a well-defined shape but no need for its own table stores directly, keeping its type on both ends:</p>
  ${editor({file: 'User.kt', tag: 'Kotlin · Storm', code: CODE_JSON_COMPLEX})}

  <h2><span class="hno">04</span>One-to-many in one query</h2>
  <p>The same machinery powers a technique worth knowing: aggregate related rows into a JSON array inside the query, and Storm deserializes them into a typed list. A one-to-many loads in a single round trip, no N+1, no second query:</p>
  ${editor({file: 'UserRepository.kt', tag: 'Kotlin · Storm', code: CODE_JSON_AGG, sql: SQL_JSON_AGG})}
  <p>This shines when the aggregated collection is moderate in size; for very large collections, splitting into two queries is cheaper than shipping megabytes of JSON. The <a class="tlink" href="/docs/json">JSON docs</a> cover the performance envelope and when to split.</p>

  <h2><span class="hno">05</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/json">JSON</a>
    <a href="/docs/serialization">Serialization</a>
    <a href="/docs/converters">Converters</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" target="_blank" rel="noopener" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function JsonColumnsTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="json-columns" body={BODY} />;
}
