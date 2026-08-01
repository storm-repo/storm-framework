import React from 'react';
import {BlogPage} from '../../components/blog/blogTheme';

const TITLE = 'Three abstractions and nothing else: the ST/ORM model';
const DESC =
  'ST/ORM has three moving parts: Entity, Repository, and SQL Template. ' +
  'A model you cannot hold in your head is one you end up debugging later.';
const SLUG = 'three-abstractions';
const DATE = '2026-02-17';

const BODY = `
<div class="art">
  <div class="crumbs"><a href="/blog/">Blog</a><span class="sep">/</span>Three abstractions</div>
  <h1><span class="grad">Three abstractions</span> and nothing else</h1>
  <p class="dek">There is a kind of framework complexity that gets mistaken for power. If the model does not fit on a whiteboard, you will meet the rest of it in a debugger. ST/ORM has three user-facing concepts, and they cover the whole thing.</p>
  <div class="meta"><span>February 17, 2026</span><span>Design</span><span>4 min read</span></div>

  <h2>Entity</h2>
  <p>The data your application works with. A Kotlin data class or a Java record with a couple of annotations, <code>@PK</code> for the primary key and <code>@FK</code> for a foreign key. ST/ORM connects entities to tables and columns by convention, so annotations show up only where the convention does not fit. An entity carries no hidden state and no behavior. It is <a class="tlink" href="/blog/entities-should-be-values">database-backed data as a plain value</a>, and nothing else.</p>

  <h2>Repository</h2>
  <p>CRUD operations and type-safe queries for one entity. You define an interface and write the query method bodies with the DSL. There is no method-name parsing that turns <code>findByStatusAndCreatedAtAfter</code> into a hidden query, and no generated query you have to reverse-engineer from a log. If a repository runs a query, you wrote it, and you can read it.</p>

  <h2>SQL Template</h2>
  <p>Direct access to SQL with type-safe binding and result mapping, for when the DSL is not the right tool: joins, reports, database-specific features, or queries where SQL should stay SQL. It sits beside the DSL rather than under it; reaching for it is a choice, not a failure. Type references and metamodel columns keep it checked; parameters are bound automatically. It follows the case for <a class="tlink" href="/blog/stop-hiding-my-sql">keeping SQL in view rather than hiding it</a>.</p>

  <h2>Why a small model is the point</h2>
  <p>There is more underneath, of course: transactions, a static metamodel, change detection, generated code. It is all there to support these three, not to add a fourth concept you have to learn.</p>
  <p>These three share one principle: nothing happens that you cannot see in the code. No query fires that you did not write, the SQL each one produces is predictable, relationships load when you ask for them, and transaction boundaries are declared rather than inferred. In practice that means you can teach the whole model to a new engineer in an afternoon, and you never have to reconstruct what the framework decided to do on your behalf.</p>

  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="/docs/repositories" class="btn">Repositories</a>
  </div>
</div>`;

export default function Page() {
  return <BlogPage title={TITLE} description={DESC} slug={SLUG} dateISO={DATE} body={BODY} />;
}
