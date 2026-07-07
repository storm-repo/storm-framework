import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: modeling and querying, Exposed vs Storm. First entry of the
// "Exposed to Storm" series. Tone: fully respectful of Exposed; type safety
// is presented as a tie. The factual difference is repetition: joins and
// mappings are per-query knowledge in Exposed, one-time model knowledge in
// Storm, which compounds with schema size.

const TITLE = 'One Model Instead of Three: Exposed vs Storm';
const DESC =
  'Exposed earned its reputation: JetBrains-built, type-safe end to end, and ' +
  'the library that defined what a Kotlin SQL DSL should feel like. The ' +
  'difference is where knowledge lives: joins and mappings travel with every ' +
  'Exposed query, while Storm declares the model once.';

const CODE_EXPOSED_DSL = [
  C('// The schema lives in table objects, fully type-safe ...\n'),
  K('object '), T('Cities'), P(' : '), T('Table'), P('('), S('"city"'), P(') {\n'),
  P('    '), K('val '), P('id = '), F('integer'), P('('), S('"id"'), P(').'), F('autoIncrement'), P('()\n'),
  P('    '), K('val '), P('name = '), F('varchar'), P('('), S('"name"'), P(', '), N('50'), P(')\n'),
  P('    '), K('override val '), P('primaryKey = '), T('PrimaryKey'), P('(id)\n'),
  P('}\n\n'),
  K('object '), T('Users'), P(' : '), T('Table'), P('('), S('"user"'), P(') {\n'),
  P('    '), K('val '), P('id = '), F('integer'), P('('), S('"id"'), P(').'), F('autoIncrement'), P('()\n'),
  P('    '), K('val '), P('email = '), F('varchar'), P('('), S('"email"'), P(', '), N('255'), P(')\n'),
  P('    '), K('val '), P('name = '), F('varchar'), P('('), S('"name"'), P(', '), N('100'), P(')\n'),
  P('    '), K('val '), P('cityId = '), F('integer'), P('('), S('"city_id"'), P(') '), K('references'), P(' '), T('Cities'), P('.id\n'),
  P('    '), K('override val '), P('primaryKey = '), T('PrimaryKey'), P('(id)\n'),
  P('}\n\n'),
  C('// ... the shape you pass around is a separate declaration ...\n'),
  K('data class '), T('UserRow'), P('('), K('val '), P('email: '), T('String'), P(', '), K('val '), P('name: '), T('String'), P(', '), K('val '), P('cityName: '), T('String'), P(')\n\n'),
  C('// ... and each query bridges the two\n'),
  K('val '), P('inSunnyvale = '), F('transaction'), P(' {\n'),
  P('    ('), T('Users'), P(' '), K('innerJoin'), P(' '), T('Cities'), P(')\n'),
  P('        .'), F('selectAll'), P('()\n'),
  P('        .'), F('where'), P(' { '), T('Cities'), P('.name '), K('eq'), P(' '), S('"Sunnyvale"'), P(' }\n'),
  P('        .'), F('map'), P(' { '), T('UserRow'), P('(it['), T('Users'), P('.email], it['), T('Users'), P('.name], it['), T('Cities'), P('.name]) }\n'),
  P('}\n\n'),
  C('// The next query writes the same join and the same mapping again\n'),
  K('val '), P('atAcme = '), F('transaction'), P(' {\n'),
  P('    ('), T('Users'), P(' '), K('innerJoin'), P(' '), T('Cities'), P(')\n'),
  P('        .'), F('selectAll'), P('()\n'),
  P('        .'), F('where'), P(' { '), T('Users'), P('.email '), K('like'), P(' '), S('"%@acme.io"'), P(' }\n'),
  P('        .'), F('map'), P(' { '), T('UserRow'), P('(it['), T('Users'), P('.email], it['), T('Users'), P('.name], it['), T('Cities'), P('.name]) }\n'),
  P('}'),
].join('');

const CODE_EXPOSED_DAO = [
  C('// The DAO layer trades the mapping for a third representation\n'),
  K('class '), T('User'), P('(id: '), T('EntityID'), P('<'), T('Int'), P('>) : '), T('IntEntity'), P('(id) {\n'),
  P('    '), K('companion object'), P(' : '), T('IntEntityClass'), P('<'), T('User'), P('>('), T('Users'), P(')\n'),
  P('    '), K('var '), P('email '), K('by'), P(' '), T('Users'), P('.email\n'),
  P('    '), K('var '), P('name '), K('by'), P(' '), T('Users'), P('.name\n'),
  P('    '), K('var '), P('city '), K('by'), P(' '), T('City'), P(' '), K('referencedOn'), P(' '), T('Users'), P('.cityId\n'),
  P('}\n\n'),
  K('val '), P('lines = '), F('transaction'), P(' {\n'),
  P('    '), T('User'), P('.'), F('all'), P('().'), F('with'), P('('), T('User'), P('::city)   '), C('// with() opts into eager loading per call site\n'),
  P('        .'), F('map'), P(' { '), S('"${it.name} lives in ${it.city.name}"'), P(' }\n'),
  P('}'),
].join('');

const CODE_STORM_MODEL = [
  C('// The data class is the schema mapping, the query target, and the value\n'),
  K('data class '), T('City'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('name: '), T('String'), P(',\n'),
  P(') : '), T('Entity'), P('<'), T('Int'), P('>\n\n'),
  K('data class '), T('User'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('email: '), T('String'), P(',\n'),
  P('    '), K('val '), P('name: '), T('String'), P(',\n'),
  P('    '), A('@FK'), P(' '), K('val '), P('city: '), T('City'), P(',   '), C('// the join is declared here, once\n'),
  P(') : '), T('Entity'), P('<'), T('Int'), P('>\n\n'),
  K('val '), P('inSunnyvale = orm.'), F('entity'), P('<'), T('User'), P('>().'), F('findAll'), P('('), T('User_'), P('.city.name '), K('eq'), P(' '), S('"Sunnyvale"'), P(')\n\n'),
  C('// The next query reuses the model; the join is never restated\n'),
  K('val '), P('atAcme = orm.'), F('entity'), P('<'), T('User'), P('>().'), F('findAll'), P('('), T('User_'), P('.email '), K('like'), P(' '), S('"%@acme.io"'), P(')'),
].join('');

const SQL_STORM_MODEL = [
  QC('-- both queries derive the join and the column list from the model'), '\n',
  QK('SELECT'), ' u.id, u.email, u.name, c.id, c.name\n',
  QK('FROM'), ' "user" u\n',
  QK('INNER JOIN'), ' city c ', QK('ON'), ' u.city_id = c.id\n',
  QK('WHERE'), ' c.name = ', QQ('?'), '\n\n',
  QK('SELECT'), ' u.id, u.email, u.name, c.id, c.name\n',
  QK('FROM'), ' "user" u\n',
  QK('INNER JOIN'), ' city c ', QK('ON'), ' u.city_id = c.id\n',
  QK('WHERE'), ' u.email ', QK('LIKE'), ' ', QQ('?'),
].join('');

const CODE_STORM_VALUES = [
  C('// Results are plain values: they leave the data layer as they are\n'),
  K('fun '), F('usersIn'), P('(cityName: '), T('String'), P('): '), T('List'), P('<'), T('User'), P('> =\n'),
  P('    orm.'), F('entity'), P('<'), T('User'), P('>().'), F('findAll'), P('('), T('User_'), P('.city.name '), K('eq'), P(' cityName)\n\n'),
  C('// Serialize them, cache them, hand them to another thread; there is\n'),
  C('// no session or transaction they depend on\n'),
  K('val '), P('json = objectMapper.'), F('writeValueAsString'), P('('), F('usersIn'), P('('), S('"Sunnyvale"'), P('))'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Exposed vs Storm: the model</div>
  <h1>One model instead of <span class="grad">three</span></h1>
  <p class="dek">Exposed earned its reputation: built by JetBrains, type-safe end to end, and the library that defined what a Kotlin SQL DSL should feel like. The difference is where knowledge lives. Exposed writes joins and mappings with every query; Storm declares them once, in the model, and that difference compounds as the schema grows.</p>
  <div class="meta"><span>Series · Exposed to Storm</span><span>6 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>Two tables, users and cities, one foreign key. List the users of a city with the city name, then a second query filtering on email, and return typed results from a service so a controller can serialize them. The everyday shape of a data layer.</p>

  <h2><span class="hno">02</span>The Exposed way</h2>
  <p>Exposed pioneered the typed SQL DSL in Kotlin, and a decade of polish shows. The schema lives in table objects, every column reference is checked by the compiler, nothing about the SQL is hidden, and the library carries JetBrains' first-party commitment to the language. For this task it looks like this:</p>
  ${editor({file: 'Users.kt', tag: 'Kotlin · Exposed DSL', code: CODE_EXPOSED_DSL})}
  <p>Nothing here is wrong, and all of it is type-safe. The observation is about where the knowledge lives: the join path and the row mapping belong to each query, so the second query restates both. Two queries mean two copies; a real schema with hundreds of queries means hundreds of copies, and adding a column to <code>UserRow</code> means visiting each one. The DAO layer removes the mapping but adds a third representation of the same data:</p>
  ${editor({file: 'User.kt', tag: 'Kotlin · Exposed DAO', code: CODE_EXPOSED_DAO})}
  <p>DAO entities are comfortable inside the transaction that created them. References load lazily on first touch, with <code>.with()</code> as the per-call-site opt-in to eager loading, and by default a loaded reference is meant to be used within the same transaction. These are coherent choices for an active-record design; they do mean the data layer's outputs are not plain values, which is a constraint the rest of the application designs around. There is also a subtler cost: the SQL a line produces now depends on a modifier that is easy to omit, so <code>User.all()</code> with and without <code>.with(User::city)</code> look almost identical while behaving very differently, the kind of divergence the DSL was designed to avoid.</p>

  <h2><span class="hno">03</span>The Storm way</h2>
  <p>Storm makes a different bet: relations are model knowledge, not query knowledge. The data class declares the join once, and every query reuses it:</p>
  ${editor({file: 'Entities.kt', tag: 'Kotlin · Storm', code: CODE_STORM_MODEL, sql: SQL_STORM_MODEL})}
  <p>The second query never mentions the join because there is nothing to restate; <code>User_.city.name</code> is a compile-checked path through the one declaration that exists. Type safety is a tie between these two libraries. The difference is arithmetic: per-query joins and mappings grow with the number of queries, while a model grows only with the number of tables. On a large database, that is the whole argument.</p>

  <h2><span class="hno">04</span>Results are values</h2>
  <p>The DSL's mapped rows are plain values too; it is the DAO's records that stay bound to their transaction. Storm's results are immutable data classes without the mapping step, so the data layer's output is ordinary Kotlin:</p>
  ${editor({file: 'UserService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_VALUES})}
  <p>Reads do not need a <code>transaction { }</code> wrapper; repositories manage connections per operation, and transactions come in where atomicity matters. Lazy loading exists in Storm too, but as a type: declare the field <code>Ref&lt;City&gt;</code> and loading becomes an explicit <code>fetch()</code> call, visible in code review. The <a class="tlink" href="/tutorials/n-plus-one">N+1 tutorial</a> covers that model in depth.</p>

  <h2><span class="hno">05</span>Side by side</h2>
  <table class="cmp">
    <tr><th></th><th>Exposed</th><th>Storm</th></tr>
    <tr><td>Type safety</td><td>Compile-checked column references</td><td>Compile-checked model paths; a tie</td></tr>
    <tr><td>Model declarations</td><td>Table object plus row mapping (DSL), plus entity class (DAO)</td><td>One data class per table</td></tr>
    <tr><td>Joins</td><td>Written per query</td><td>Declared once via <code>@FK</code>, reused by every query</td></tr>
    <tr><td>Results are</td><td>Rows to map, or entities tied to their transaction</td><td>Immutable values, safe across layers and threads</td></tr>
    <tr><td>Reads</td><td>Inside <code>transaction { }</code></td><td>Repositories manage connections; transactions where atomicity matters</td></tr>
  </table>

  <h2><span class="hno">06</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/entities">Entities</a>
    <a href="/docs/relationships">Relationships</a>
    <a href="/docs/queries">Queries</a>
    <a href="/docs/comparison">Framework Comparison</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" target="_blank" rel="noopener" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function ExposedEntitiesTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="exposed-entities" body={BODY} />;
}
