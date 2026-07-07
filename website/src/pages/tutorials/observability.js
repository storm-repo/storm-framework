import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: SQL logging and metrics. Storm-way how-to; content verified
// against docs/sql-logging.md (@SqlLog on repositories/methods,
// inlineParameters) and docs/metrics.md (JMX template/dirty-check metrics).

const TITLE = 'Seeing What Storm Does: SQL Logging and Metrics';
const DESC =
  'Annotate a repository with @SqlLog to see every statement it runs, inline ' +
  'the parameters to get copy-paste executable SQL, and read cache and ' +
  'dirty-checking behavior from the built-in metrics.';

const CODE_SQLLOG = [
  C('// Log every statement this repository executes\n'),
  A('@SqlLog'), P('\n'),
  K('interface '), T('UserRepository'), P(' : '), T('EntityRepository'), P('<'), T('User'), P(', '), T('Int'), P('> {\n\n'),
  P('    '), K('fun '), F('findByEmail'), P('(email: '), T('String'), P('): '), T('User'), P('? =\n'),
  P('        '), F('find'), P('('), T('User_'), P('.email '), K('eq'), P(' email)\n\n'),
  P('    '), K('fun '), F('findActiveUsers'), P('(): '), T('List'), P('<'), T('User'), P('> =\n'),
  P('        '), F('findAll'), P('('), T('User_'), P('.active '), K('eq'), P(' '), K('true'), P(')\n'),
  P('}'),
].join('');

const CODE_INLINE = [
  C('// For debugging: inline the bound values into the logged SQL\n'),
  A('@SqlLog'), P('(inlineParameters = '), K('true'), P(')\n'),
  K('interface '), T('UserRepository'), P(' : '), T('EntityRepository'), P('<'), T('User'), P(', '), T('Int'), P('> {\n'),
  P('    '), K('fun '), F('findByEmail'), P('(email: '), T('String'), P('): '), T('User'), P('? =\n'),
  P('        '), F('find'), P('('), T('User_'), P('.email '), K('eq'), P(' email)\n'),
  P('}'),
].join('');

const SQL_INLINE = [
  QC('-- inlineParameters = false (default): the statement as sent'), '\n',
  QK('SELECT'), ' u.id, u.email ', QK('FROM'), ' "user" u ', QK('WHERE'), ' u.email = ', QQ('?'), '\n\n',
  QC('-- inlineParameters = true: paste it straight into your database client'), '\n',
  QK('SELECT'), ' u.id, u.email ', QK('FROM'), ' "user" u ', QK('WHERE'), ' u.email = ', QQ("'alice@example.com'"),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Observability</div>
  <h1>Seeing what <span class="grad">Storm does</span></h1>
  <p class="dek">Predictable SQL is only reassuring if you can look at it. @SqlLog shows every statement a repository runs, inlined parameters make the output executable, and metrics expose what the caches are doing.</p>
  <div class="meta"><span>Series · The Storm way</span><span>3 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>A query misbehaves in one corner of the application. You want to see the exact SQL that corner runs, reproduce it in a database client, and confirm the fix, without turning on firehose logging for the whole system.</p>

  <h2><span class="hno">02</span>Scoped SQL logging</h2>
  <p><code>@SqlLog</code> works at the repository or method level, so the logging follows the code you are investigating rather than the whole datasource. It lives in <code>storm-foundation</code> and needs no Spring AOP:</p>
  ${editor({file: 'UserRepository.kt', tag: 'Kotlin · Storm', code: CODE_SQLLOG})}

  <h2><span class="hno">03</span>Copy-paste executable SQL</h2>
  <p>By default the log shows the statement as sent, placeholders included. For debugging, <code>inlineParameters = true</code> substitutes the bound values, producing SQL you can paste into a client to inspect the result set or check the plan with EXPLAIN:</p>
  ${editor({file: 'UserRepository.kt', tag: 'Kotlin · Storm', code: CODE_INLINE, sql: SQL_INLINE})}
  <p>Log level and logger name are configurable per annotation, so the output can route to its own appender. Inlined values are for debugging, not production logs; keep the default for statements that carry sensitive data.</p>

  <h2><span class="hno">04</span>Beyond logging</h2>
  <p>Two companions complete the picture. In tests, <a class="tlink" href="/tutorials/testing">SqlCapture</a> turns statement counts and shapes into assertions, so what you observed while debugging becomes a regression test. And at runtime, Storm exposes metrics for the template cache, entity cache, and dirty checking over JMX, which answer the quieter questions: are cached plans being reused, and how much work is dirty checking saving. See <a class="tlink" href="/docs/metrics">Metrics</a>.</p>

  <h2><span class="hno">05</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/sql-logging">SQL Logging</a>
    <a href="/docs/metrics">Metrics</a>
    <a href="/docs/testing">Testing</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" target="_blank" rel="noopener" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function ObservabilityTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="observability" body={BODY} />;
}
