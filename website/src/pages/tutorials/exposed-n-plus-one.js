import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: eager loading and N+1, Exposed vs Storm. Exposed facts verified
// against the DAO relationships docs: references load lazily on first use,
// .with() batch-loads them eagerly per call site, and loaded references are
// meant to be used within the same transaction.

const TITLE = 'Eager Loading and N+1: Exposed vs Storm';
const DESC =
  'Exposed DAO references load lazily and .with() batches them efficiently, ' +
  'one call site at a time. Storm puts the loading policy in the model, so ' +
  'the N+1 problem has nowhere to come back from.';

const CODE_EXPOSED_DAO = [
  K('object '), T('Users'), P(' : '), T('IntIdTable'), P('('), S('"user"'), P(') {\n'),
  P('    '), K('val '), P('name = '), F('varchar'), P('('), S('"name"'), P(', '), N('100'), P(')\n'),
  P('    '), K('val '), P('city = '), F('reference'), P('('), S('"city_id"'), P(', '), T('Cities'), P(')\n'),
  P('}\n\n'),
  K('class '), T('User'), P('(id: '), T('EntityID'), P('<'), T('Int'), P('>) : '), T('IntEntity'), P('(id) {\n'),
  P('    '), K('companion object'), P(' : '), T('IntEntityClass'), P('<'), T('User'), P('>('), T('Users'), P(')\n'),
  P('    '), K('var '), P('name '), K('by'), P(' '), T('Users'), P('.name\n'),
  P('    '), K('var '), P('city '), K('by'), P(' '), T('City'), P(' '), K('referencedOn'), P(' '), T('Users'), P('.city   '), C('// loads lazily, on first touch\n'),
  P('}'),
].join('');

const CODE_EXPOSED_LOOP = [
  C('// Lazy by default: each city loads when first touched\n'),
  K('val '), P('lines = '), F('transaction'), P(' {\n'),
  P('    '), T('User'), P('.'), F('all'), P('().'), F('map'), P(' { '), S('"${it.name} lives in ${it.city.name}"'), P(' }   '), C('// 1 + N\n'),
  P('}\n\n'),
  C('// The fix: opt into eager loading, one call site at a time\n'),
  K('val '), P('fixed = '), F('transaction'), P(' {\n'),
  P('    '), T('User'), P('.'), F('all'), P('().'), F('with'), P('('), T('User'), P('::city)\n'),
  P('        .'), F('map'), P(' { '), S('"${it.name} lives in ${it.city.name}"'), P(' }\n'),
  P('}'),
].join('');

const SQL_EXPOSED_LOOP = [
  QC('-- without with(): one query per city on first touch'), '\n',
  QK('SELECT'), ' ... ', QK('FROM'), ' "user"\n',
  QK('SELECT'), ' ... ', QK('FROM'), ' city ', QK('WHERE'), ' city.id = ', QQ('?'), '\n',
  QK('SELECT'), ' ... ', QK('FROM'), ' city ', QK('WHERE'), ' city.id = ', QQ('?'), '\n',
  QC('-- ...'), '\n\n',
  QC('-- with(User::city): the references batch into a second query'), '\n',
  QK('SELECT'), ' ... ', QK('FROM'), ' "user"\n',
  QK('SELECT'), ' ... ', QK('FROM'), ' city ', QK('WHERE'), ' city.id ', QK('IN'), ' (', QQ('?'), ', ', QQ('?'), ', ', QQ('?'), ')',
].join('');

const CODE_STORM_MODEL = [
  C('// The loading policy is declared once, in the model\n'),
  K('data class '), T('User'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('name: '), T('String'), P(',\n'),
  P('    '), A('@FK'), P(' '), K('val '), P('city: '), T('City'), P(',   '), C('// plain field: joined in the same query, always\n'),
  P(') : '), T('Entity'), P('<'), T('Int'), P('>\n\n'),
  K('val '), P('lines = orm.'), F('entity'), P('<'), T('User'), P('>().'), F('findAll'), P('()\n'),
  P('    .'), F('map'), P(' { '), S('"${it.name} lives in ${it.city.name}"'), P(' }   '), C('// no database access in the loop'),
].join('');

const SQL_STORM_MODEL = [
  QC('-- one query, for every caller, with nothing to remember'), '\n',
  QK('SELECT'), ' u.id, u.name, c.id, c.name\n',
  QK('FROM'), ' "user" u\n',
  QK('INNER JOIN'), ' city c ', QK('ON'), ' u.city_id = c.id',
].join('');

const CODE_STORM_REF = [
  C('// When laziness is what you want, it is a type, not a default\n'),
  K('data class '), T('User'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('name: '), T('String'), P(',\n'),
  P('    '), A('@FK'), P(' '), K('val '), P('city: '), T('Ref'), P('<'), T('City'), P('>,   '), C('// reads city_id, joins nothing\n'),
  P(') : '), T('Entity'), P('<'), T('Int'), P('>\n\n'),
  K('val '), P('user = orm.'), F('entity'), P('<'), T('User'), P('>().'), F('getById'), P('('), N('1'), P(')\n'),
  K('val '), P('city = user.city.'), F('fetch'), P('()   '), C('// loading is a visible, deliberate call'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Exposed vs Storm: eager loading</div>
  <h1>Eager loading and <span class="grad">N+1</span></h1>
  <p class="dek">Exposed's answer to N+1 is a good one: batch the references with one call. The difference is where that decision lives. In Exposed it is made per call site; in Storm it is made once, in the model, and every query inherits it.</p>
  <div class="meta"><span>Series · Exposed to Storm</span><span>5 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>Render 50 users with the name of the city each lives in. Two tables, one foreign key. The question every persistence library answers differently: when does the city load?</p>

  <h2><span class="hno">02</span>The Exposed way</h2>
  <p>Exposed's DAO layer models the relation with a delegated property, and its laziness is deliberate: nothing loads until touched:</p>
  ${editor({file: 'User.kt', tag: 'Kotlin · Exposed DAO', code: CODE_EXPOSED_DAO})}
  <p>Touched in a loop, each reference loads with its own query. The remedy is <code>.with()</code>, and credit where due: it batches all the references into a single IN query, which is efficient:</p>
  ${editor({file: 'FeedService.kt', tag: 'Kotlin · Exposed DAO', code: CODE_EXPOSED_LOOP, sql: SQL_EXPOSED_LOOP})}
  <p>The catch is not the mechanism; it is the location of the decision. Eager loading is opted into per call site, so every new query that touches the relation is a fresh chance to forget it, and the query that forgets looks identical to the one that does not. Abstraction itself is not the issue; Storm's one-liners do not look like SQL either. The difference is that here two very different SQL behaviors, the batched path and the 1 + N path, hide behind nearly identical code, separated only by a call that is easy to omit. Loaded references are also meant to stay within the transaction that loaded them, so the pattern shapes how results move through the application.</p>

  <h2><span class="hno">03</span>The Storm way</h2>
  <p>Storm moves the decision into the model. A relation declared with its plain type is always loaded, joined in the same query, for every caller. The code is as concise as an abstraction can be, and it is deterministic: the same call produces the same SQL, every time, for everyone:</p>
  ${editor({file: 'FeedService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_MODEL, sql: SQL_STORM_MODEL})}
  <p>There is no eager-loading call to remember because there is no lazy default to escape. And when deferring the load is genuinely the right call, that decision is also made in the model, as a type:</p>
  ${editor({file: 'Entities.kt', tag: 'Kotlin · Storm', code: CODE_STORM_REF})}
  <p>Refs that point to the same id share one instance inside a transaction, so fetching across a result list loads each distinct city once. The <a class="tlink" href="/tutorials/n-plus-one">JPA edition of this tutorial</a> covers the mechanics in more depth, and query counts are one-line assertions with <a class="tlink" href="/tutorials/testing">SqlCapture</a>.</p>

  <h2><span class="hno">04</span>Side by side</h2>
  <table class="cmp">
    <tr><th></th><th>Exposed DAO</th><th>Storm</th></tr>
    <tr><td>Default</td><td>Lazy; each reference loads on first touch</td><td>Joined in the same query</td></tr>
    <tr><td>Eager loading</td><td><code>.with()</code>, batched and efficient, per call site</td><td>Nothing to opt into; the model already decided</td></tr>
    <tr><td>Choosing laziness</td><td>The default, everywhere</td><td>A type: <code>Ref&lt;T&gt;</code>, loaded by an explicit <code>fetch()</code></td></tr>
    <tr><td>Result scope</td><td>References belong to their transaction</td><td>Plain values, usable anywhere</td></tr>
  </table>

  <h2><span class="hno">05</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/relationships">Relationships</a>
    <a href="/docs/refs">Refs</a>
    <a href="/docs/entity-cache">Entity Cache</a>
    <a href="/docs/comparison">Framework Comparison</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function ExposedNPlusOneTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="exposed-n-plus-one" body={BODY} />;
}
