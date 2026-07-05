import React from 'react';
import {BlogPage} from '../../components/blog/blogTheme';
import {editor, K, T, A, P} from '../../components/tutorial/tutorialTheme';

const TITLE = 'A first-principles approach to a JVM data layer';
const DESC =
  'A short walk from the most concise way to work with a database table to a ' +
  'complete data layer, adding nothing on the way. The smallest typed shape ' +
  'turns out to be the carrier of your data, and the contract for querying the ' +
  'relation graph.';
const SLUG = 'data-layer-first-principles';
const DATE = '2025-09-30';

const CODE_CITY = [
  K('data class '), T('City'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(',\n'),
  P('    '), K('val '), P('name: '), T('String'), P(',\n'),
  P('    '), K('val '), P('country: '), T('String'), P('\n'),
  P(')'),
].join('');

const CODE_USER = [
  K('data class '), T('User'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(',\n'),
  P('    '), K('val '), P('name: '), T('String'), P(',\n'),
  P('    '), A('@FK'), P(' '), K('val '), P('city: '), T('City'), P('\n'),
  P(')'),
].join('');

const BODY = `
<div class="art">
  <div class="crumbs"><a href="/blog/">Blog</a><span class="sep">/</span>A first-principles approach</div>
  <h1>A first-principles approach to <span class="grad">your data layer</span></h1>
  <p class="dek">Take the most concise thing you can write to work with a database table, then follow where it leads. Nothing gets added along the way. That one small definition keeps turning out to be the answer to the next problem, and the next, until it adds up to a whole data layer.</p>
  <div class="meta"><span>September 30, 2025</span><span>Design</span><span>4 min read</span></div>

  <h2>The simplest thing you can write</h2>
  <p>Start at the bottom. The database owns the data model; the application just needs a concise, typed way to work with it. So what is the least you can write to make a database row usable in application code? Only its shape: a typed, named set of fields. In a modern language that is a record or a data class, and there is a principle hiding in how little that is.</p>
  ${editor({file: 'City.kt', tag: 'Kotlin', code: CODE_CITY})}
  <p>The names come from convention, and Kotlin nullability says which values may be absent in application code. That is the entire application-facing shape. The first principle is to keep it exactly this small: nothing more than the data, with no hidden lifecycle, behavior, or framework state attached. Everything that follows, follows from refusing to add to it.</p>

  <h2>It happens to be the perfect carrier</h2>
  <p>Here is the first thing you get for free. That most concise form, a plain data class, happens to be the ideal way to carry the data around. Nothing is attached to it, so it moves through every layer untouched: out of the repository, into your services, through the controller, out to a serializer, across a thread boundary, and back. You did not design a carrier. You wrote the smallest useful application shape, and it turns out that a carrier is exactly what that is. What you loaded is what you pass.</p>

  <h2>And it queries the whole graph</h2>
  <p>Then the second thing falls out. A relationship can be part of that shape too. If a user always needs its city, the field is typed as <code>City</code>; if the load should be deferred, it is typed as <code>Ref&lt;City&gt;</code>.</p>
  ${editor({file: 'User.kt', tag: 'Kotlin', code: CODE_USER})}
  <p>So relationships live in the shape, and the same definition that carries the data also describes how to query it. ST/ORM generates a metamodel from it, and because the relationships are in the shape, you can follow them across the relation graph: <code>users.findAll(User_.city.name eq "Sunnyvale")</code> reaches from a user to its city in one line, type-checked, with the join derived from the relationship path rather than written by hand at every call site.</p>

  <h2>What falls out of keeping it small</h2>
  <p>Step back and the picture is simple. The same small shape gives application code a value to pass around, a typed contract for queries, and a path into the relation graph. Those are usually separate pieces you write, map, and keep in sync. In ST/ORM they fall out of one decision: keep the application-facing view of database data small, typed, and free of hidden state.</p>

  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="/docs/first-query" class="btn">See the one-line queries</a>
  </div>
</div>`;

export default function Page() {
  return <BlogPage title={TITLE} description={DESC} slug={SLUG} dateISO={DATE} body={BODY} />;
}
