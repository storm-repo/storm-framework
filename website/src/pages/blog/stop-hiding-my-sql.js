import React from 'react';
import {BlogPage} from '../../components/blog/blogTheme';
import {editor, K, T, S, C, F, P} from '../../components/tutorial/tutorialTheme';

const TITLE = 'Stop hiding my SQL: why ORM queries should stay visible';
const DESC =
  'SQL is the one interface every relational database and every backend ' +
  "engineer already shares. An ORM's job is to make it type-safe, not to " +
  'replace it with a second query language.';
const SLUG = 'stop-hiding-my-sql';
const DATE = '2026-01-20';

const CODE_SQL_TEMPLATE = [
  C('// Only RANK() OVER is raw SQL; the columns and table are typed\n'),
  K('data class '), T('RankedCity'), P('('), K('val '), P('name: '), T('String'), P(', '), K('val '), P('rank: '), T('Long'), P(')\n\n'),
  K('val '), P('ranked = orm.'), F('query'), P(' { '), S('"""'), P('\n'),
  P('    '), K('SELECT'), P(' '), T('\${City_.name}'), P(', '), K('RANK'), P('() '), K('OVER'), P(' ('), K('ORDER BY'), P(' '), T('\${City_.population}'), P(' '), K('DESC'), P(')\n'),
  P('    '), K('FROM'), P(' '), T('\${City::class}'), P('\n'),
  P('    '), K('WHERE'), P(' '), T('\${City_.country}'), P(' = '), T('\$country'), P('\n'),
  S('"""'), P(' }.'), F('resultList'), P('<'), T('RankedCity'), P('>()'),
].join('');

const BODY = `
<div class="art">
  <div class="crumbs"><a href="/blog/">Blog</a><span class="sep">/</span>Stop hiding my SQL</div>
  <h1><span class="grad">Stop hiding</span> my SQL</h1>
  <p class="dek">Somewhere along the way, hiding SQL became the default definition of a good ORM. It is worth questioning that, because SQL was rarely the part that caused the pain.</p>
  <div class="meta"><span>January 20, 2026</span><span>SQL</span><span>4 min read</span></div>

  <h2>The second query language problem</h2>
  <p>To hide SQL, an ORM gives you something to write instead: JPQL, a criteria builder, or a fluent DSL that models joins and predicates as method calls. A DSL is a good thing to have, and ST/ORM has one. The problem starts when it becomes the only safe path and SQL becomes the unsafe escape hatch, because that path always ends somewhere. You hit an aggregate, a window function, a recursive CTE, or a database-specific feature the abstraction never modeled, and you drop to a native query. At which point you are writing SQL anyway, except now it is a raw string with no type safety, because the safe path only covered the cases that did not need it.</p>

  <h2>SQL was never the weak point</h2>
  <p>SQL is declarative, portable enough for real work, and understood by almost every backend engineer who works near a relational database. It is one of the most durable interfaces in our field. The weak point in hand-written database code was never the SQL itself. It was the stuff around it: column names as bare strings that no compiler checks, parameters concatenated into the query by hand, result sets mapped to objects by index one brittle line at a time, and mappings that drift silently when the query changes.</p>
  <p>So fix that part. Leave the SQL alone.</p>

  <h2>Type the SQL, do not bury it</h2>
  <p>ST/ORM's SQL templates, the piece that <a class="tlink" href="/blog/why-we-built-storm">started the whole project</a>, let you write real SQL with type-safe interpolation and automatic result mapping. Column references go through the generated metamodel, so a renamed field, a broken path, or a wrong type is a compile error, not a runtime surprise. Interpolated values become bind parameters, so a value is never concatenated into the SQL text. Results map to plain data classes and records. Take a window function, the kind of query a DSL will not express for you, and none of the safety goes away:</p>
  ${editor({file: 'ReportService.kt', tag: 'Kotlin · ST/ORM', code: CODE_SQL_TEMPLATE})}
  <p>For the boring, high-volume queries there is a concise DSL, so you are not writing SQL for a find-by-id. The DSL is there for the common queries; the template is there when SQL is the clearest language for the job. It is not a trapdoor you fall through when the abstraction fails but a first-class way to work, sitting right next to the DSL, so the full power of your database is always one line away. You get typed references, safe parameters, and automatic mapping, while the SQL stays visible.</p>

  <div class="cta">
    <a href="/docs/sql-templates" class="btn primary">SQL templates →</a>
    <a href="/docs/metamodel" class="btn">The metamodel</a>
  </div>
</div>`;

export default function Page() {
  return <BlogPage title={TITLE} description={DESC} slug={SLUG} dateISO={DATE} body={BODY} />;
}
