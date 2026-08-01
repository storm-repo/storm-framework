import React from 'react';
import {BlogPage} from '../../components/blog/blogTheme';

const TITLE = 'Dirty checking without proxies';
const DESC =
  'You do not need bytecode enhancement or a session-bound proxy to know ' +
  'what changed on an entity. You need the value before and the value after. ' +
  'Here is how ST/ORM detects changes with neither.';
const SLUG = 'dirty-checking-without-proxies';
const DATE = '2026-03-17';

const BODY = `
<div class="art">
  <div class="crumbs"><a href="/blog/">Blog</a><span class="sep">/</span>Dirty checking without proxies</div>
  <h1>Dirty checking <span class="grad">without proxies</span></h1>
  <p class="dek">Dirty checking is one of the genuinely nice things a traditional ORM does for you: it writes only the columns that changed. The usual objection to value-based ORMs is that you lose it. You do not.</p>
  <div class="meta"><span>March 17, 2026</span><span>Internals</span><span>4 min read</span></div>

  <h2>How the classic approach works</h2>
  <p>Hibernate has two ways to know what changed. It can snapshot every managed entity when it loads, and at flush time diff the current state against the snapshot to build the UPDATE. Or it can enhance your bytecode so that setters record which fields were touched. Both are clever, and both are the reason the session has to own your objects: the change tracking is a property of the managed lifecycle, not of the data. That ownership is exactly what gives you detached-state bugs and <a class="tlink" href="/blog/lazyinitializationexception">proxies that outlive their session</a>.</p>

  <h2>How ST/ORM does it</h2>
  <p>ST/ORM detects changes the obvious way. Inside a transaction, it compares the entity you read with the entity you write, and it issues an UPDATE for only the columns that differ. That is the whole mechanism. No proxy, no bytecode enhancement, no snapshot held by a session, because the two values are all it needs. You read a <code>User</code>, you produce an updated copy, you save it, and ST/ORM writes the delta.</p>
  <p>Because entities are immutable, producing an updated copy is the natural way to change one anyway, and the before and the after are two distinct values, which is precisely what a diff wants. Immutability pays off twice here. If you save the very same value you read, it is the same object, so reference identity settles the question before a single field is compared: nothing changed, and nothing is written. And when there is a change to inspect, ST/ORM reads the fields through the <a class="tlink" href="/blog/static-metamodel">generated metamodel</a> rather than by reflection, so the comparison is direct, generated access with no runtime lookup in the path.</p>

  <h2>What that changes</h2>
  <p>The benefit is the same, minimal updates, but the cost structure is completely different. Change detection is now a function of two values rather than a lifecycle you have to keep an entity inside of. The entity outside a transaction is just data. Nothing is tracking it, nothing will flush it, and it cannot go stale in the way a managed object can. You keep dirty checking without the session coupling that usually comes attached to it.</p>

  <div class="cta">
    <a href="/docs/dirty-checking" class="btn primary">How dirty checking works →</a>
    <a href="/blog/entities-should-be-values" class="btn">Entities should be values</a>
  </div>
</div>`;

export default function Page() {
  return <BlogPage title={TITLE} description={DESC} slug={SLUG} dateISO={DATE} body={BODY} />;
}
