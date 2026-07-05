import React from 'react';
import {BlogPage} from '../../components/blog/blogTheme';

const TITLE = 'The static metamodel behind ST/ORM: type-safe queries without reflection';
const DESC =
  'ST/ORM generates a static metamodel from each entity at compile time. It ' +
  'makes column and path references type-checked, and it lets queries and ' +
  'hydration run on generated code instead of reflection.';
const SLUG = 'static-metamodel';
const DATE = '2026-03-03';

const BODY = `
<div class="art">
  <div class="crumbs"><a href="/blog/">Blog</a><span class="sep">/</span>The static metamodel</div>
  <h1>The static <span class="grad">metamodel</span></h1>
  <p class="dek">Every entity you write gets a generated metamodel class, produced at compile time. It is a quiet piece of the design, and it is where type safety and speed turn out to be the same decision.</p>
  <div class="meta"><span>March 3, 2026</span><span>Internals</span><span>4 min read</span></div>

  <h2>A generated metamodel class</h2>
  <p>For an entity like <code>City</code>, ST/ORM generates a small metamodel class, <code>City_</code>, at compile time. There is nothing in it you write by hand. The metamodel does not define the data model; it exposes the database-backed shape of the entity to application code. From that shape it derives one typed handle per field, and one per relationship, standing in for the columns and the paths you can reach from that row. It is a companion to the entity, regenerated whenever the entity changes, so it never drifts from the thing it describes.</p>

  <h2>Type safety you cannot forget to ask for</h2>
  <p>Because those handles are real, typed code, the compiler checks every reference you build a query from. <code>User_.city.name eq "Sunnyvale"</code> is not a string that happens to match a column. It is a path the compiler knows, so a renamed field, a broken path, or a wrong type is a build error, not something you find in production. This is the same generated metamodel that lets <a class="tlink" href="/blog/data-layer-first-principles">a relationship become a query across the graph</a>, and the same one that keeps <a class="tlink" href="/blog/stop-hiding-my-sql">real SQL templates checked</a> against known fields and types. You do not opt in to the safety. It is the only way the references exist.</p>

  <h2>Generated paths, not reflection</h2>
  <p>The other half is what does not happen at runtime. Many ORMs discover your fields by reflection: they ask the class, while the program runs, what it has, then read and write it through that reflective handle. It works, but it is slower than plain field access, and it moves a class of errors from the compiler to the first request that reaches the path. Because ST/ORM already holds the metamodel as generated code, none of that is necessary. The paths are known ahead of time, so reading and writing a field is direct, generated access, with no per-row reflective lookup in the way.</p>

  <h2>Where it pays off: hydration</h2>
  <p>The clearest place you feel this is hydration, turning a result row into an entity. That happens for every row of every query, so it is the part that has to be fast. With a generated metamodel, hydration is code-generated too: each column is read through a known path and lands in the right constructor argument, without asking the class at runtime what to do with it. There is no per-row reflection to pay for, so the mapping runs close to the cost of the work itself.</p>

  <h2>Why it matters</h2>
  <p>The point is that type safety and performance came from one decision, not from two separate features you have to balance. Generate the model's shape once, at compile time, and the compiler can check your queries while the runtime skips reflection entirely. Everything downstream, from a query predicate to change detection, gets to stand on generated paths instead of runtime discovery. It is a small piece of generated code, but almost every part of the data layer gets to lean on it.</p>

  <div class="cta">
    <a href="/docs/metamodel" class="btn primary">The metamodel →</a>
    <a href="/blog/" class="btn">More from the blog</a>
  </div>
</div>`;

export default function Page() {
  return <BlogPage title={TITLE} description={DESC} slug={SLUG} dateISO={DATE} body={BODY} />;
}
