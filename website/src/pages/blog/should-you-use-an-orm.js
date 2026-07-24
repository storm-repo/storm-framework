import React from 'react';
import {BlogPage} from '../../components/blog/blogTheme';

const TITLE = "Why shouldn't we use an ORM? The case for visible SQL";
const DESC =
  'The strongest arguments against ORMs are really arguments against ORMs ' +
  'that hide SQL. Take the concealment away and most of the case dissolves, ' +
  'which leaves room for a third option.';
const SLUG = 'should-you-use-an-orm';
const DATE = '2026-06-09';

const BODY = `
<div class="art">
  <div class="crumbs"><a href="/blog/">Blog</a><span class="sep">/</span>Why shouldn't we use an ORM?</div>
  <h1>Why <span class="grad">shouldn't</span> we use an ORM?</h1>
  <p class="dek">It is a fair question, and the people asking it usually have scars to back it up. But listen closely to the complaints and they are almost never about mapping rows to types. They are usually about everything else the ORM does.</p>
  <div class="meta"><span>June 9, 2026</span><span>Opinion</span><span>5 min read</span></div>

  <h2>The strongest case against ORMs</h2>
  <p>Let us make the argument properly, because it deserves it. ORMs hide the query, so you cannot see what runs against your database. They make performance opaque, so an <a class="tlink" href="/blog/n-plus-one-query-problem">N+1</a> or a missing index surfaces in production instead of in review. In the JPA world, they often put JPQL or a criteria API between you and the SQL you already know, and then abandon it the moment you need something it cannot express. And they couple your objects to a session lifecycle, which is where <a class="tlink" href="/blog/lazyinitializationexception">a whole genre of runtime errors</a> comes from.</p>
  <p>There is truth in all of those complaints. If that were the whole story, the answer would be simple: drop the ORM and write SQL by hand.</p>

  <h2>Notice what the complaints have in common</h2>
  <p>None of them is about mapping. Connecting a plain record to a table, binding a parameter safely, turning a result row into a typed value: nobody writes an angry post about that part. It is useful and quiet. The common theme is concealment. You cannot see the query, you cannot track the state, and you have to keep the lifecycle in your head. The objection to ORMs is really an objection to hiding.</p>
  <p>That matters, because hiding is not intrinsic to mapping. You can have the ergonomics of an ORM without the concealment. Those two things got bundled together historically, but they do not have to be.</p>
  <p>And none of it makes such an ORM wrong. We <a class="tlink" href="/blog/why-we-built-storm">reached for Hibernate by default for years</a>, and liked it. But hidden machinery is like many conveniences in life: convenient, until it isn't, and that moment usually picks production to introduce itself.</p>

  <h2>A third option</h2>
  <p>ST/ORM keeps the useful half and drops the resented half. It maps rows to <a class="tlink" href="/blog/entities-should-be-values">plain values</a> and keeps the mapping concise by convention, so you are not hand-writing result mapping or annotating every column. But the SQL stays in view. There is a type-safe DSL for the boring, high-volume queries, and real SQL templates for the queries where SQL should stay SQL, with type-checked column references and parameters bound automatically. There is no JPQL to learn and no proxy to outlive.</p>
  <p>So the answer to "why shouldn't we use an ORM?" is: avoid the kind that hides your SQL and your state, and asks you to reason about a lifecycle you cannot see. That is a real position, and we agree with it. It is also the kind of ORM we chose not to build.</p>

  <div class="cta">
    <a href="/docs/sql-templates" class="btn primary">See SQL templates →</a>
    <a href="/blog/stop-hiding-my-sql" class="btn">Stop hiding my SQL</a>
  </div>
</div>`;

export default function Page() {
  return <BlogPage title={TITLE} description={DESC} slug={SLUG} dateISO={DATE} body={BODY} />;
}
