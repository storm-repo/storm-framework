import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: transactions, Exposed vs Storm. A knowledge-mapping page: the two
// libraries agree on explicit transaction blocks, so this translates concepts
// rather than argues. Exposed facts verified against the transactions docs
// (parameters, suspendTransaction, nested behavior via savepoints, built-in
// retry via maxAttempts, no built-in commit hooks).

const TITLE = 'Transactions Translate Almost One to One: Exposed vs Storm';
const DESC =
  'Exposed and Storm agree that transactions are explicit blocks, not ' +
  'annotations. If you know one API you nearly know the other; this page maps ' +
  'the concepts and marks the few places where they differ.';

const CODE_EXPOSED_TX = [
  C('// Explicit scope, explicit settings: Exposed got this right\n'),
  F('transaction'), P('(\n'),
  P('    transactionIsolation = '), T('Connection'), P('.'), T('TRANSACTION_REPEATABLE_READ'), P(',\n'),
  P('    readOnly = '), K('false'), P(',\n'),
  P(') {\n'),
  P('    maxAttempts = '), N('3'), P('      '), C('// built-in retry on SQLException\n'),
  P('    queryTimeout = '), N('5'), P('\n\n'),
  P('    '), T('Orders'), P('.'), F('insert'), P(' { it[product] = productId; it[quantity] = '), N('2'), P(' }\n'),
  P('    '), T('Inventory'), P('.'), F('update'), P('({ '), T('Inventory'), P('.product '), K('eq'), P(' productId }) {\n'),
  P('        it[stock] = stock - '), N('2'), P('\n'),
  P('    }\n'),
  P('}'),
].join('');

const CODE_STORM_TX = [
  C('// The same idea, the same shape\n'),
  F('transaction'), P('(isolation = '), T('REPEATABLE_READ'), P(', timeoutSeconds = '), N('5'), P(') {\n'),
  P('    '), K('val '), P('order = orders.'), F('insertAndFetch'), P('(newOrder)\n'),
  P('    inventory.'), F('decrease'), P('(order.product, order.quantity)\n'),
  P('}\n'),
  C('// commits on success, rolls back on exception, like Exposed'),
].join('');

const CODE_STORM_PROPAGATION = [
  C('// Nesting is configured per block, not per database\n'),
  F('transaction'), P(' {\n'),
  P('    orders.'), F('insertAndFetch'), P('(newOrder)\n\n'),
  P('    '), F('transaction'), P('(propagation = '), T('NESTED'), P(') {\n'),
  P('        '), C('// savepoint semantics for this block only\n'),
  P('        audit.'), F('record'), P('(newOrder)\n'),
  P('    }\n\n'),
  P('    '), F('transaction'), P('(propagation = '), T('REQUIRES_NEW'), P(') {\n'),
  P('        '), C('// an independent transaction, committed on its own\n'),
  P('        metrics.'), F('bump'), P('('), S('"orders"'), P(')\n'),
  P('    }\n'),
  P('}'),
].join('');

const CODE_STORM_CALLBACKS = [
  F('transaction'), P(' {\n'),
  P('    '), K('val '), P('order = orders.'), F('insertAndFetch'), P('(newOrder)\n\n'),
  P('    '), F('onCommit'), P(' {\n'),
  P('        mailer.'), F('sendConfirmation'), P('(order)   '), C('// only after the commit succeeded\n'),
  P('    }\n'),
  P('    '), F('onRollback'), P(' {\n'),
  P('        metrics.'), F('increment'), P('('), S('"orders.failed"'), P(')\n'),
  P('    }\n'),
  P('}'),
].join('');

const CODE_STORM_SUSPEND = [
  C('// Exposed: suspendTransaction { }; Storm: transaction is a suspend function\n'),
  F('transaction'), P(' {\n'),
  P('    '), K('val '), P('pending = orders.'), F('findAllPending'), P('()\n\n'),
  P('    '), K('val '), P('processed = '), F('withContext'), P('('), T('Dispatchers'), P('.'), T('Default'), P(') {\n'),
  P('        '), F('heavyComputation'), P('(pending)   '), C('// the transaction context travels along\n'),
  P('    }\n\n'),
  P('    orders.'), F('update'), P('(processed)   '), C('// still the same transaction\n'),
  P('}'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Exposed vs Storm: transactions</div>
  <h1>Transactions translate <span class="grad">almost one to one</span></h1>
  <p class="dek">Exposed and Storm agree on the important part: a transaction is an explicit block of code, not an annotation woven around a bean. If you know one API you nearly know the other. This page maps the concepts and marks the few places they differ.</p>
  <div class="meta"><span>Series · Exposed to Storm</span><span>5 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>Write an order and an inventory change atomically, control isolation and timeouts, nest safely, send a confirmation only after the commit, and do all of it from coroutine code. The full transactional toolbox.</p>

  <h2><span class="hno">02</span>Where the two agree</h2>
  <p>Exposed made explicit transaction scope normal in Kotlin, and its block carries the settings right at the call site:</p>
  ${editor({file: 'OrderService.kt', tag: 'Kotlin · Exposed', code: CODE_EXPOSED_TX})}
  <p>Note <code>maxAttempts</code>: built-in retry on <code>SQLException</code> is a genuinely useful feature that Storm does not have; retries in Storm are application code. Storm's block will look immediately familiar:</p>
  ${editor({file: 'OrderService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_TX})}
  <p><code>transactionIsolation</code> maps to <code>isolation</code>, <code>queryTimeout</code> to <code>timeoutSeconds</code>, <code>readOnly</code> to <code>readOnly</code>. One structural difference sits outside the block: Exposed requires a transaction for every operation including reads, while Storm's repositories manage connections per operation, and the block appears where atomicity matters.</p>

  <h2><span class="hno">03</span>Nesting and propagation</h2>
  <p>In Exposed, nested <code>transaction</code> blocks share the outer transaction by default; setting <code>useNestedTransactions = true</code> on the database turns inner blocks into savepoints. Storm expresses the same choices per block instead of per database, using the propagation vocabulary from enterprise transaction managers:</p>
  ${editor({file: 'OrderService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_PROPAGATION})}
  <p>The default, <code>REQUIRED</code>, joins the surrounding transaction, which is Exposed's default behavior too. <code>NESTED</code> is the savepoint mode, and <code>REQUIRES_NEW</code>, <code>MANDATORY</code>, <code>NEVER</code> and the rest cover the cases a global flag cannot.</p>

  <h2><span class="hno">04</span>After the outcome</h2>
  <p>The place where the toolboxes differ most. With explicit blocks it is tempting to run follow-up logic right after the block, and in Exposed that is the available pattern. It works only as long as the block owns the physical transaction. The moment the same function is called inside an outer transaction, and joining the outer transaction is the default in both libraries, the end of the inner block commits nothing: the outer transaction decides later, and may still roll everything back. Code after the block then announces work that never happened. <code>onCommit</code> exists for exactly this case: it can be registered inside any block, however deeply joined, and fires only after the physical transaction commits:</p>
  ${editor({file: 'OrderService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_CALLBACKS})}
  <p>If the commit itself fails, <code>onCommit</code> is skipped and <code>onRollback</code> runs, so a confirmation can never precede its order. This is also what makes the function composable: a caller can wrap it in a larger transaction without breaking its side effects.</p>

  <h2><span class="hno">05</span>Coroutines</h2>
  <p>Both libraries support suspending transactions: Exposed with <code>suspendTransaction { }</code>, Storm by making <code>transaction</code> a suspend function. Storm's transaction context also survives dispatcher switches inside the block:</p>
  ${editor({file: 'BatchJob.kt', tag: 'Kotlin · Storm', code: CODE_STORM_SUSPEND})}
  <p>Because the transaction is part of the coroutine context, the code inside the switched dispatcher remains transaction-aware: a repository call there joins the same transaction rather than opening a new one.</p>

  <h2><span class="hno">06</span>The translation table</h2>
  <table class="cmp">
    <tr><th>Exposed</th><th>Storm</th></tr>
    <tr><td><code>transaction(transactionIsolation, readOnly)</code></td><td><code>transaction(isolation, readOnly)</code></td></tr>
    <tr><td><code>queryTimeout</code></td><td><code>timeoutSeconds</code></td></tr>
    <tr><td><code>maxAttempts</code>, built-in retry</td><td>Not built in; retries are application code</td></tr>
    <tr><td>Nested blocks share the transaction; <code>useNestedTransactions</code> for savepoints</td><td>Propagation per block: <code>REQUIRED</code>, <code>NESTED</code>, <code>REQUIRES_NEW</code>, ...</td></tr>
    <tr><td>After-commit logic by hand</td><td><code>onCommit { }</code> and <code>onRollback { }</code></td></tr>
    <tr><td><code>suspendTransaction { }</code></td><td><code>transaction { }</code> is a suspend function</td></tr>
    <tr><td>Reads also need a transaction</td><td>Repositories manage connections; blocks where atomicity matters</td></tr>
  </table>

  <h2><span class="hno">07</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/transactions">Transactions</a>
    <a href="/docs/error-handling">Error Handling</a>
    <a href="/docs/comparison">Framework Comparison</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" target="_blank" rel="noopener" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function ExposedTransactionsTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="exposed-transactions" body={BODY} />;
}
