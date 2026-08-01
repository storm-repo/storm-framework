import React from 'react';
import {BlogPage} from '../../components/blog/blogTheme';

const TITLE = 'The N+1 query problem in ORMs, and loading you can see';
const DESC =
  'You do not fix the N+1 query problem by remembering a fetch clause. You fix ' +
  'it by making loading a decision the code shows you, visible in the type and ' +
  'in the source.';
const SLUG = 'n-plus-one-query-problem';
const DATE = '2025-12-16';

const BODY = `
<div class="art">
  <div class="crumbs"><a href="/blog/">Blog</a><span class="sep">/</span>The N+1 problem</div>
  <h1>The N+1 problem, and <span class="grad">loading you can see</span></h1>
  <p class="dek">The N+1 problem keeps coming back to codebases that have already fixed it more than once. Not because anyone was careless: the default hides queries, and the fix is something you have to remember.</p>
  <div class="meta"><span>December 16, 2025</span><span>Hibernate</span><span>4 min read</span></div>

  <h2>The problem that keeps coming back</h2>
  <p>Render fifty users with the name of each user's city. Two tables, one foreign key. The list query fetches the users, and then each read of user.city.name runs its own select. One query becomes fifty-one. Nothing in the code, the types, or the compiler warned you, because nothing is broken: the code is correct, and it does exactly what the framework's default asks, which is to load the city the moment you read it. You find out instead from the query log, or from production latency. You fix it with a JOIN FETCH or an entity graph, and those work. Then a few weeks later someone writes a new query for a new screen, forgets the fetch clause, and it is back. The team has fixed this exact problem before. They will fix it again.</p>

  <h2>Why it keeps returning</h2>
  <p>Because the fix lives in the wrong place. The mapping declares one loading behavior, individual queries override it per call site, and the compiler checks none of it. Every new query is a fresh chance to forget, and forgetting is silent.</p>

  <h2>Put the loading decision in the type</h2>
  <p>ST/ORM moves the loading decision out of the query and into the entity, as a type. It is the same move behind <a class="tlink" href="/blog/entities-should-be-values">entities being plain data</a>. For a single-valued relationship, a foreign key written as its plain entity is always loaded with the row, through a join: write <code>val city: City</code> and the city comes back with the user. There is no lazy variant of it, so there is nothing to forget, and every caller behaves the same way. When deferring the load is the right call, you say so in the type: write the field as <code>val city: Ref&lt;City&gt;</code> and ST/ORM reads only the foreign key, until you fetch the city on purpose. The choice between the two is visible to the compiler and to the reviewer. If a loop makes that fetch per row, the extra queries are right there in the source, where a reviewer can see them, instead of hidden inside a getter.</p>

  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="/blog/" class="btn">More from the blog</a>
  </div>
</div>`;

export default function Page() {
  return <BlogPage title={TITLE} description={DESC} slug={SLUG} dateISO={DATE} body={BODY} />;
}
