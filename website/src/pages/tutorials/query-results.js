import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: typed query results (what Spring Data calls DTO projections),
// JPA vs Storm. Storm's
// answer to the per-query result shape is a plain data class mapped by
// position. The reusable read-model concept (Projection) has its own
// tutorial: projections.js.

const TITLE = 'Typed Query Results Without the Mapping Layer: JPA vs Storm';
const DESC =
  'DTO projections in Spring Data JPA rely on interface proxies and constructor ' +
  'expressions that resolve at runtime. In Storm, any data class is a result ' +
  'type: define it next to the query and rows map onto it by position, with no ' +
  'mapping layer to maintain.';

const CODE_JPA_INTERFACE = [
  C('// Interface-based projection: implemented by a runtime proxy.\n'),
  K('interface '), T('OwnerListItem'), P(' {\n'),
  P('    '), K('val '), P('id: '), T('Int'), P('\n'),
  P('    '), K('val '), P('firstName: '), T('String'), P('\n'),
  P('    '), K('val '), P('lastName: '), T('String'), P('\n'),
  P('}\n\n'),
  K('interface '), T('OwnerRepository'), P(' : '), T('JpaRepository'), P('<'), T('Owner'), P(', '), T('Int'), P('> {\n'),
  P('    '), K('fun '), F('findAllProjectedBy'), P('(): '), T('List'), P('<'), T('OwnerListItem'), P('>   '), C('// the method name selects the projection type\n'),
  P('}'),
].join('');

const CODE_JPA_CONSTRUCTOR = [
  K('data class '), T('OwnerDto'), P('('), K('val '), P('id: '), T('Int'), P(', '), K('val '), P('firstName: '), T('String'), P(', '), K('val '), P('lastName: '), T('String'), P(')\n\n'),
  K('interface '), T('OwnerRepository'), P(' : '), T('JpaRepository'), P('<'), T('Owner'), P(', '), T('Int'), P('> {\n\n'),
  C('    // The fully qualified class name lives inside a query string.\n'),
  P('    '), A('@Query'), P('('), S('"SELECT new com.acme.owners.OwnerDto(o.id, o.firstName, o.lastName) FROM Owner o"'), P(')\n'),
  P('    '), K('fun '), F('findAllSummaries'), P('(): '), T('List'), P('<'), T('OwnerDto'), P('>\n'),
  P('}'),
].join('');

const CODE_STORM_RESULT = [
  C('// Any data class is a result type: no marker interface, no registration\n'),
  K('data class '), T('OwnerListItem'), P('('), K('val '), P('id: '), T('Int'), P(', '), K('val '), P('firstName: '), T('String'), P(', '), K('val '), P('lastName: '), T('String'), P(')\n\n'),
  K('val '), P('owners = orm.'), F('query'), P(' { '), S('"""'), P('\n'),
  P('    '), K('SELECT'), P(' id, first_name, last_name\n'),
  P('    '), K('FROM'), P(' owner\n'),
  S('"""'), P(' }.'), F('resultList'), P('<'), T('OwnerListItem'), P('>()'),
].join('');

const CODE_STORM_AGGREGATE = [
  C('// Aggregates and reports work the same way\n'),
  K('data class '), T('VisitCount'), P('('), K('val '), P('petName: '), T('String'), P(', '), K('val '), P('visits: '), T('Long'), P(')\n\n'),
  K('val '), P('counts = orm.'), F('query'), P(' { '), S('"""'), P('\n'),
  P('    '), K('SELECT'), P(' p.name, COUNT(v.id)\n'),
  P('    '), K('FROM'), P(' pet p\n'),
  P('    '), K('JOIN'), P(' visit v '), K('ON'), P(' v.pet_id = p.id\n'),
  P('    '), K('WHERE'), P(' v.visit_date >= '), T('$since'), P('\n'),
  P('    '), K('GROUP BY'), P(' p.name\n'),
  S('"""'), P(' }.'), F('resultList'), P('<'), T('VisitCount'), P('>()'),
].join('');

const SQL_STORM_AGGREGATE = [
  QC('-- $since compiles to a bind parameter, never string concatenation'), '\n',
  QK('SELECT'), ' p.name, ', QK('COUNT'), '(v.id)\n',
  QK('FROM'), ' pet p\n',
  QK('JOIN'), ' visit v ', QK('ON'), ' v.pet_id = p.id\n',
  QK('WHERE'), ' v.visit_date >= ', QQ('?'), '\n',
  QK('GROUP BY'), ' p.name',
].join('');

const CODE_STORM_BUILDER = [
  C('// The query builder selects into a result type too\n'),
  K('data class '), T('CityCount'), P('('), K('val '), P('city: '), T('String'), P(', '), K('val '), P('count: '), T('Long'), P(')\n\n'),
  K('val '), P('counts = orm.'), F('entity'), P('<'), T('Owner'), P('>()\n'),
  P('    .'), F('select'), P('<'), T('CityCount'), P(', _, _> { '), S('"${Owner_.city}, COUNT(*)"'), P(' }\n'),
  P('    .'), F('where'), P('('), T('Owner_'), P('.city '), K('like'), P(' '), S('"S%"'), P(')\n'),
  P('    .'), F('groupBy'), P('('), T('Owner_'), P('.city)\n'),
  P('    .resultList'),
].join('');

const SQL_STORM_BUILDER = [
  QC('-- only the select clause is yours; the rest stays generated and type-safe'), '\n',
  QK('SELECT'), ' o.city, ', QK('COUNT'), '(*)\n',
  QK('FROM'), ' owner o\n',
  QK('WHERE'), ' o.city ', QK('LIKE'), ' ', QQ('?'), '\n',
  QK('GROUP BY'), ' o.city',
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Query results</div>
  <h1>Typed query results without <span class="grad">the mapping layer</span></h1>
  <p class="dek">A list screen needs three columns, a report needs an aggregate, and neither deserves an entity. JPA shapes results with proxies and constructor expressions; in Storm, any data class is a result type.</p>
  <div class="meta"><span>Series · JPA to Storm</span><span>5 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>An owners list screen needs three fields: id, first name, last name. The <code>owner</code> table has a dozen columns. A reporting endpoint needs visit counts per pet, which matches no table at all. Both want a typed result shape without a hand-written mapping layer between query results and DTOs.</p>

  <h2><span class="hno">02</span>The JPA way</h2>
  <p>Spring Data JPA offers two main routes. The first is an interface projection, resolved by method-name convention and implemented by a proxy at runtime:</p>
  ${editor({file: 'OwnerRepository.kt', tag: 'Kotlin · JPA', code: CODE_JPA_INTERFACE})}
  <p>The second is a class-based DTO through a JPQL constructor expression:</p>
  ${editor({file: 'OwnerRepository.kt', tag: 'Kotlin · JPA', code: CODE_JPA_CONSTRUCTOR})}
  <p>Both work, and both resolve at runtime. The constructor expression embeds a fully qualified class name inside a string, so renaming the class or reordering its parameters fails when the query first runs, not when the code compiles. Interface projections return proxies, and which columns are actually selected depends on the projection kind and how the query was derived, so the SQL log is the only place to confirm you got the narrow query you wanted.</p>

  <h2><span class="hno">03</span>The Storm way</h2>
  <p>Storm needs no special machinery for result shapes. Any data class whose constructor matches the query's columns by position and type hydrates directly. Start with the type-safe query builder: you supply only the select clause, and the where and grouping stay compile-checked against the metamodel:</p>
  ${editor({file: 'ReportService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_BUILDER, sql: SQL_STORM_BUILDER})}
  <p>No proxy, no naming convention, no class name inside a string. The result is a list of plain immutable objects that serialize to JSON as they are.</p>

  <h2><span class="hno">04</span>Full SQL works the same way</h2>
  <p>When a query outgrows the builder, or you simply want to write the SQL, the same positional mapping applies. The DTO is just a class, defined next to the query that fills it:</p>
  ${editor({file: 'OwnerService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_RESULT})}
  <p>Aggregates work identically, and interpolated values compile to bind parameters, so dynamic filters stay injection-safe:</p>
  ${editor({file: 'ReportService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_AGGREGATE, sql: SQL_STORM_AGGREGATE})}
  <p>Define the class next to the query, and delete both together when the report goes away. The full template syntax, including letting Storm generate column lists and joins for you, is covered in the <a class="tlink" href="/tutorials/sql-templates">SQL templates tutorial</a>.</p>

  <h2><span class="hno">05</span>When the shape earns more</h2>
  <div class="note">A result class is for mapping on a case-by-case basis. The moment a shape wants to be reused, nested in other types, filtered with type-safe predicates, or served from more than one place, it has become a read model, and Storm has a first-class concept for that: <code>Projection</code>. See <a href="/tutorials/projections">Projections</a>.</div>

  <h2><span class="hno">06</span>Side by side</h2>
  <table class="cmp">
    <tr><th></th><th>Spring Data JPA</th><th>Storm</th></tr>
    <tr><td>Defining a DTO</td><td>An interface plus naming conventions, or a class name inside a JPQL string</td><td>A data class next to the query</td></tr>
    <tr><td>Runtime machinery</td><td>Proxies and reflection over strings</td><td>Positional constructor mapping</td></tr>
    <tr><td>Refactoring safety</td><td>Renames break at first query execution</td><td>The class is ordinary code; the SQL is the only contract</td></tr>
    <tr><td>Reuse path</td><td>Copy the pattern to another repository method</td><td>Promote the shape to a <code>Projection</code></td></tr>
  </table>

  <h2><span class="hno">07</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/hydration">Hydration</a>
    <a href="/docs/sql-templates">SQL Templates</a>
    <a href="/docs/projections">Projections</a>
    <a href="/docs/security">Security</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" target="_blank" rel="noopener" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function QueryResultsTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="query-results" body={BODY} />;
}
