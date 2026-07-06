import React, {useEffect} from 'react';
import Head from '@docusaurus/Head';
import {TUT_CSS, navHtml, FOOT_HTML} from '../../components/tutorial/tutorialTheme';

// The blog hub at /blog, rendered in the landing-page style (see
// tutorialTheme.js) rather than with Docusaurus's built-in blog plugin, so it
// matches the tutorials and examples. Each article lives next to this file as
// its own custom page; keep this list newest-first and in sync with them.

const TITLE = 'Storm Blog · Design decisions and deep dives';
const DESC =
  'The design decisions behind ST/ORM, deep dives into how it works, and the ' +
  'thinking that shapes the API. Plain values, explicit SQL, and no hidden ' +
  'state, argued one decision at a time.';

// Kept newest-first in this array, but rendered oldest-first on the page so the
// hub reads top to bottom in series order, starting from the origin post.
const POSTS = [
  {
    slug: 'should-you-use-an-orm',
    date: 'June 9, 2026',
    tag: 'Opinion',
    title: "Why shouldn't we use an ORM?",
    blurb:
      'The strongest arguments against ORMs are really arguments against ORMs that hide SQL. Take the concealment away and most of the case dissolves.',
  },
  {
    slug: 'why-we-didnt-choose-exposed',
    date: 'April 14, 2026',
    tag: 'Comparison',
    title: "Why we didn't choose Exposed",
    blurb:
      'We build in Kotlin, so why not build ST/ORM on JetBrains Exposed? Exposed is excellent at modeling a database. ST/ORM draws the boundary differently: the model stays in the database, and application code gets plain, typed records.',
  },
  {
    slug: 'dirty-checking-without-proxies',
    date: 'March 17, 2026',
    tag: 'Internals',
    title: 'Dirty checking without proxies',
    blurb:
      'You do not need bytecode enhancement or a session-bound proxy to write only the columns that changed. You need the value before and the value after.',
  },
  {
    slug: 'static-metamodel',
    date: 'March 3, 2026',
    tag: 'Internals',
    title: 'The static metamodel',
    blurb:
      'Every entity gets a companion generated at compile time. It makes column and path references type-checked, and it lets queries and hydration run on generated code instead of reflection.',
  },
  {
    slug: 'three-abstractions',
    date: 'February 17, 2026',
    tag: 'Design',
    title: 'Three abstractions and nothing else',
    blurb:
      'Entity, Repository, SQL Template. A model you can hold in your head is a model that does not surprise you.',
  },
  {
    slug: 'stop-hiding-my-sql',
    date: 'January 20, 2026',
    tag: 'SQL',
    title: 'Stop hiding my SQL',
    blurb:
      'SQL is the one interface every database and every backend engineer already shares. Type it and keep it in view, do not replace it with a second query language.',
  },
  {
    slug: 'n-plus-one-query-problem',
    date: 'December 16, 2025',
    tag: 'Hibernate',
    title: 'The N+1 problem, and loading you can see',
    blurb:
      'You do not fix N+1 by remembering a fetch clause. You fix it by making loading a decision the code shows you, and one you can assert in a test.',
  },
  {
    slug: 'lazyinitializationexception',
    date: 'November 25, 2025',
    tag: 'Hibernate',
    title: "LazyInitializationException, and what it's telling you",
    blurb:
      'The most-googled Hibernate error is not a bug in your code. It is a lazy promise coming due after the session closed, and value entities cannot throw it.',
  },
  {
    slug: 'entities-should-be-values',
    date: 'October 28, 2025',
    tag: 'Design',
    title: 'Entities are just data',
    blurb:
      'An entity in ST/ORM is a plain record: a value with no session and no hidden state. Here is what that buys you across your application, without giving up dirty checking or lazy loading.',
  },
  {
    slug: 'data-layer-first-principles',
    date: 'September 30, 2025',
    tag: 'Design',
    title: 'A first-principles approach to your data layer',
    blurb:
      'The most concise way to describe a table turns out to be the ultimate carrier of your data, and the contract for querying the entire relation graph. Simplicity is the ultimate sophistication.',
  },
  {
    slug: 'why-we-built-storm',
    date: 'September 2, 2025',
    tag: 'Origin',
    title: 'Why we built ST/ORM',
    blurb:
      'If you have worked with JPA and Hibernate long enough, you probably know the feeling. Most of the time, things are fine. Then the model grows, the codebase gets larger, and there is some drama. One day we got the opportunity to start over. That is where our story starts.',
  },
];

const ORDERED = [...POSTS].reverse();

// Distinct categories in series order (first appearance, oldest-first), each
// with a post count, for the clickable filter bar.
const CATEGORIES = ORDERED.reduce((acc, p) => {
  const found = acc.find((c) => c.tag === p.tag);
  if (found) found.count += 1;
  else acc.push({tag: p.tag, count: 1});
  return acc;
}, []);

const cards = ORDERED.map(
  (p) => `
  <a class="tcard" href="/blog/${p.slug}" data-tag="${p.tag}">
    <div class="tt">${p.title}<span class="arrow">→</span></div>
    <div class="td">${p.blurb}</div>
    <div class="tm"><span>${p.date}</span><span>${p.tag}</span></div>
  </a>`,
).join('');

// One chip per category. No chip is active by default, so every card shows;
// wireBlogFilters() applies the selected category and toggles it back off.
const filterBar = `
<div class="bfilters">
  ${CATEGORIES.map(
    (c) => `<button type="button" class="bchip" data-filter="${c.tag}">${c.tag}<b>${c.count}</b></button>`,
  ).join('')}
</div>`;

const BLOG_FILTER_CSS = `
  .storm-tut .bfilters{display:flex;gap:12px;flex-wrap:wrap;margin-top:28px}
  .storm-tut .bchip{font-family:var(--mono);font-size:12.5px;color:#b1b5da;border:1px solid rgba(129,140,248,.20);border-radius:999px;padding:8px 16px;background:rgba(129,140,248,.07);cursor:pointer;transition:.16s;line-height:1}
  .storm-tut .bchip:hover{color:#e9eaf7;border-color:rgba(129,140,248,.45);background:rgba(129,140,248,.13)}
  .storm-tut .bchip.on{color:#0a0a0f;background:var(--accent);border-color:var(--accent);font-weight:600}
  .storm-tut .bchip b{color:rgba(178,182,220,.55);font-weight:500;margin-left:7px}
  .storm-tut .bchip.on b{color:rgba(10,10,15,.55)}
`;

// Wires the filter chips: clicking one highlights it and shows only the cards
// whose data-tag matches (an empty filter shows all). Mirrors wireSqlToggles().
function wireBlogFilters() {
  const root = document.querySelector('.storm-tut');
  if (!root) return () => {};
  const chips = Array.from(root.querySelectorAll('.bchip'));
  const cardEls = Array.from(root.querySelectorAll('.cards .tcard'));
  const handlers = [];
  // No active chip means show everything; an active chip filters to its
  // category; clicking the active chip again deselects it and shows all.
  const apply = (filter) => {
    chips.forEach((c) => c.classList.toggle('on', !!filter && c.getAttribute('data-filter') === filter));
    cardEls.forEach((card) => {
      const show = !filter || card.getAttribute('data-tag') === filter;
      card.style.display = show ? '' : 'none';
    });
  };
  chips.forEach((chip) => {
    const onClick = () => {
      const next = chip.classList.contains('on') ? '' : chip.getAttribute('data-filter');
      apply(next);
    };
    chip.addEventListener('click', onClick);
    handlers.push([chip, onClick]);
  });
  return () => handlers.forEach(([chip, fn]) => chip.removeEventListener('click', fn));
}

const BODY = `
${navHtml('blog')}

<div class="tuthero">
  <h1>Ideas behind<br><span class="grad">the framework.</span></h1>
  <p class="sub">Design decisions, deep dives, and the reasoning that shapes ST/ORM.</p>
${filterBar}
</div>

<div class="shead" id="articles"><span class="mark">//</span>Articles<span class="sdesc">Why ST/ORM is built the way it is, and what that means for the code you write.</span></div>
<div class="cards">
${cards}
</div>

${FOOT_HTML}
`;

export default function Blog() {
  useEffect(() => wireBlogFilters(), []);
  return (
    <>
      <Head>
        <html lang="en" />
        <title>{TITLE}</title>
        <meta name="description" content={DESC} />
        <link rel="canonical" href="https://orm.st/blog/" />
        <meta property="og:type" content="website" />
        <meta property="og:title" content={TITLE} />
        <meta property="og:description" content={DESC} />
        <meta name="twitter:title" content={TITLE} />
        <meta name="twitter:description" content={DESC} />
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;700&display=swap"
          rel="stylesheet"
        />
      </Head>
      <style dangerouslySetInnerHTML={{__html: TUT_CSS + BLOG_FILTER_CSS}} />
      <div className="storm-tut" dangerouslySetInnerHTML={{__html: BODY}} />
    </>
  );
}
