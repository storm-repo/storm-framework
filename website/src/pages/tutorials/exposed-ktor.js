import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: Ktor integration, Exposed vs Storm. Knowledge-mapping for the
// shared audience: Kotlin-native services. Storm facts verified against
// docs/ktor-integration.md (install(Storm), orm extensions, reads without
// transactions, coroutine-propagated transaction blocks).

const TITLE = 'Ktor Services: Exposed and Storm Side by Side';
const DESC =
  'Ktor is home ground for both libraries. Exposed connects a Database and ' +
  'wraps every route operation in a transaction; Storm installs as a plugin, ' +
  'reads without ceremony, and its suspend transactions ride the coroutine context.';

const CODE_EXPOSED_KTOR = [
  K('fun '), T('Application'), P('.'), F('module'), P('() {\n'),
  P('    '), T('Database'), P('.'), F('connect'), P('('), F('hikariDataSource'), P('())\n\n'),
  P('    routing {\n'),
  P('        '), F('get'), P('('), S('"/users/{id}"'), P(') {\n'),
  P('            '), K('val '), P('user = '), F('suspendTransaction'), P(' {   '), C('// every operation needs a transaction\n'),
  P('                '), T('User'), P('.'), F('findById'), P('(call.parameters.'), F('getOrFail'), P('('), S('"id"'), P(').'), F('toInt'), P('())\n'),
  P('            }\n'),
  P('            call.'), F('respond'), P('(user ?: '), T('HttpStatusCode'), P('.'), T('NotFound'), P(')\n'),
  P('        }\n'),
  P('    }\n'),
  P('}'),
].join('');

const CODE_STORM_KTOR = [
  K('interface '), T('UserRepository'), P(' : '), T('EntityRepository'), P('<'), T('User'), P(', '), T('Int'), P('>\n\n'),
  K('fun '), T('Application'), P('.'), F('module'), P('() {\n'),
  P('    '), F('install'), P('('), T('Storm'), P(')   '), C('// pool from application.conf; repositories register automatically\n\n'),
  P('    routing {\n'),
  P('        '), F('get'), P('('), S('"/users/{id}"'), P(') {\n'),
  P('            '), K('val '), P('users = '), F('repository'), P('<'), T('UserRepository'), P('>()\n'),
  P('            '), K('val '), P('user = users.'), F('findById'), P('(call.parameters.'), F('getOrFail'), P('('), S('"id"'), P(').'), F('toInt'), P('())\n'),
  P('            call.'), F('respond'), P('(user ?: '), T('HttpStatusCode'), P('.'), T('NotFound'), P(')\n'),
  P('        }\n'),
  P('    }\n'),
  P('}'),
].join('');

const CODE_STORM_WRITE = [
  F('post'), P('('), S('"/orders"'), P(') {\n'),
  P('    '), K('val '), P('request = call.'), F('receive'), P('<'), T('CreateOrderRequest'), P('>()\n'),
  P('    '), K('val '), P('orders = '), F('repository'), P('<'), T('OrderRepository'), P('>()\n'),
  P('    '), K('val '), P('inventory = '), F('repository'), P('<'), T('InventoryRepository'), P('>()\n\n'),
  P('    '), K('val '), P('order = '), F('transaction'), P(' {   '), C('// suspend-friendly, rides the coroutine context\n'),
  P('        '), K('val '), P('order = orders.'), F('insertAndFetch'), P('(request.'), F('toOrder'), P('())\n'),
  P('        inventory.'), F('decrease'), P('(order.product, order.quantity)\n'),
  P('        '), F('onCommit'), P(' { events.'), F('publish'), P('('), T('OrderCreated'), P('(order)) }\n'),
  P('        order\n'),
  P('    }\n\n'),
  P('    call.'), F('respond'), P('('), T('HttpStatusCode'), P('.'), T('Created'), P(', order)\n'),
  P('}'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Exposed vs Storm: Ktor</div>
  <h1>Ktor services, <span class="grad">side by side</span></h1>
  <p class="dek">Ktor is home ground for both libraries: coroutine-native, annotation-free, Kotlin all the way down. The integration styles differ in where the ceremony sits.</p>
  <div class="meta"><span>Series · Exposed to Storm</span><span>5 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>A Ktor service with a read endpoint and a write endpoint: fetch a user by id, and create an order atomically with an inventory update, publishing an event only after the commit.</p>

  <h2><span class="hno">02</span>The Exposed way</h2>
  <p>Exposed connects a <code>Database</code> at startup and wraps route operations in transactions, with <code>suspendTransaction</code> keeping things coroutine-friendly:</p>
  ${editor({file: 'Application.kt', tag: 'Kotlin · Exposed + Ktor', code: CODE_EXPOSED_KTOR})}
  <p>This works well, and it is how a large share of Ktor services are built today. The ceremony is the transaction wrapper: Exposed requires one around every operation, including single reads, so it appears in every route.</p>

  <h2><span class="hno">03</span>The Storm way</h2>
  <p>Storm ships a Ktor plugin. <code>install(Storm)</code> builds the connection pool from <code>application.conf</code> and automatically registers every repository interface from the compile-time index, created eagerly so a broken definition fails at startup. Route handlers fetch them with a bare <code>repository&lt;T&gt;()</code>, no registration block, no lookup ceremony:</p>
  ${editor({file: 'Application.kt', tag: 'Kotlin · Storm + Ktor', code: CODE_STORM_KTOR})}
  <p>Repositories are stateless, so each read borrows a pooled connection just for the query; there is no transaction wrapper because none is needed. For quick endpoints, bare <code>entity&lt;User&gt;()</code> and <code>projection&lt;T&gt;()</code> extensions skip even the interface. Transactions appear where atomicity matters, and because they are suspend functions propagated through the coroutine context, they compose with Ktor naturally:</p>
  ${editor({file: 'Routes.kt', tag: 'Kotlin · Storm + Ktor', code: CODE_STORM_WRITE})}
  <p>The <code>onCommit</code> callback keeps the event publication out of the transaction, so a rollback never announces an order that does not exist. See the <a class="tlink" href="/tutorials/exposed-transactions">transactions comparison</a> for the full mapping.</p>
  <p>For applications with a service layer, the plugin exposes the template and every repository through Ktor's built-in dependency injection, each under its own interface type: services resolve them with <code>by dependencies</code>. Koin users keep the same wiring with a few lines of application code; the integration docs include the recipe.</p>

  <h2><span class="hno">04</span>The translation table</h2>
  <table class="cmp">
    <tr><th>Exposed + Ktor</th><th>Storm + Ktor</th></tr>
    <tr><td><code>Database.connect(dataSource)</code> at startup</td><td><code>install(Storm)</code>, pool from <code>application.conf</code></td></tr>
    <tr><td><code>suspendTransaction { }</code> around every operation</td><td>Reads bare; <code>transaction { }</code> where atomicity matters</td></tr>
    <tr><td>Query logic in table objects and DAO companions</td><td>Repository interfaces, auto-registered at install, fetched via <code>repository&lt;T&gt;()</code></td></tr>
    <tr><td>Results used inside the transaction (DAO) or mapped out</td><td>Plain values, returned and serialized directly</td></tr>
    <tr><td>Side effects after commit by hand</td><td><code>onCommit { }</code> in the block</td></tr>
  </table>

  <h2><span class="hno">05</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/ktor-integration">Ktor Integration</a>
    <a href="/docs/transactions">Transactions</a>
    <a href="/docs/serialization">Serialization</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" target="_blank" rel="noopener" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function ExposedKtorTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="exposed-ktor" body={BODY} />;
}
