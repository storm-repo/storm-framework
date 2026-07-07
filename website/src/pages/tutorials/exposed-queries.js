import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: queries and full SQL, Exposed vs Storm. Knowledge-mapping for the
// everyday query shapes, plus the one real gap: typed raw SQL. Exposed facts
// verified against the DSL querying/CRUD docs (selectAll().where, select with
// columns + groupBy, exec with a raw ResultSet transform).

const TITLE = 'Queries and Full SQL: Exposed vs Storm';
const DESC =
  'Everyday Exposed queries translate to Storm almost line by line. The gap ' +
  'appears past the DSL: Exposed\'s exec() hands you a raw ResultSet, while ' +
  'Storm\'s SQL templates keep typed rows and bind-safe parameters.';

const CODE_EXPOSED_QUERIES = [
  C('// Filtering: type-safe and readable\n'),
  K('val '), P('atAcme = '), F('transaction'), P(' {\n'),
  P('    '), T('Users'), P('.'), F('selectAll'), P('()\n'),
  P('        .'), F('where'), P(' { '), T('Users'), P('.email '), K('like'), P(' '), S('"%@acme.io"'), P(' }\n'),
  P('        .'), F('map'), P(' { it['), T('Users'), P('.email] to it['), T('Users'), P('.name] }\n'),
  P('}\n\n'),
  C('// Aggregating: select specific columns, group, map the rows\n'),
  K('val '), P('perCity = '), F('transaction'), P(' {\n'),
  P('    '), T('Users'), P('.'), F('select'), P('('), T('Users'), P('.cityId, '), T('Users'), P('.id.'), F('count'), P('())\n'),
  P('        .'), F('groupBy'), P('('), T('Users'), P('.cityId)\n'),
  P('        .'), F('map'), P(' { it['), T('Users'), P('.cityId] to it['), T('Users'), P('.id.'), F('count'), P('()] }\n'),
  P('}'),
].join('');

const CODE_STORM_QUERIES = [
  C('// Filtering: the same idea, one line, no mapping step\n'),
  K('val '), P('atAcme = orm.'), F('findAll'), P('('), T('User_'), P('.email '), K('like'), P(' '), S('"%@acme.io"'), P(')\n\n'),
  C('// Aggregating: supply the select clause, keep the typed rest\n'),
  K('data class '), T('CityCount'), P('('), K('val '), P('city: '), T('City'), P(', '), K('val '), P('count: '), T('Long'), P(')\n\n'),
  K('val '), P('perCity = orm.'), F('entity'), P('<'), T('User'), P('>()\n'),
  P('    .'), F('select'), P('<'), T('CityCount'), P(', _, _> { '), S('"${City::class}, COUNT(*)"'), P(' }\n'),
  P('    .'), F('groupBy'), P('('), T('User_'), P('.city)\n'),
  P('    .resultList'),
].join('');

const SQL_STORM_QUERIES = [
  QC('-- the aggregate: City expands to its columns, the join comes from the model'), '\n',
  QK('SELECT'), ' c.id, c.name, c.population, c.country, ', QK('COUNT'), '(*)\n',
  QK('FROM'), ' "user" u\n',
  QK('INNER JOIN'), ' city c ', QK('ON'), ' u.city_id = c.id\n',
  QK('GROUP BY'), ' u.city_id',
].join('');

const CODE_EXPOSED_EXEC = [
  C('// Past the DSL, exec() hands you the raw JDBC ResultSet\n'),
  K('val '), P('ranked = '), F('transaction'), P(' {\n'),
  P('    '), F('exec'), P('('), S('"""\n        SELECT name, population, RANK() OVER (ORDER BY population DESC) AS rank\n        FROM city\n    """'), P(') { resultSet ->\n'),
  P('        '), F('buildList'), P(' {\n'),
  P('            '), K('while'), P(' (resultSet.'), F('next'), P('()) {\n'),
  P('                '), F('add'), P('('), T('RankedCity'), P('(resultSet.'), F('getString'), P('('), N('1'), P('), resultSet.'), F('getInt'), P('('), N('2'), P('), resultSet.'), F('getLong'), P('('), N('3'), P(')))\n'),
  P('            }\n'),
  P('        }\n'),
  P('    }\n'),
  P('}'),
].join('');

const CODE_STORM_TEMPLATE = [
  C('// Full SQL stays typed: rows map by position, values bind safely\n'),
  K('data class '), T('RankedCity'), P('('), K('val '), P('name: '), T('String'), P(', '), K('val '), P('population: '), T('Int'), P(', '), K('val '), P('rank: '), T('Long'), P(')\n\n'),
  K('val '), P('ranked = orm.'), F('query'), P(' { '), S('"""'), P('\n'),
  P('    '), K('SELECT'), P(' name, population, RANK() '), K('OVER'), P(' ('), K('ORDER BY'), P(' population '), K('DESC'), P(')\n'),
  P('    '), K('FROM'), P(' city\n'),
  P('    '), K('WHERE'), P(' country = '), T('$country'), P('\n'),
  S('"""'), P(' }.'), F('resultList'), P('<'), T('RankedCity'), P('>()'),
].join('');

const SQL_STORM_TEMPLATE = [
  QC('-- $country compiles to a bind parameter'), '\n',
  QK('SELECT'), ' name, population, ', QK('RANK'), '() ', QK('OVER'), ' (', QK('ORDER BY'), ' population ', QK('DESC'), ')\n',
  QK('FROM'), ' city\n',
  QK('WHERE'), ' country = ', QQ('?'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Exposed vs Storm: queries</div>
  <h1>Queries and <span class="grad">full SQL</span></h1>
  <p class="dek">The everyday queries translate almost line by line, and Exposed's are perfectly pleasant to write. The gap opens past the DSL, where a window function or a vendor feature is involved.</p>
  <div class="meta"><span>Series · Exposed to Storm</span><span>5 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>Three query shapes cover most of a data layer: a filter, an aggregate, and the occasional query that outgrows any DSL, here a window function ranking cities by population.</p>

  <h2><span class="hno">02</span>The everyday queries</h2>
  <p>Exposed's DSL handles filters and aggregates with compile-checked columns:</p>
  ${editor({file: 'UserService.kt', tag: 'Kotlin · Exposed', code: CODE_EXPOSED_QUERIES})}
  <p>Storm's versions will read familiarly; the mapping step disappears because results hydrate into your types directly, and the join in the aggregate comes from the model rather than the query:</p>
  ${editor({file: 'UserService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_QUERIES, sql: SQL_STORM_QUERIES})}

  <h2><span class="hno">03</span>Past the DSL</h2>
  <p>Every DSL has an edge. Exposed's escape hatch is <code>exec()</code>, which runs the SQL and hands you the raw JDBC <code>ResultSet</code> to read yourself:</p>
  ${editor({file: 'ReportService.kt', tag: 'Kotlin · Exposed', code: CODE_EXPOSED_EXEC})}
  <p>Storm was built SQL-first, so dropping to full SQL keeps everything the DSL had: rows map positionally onto any data class, and interpolated Kotlin values compile to bind parameters instead of concatenation:</p>
  ${editor({file: 'ReportService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_TEMPLATE, sql: SQL_STORM_TEMPLATE})}
  <p>The template engine goes further than pass-through SQL: type references expand to column lists and joins, and metamodel paths are compile-checked inside the SQL text. The <a class="tlink" href="/tutorials/sql-templates">SQL templates tutorial</a> covers that in depth.</p>

  <h2><span class="hno">04</span>The translation table</h2>
  <table class="cmp">
    <tr><th>Exposed</th><th>Storm</th></tr>
    <tr><td><code>selectAll().where { col like x }</code> plus <code>map</code></td><td><code>findAll(User_.email like x)</code></td></tr>
    <tr><td><code>select(columns).groupBy(col)</code> plus <code>map</code></td><td><code>select&lt;CityCount, _, _&gt; { ... }.groupBy(...)</code>, typed rows</td></tr>
    <tr><td><code>exec(sql) { resultSet -> ... }</code></td><td><code>query { sql }.resultList&lt;T&gt;()</code></td></tr>
    <tr><td>Every query inside <code>transaction { }</code></td><td>Repositories manage connections for reads</td></tr>
  </table>

  <h2><span class="hno">05</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/queries">Queries</a>
    <a href="/docs/sql-templates">SQL Templates</a>
    <a href="/docs/hydration">Hydration</a>
    <a href="/docs/comparison">Framework Comparison</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" target="_blank" rel="noopener" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function ExposedQueriesTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="exposed-queries" body={BODY} />;
}
