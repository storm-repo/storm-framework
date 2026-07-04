import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: read models with Projection. A Storm Projection is a read-only
// view on the data (a table subset or a database view), modeled as a
// first-class type. Custom per-query result shapes are covered in the
// query-results and sql-templates tutorials instead.

const TITLE = 'Projections: Read-Only Views on Your Data';
const DESC =
  'A Storm projection is a read-only view on your data, sometimes a subset of ' +
  'a table, sometimes a database view, modeled as a first-class record with its ' +
  'own repository, type-safe queries, and nesting. JPA has no equivalent concept.';

const CODE_JPA_IMMUTABLE = [
  C('// The closest JPA gets: an entity over a view, made read-only by convention\n'),
  A('@Entity'), P('\n'),
  A('@Immutable'), P('   '), C('// Hibernate-specific, not JPA\n'),
  A('@Table'), P('(name = '), S('"owner_address_view"'), P(')\n'),
  K('class '), T('OwnerAddressView'), P('(\n'),
  P('    '), A('@Id'), P(' '), K('var '), P('id: '), T('Int'), P('? = '), K('null'), P(',\n'),
  P('    '), K('var '), P('fullName: '), T('String'), P(' = '), S('""'), P(',\n'),
  P('    '), K('var '), P('address: '), T('String'), P(' = '), S('""'), P(',\n'),
  P('    '), K('var '), P('city: '), T('String'), P(' = '), S('""'), P(',\n'),
  P(')'),
].join('');

const CODE_STORM_SUBSET = [
  C('// A read-only view on the owner table: just these columns, no writes.\n'),
  A('@DbTable'), P('('), S('"owner"'), P(')\n'),
  K('data class '), T('OwnerSummary'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(',\n'),
  P('    '), K('val '), P('firstName: '), T('String'), P(',\n'),
  P('    '), K('val '), P('lastName: '), T('String'), P(',\n'),
  P(') : '), T('Projection'), P('<'), T('Int'), P('>'),
].join('');

const CODE_STORM_VIEW = [
  C('// Database views map the same way; the class name matches the view here,\n'),
  C('// so no @DbTable is needed (OwnerAddressView -> owner_address_view)\n'),
  K('data class '), T('OwnerAddressView'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(',\n'),
  P('    '), K('val '), P('fullName: '), T('String'), P(',\n'),
  P('    '), K('val '), P('address: '), T('String'), P(',\n'),
  P('    '), K('val '), P('city: '), T('String'), P(',\n'),
  P(') : '), T('Projection'), P('<'), T('Int'), P('>'),
].join('');

const CODE_STORM_QUERY = [
  K('val '), P('owners = orm.'), F('projection'), P('<'), T('OwnerSummary'), P('>().'), F('findAll'), P('()\n\n'),
  C('// Filters are type-checked against the generated metamodel.\n'),
  K('val '), P('smiths = orm.'), F('projection'), P('<'), T('OwnerSummary'), P('>().'), F('select'), P('()\n'),
  P('    .'), F('where'), P('('), T('OwnerSummary_'), P('.lastName, '), T('EQUALS'), P(', '), S('"Smith"'), P(')\n'),
  P('    .resultList'),
].join('');

const SQL_STORM_QUERY = [
  QC('-- findAll(): only the projected columns leave the database'), '\n',
  QK('SELECT'), ' o.id, o.first_name, o.last_name ', QK('FROM'), ' owner o\n\n',
  QC('-- where(OwnerSummary_.lastName, EQUALS, "Smith")'), '\n',
  QK('SELECT'), ' o.id, o.first_name, o.last_name ', QK('FROM'), ' owner o ', QK('WHERE'), ' o.last_name = ', QQ('?'),
].join('');

const CODE_STORM_PQ = [
  C('// A read model backed by SQL you control.\n'),
  A('@ProjectionQuery'), P('('), S('"""\n    SELECT o.id, o.first_name, o.last_name, COUNT(p.id) AS pet_count\n    FROM owner o\n    LEFT JOIN pet p ON p.owner_id = o.id\n    GROUP BY o.id, o.first_name, o.last_name\n"""'), P(')\n'),
  K('data class '), T('OwnerWithPetCount'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(',\n'),
  P('    '), K('val '), P('firstName: '), T('String'), P(',\n'),
  P('    '), K('val '), P('lastName: '), T('String'), P(',\n'),
  P('    '), K('val '), P('petCount: '), T('Int'), P(',\n'),
  P(') : '), T('Projection'), P('<'), T('Int'), P('>\n\n'),
  C('// Type-safe predicates apply on top, even on the aggregate\n'),
  K('val '), P('prolificOwners = orm.'), F('projection'), P('<'), T('OwnerWithPetCount'), P('>()\n'),
  P('    .'), F('findAll'), P('('), T('OwnerWithPetCount_'), P('.petCount '), K('greaterEq'), P(' '), N('3'), P(')'),
].join('');

const SQL_STORM_PQ = [
  QC('-- the projection query runs as a derived table; predicates apply on top'), '\n',
  QK('SELECT'), ' x.id, x.first_name, x.last_name, x.pet_count\n',
  QK('FROM'), ' (\n',
  '    ', QK('SELECT'), ' o.id, o.first_name, o.last_name, ', QK('COUNT'), '(p.id) ', QK('AS'), ' pet_count\n',
  '    ', QK('FROM'), ' owner o\n',
  '    ', QK('LEFT JOIN'), ' pet p ', QK('ON'), ' p.owner_id = o.id\n',
  '    ', QK('GROUP BY'), ' o.id, o.first_name, o.last_name\n',
  ') x\n',
  QK('WHERE'), ' x.pet_count >= ', QQ('?'),
].join('');

const CODE_STORM_NESTED = [
  A('@DbTable'), P('('), S('"pet"'), P(')\n'),
  K('data class '), T('PetView'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(',\n'),
  P('    '), K('val '), P('name: '), T('String'), P(',\n'),
  P('    '), A('@FK'), P(' '), K('val '), P('owner: '), T('OwnerSummary'), P(',   '), C('// a projection nested in a projection\n'),
  P(') : '), T('Projection'), P('<'), T('Int'), P('>\n\n'),
  C('// Predicates follow the nested path, checked at compile time\n'),
  K('val '), P('smithsPets = orm.'), F('projection'), P('<'), T('PetView'), P('>().'), F('select'), P('()\n'),
  P('    .'), F('where'), P('('), T('PetView_'), P('.owner.lastName, '), T('EQUALS'), P(', '), S('"Smith"'), P(')\n'),
  P('    .resultList'),
].join('');

const SQL_STORM_NESTED = [
  QC('-- the nested projection joins in the same query, no N+1'), '\n',
  QK('SELECT'), ' p.id, p.name, o.id, o.first_name, o.last_name\n',
  QK('FROM'), ' pet p\n',
  QK('INNER JOIN'), ' owner o ', QK('ON'), ' p.owner_id = o.id\n',
  QK('WHERE'), ' o.last_name = ', QQ('?'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Projections</div>
  <h1>Projections: read-only <span class="grad">views on your data</span></h1>
  <p class="dek">Most of an application only reads. A Storm projection models that read-only view of the data as a first-class type, sometimes over a table, sometimes over a database view, with the write API gone by construction.</p>
  <div class="meta"><span>Series · JPA to Storm</span><span>6 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>An owners list screen needs three columns of the <code>owner</code> table. A reporting screen reads from a database view the DBA maintains. Neither should ever write, and both deserve to be modeled as real types with real queries, not as ad-hoc result shapes scattered across repository methods.</p>

  <h2><span class="hno">02</span>The JPA way</h2>
  <p>JPA has entities, and entities are read-write. The usual approximation of a read model is an entity mapped over a view, marked immutable:</p>
  ${editor({file: 'OwnerAddressView.kt', tag: 'Kotlin · JPA', code: CODE_JPA_IMMUTABLE})}
  <p>It works, but read-onliness is a runtime convention, not a property of the type. <code>@Immutable</code> is Hibernate-specific, the instances still live in the persistence context, and updates are silently skipped rather than rejected by the compiler. For the other half of the task, a narrow read-only slice of a table, there is no good answer at all: mapping a second entity onto the same table leaves the persistence context managing two disconnected representations of the same row, both of them writable.</p>

  <h2><span class="hno">03</span>The Storm way</h2>
  <p>Storm separates the concept. An <code>Entity</code> is read-write; a <code>Projection</code> is a read-only view on the data. It can map a subset of a table's columns:</p>
  ${editor({file: 'OwnerSummary.kt', tag: 'Kotlin · Storm', code: CODE_STORM_SUBSET})}
  <p>Or a database view:</p>
  ${editor({file: 'OwnerAddressView.kt', tag: 'Kotlin · Storm', code: CODE_STORM_VIEW})}
  <p>Projections get their own repository with the full read API: <code>findAll</code>, <code>findById</code>, counting, paging, and the type-safe query builder. There is no <code>insert</code>, <code>update</code>, or <code>delete</code> to misuse; read-onliness is enforced by the type system, not by an annotation the runtime promises to respect:</p>
  ${editor({file: 'OwnerService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_QUERY, sql: SQL_STORM_QUERY})}
  <p>The record selects exactly its own fields, serializes directly to JSON with no proxy surprises, and <code>OwnerSummary_.lastName</code> is checked by the compiler against the generated metamodel.</p>

  <h2><span class="hno">04</span>Read models are built for reuse</h2>
  <p>Because a projection is a modeled type rather than a query result, the rest of the codebase can lean on it. Projections compose: other projections and entities can reference them with <code>@FK</code>, Storm joins them in the same query, and where predicates follow the nested path with compile-time checking:</p>
  ${editor({file: 'PetView.kt', tag: 'Kotlin · Storm', code: CODE_STORM_NESTED, sql: SQL_STORM_NESTED})}
  <p>The nested <code>OwnerSummary</code> instances are shared per id across the result list, so a thousand pets owned by fifty owners hydrate into fifty owner objects. See the <a class="tlink" href="/tutorials/n-plus-one">N+1 tutorial</a> for how that sharing works.</p>
  <p>A projection can also be backed by SQL of its own via <code>@ProjectionQuery</code> and still behave like any other queryable type. Storm runs the annotated SQL as a derived table, so type-safe predicates work on top of it, including on the aggregate, with no HAVING gymnastics:</p>
  ${editor({file: 'OwnerWithPetCount.kt', tag: 'Kotlin · Storm', code: CODE_STORM_PQ, sql: SQL_STORM_PQ})}
  <div class="note">A projection is not a DTO trick; it is a modeled, reusable view on your data. When all you need is a custom result shape for one query, Storm has lighter tools: see <a href="/tutorials/query-results">Typed query results</a> for case-by-case result mapping and <a href="/tutorials/sql-templates">SQL templates</a> for one-off shapes inside full SQL.</div>

  <h2><span class="hno">05</span>Side by side</h2>
  <table class="cmp">
    <tr><th></th><th>JPA with Hibernate</th><th>Storm</th></tr>
    <tr><td>Read-only concept</td><td>None in JPA; Hibernate's <code>@Immutable</code> by convention</td><td><code>Projection</code>: the write API does not exist</td></tr>
    <tr><td>Subset of a table</td><td>No good option; second entities conflict</td><td><code>@DbTable</code> points the projection at the table</td></tr>
    <tr><td>Database views</td><td>Entity over the view, still managed state</td><td>A projection, plain immutable records</td></tr>
    <tr><td>Reuse</td><td>Entity semantics wherever it goes</td><td>Nests in other types, filters with typed predicates, own repository</td></tr>
  </table>

  <h2><span class="hno">06</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/projections">Projections</a>
    <a href="/docs/repositories">Repositories</a>
    <a href="/docs/serialization">Serialization</a>
    <a href="/docs/metamodel">Metamodel</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function ProjectionsTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="projections" body={BODY} />;
}
