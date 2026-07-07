import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: dropping to full SQL, JPA native queries vs Storm SQL templates.

const TITLE = 'Full SQL Without Giving Up Safety: Native Queries vs Storm';
const DESC =
  'JPA native queries need interface proxies or @SqlResultSetMapping to produce ' +
  'typed results, and dynamic SQL invites concatenation. Storm\'s SQL templates ' +
  'map rows onto plain data classes, turn interpolated values into bind ' +
  'parameters, and understand your model.';

const CODE_JPA_NATIVE = [
  C('// Spring Data maps native rows onto an interface projection by alias\n'),
  K('interface '), T('RankedCity'), P(' {\n'),
  P('    '), K('val '), P('name: '), T('String'), P('\n'),
  P('    '), K('val '), P('population: '), T('Int'), P('\n'),
  P('    '), K('val '), P('rank: '), T('Long'), P('\n'),
  P('}\n\n'),
  A('@Query'), P('(\n'),
  P('    value = '), S('"""\n        SELECT name, population,\n               RANK() OVER (ORDER BY population DESC) AS rank\n        FROM city\n        WHERE country = :country\n    """'), P(',\n'),
  P('    nativeQuery = '), K('true'), P(',\n'),
  P(')\n'),
  K('fun '), F('rankedCities'), P('(country: '), T('String'), P('): '), T('List'), P('<'), T('RankedCity'), P('>'),
].join('');

const CODE_STORM_QUERY = [
  K('data class '), T('RankedCity'), P('('), K('val '), P('name: '), T('String'), P(', '), K('val '), P('population: '), T('Int'), P(', '), K('val '), P('rank: '), T('Long'), P(')\n\n'),
  K('val '), P('ranked = orm.'), F('query'), P(' { '), S('"""'), P('\n'),
  P('    '), K('SELECT'), P(' name, population, RANK() '), K('OVER'), P(' ('), K('ORDER BY'), P(' population '), K('DESC'), P(')\n'),
  P('    '), K('FROM'), P(' city\n'),
  P('    '), K('WHERE'), P(' country = '), T('$country'), P('   '), C('-- a Kotlin value, bound safely\n'),
  S('"""'), P(' }.'), F('resultList'), P('<'), T('RankedCity'), P('>()'),
].join('');

const SQL_STORM_QUERY = [
  QC('-- $country compiles to a bind parameter, never concatenation'), '\n',
  QK('SELECT'), ' name, population, ', QK('RANK'), '() ', QK('OVER'), ' (', QK('ORDER BY'), ' population ', QK('DESC'), ')\n',
  QK('FROM'), ' city\n',
  QK('WHERE'), ' country = ', QQ('?'),
].join('');

const CODE_STORM_TEMPLATE = [
  C('// The template engine knows your model: types and columns expand\n'),
  K('val '), P('users = orm.'), F('query'), P(' { '), S('"""'), P('\n'),
  P('    '), K('SELECT'), P(' '), T('${User::class}'), P('\n'),
  P('    '), K('FROM'), P(' '), T('${User::class}'), P('\n'),
  P('    '), K('WHERE'), P(' '), T('${User_.city.name}'), P(' = '), T('$cityName'), P('\n'),
  S('"""'), P(' }.'), F('resultList'), P('<'), T('User'), P('>()'),
].join('');

const SQL_STORM_TEMPLATE = [
  QC('-- User expands to its columns, FROM gains the @FK auto-joins,'), '\n',
  QC('-- and the metamodel path resolves to the right alias'), '\n',
  QK('SELECT'), ' u.id, u.email, u.name, c.id, c.name, c.population, c.country\n',
  QK('FROM'), ' "user" u\n',
  QK('INNER JOIN'), ' city c ', QK('ON'), ' u.city_id = c.id\n',
  QK('WHERE'), ' c.name = ', QQ('?'),
].join('');

const CODE_STORM_DATA = [
  C('// A single-use result shape: Data enables templates, no repository needed\n'),
  A('@DbTable'), P('('), S('"pet"'), P(')\n'),
  K('data class '), T('PetWithOwner'), P('(\n'),
  P('    '), K('val '), P('name: '), T('String'), P(',\n'),
  P('    '), K('val '), P('birthDate: '), T('LocalDate'), P('?,\n'),
  P('    '), A('@FK'), P(' '), K('val '), P('owner: '), T('Owner'), P(',\n'),
  P(') : '), T('Data'), P('\n\n'),
  K('val '), P('pets = orm.'), F('query'), P(' { '), S('"""'), P('\n'),
  P('    '), K('SELECT'), P(' '), T('${PetWithOwner::class}'), P('\n'),
  P('    '), K('FROM'), P(' '), T('${PetWithOwner::class}'), P('\n'),
  P('    '), K('WHERE'), P(' '), T('${Owner_.city}'), P(' = '), T('$city'), P('\n'),
  S('"""'), P(' }.'), F('resultList'), P('<'), T('PetWithOwner'), P('>()'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>SQL templates</div>
  <h1>Full SQL without <span class="grad">giving up safety</span></h1>
  <p class="dek">Every ORM eventually meets a query it cannot express. JPA answers with native queries and untyped rows. Storm treats full SQL as a first-class citizen with the same safety as the rest of the framework.</p>
  <div class="meta"><span>Series · JPA to Storm</span><span>6 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>A report needs a window function: rank cities by population within a country. No entity abstraction is going to express <code>RANK() OVER</code>, and it should not have to. The question is what dropping to SQL costs you.</p>

  <h2><span class="hno">02</span>The JPA way</h2>
  <p>JPA's escape hatch is the native query, and Spring Data can map its rows onto an interface projection, so the untyped <code>Object[]</code> days are mostly behind us:</p>
  ${editor({file: 'CityRepository.kt', tag: 'Kotlin · JPA', code: CODE_JPA_NATIVE})}
  <p>This works, with a contract attached. Every property must line up with a column alias, and that contract is verified at runtime, per query. What comes back is a proxy behind an interface, not a real class. If you want an actual class, there is no direct mapping for native queries: constructor expressions are JPQL-only, so you are into <code>@NamedNativeQuery</code> plus <code>@SqlResultSetMapping</code> territory. And the moment the query needs a dynamic clause, string concatenation appears, and with it the possibility of injection.</p>

  <h2><span class="hno">03</span>The Storm way</h2>
  <p>Storm was built SQL-first, so full SQL is not an escape hatch; it is the same query API. Interpolated Kotlin values compile to bind parameters, and rows map positionally onto any data class:</p>
  ${editor({file: 'ReportService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_QUERY, sql: SQL_STORM_QUERY})}
  <p>There is no string concatenation to get wrong: <code>$country</code> is a Kotlin expression, and the template turns it into a <code>?</code> placeholder with a bound value. The result type is an ordinary data class defined next to the query that fills it.</p>

  <h2><span class="hno">04</span>Templates that know your model</h2>
  <p>The same engine understands your entities. Reference a type and it expands to its column list and its FROM clause with the joins your <code>@FK</code> fields imply; reference a metamodel path and it resolves to the right column on the right alias, checked by the compiler:</p>
  ${editor({file: 'UserService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_TEMPLATE, sql: SQL_STORM_TEMPLATE})}
  <p>This is the middle ground JPA never had: hand-written SQL for the parts that need it, generated columns and joins for the parts that do not, in one statement.</p>

  <h2><span class="hno">05</span>One-off result shapes</h2>
  <p>Reports rarely deserve an entity. Mark a result type with <code>Data</code> and it gains template expansion and auto-joins without any repository machinery:</p>
  ${editor({file: 'ReportService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_DATA})}
  <p>This completes a small ladder of result shapes. A plain data class like <code>RankedCity</code> from section 03 needs no marker interface at all: you write the whole SELECT, rows map by position, case by case (see <a class="tlink" href="/tutorials/query-results">Typed query results</a>). Marking a class <code>Data</code> adds template expansion and auto-joins for the type, still with no repository. And when a shape deserves reuse across the codebase, with its own repository, nesting, and predicates, it has become a read model: make it a <code>Projection</code>, the subject of the <a class="tlink" href="/tutorials/projections">Projections tutorial</a>.</p>

  <h2><span class="hno">06</span>Side by side</h2>
  <table class="cmp">
    <tr><th></th><th>JPA native queries</th><th>Storm SQL templates</th></tr>
    <tr><td>Parameters</td><td>Named placeholders; concatenation for dynamic SQL</td><td>Interpolated values compile to bind parameters</td></tr>
    <tr><td>Result mapping</td><td>Interface proxies matched by alias; <code>@SqlResultSetMapping</code> for classes</td><td>Any plain data class, no proxies</td></tr>
    <tr><td>Model awareness</td><td>None; the string is opaque</td><td>Types expand to columns and joins; metamodel paths are compile-checked</td></tr>
    <tr><td>Relation to the ORM</td><td>A bypass around it</td><td>The same engine that powers the query builder</td></tr>
  </table>

  <h2><span class="hno">07</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/sql-templates">SQL Templates</a>
    <a href="/docs/queries">Queries</a>
    <a href="/docs/hydration">Hydration</a>
    <a href="/docs/metamodel">Metamodel</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" target="_blank" rel="noopener" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function SqlTemplatesTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="sql-templates" body={BODY} />;
}
