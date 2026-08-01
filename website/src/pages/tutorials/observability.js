import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: SQL logging and summaries. Storm-way how-to; content verified
// against docs/sql-logging.md (st.orm.sql logger, per-type child loggers,
// TRACE values, per-call summaries) and docs/metrics.md (JMX metrics).

const TITLE = 'Seeing What Storm Does: The SQL Log';
const DESC =
  'Raise a log level to see every statement as it executes, raise it further ' +
  'to make the output copy-paste executable, and turn on per-call summaries ' +
  'to see what each request or listener cost the database.';

const CODE_LOGGER = [
  C('# application.yml: the log level is the only switch\n'),
  P('logging:\n'),
  P('  level:\n'),
  P('    st.orm.sql.UserView: ', ), S('DEBUG'), P('   '), C('# one type, not the firehose\n'),
].join('');

const SQL_STATEMENT = [
  QC('-- Every executed statement logs once, prefixed with what it does and targets'), '\n',
  QC('-- SQL (SELECT UserView):'), '\n',
  QK('SELECT'), ' u.id, u.email ', QK('FROM'), ' "user" u ', QK('WHERE'), ' u.email = ', QQ('?'),
].join('');

const CODE_TRACE = [
  C('# TRACE renders the bound values into the statement\n'),
  P('logging:\n'),
  P('  level:\n'),
  P('    st.orm.sql.UserView: ', ), S('TRACE'), P('\n'),
].join('');

const SQL_TRACE = [
  QC('-- Paste it straight into your database client, EXPLAIN and all'), '\n',
  QK('SELECT'), ' u.id, u.email ', QK('FROM'), ' "user" u ', QK('WHERE'), ' u.email = ', QQ("'alice@example.com'"),
].join('');

const CODE_SUMMARY = [
  C('# One summary per unit of work: requests, @Scheduled tasks,\n'),
  C('# Kafka/Rabbit/JMS/SQS listeners: every way work enters the app\n'),
  P('storm:\n'),
  P('  sql-log:\n'),
  P('    enabled: '), K('true'), P('\n'),
  P('    threshold:\n'),
  P('      statements: '), N('50'), P('      '), C('# production guardrail: report only\n'),
  P('      duration: '), S('500ms'), P('    '), C('# the calls that exceed a threshold\n'),
].join('');

const CODE_SUMMARY_OUT = [
  C('SQL (GET /owners): 12 statements, 8 fetches, 214 ms in database, 678 ms total\n'),
  P('   96 ms  6408 rows  7x  Visit         SELECT v.id, … FROM visit v WHERE v.pet_id = ?\n'),
  P('   28 ms     8 rows  8x  City   '), K('fetch'), P('  SELECT c.id, c.name FROM city c WHERE c.id = ?\n'),
  P('   18 ms   112 rows  4x  Pet           SELECT p.id, … FROM pet p WHERE p.owner_id = ?\n'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Observability</div>
  <h1>Seeing what <span class="grad">Storm does</span></h1>
  <p class="dek">Predictable SQL is only reassuring if you can look at it. The SQL log shows every statement where it executes, TRACE makes the output executable, and per-call summaries show what each request cost the database.</p>
  <div class="meta"><span>Series · The Storm way</span><span>3 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>A query misbehaves in one corner of the application. You want to see the exact SQL that corner runs, reproduce it in a database client, and confirm the fix, without turning on firehose logging for the whole system.</p>

  <h2><span class="hno">02</span>Statements, scoped by type</h2>
  <p>Storm logs statements where they execute, under the <code>st.orm.sql</code> logger. Raise the log level; there is no other switch, nothing to annotate, and nothing about the execution changes: compiled query plans and the template cache stay in effect, so what you observe is the path that runs in production. Each entity and projection has its own child logger, so the logging follows the type you are investigating rather than the whole datasource:</p>
  ${editor({file: 'application.yml', tag: 'YAML', code: CODE_LOGGER, sql: SQL_STATEMENT})}

  <h2><span class="hno">03</span>Copy-paste executable SQL</h2>
  <p>At <code>DEBUG</code> the log shows the statement as sent, placeholders included. At <code>TRACE</code> the bound values are rendered into it, producing SQL you can paste into a client to inspect the result set or check the plan with EXPLAIN:</p>
  ${editor({file: 'application.yml', tag: 'YAML', code: CODE_TRACE, sql: SQL_TRACE})}
  <p>Values are database values: credentials, personal data, whatever your entities carry. That is why they appear only at <code>TRACE</code>, the level nobody enables in production by accident; <code>DEBUG</code> is the level to leave available for on-demand diagnosis.</p>

  <h2><span class="hno">04</span>What one call cost</h2>
  <p>Individual statements answer what ran; they do not answer what a unit of work cost, and that total is the part you act on. With one property, every way work enters the application (HTTP requests, <code>@Scheduled</code> tasks, Kafka, RabbitMQ, JMS and SQS listeners) reports as a single summary: one row per distinct statement, heaviest first, with a statement that resolved a reference marked <code>fetch</code>. A statement run many times cheaply ranks above one slow statement when it cost more in total, which is what puts an N+1 at the top instead of burying it under the slowest single query:</p>
  ${editor({file: 'application.yml', tag: 'YAML', code: CODE_SUMMARY})}
  ${editor({file: 'console', tag: 'st.orm.sql.summary', code: CODE_SUMMARY_OUT})}
  <p>The summary carries no parameter values, so it is safe to log in production, and the thresholds turn it into a guardrail that stays silent until a call exceeds one. For a narrower boundary than a request, wrap a block with <code>sqlLog("importOwners")&nbsp;{ }</code>.</p>

  <h2><span class="hno">05</span>Beyond logging</h2>
  <p>Two things sit next to the log. In tests, <a class="tlink" href="/tutorials/testing">SqlCapture</a> turns statement counts, origins and durations into assertions, so what you observed while debugging becomes a regression test. In production, Storm reports queries and transactions as Micrometer observations, alongside JMX metrics for the template cache, entity cache, and dirty checking. The <code>storm.origin</code> tag makes the cost of resolving references a quantity you can chart and alert on. See <a class="tlink" href="/docs/metrics">Metrics</a>.</p>

  <h2><span class="hno">06</span>Keep going</h2>
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
