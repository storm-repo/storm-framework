import React from 'react';
import {BlogPage} from '../../components/blog/blogTheme';

const TITLE = 'The M in ORM stands for mapping, not management';
const DESC =
  'Ask what the M in ORM stands for and everyone answers mapping. Watch what ' +
  'the tools wearing the name actually do, and the answer looks more like ' +
  'management. You should get to want one without the other.';
const SLUG = 'mapping-not-management';
const DATE = '2026-07-24';

const BODY = `
<div class="art">
  <div class="crumbs"><a href="/blog/">Blog</a><span class="sep">/</span>The M in ORM</div>
  <h1>The M in ORM stands for <span class="grad">…</span></h1>
  <p class="dek">Ask anyone what the M stands for and they answer mapping, a translation between relational data and typed values. Then watch what the tools wearing the name actually do all day.</p>
  <div class="meta"><span>July 24, 2026</span><span>Opinion</span><span>7 min read</span></div>

  <h2>What the words actually say</h2>
  <p>Read the term literally. On one side you have tables, rows, keys. On the other side, the types and references of your language. Mapping is the translation between the two, and it goes both ways. Reads turn rows into typed values. Writes turn values back into statements, in the right order, with generated keys ending up where they need to go.</p>
  <p>That work is not trivial. Hydrating results, deduplicating repeated rows, ordering writes by their dependencies, batching, key propagation. All of that is real work, and any serious mapper has to do it. But none of it requires your objects to have a lifecycle.</p>
  <p>Nothing in the name mentions a session, a proxy, or a flush. That machinery has its own name: management.</p>

  <h2>What management means, precisely</h2>
  <p>By management we mean something specific here: a persistence context that outlives individual operations and holds your entities inside it. The context tracks which objects are attached, watches them for mutation, keeps an identity map across operations, stands proxies in for rows you have not read yet, and decides at flush time which of your changes become SQL, and in what order.</p>
  <p>In other words: a stateful runtime that owns your objects between statements. To be clear, short-lived coordination inside a query or a write is just the job. The criticism is about the long-lived kind.</p>

  <h2>How the label drifted</h2>
  <p>The history is worth getting right. TopLink was doing identity maps and units of work in the nineties. Hibernate arrived in 2001 and made that persistence model the default mental image of the word ORM. Fowler catalogued the underlying patterns in 2002: Data Mapper, Unit of Work, Identity Map, Lazy Load. JPA later standardized a lifecycle and API in the same family. After twenty years of that, the M might as well stand for management.</p>
  <p>Listen to the standard complaints with that in mind. <a class="tlink" href="/blog/n-plus-one-query-problem">N+1 queries</a> usually trace back to implicit lazy loading of associations, though nothing stops you from writing one by hand. The <a class="tlink" href="/blog/lazyinitializationexception">LazyInitializationException</a> is a session-lifecycle error by definition. A surprising UPDATE can come from dirty tracking, from a cascade, from relationship synchronization, or from flush timing, and the debugging session looks the same in every case: the mutation happened in one place and the SQL in another. Autoflush makes that gap structural, even where an explicit flush is available.</p>
  <p>None of these complaints are about mapping. They are all about management.</p>
  <p>Mapping has no comparable list of famous failures. It does not need to hide when database work happens, and hiding is where the resentment comes from. We <a class="tlink" href="/blog/should-you-use-an-orm">made a similar point about hidden SQL</a> before.</p>
  <p>This is not just a JVM thing, by the way. Every ecosystem with a relational database has ended up at the same fork. You can tell by the vocabulary: the term "micro-ORM" exists because someone had to invent a word for "just the mapping, please".</p>

  <h2>Convenient, until it isn't</h2>
  <p>To be fair, you get real things back. You can walk an object graph as if it were in memory. You configure cascades once instead of ordering writes by hand. Batching happens at flush time without you thinking about it. The managers implement all of this deliberately, and mostly well.</p>
  <p>But you pay for it. Your objects now have attached and detached states, plus the ceremony for moving between them. Identity is scoped to the context, so equality across contexts is suddenly something you have to design for. The moment a change becomes SQL is the flusher's decision unless you step in. And the failure modes have their own names, which is rarely a good sign for an abstraction.</p>
  <p>There is nothing wrong with an ORM that also manages. We <a class="tlink" href="/blog/why-we-built-storm">reached for one by default for years</a>. But management is like many conveniences in life: convenient, until it isn't, and with a growing model that moment tends to arrive in production. The problem is the label: the word says mapping, but what you usually get is management.</p>

  <h2>Both directions, no context</h2>
  <p>The fair question is whether mapping alone gets you far enough. It does, but you have to take both directions seriously. In ST/ORM, a three-table join hydrates into <a class="tlink" href="/blog/entities-should-be-values">immutable nested values</a>, an owner holding its pets, with repeated rows resolving to the same instance by primary key during hydration: the useful half of an identity map, without the lifetime. A write set runs the translation the other way: hand it a mixed collection of values and it partitions them by type, orders the types by their foreign key dependencies, writes each level as a batch, and propagates generated keys into the rows that reference them. Updates <a class="tlink" href="/blog/dirty-checking-without-proxies">skip unchanged rows</a> by comparing the value being written against the value observed in the same transaction. There is no attach, no merge, no dirty-tracked entity waiting for a flush.</p>
  <p>None of that requires a manager. It requires a good translator.</p>

  <h2>One without the other</h2>
  <p>If you sort tools by what they do instead of what they call themselves, you get three groups. Persistence-context ORMs: Hibernate and JPA, and the entity layers that follow their model. SQL-shaped query tools and row mappers: jOOQ, JDBI, MyBatis, which translate queries and results but stop short of graph persistence; jOOQ pointedly declines the ORM label, and the refusal is reasonable. Then sessionless graph mapping, which is precisely where ST/ORM sits.</p>
  <p>The point is not to police who gets to use the word. The extras are real features and choosing them is not a mistake. All we are saying is that ORM should not be a word you avoid just because you do not want those features. You should get to want one without the other.</p>
  <p>ST/ORM maps objects. It does not manage them. That is not a diminished ORM. It is exactly what the letters spell.</p>

  <div class="cta">
    <a href="/docs/write-sets" class="btn primary">See write sets →</a>
    <a href="/blog/entities-should-be-values" class="btn">Entities should be values</a>
  </div>
</div>`;

export default function Page() {
  return <BlogPage title={TITLE} description={DESC} slug={SLUG} dateISO={DATE} body={BODY} />;
}
