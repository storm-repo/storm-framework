import React from 'react';
import {BlogPage} from '../../components/blog/blogTheme';

const TITLE =
  "LazyInitializationException: what it means, and why value entities can't throw it";
const DESC =
  'LazyInitializationException is the most-googled Hibernate error. It is not ' +
  'a bug in your code. It is a lazy promise coming due after the session ' +
  'closed, and value-based entities cannot throw it.';
const SLUG = 'lazyinitializationexception';
const DATE = '2025-11-25';

const BODY = `
<div class="art">
  <div class="crumbs"><a href="/blog/">Blog</a><span class="sep">/</span>LazyInitializationException</div>
  <h1><span class="grad">LazyInitializationException</span>, and what it's telling you</h1>
  <p class="dek">It is one of the most searched Hibernate errors, and everyone who uses Hibernate long enough meets it. It is worth understanding what it actually is, because it is not a mistake in your code. It is the model working exactly as designed.</p>
  <div class="meta"><span>November 25, 2025</span><span>Hibernate</span><span>4 min read</span></div>

  <h2>The error everyone meets</h2>
  <p>You load an entity in a service method, pass it up to a controller or a template, and something touches a lazy relationship. Out comes LazyInitializationException: could not initialize proxy, no Session. The first few times, you reach for open-session-in-view, or a JOIN FETCH, or mapping to a DTO before you leave the transaction. All of them work, and none of them explain what happened.</p>

  <h2>Where it actually comes from</h2>
  <p>A lazy association is not data. It is a promise to call the database later. The proxy standing in for the related object holds on to the session that made the promise, and when you touch it, the proxy tries to keep that promise. If the session that backed it has already closed, there is no one left to make the call, so it throws. That is the whole mechanism, and every workaround you have used is a way of managing the timing of that promise: keep the session open longer, force the load earlier, or copy the data out before the session ends. You are not fixing a bug. You are scheduling around a design.</p>

  <h2>Data cannot throw it</h2>
  <p>ST/ORM makes a different choice at the root, the one behind <a class="tlink" href="/blog/entities-should-be-values">treating entities as plain data</a>. An entity is a value, fully there when you receive it. A field typed as its entity was loaded in the same query, through a join. A field you want to defer is a Ref, which holds the foreign key and nothing else, and loading it is a call you write in your own code. There is no proxy, because there is nothing to stand in for, and no session to outlive, because the value never belonged to one. You can return it, serialize it, cache it, or hand it to another thread, and none of that can produce a LazyInitializationException, because the conditions that create one are gone.</p>

  <h2>The trade</h2>
  <p>That is the trade ST/ORM makes. You give up transparent lazy loading, and in return the most reliable error in the Hibernate world stops being something you design around. It is simply not a shape your code can take.</p>

  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="/blog/" class="btn">More from the blog</a>
  </div>
</div>`;

export default function Page() {
  return <BlogPage title={TITLE} description={DESC} slug={SLUG} dateISO={DATE} body={BODY} />;
}
