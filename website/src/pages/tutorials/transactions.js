import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: transactions, Spring @Transactional vs Storm's programmatic API.

const TITLE = 'Transactions Without the Proxy Rules: Spring vs Storm';
const DESC =
  '@Transactional works through proxies, with rules about self-invocation and ' +
  'method visibility that fail silently. Storm\'s transaction blocks make the ' +
  'scope visible in code, add commit callbacks, and stay coroutine-aware.';

const CODE_SPRING_TX = [
  A('@Service'), P('\n'),
  K('class '), T('OrderService'), P('(\n'),
  P('    '), K('private val '), P('orders: '), T('OrderRepository'), P(',\n'),
  P('    '), K('private val '), P('mailer: '), T('Mailer'), P(',\n'),
  P(') {\n'),
  P('    '), A('@Transactional'), P('\n'),
  P('    '), K('fun '), F('placeOrder'), P('(order: '), T('Order'), P(') {\n'),
  P('        orders.'), F('save'), P('(order)\n'),
  P('        mailer.'), F('sendConfirmation'), P('(order)   '), C('// sent before commit: the order may still roll back\n'),
  P('    }\n\n'),
  P('    '), K('fun '), F('importAll'), P('(batch: '), T('List'), P('<'), T('Order'), P('>) {\n'),
  P('        batch.'), F('forEach'), P(' { '), F('placeOrder'), P('(it) }   '), C('// self-invocation: @Transactional is silently ignored\n'),
  P('    }\n'),
  P('}'),
].join('');

const CODE_STORM_TX = [
  C('// The transaction is a block. Its scope is exactly what you can see.\n'),
  F('transaction'), P(' {\n'),
  P('    '), K('val '), P('order = orders.'), F('insertAndFetch'), P('(newOrder)\n'),
  P('    inventory.'), F('decrease'), P('(order.product, order.quantity)\n'),
  P('}\n'),
  C('// commits on success, rolls back on exception, no proxy involved'),
].join('');

const SQL_STORM_TX = [
  QK('BEGIN'), '\n',
  QK('INSERT INTO'), ' "order" (product_id, quantity) ', QK('VALUES'), ' (', QQ('?'), ', ', QQ('?'), ')\n',
  QK('UPDATE'), ' inventory ', QK('SET'), ' stock = stock - ', QQ('?'), ' ', QK('WHERE'), ' product_id = ', QQ('?'), '\n',
  QK('COMMIT'),
].join('');

const CODE_STORM_CALLBACKS = [
  F('transaction'), P(' {\n'),
  P('    '), K('val '), P('order = orders.'), F('insertAndFetch'), P('(newOrder)\n'),
  P('    inventory.'), F('decrease'), P('(order.product, order.quantity)\n\n'),
  P('    '), F('onCommit'), P(' {\n'),
  P('        '), C('// runs only after the commit succeeded: the data is durable\n'),
  P('        mailer.'), F('sendConfirmation'), P('(order)\n'),
  P('        events.'), F('publish'), P('('), T('OrderCreated'), P('(order))\n'),
  P('    }\n\n'),
  P('    '), F('onRollback'), P(' {\n'),
  P('        metrics.'), F('increment'), P('('), S('"orders.failed"'), P(')\n'),
  P('    }\n'),
  P('}'),
].join('');

const CODE_STORM_CONFIG = [
  C('// Propagation, isolation and timeout, visible at the call site\n'),
  F('transaction'), P('(propagation = '), T('REQUIRES_NEW'), P(', isolation = '), T('REPEATABLE_READ'), P(', timeoutSeconds = '), N('5'), P(') {\n'),
  P('    '), K('val '), P('city = orm '), K('insert '), T('City'), P('(name = '), S('"San Jose"'), P(', population = '), N('1_013_240'), P(')\n'),
  P('    orm '), K('insert '), T('User'), P('(email = '), S('"alice@acme.io"'), P(', name = '), S('"Alice"'), P(', city = city)\n'),
  P('}'),
].join('');

const SQL_STORM_CONFIG = [
  QK('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ'), '\n',
  QK('BEGIN'), '\n',
  QK('INSERT INTO'), ' city (name, population) ', QK('VALUES'), ' (', QQ('?'), ', ', QQ('?'), ')\n',
  QK('INSERT INTO'), ' "user" (email, name, city_id) ', QK('VALUES'), ' (', QQ('?'), ', ', QQ('?'), ', ', QQ('?'), ')\n',
  QK('COMMIT'),
].join('');

const CODE_STORM_COROUTINE = [
  C('// Suspend transactions survive dispatcher switches\n'),
  F('transaction'), P(' {\n'),
  P('    '), K('val '), P('pending = orders.'), F('findAllPending'), P('()\n\n'),
  P('    '), K('val '), P('processed = '), F('withContext'), P('('), T('Dispatchers'), P('.'), T('Default'), P(') {\n'),
  P('        '), F('heavyComputation'), P('(pending)   '), C('// the transaction context travels along\n'),
  P('    }\n\n'),
  P('    orders.'), F('update'), P('(processed)          '), C('// still the same transaction\n'),
  P('}'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Transactions</div>
  <h1>Transactions without <span class="grad">the proxy rules</span></h1>
  <p class="dek">@Transactional does a lot for you, and it comes with rules: proxies, self-invocation, visibility, rollback defaults. Storm makes the transaction a block of code whose scope you can see.</p>
  <div class="meta"><span>Series · JPA to Storm</span><span>6 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>Insert an order and decrease inventory atomically. Send the confirmation email only if the transaction actually committed. This is the bread and butter of transactional code, and also where the classic mistakes live.</p>

  <h2><span class="hno">02</span>The Spring way</h2>
  <p><code>@Transactional</code> handles the atomicity, but it works through a proxy that wraps the bean, and the proxy has rules:</p>
  ${editor({file: 'OrderService.kt', tag: 'Kotlin · Spring', code: CODE_SPRING_TX})}
  <p>Both bugs in that snippet are silent. The email goes out before the commit, so a rollback leaves a customer confirmed for an order that does not exist; the fix requires registering a <code>TransactionSynchronization</code> by hand. And <code>importAll</code> calls <code>placeOrder</code> on <code>this</code> rather than through the proxy, so the annotation simply does not apply: no transaction, no rollback, and nothing in the code or the log tells you. Add the visibility rules and the rollback-only-on-unchecked default, and correct usage depends on knowing the manual.</p>

  <h2><span class="hno">03</span>The Storm way</h2>
  <p>Storm's transaction is a function you call, not an aspect woven around you. The scope is the block, and it works the same in any class, any method, any visibility:</p>
  ${editor({file: 'OrderService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_TX, sql: SQL_STORM_TX})}
  <p>There is no proxy, so there is nothing to self-invoke around. Calling a function that opens a <code>transaction</code> block from anywhere just works, and nesting follows the propagation you specify rather than the annotation plumbing.</p>

  <h2><span class="hno">04</span>Side effects, after the outcome</h2>
  <p>The email problem has a first-class answer: register callbacks inside the block, and they fire only once the outcome is final. Running the email after the block is not equivalent, and not just stylistically: with <code>REQUIRED</code> propagation the block may have joined an outer transaction, in which case the end of the block commits nothing, and the outer transaction may still roll back. Callbacks bind to the physical transaction, so the code stays correct however deeply callers nest it:</p>
  ${editor({file: 'OrderService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_CALLBACKS})}
  <p><code>onCommit</code> runs only when the data is durable; if the commit itself fails, it is skipped and <code>onRollback</code> runs instead. No synchronization registration, no event-listener detour.</p>

  <h2><span class="hno">05</span>Full control when you need it</h2>
  <p>Propagation, isolation, and timeout are parameters of the block, so a reader sees the transaction's guarantees at the call site instead of hunting for annotation attributes:</p>
  ${editor({file: 'Provisioning.kt', tag: 'Kotlin · Storm', code: CODE_STORM_CONFIG, sql: SQL_STORM_CONFIG})}

  <h2><span class="hno">06</span>Coroutine-aware</h2>
  <p><code>transaction</code> is a suspend function, and the transaction context survives dispatcher switches, which annotation-driven transaction management famously does not handle well:</p>
  ${editor({file: 'BatchJob.kt', tag: 'Kotlin · Storm', code: CODE_STORM_COROUTINE})}
  <p>The transaction is part of the coroutine context, so the code inside the switched dispatcher is still transaction-aware: a repository call there joins the same transaction rather than opening a new one. For synchronous code, <code>transactionBlocking</code> is the drop-in equivalent.</p>

  <h2><span class="hno">07</span>Mixing with Spring</h2>
  <p>If you run Spring, the two systems bridge, on one condition: the template must be wired to Spring's transaction management. The Spring Boot starters do this by default when a transaction manager is present; outside Boot, compose the template with <code>springOrmTemplate</code>. With the bridge in place, Storm's blocks delegate to Spring's transaction manager: a <code>transactionBlocking</code> inside a <code>@Transactional</code> method joins the surrounding transaction, and propagation behaves as one system. Without the bridge, the two managers are independent, and a block inside a <code>@Transactional</code> method would open its own connection and its own transaction.</p>
  <p>One trade-off to know: suspend <code>transaction</code> blocks are not available while Spring manages Storm's transactions; Storm rejects them with a clear error. Coroutine-aware transactions belong to setups where Storm manages transactions itself, such as Ktor or standalone services. See <a class="tlink" href="/docs/spring-integration">Spring Integration</a> for the details.</p>

  <h2><span class="hno">08</span>Side by side</h2>
  <table class="cmp">
    <tr><th></th><th>Spring @Transactional</th><th>Storm</th></tr>
    <tr><td>Mechanism</td><td>Proxy woven around the bean</td><td>A function with a block</td></tr>
    <tr><td>Self-invocation</td><td>Silently skips the transaction</td><td>Not a concept; blocks work anywhere</td></tr>
    <tr><td>After-commit effects</td><td>Register a <code>TransactionSynchronization</code></td><td><code>onCommit</code> and <code>onRollback</code> in the block</td></tr>
    <tr><td>Coroutines</td><td>Thread-bound context, fragile across dispatchers</td><td>Suspend transactions survive context switches</td></tr>
  </table>

  <h2><span class="hno">09</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/transactions">Transactions</a>
    <a href="/docs/spring-integration">Spring Integration</a>
    <a href="/docs/error-handling">Error Handling</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" target="_blank" rel="noopener" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function TransactionsTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="transactions" body={BODY} />;
}
