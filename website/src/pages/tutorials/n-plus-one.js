import React, {useEffect} from 'react';
import Head from '@docusaurus/Head';
import {
  TUT_CSS, navHtml, FOOT_HTML, editor, wireSqlToggles,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: the N+1 problem, JPA vs Storm. Part of the "JPA to Storm" series:
// each page takes a familiar JPA task, shows the standard JPA approach next to
// the Storm approach, and lets the reader inspect the SQL both produce.

const TITLE = 'Solving the N+1 Problem: JPA vs Storm';
const DESC =
  'Why JPA list queries degrade into 1 + N selects, what JOIN FETCH and entity ' +
  'graphs actually fix, and how Storm removes the problem by making loading ' +
  'policy part of the entity model. With the SQL each approach runs.';

const CODE_JPA_ENTITY = [
  C('// Open, mutable, nullable: the shape JPA needs (via the kotlin-jpa plugin).\n'),
  A('@Entity'), P('\n'),
  K('class '), T('User'), P('(\n'),
  P('    '), A('@Id'), P(' '), A('@GeneratedValue'), P('\n'),
  P('    '), K('var '), P('id: '), T('Int'), P('? = '), K('null'), P(',\n\n'),
  P('    '), K('var '), P('email: '), T('String'), P(' = '), S('""'), P(',\n'),
  P('    '), K('var '), P('name: '), T('String'), P(' = '), S('""'), P(',\n\n'),
  P('    '), A('@ManyToOne'), P('(fetch = '), T('FetchType'), P('.'), T('LAZY'), P(')\n'),
  P('    '), K('var '), P('city: '), T('City'), P('? = '), K('null'), P(',\n'),
  P(')'),
].join('');

const CODE_JPA_LOOP = [
  K('val '), P('users = userRepository.'), F('findAll'), P('()   '), C('// 1 query\n'),
  K('for'), P(' (user '), K('in'), P(' users) {\n'),
  P('    '), F('render'), P('(user.city!!.name)         '), C('// + 1 query per uninitialized proxy\n'),
  P('}'),
].join('');

const SQL_JPA_LOOP = [
  QC('-- findAll(): one query for the users ...'), '\n',
  QK('SELECT'), ' u.id, u.email, u.name, u.city_id ', QK('FROM'), ' user u\n\n',
  QC('-- ... then one more per city while rendering'), '\n',
  QK('SELECT'), ' c.id, c.name ', QK('FROM'), ' city c ', QK('WHERE'), ' c.id = ', QQ('?'), '\n',
  QK('SELECT'), ' c.id, c.name ', QK('FROM'), ' city c ', QK('WHERE'), ' c.id = ', QQ('?'), '\n',
  QK('SELECT'), ' c.id, c.name ', QK('FROM'), ' city c ', QK('WHERE'), ' c.id = ', QQ('?'), '\n',
  QC('-- 50 users in 50 cities: 51 round trips'),
].join('');

const CODE_JPA_FIXES = [
  K('interface '), T('UserRepository'), P(' : '), T('JpaRepository'), P('<'), T('User'), P(', '), T('Int'), P('> {\n\n'),
  C('    // Fix 1: JOIN FETCH, applied one JPQL query at a time\n'),
  P('    '), A('@Query'), P('('), S('"SELECT u FROM User u JOIN FETCH u.city"'), P(')\n'),
  P('    '), K('fun '), F('findAllWithCity'), P('(): '), T('List'), P('<'), T('User'), P('>\n\n'),
  C('    // Fix 2: an entity graph, applied one call site at a time\n'),
  P('    '), A('@EntityGraph'), P('(attributePaths = ['), S('"city"'), P('])\n'),
  P('    '), K('override fun '), F('findAll'), P('(): '), T('List'), P('<'), T('User'), P('>\n'),
  P('}'),
].join('');

const CODE_STORM_ENTITIES = [
  C('// Loading policy is part of the model, not of each query.\n'),
  K('data class '), T('City'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('name: '), T('String'), P(',\n'),
  P(') : '), T('Entity'), P('<'), T('Int'), P('>\n\n'),
  K('data class '), T('User'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('email: '), T('String'), P(',\n'),
  P('    '), K('val '), P('name: '), T('String'), P(',\n'),
  P('    '), A('@FK'), P(' '), K('val '), P('city: '), T('City'), P(',   '), C('// plain field: joined in the same query, always\n'),
  P(') : '), T('Entity'), P('<'), T('Int'), P('>'),
].join('');

const CODE_STORM_LOOP = [
  K('val '), P('users = userRepository.'), F('findAll'), P('()   '), C('// 1 query, cities included\n'),
  K('for'), P(' (user '), K('in'), P(' users) {\n'),
  P('    '), F('render'), P('(user.city.name)             '), C('// plain field access, no database call\n'),
  P('}'),
].join('');

const SQL_STORM_LOOP = [
  QC('-- findAll() loads the city graph in the same round trip'), '\n',
  QK('SELECT'), ' u.id, u.email, u.name, c.id, c.name\n',
  QK('FROM'), ' "user" u\n',
  QK('INNER JOIN'), ' city c ', QK('ON'), ' u.city_id = c.id\n',
  QC('-- 50 users in 50 cities: 1 round trip'),
].join('');

const CODE_STORM_REF = [
  K('data class '), T('User'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('email: '), T('String'), P(',\n'),
  P('    '), K('val '), P('name: '), T('String'), P(',\n'),
  P('    '), A('@FK'), P(' '), K('val '), P('city: '), T('Ref'), P('<'), T('City'), P('>,   '), C('// Ref field: reads city_id, joins nothing\n'),
  P(') : '), T('Entity'), P('<'), T('Int'), P('>\n\n'),
  K('val '), P('user = userRepository.'), F('getById'), P('('), N('1'), P(')   '), C('// no join issued\n'),
  K('val '), P('city = user.city.'), F('fetch'), P('()           '), C('// loading is a visible, deliberate call'),
].join('');

const SQL_STORM_REF = [
  QC('-- getById(1): no join, the ref holds the foreign key'), '\n',
  QK('SELECT'), ' u.id, u.email, u.name, u.city_id ', QK('FROM'), ' "user" u ', QK('WHERE'), ' u.id = ', QQ('?'), '\n\n',
  QC('-- fetch(): runs only where your code says so'), '\n',
  QK('SELECT'), ' c.id, c.name ', QK('FROM'), ' city c ', QK('WHERE'), ' c.id = ', QQ('?'),
].join('');

const CODE_TEST = [
  A('@StormTest'), P('(scripts = ['), S('"/schema.sql"'), P(', '), S('"/data.sql"'), P('])\n'),
  K('class '), T('UserQueryTest'), P(' {\n\n'),
  P('    '), A('@Test'), P('\n'),
  P('    '), K('fun '), F('`users and cities load in a single query`'), P('(orm: '), T('ORMTemplate'), P(', capture: '), T('SqlCapture'), P(') {\n'),
  P('        '), K('val '), P('users = capture.'), F('execute'), P(' { orm.'), F('entity'), P('<'), T('User'), P('>().'), F('findAll'), P('() }\n\n'),
  P('        users.'), F('forEach'), P(' { '), F('render'), P('(it.city.name) }   '), C('// walk the graph freely\n'),
  P('        capture.'), F('count'), P('('), T('Operation'), P('.'), T('SELECT'), P(') '), K('shouldBe'), P(' '), N('1'), P('\n'),
  P('    }\n'),
  P('}'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Solving the N+1 problem</div>
  <h1>Solving the <span class="grad">N+1 problem</span></h1>
  <p class="dek">The most common performance problem in JPA applications, and how Storm removes the conditions that create it. Side by side, with the SQL each approach actually runs.</p>
  <div class="meta"><span>Series · JPA to Storm</span><span>7 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>Render a list of 50 users with the name of the city each user lives in. Two tables, one foreign key, no aggregation. This is the simplest possible relational read, and it is where most JPA applications quietly issue 51 queries instead of one.</p>

  <h2><span class="hno">02</span>The JPA way</h2>
  <p>A typical JPA mapping marks the relation <code>LAZY</code> so that loading a user does not always drag in its city. Note the shape JPA needs from the Kotlin class: open (via the compiler plugin), mutable, with nullable defaults, and no <code>data class</code>:</p>
  ${editor({file: 'User.kt', tag: 'Kotlin · JPA', code: CODE_JPA_ENTITY})}
  <p>The repository call and the rendering loop both look harmless:</p>
  ${editor({file: 'UserService.kt', tag: 'Kotlin · JPA', code: CODE_JPA_LOOP, sql: SQL_JPA_LOOP})}
  <p>The list query fetches users only. Each <code>user.city</code> holds a proxy, and the first <code>name</code> access on each proxy triggers its own SELECT. Nothing in the code, the types, or the compiler warns you; you find out from the query log, or from production latency. Switching to <code>EAGER</code> does not fix the list case either: JPQL list queries still fetch each city with a follow-up select, it just happens before your code runs. And if a proxy escapes the session before being touched, you get the famous <code>LazyInitializationException</code> instead of a result.</p>

  <h2><span class="hno">03</span>The standard fixes</h2>
  <p>JPA has two well-known remedies, and they work:</p>
  ${editor({file: 'UserRepository.kt', tag: 'Kotlin · JPA', code: CODE_JPA_FIXES})}
  <p>Both share the same limitation: they are opt-in, per query or per call site. The entity declares one loading behavior, individual queries override it, and the compiler verifies none of it. Every new query is a fresh opportunity to forget the fetch clause, which is why the N+1 problem keeps returning to codebases that have already fixed it several times.</p>
  <div class="note">The JPA snippets above follow the shape you will find in Baeldung's <a href="https://www.baeldung.com/spring-hibernate-n1-problem" rel="noopener">guide to the N+1 problem</a>, which is the reference most teams reach for. JOIN FETCH and entity graphs are the right fixes within JPA. This page is about what changes when the problem cannot occur by accident in the first place.</div>

  <h2><span class="hno">04</span>The Storm way</h2>
  <p>Storm moves the loading decision out of individual queries and into the model. A foreign key field declared with its plain entity type is always loaded, in the same query, via a join. There is no lazy variant of it, so there is nothing to forget:</p>
  ${editor({file: 'Entities.kt', tag: 'Kotlin · Storm', code: CODE_STORM_ENTITIES})}
  <p>The same list-and-render code now runs one query, every time, for every caller:</p>
  ${editor({file: 'UserService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_LOOP, sql: SQL_STORM_LOOP})}
  <p>The result is a list of plain, immutable data classes. <code>user.city</code> is a field holding a <code>City</code>, not a proxy holding a session reference. You can serialize it, cache it, or hand it to another thread; there is no session to outlive, so <code>LazyInitializationException</code> does not exist in Storm.</p>
  <p>The join does not bloat memory either. The join result carries the city columns on every row, but relations in the returned graph that represent the same id share a single instance: 50 users spread over 5 cities <a class="tlink" href="/docs/hydration">hydrate</a> into exactly 5 <code>City</code> objects, each constructed once and shared across the list. Immutability is what makes that sharing safe.</p>

  <h2><span class="hno">05</span>When you do not want the join</h2>
  <p>Sometimes the join is genuinely wasted work, and lazy loading is the right call. Storm makes that a type, not a runtime behavior. Declare the field as <code>Ref&lt;City&gt;</code> and Storm reads only the foreign key column; loading the city becomes an explicit method call that is visible in code review:</p>
  ${editor({file: 'Entities.kt', tag: 'Kotlin · Storm', code: CODE_STORM_REF, sql: SQL_STORM_REF})}
  <p>This is the whole model: <code>City</code> means loaded, <code>Ref&lt;City&gt;</code> means not loaded. The decision is written once, in the entity, and every query in the codebase behaves the same way. If a loop calls <code>fetch()</code> per iteration, the N+1 is right there in the code where a reviewer can see it, not hidden inside a getter.</p>
  <p>The instance sharing from the previous section applies to refs as well. Within a transaction, refs that represent the same id resolve to one shared instance through the <a class="tlink" href="/docs/entity-cache">entity cache</a>, so the data behind them is loaded once per distinct id, not once per row: fetching the cities of 50 users spread over 5 cities issues at most 5 queries. And fetching the same ref twice never queries twice.</p>

  <h2><span class="hno">06</span>Prove it in a test</h2>
  <p>Because query shapes are deterministic, "no N+1" is not a code-review hope, it is an assertion. Storm's test module captures the SQL a block generates, so a regression that adds a query fails the build:</p>
  ${editor({file: 'UserQueryTest.kt', tag: 'Kotlin · storm-test', code: CODE_TEST})}
  <p><code>@StormTest</code> spins up an in-memory H2 database, runs your schema scripts, and injects the <code>ORMTemplate</code> and <code>SqlCapture</code> parameters. See <a class="tlink" href="/docs/testing">Testing</a> for the full setup, including running the same test against PostgreSQL with Testcontainers.</p>

  <h2><span class="hno">07</span>Side by side</h2>
  <table class="cmp">
    <tr><th></th><th>JPA with Hibernate</th><th>Storm</th></tr>
    <tr><td>Where loading is decided</td><td>Per query (JOIN FETCH, entity graphs) or at access time through proxies</td><td>In the entity model: a <code>City</code> field joins, a <code>Ref&lt;City&gt;</code> field does not</td></tr>
    <tr><td>N+1 risk</td><td>Present by default; every lazy association is a potential 1&nbsp;+&nbsp;N</td><td>Absent by default; extra queries happen only where <code>fetch()</code> is written</td></tr>
    <tr><td>Failure mode</td><td><code>LazyInitializationException</code> when a proxy outlives its session</td><td>None; results are plain records with no session to outlive</td></tr>
    <tr><td>Verifying behavior</td><td>Inspect <code>show_sql</code> logging by hand</td><td>Assert query counts in a unit test with <code>SqlCapture</code></td></tr>
  </table>

  <h2><span class="hno">08</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/refs">Refs</a>
    <a href="/docs/relationships">Relationships</a>
    <a href="/docs/queries">Queries</a>
    <a href="/docs/hydration">Hydration</a>
    <a href="/docs/testing">Testing</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" target="_blank" rel="noopener" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function NPlusOneTutorial() {
  useEffect(() => wireSqlToggles(), []);

  return (
    <>
      <Head>
        <html lang="en" />
        <title>{`${TITLE} · ST/ORM Tutorials`}</title>
        <meta name="description" content={DESC} />
        <link rel="canonical" href="https://orm.st/tutorials/n-plus-one" />
        <meta property="og:type" content="article" />
        <meta property="og:title" content={TITLE} />
        <meta property="og:description" content={DESC} />
        <meta name="twitter:title" content={TITLE} />
        <meta name="twitter:description" content={DESC} />
        <script type="application/ld+json">
          {JSON.stringify({
            '@context': 'https://schema.org',
            '@type': 'TechArticle',
            headline: TITLE,
            description: DESC,
            url: 'https://orm.st/tutorials/n-plus-one',
            proficiencyLevel: 'Beginner',
            author: {
              '@type': 'Organization',
              name: 'Storm',
              url: 'https://github.com/storm-orm',
            },
          })}
        </script>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;700&display=swap"
          rel="stylesheet"
        />
      </Head>
      <style dangerouslySetInnerHTML={{__html: TUT_CSS}} />
      <div className="storm-tut" dangerouslySetInnerHTML={{__html: BODY}} />
    </>
  );
}
