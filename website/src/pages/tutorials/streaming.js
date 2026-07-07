import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: streaming large result sets. Storm-way how-to; content verified
// against docs/batch-streaming.md and docs/hydration.md (resultFlow,
// structured-concurrency cleanup, interner memory safety, batch writes).

const TITLE = 'Streaming Large Result Sets';
const DESC =
  'Process millions of rows with constant memory: resultFlow streams rows as ' +
  'they arrive, structured concurrency cleans up cursors automatically, and ' +
  'batched writes handle the other direction.';

const CODE_FLOW = [
  C('// A Flow streams rows as they arrive; memory stays constant\n'),
  K('val '), P('users: '), T('Flow'), P('<'), T('User'), P('> = orm.'), F('entity'), P('<'), T('User'), P('>().'), F('select'), P('().resultFlow\n\n'),
  P('users.'), F('collect'), P(' { user ->\n'),
  P('    '), F('processUser'), P('(user)   '), C('// one row in memory at a time\n'),
  P('}\n\n'),
  C('// Flow operators compose before anything loads\n'),
  K('val '), P('emails: '), T('List'), P('<'), T('String'), P('> = users.'), F('map'), P(' { it.email }.'), F('toList'), P('()'),
].join('');

const CODE_FILTERED = [
  C('// Push the filtering to the database, stream what remains\n'),
  K('val '), P('recentOrders: '), T('Flow'), P('<'), T('Order'), P('> = orm.'), F('entity'), P('<'), T('Order'), P('>()\n'),
  P('    .'), F('select'), P('()\n'),
  P('    .'), F('where'), P('('), T('Order_'), P('.status '), K('eq'), P(' '), T('Status'), P('.'), T('PENDING'), P(')\n'),
  P('    .resultFlow'),
].join('');

const CODE_TX_STREAM = [
  C('// Read and write consistently inside one transaction\n'),
  F('transaction'), P(' {\n'),
  P('    orm.'), F('select'), P('<'), T('Order'), P('>().resultFlow.'), F('collect'), P(' { order ->\n'),
  P('        orm '), K('update'), P(' order.'), F('copy'), P('(processed = '), K('true'), P(')\n'),
  P('    }\n'),
  P('}'),
].join('');

const CODE_BATCH = [
  C('// The write direction: lists and Flows batch through JDBC batching\n'),
  P('orm '), K('insert'), P(' users              '), C('// batched insert from a list\n\n'),
  K('val '), P('incoming: '), T('Flow'), P('<'), T('User'), P('> = '), F('readFromKafka'), P('()\n'),
  P('orm.'), F('entity'), P('<'), T('User'), P('>().'), F('update'), P('(incoming, batchSize = '), N('500'), P(')   '), C('// stream in, batch out'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Streaming</div>
  <h1>Streaming <span class="grad">large result sets</span></h1>
  <p class="dek">A nightly job touches five million rows. Loading them into a list is a heap dump waiting to happen. Storm streams rows through a Flow with constant memory, and batches the write direction.</p>
  <div class="meta"><span>Series · The Storm way</span><span>4 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>Export, migrate, or reprocess a table that does not fit in memory, with database-side filtering, consistent transactional semantics where needed, and efficient writes on the way back.</p>

  <h2><span class="hno">02</span>Read as a Flow</h2>
  <p>Every query builder exposes <code>resultFlow</code>. Rows hydrate as they arrive from the database, and structured concurrency handles the cleanup: when the Flow completes or the coroutine is cancelled, cursors and connections are released without explicit close calls:</p>
  ${editor({file: 'ExportJob.kt', tag: 'Kotlin · Storm', code: CODE_FLOW})}
  <p>Two details make this safe at scale. Storm's per-query interner only retains entities while your code holds them, so processed rows are collected normally and do not accumulate. And because the Flow is lazy, operators like <code>map</code> and <code>filter</code> compose before any row loads.</p>

  <h2><span class="hno">03</span>Filter in the database</h2>
  <p>Streaming composes with the query builder, so selectivity happens where it belongs:</p>
  ${editor({file: 'OrderJob.kt', tag: 'Kotlin · Storm', code: CODE_FILTERED})}

  <h2><span class="hno">04</span>Stream inside a transaction</h2>
  <p>When the job reads and writes as one atomic operation, wrap the stream in a transaction; the Flow and the updates share the same connection and commit or roll back together:</p>
  ${editor({file: 'ReprocessJob.kt', tag: 'Kotlin · Storm', code: CODE_TX_STREAM})}

  <h2><span class="hno">05</span>The write direction</h2>
  <p>Bulk writes take lists or Flows and execute through JDBC batching, with a configurable batch size for streaming sources:</p>
  ${editor({file: 'ImportJob.kt', tag: 'Kotlin · Storm', code: CODE_BATCH})}

  <h2><span class="hno">06</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/batch-streaming">Batch and Streaming</a>
    <a href="/docs/hydration">Hydration</a>
    <a href="/docs/transactions">Transactions</a>
    <a href="/docs/pagination-and-scrolling">Pagination and Scrolling</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" target="_blank" rel="noopener" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function StreamingTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="streaming" body={BODY} />;
}
