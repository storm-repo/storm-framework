import React, {useEffect} from 'react';
import Head from '@docusaurus/Head';
import {TUT_CSS, navHtml, FOOT_HTML} from '../../components/tutorial/tutorialTheme';

// The tutorial hub at /tutorials, rendered in the landing-page style (see
// tutorialTheme.js). Three series: "From JPA" and "From Exposed" compare the
// familiar approach with the Storm approach; "The Storm way" is task recipes
// for people already using Storm. Each tutorial page lives next to this file.

const TITLE = 'ST/ORM Tutorials · Familiar persistence tasks, the Storm way';
const DESC =
  'Task-focused tutorials for engineers coming from JPA, Hibernate, or Exposed, ' +
  'plus how-to recipes for Storm itself. Each comparison takes a familiar ' +
  'persistence problem, shows both approaches, and shows the SQL they produce.';

const BODY = `
${navHtml('tutorials')}

<div class="tuthero">
  <h1>Familiar Tasks.<br><span class="grad">Simpler Solutions.</span></h1>
  <p class="sub">Task-focused tutorials for engineers coming from JPA, Hibernate, or Exposed, plus how-to recipes for Storm itself. Each comparison takes a persistence task you already know, shows both approaches side by side, and lets you inspect the SQL they produce.</p>
  <div class="catnav">
    <a href="#start-here">Start here<b>1</b></a>
    <a href="#from-jpa">From JPA<b>10</b></a>
    <a href="#from-exposed">From Exposed<b>7</b></a>
    <a href="#storm-way">The Storm way<b>6</b></a>
  </div>
</div>

<div class="shead" id="start-here"><span class="mark">//</span>Start here<span class="sdesc">New to Storm? Build a complete REST API from an empty project, then use the task recipes below as you go.</span></div>
<div class="cards">
  <a class="tcard" href="/tutorials/build-a-rest-api" style="grid-column:1 / -1">
    <div class="tt">Build a REST API from scratch<span class="arrow">→</span></div>
    <div class="td">Empty project to a running, tested API: two entities, their relationship loaded in a single query, CRUD routes on Ktor, and a test that asserts the SQL. The whole end-to-end path in about twenty minutes.</div>
    <div class="tm"><span>Kotlin</span><span>Ktor 3</span><span>~20 min</span></div>
  </a>
</div>

<div class="shead" id="from-jpa"><span class="mark">//</span>From JPA<span class="sdesc">You know Spring Data JPA and Hibernate. Each tutorial shows a task the JPA way and the Storm way, side by side.</span></div>
<div class="cards">
  <a class="tcard" href="/tutorials/n-plus-one">
    <div class="tt">Solving the N+1 problem<span class="arrow">→</span></div>
    <div class="td">Why JPA list queries degrade into 1 + N selects, what JOIN FETCH and entity graphs actually fix, and how Storm removes the problem by making loading policy part of the entity model.</div>
    <div class="tm"><span>JPA to Storm</span><span>7 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/projections">
    <div class="tt">Projections: read-only views on your data<span class="arrow">→</span></div>
    <div class="td">JPA entities are read-write, and @Immutable is a runtime convention. A Storm projection models a read-only view on the data, a table subset or a database view, built to be nested, filtered, and reused.</div>
    <div class="tm"><span>JPA to Storm</span><span>6 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/query-results">
    <div class="tt">Typed query results without the mapping layer<span class="arrow">→</span></div>
    <div class="td">JPA shapes custom results with proxies and constructor expressions that resolve at runtime. In Storm, any data class is a result type: define it next to the query and rows map by position.</div>
    <div class="tm"><span>JPA to Storm</span><span>5 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/pagination">
    <div class="tt">Pagination and keyset scrolling<span class="arrow">→</span></div>
    <div class="td">Offset pages scan deeper as users page further, and every request pays for a count. Storm offers classic pages when the UI needs them and cursor-based scrolling with REST-ready cursors when it does not.</div>
    <div class="tm"><span>JPA to Storm</span><span>6 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/upserts">
    <div class="tt">Upserts without the workarounds<span class="arrow">→</span></div>
    <div class="td">JPA's find-then-save pattern is two statements and one race condition. Storm generates the database's native upsert syntax: atomic, batched, and portable across all major databases.</div>
    <div class="tm"><span>JPA to Storm</span><span>5 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/optimistic-locking">
    <div class="tt">Optimistic locking with immutable entities<span class="arrow">→</span></div>
    <div class="td">JPA checks the version at flush time, far from the code that made the change. Storm checks it on the update statement itself, and immutable values make conflict handling ordinary code.</div>
    <div class="tm"><span>JPA to Storm</span><span>5 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/transactions">
    <div class="tt">Transactions without the proxy rules<span class="arrow">→</span></div>
    <div class="td">@Transactional works through proxies, with rules around self-invocation and visibility that fail silently. Storm's transaction blocks make the scope visible, add onCommit callbacks, and stay coroutine-aware.</div>
    <div class="tm"><span>JPA to Storm</span><span>6 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/mapped-collections">
    <div class="tt">Mapped collections vs queried associations<span class="arrow">→</span></div>
    <div class="td">owner.pets is one property access, and that is genuinely convenient. Storm queries the association instead: one line that loads when you decide, composes with filters and paging, and survives the join table growing columns.</div>
    <div class="tm"><span>JPA to Storm</span><span>6 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/object-graphs">
    <div class="tt">Persisting object graphs: cascades vs write sets<span class="arrow">→</span></div>
    <div class="td">CascadeType configures, once per mapping, how far every persist travels. A Storm write set decides per call: pass the entities, and unsaved parents are discovered, dependency-ordered, and keyed by instance identity.</div>
    <div class="tm"><span>JPA to Storm</span><span>6 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/sql-templates">
    <div class="tt">Full SQL without giving up safety<span class="arrow">→</span></div>
    <div class="td">Native queries return untyped rows and invite concatenation. Storm's SQL templates bind interpolated values safely, map rows to data classes, and expand your entities into columns and joins.</div>
    <div class="tm"><span>JPA to Storm</span><span>6 min read</span><span>Kotlin</span></div>
  </a>
</div>

<div class="shead" id="from-exposed"><span class="mark">//</span>From Exposed<span class="sdesc">You know and like JetBrains Exposed, with good reason. Same format: the Exposed way next to the Storm way.</span></div>
<div class="cards">
  <a class="tcard" href="/tutorials/exposed-entities">
    <div class="tt">One model instead of three<span class="arrow">→</span></div>
    <div class="td">Exposed earned its reputation, and on type safety the two are equals. The difference is where knowledge lives: joins and mappings travel with every query, while Storm declares the model once, which compounds on large schemas.</div>
    <div class="tm"><span>Exposed to Storm</span><span>6 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/exposed-ktor">
    <div class="tt">Ktor services, side by side<span class="arrow">→</span></div>
    <div class="td">Home ground for both. Exposed connects a Database and wraps every route operation in a transaction; Storm installs as a plugin, reads without ceremony, and its suspend transactions ride the coroutine context.</div>
    <div class="tm"><span>Exposed to Storm</span><span>5 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/exposed-n-plus-one">
    <div class="tt">Eager loading and N+1<span class="arrow">→</span></div>
    <div class="td">Exposed's .with() batches references efficiently, one call site at a time, a step above the SQL it runs. Storm puts loading policy in the model, so there is nothing to remember and nothing to forget.</div>
    <div class="tm"><span>Exposed to Storm</span><span>5 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/exposed-transactions">
    <div class="tt">Transactions translate almost one to one<span class="arrow">→</span></div>
    <div class="td">Exposed and Storm agree that transactions are explicit blocks. A translation table for the settings you know, plus the places they differ: propagation per block, onCommit callbacks, and built-in retry on the Exposed side.</div>
    <div class="tm"><span>Exposed to Storm</span><span>5 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/exposed-queries">
    <div class="tt">Queries and full SQL<span class="arrow">→</span></div>
    <div class="td">The everyday queries translate almost line by line. The gap opens past the DSL: exec() hands you a raw ResultSet, while Storm's SQL templates keep typed rows and bind-safe parameters.</div>
    <div class="tm"><span>Exposed to Storm</span><span>5 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/exposed-upserts">
    <div class="tt">Upserts and batching<span class="arrow">→</span></div>
    <div class="td">Both libraries generate native, dialect-aware upserts, so this is mostly a translation guide. Exposed offers finer control over the update branch; Storm works at the entity level and returns hydrated values.</div>
    <div class="tm"><span>Exposed to Storm</span><span>4 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/exposed-schema">
    <div class="tt">Who owns the schema<span class="arrow">→</span></div>
    <div class="td">Exposed generates DDL from table objects, excellent early on. Storm is schema-first: migrations own the schema and validateSchema() catches drift at startup or in CI. A winner on each side.</div>
    <div class="tm"><span>Exposed to Storm</span><span>4 min read</span><span>Kotlin</span></div>
  </a>
</div>

<div class="shead" id="storm-way"><span class="mark">//</span>The Storm way<span class="sdesc">Already using Storm? Task recipes, no comparisons, straight to the point.</span></div>
<div class="cards">
  <a class="tcard" href="/tutorials/testing">
    <div class="tt">Testing your data layer with @StormTest<span class="arrow">→</span></div>
    <div class="td">One annotation gives you an in-memory database, schema scripts, and injected parameters. Assert results and the SQL that produced them, then run the same tests against PostgreSQL with Testcontainers.</div>
    <div class="tm"><span>The Storm way</span><span>4 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/streaming">
    <div class="tt">Streaming large result sets<span class="arrow">→</span></div>
    <div class="td">Process millions of rows with constant memory: resultFlow streams rows as they arrive, structured concurrency cleans up cursors, and lists or Flows batch the write direction.</div>
    <div class="tm"><span>The Storm way</span><span>4 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/sealed-entities">
    <div class="tt">Sealed entity hierarchies<span class="arrow">→</span></div>
    <div class="td">Map a Kotlin sealed interface to a table: subtypes are data classes, the discriminator picks the type on read, and when over results is exhaustive, checked by the compiler.</div>
    <div class="tm"><span>The Storm way</span><span>4 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/json-columns">
    <div class="tt">JSON columns without the fuss<span class="arrow">→</span></div>
    <div class="td">Mark a field @Json and maps, lists, and structured objects serialize automatically. JSON aggregation goes further: a one-to-many loaded in a single query.</div>
    <div class="tm"><span>The Storm way</span><span>4 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/auditing">
    <div class="tt">Auditing with entity callbacks<span class="arrow">→</span></div>
    <div class="td">Stamp createdAt and updatedAt automatically. Because entities are immutable, before-hooks return a transformed copy: explicit, testable, no hidden mutation.</div>
    <div class="tm"><span>The Storm way</span><span>4 min read</span><span>Kotlin</span></div>
  </a>
  <a class="tcard" href="/tutorials/observability">
    <div class="tt">Seeing what Storm does<span class="arrow">→</span></div>
    <div class="td">@SqlLog shows every statement a repository runs, inlined parameters make the output copy-paste executable, and JMX metrics expose cache and dirty-checking behavior.</div>
    <div class="tm"><span>The Storm way</span><span>3 min read</span><span>Kotlin</span></div>
  </a>
</div>

<div class="soon">
  <div class="lbl">In progress</div>
  <div class="chips">
    <span>Converters and custom types</span>
    <span>Composite and natural keys</span>
    <span>Entity caching and dirty checking</span>
  </div>
  <p>Want one of these sooner, or a topic that is not listed? <a href="https://github.com/storm-orm/storm-framework/issues" target="_blank" rel="noopener">Open an issue on GitHub</a>.</p>
</div>

${FOOT_HTML}
`;

// Wires the series chips in the hero: clicking one shows only that section
// (its header and cards) and highlights the chip; clicking it again clears the
// filter and shows all. Falls back to anchor jumps when JS is unavailable.
function wireTutorialFilters() {
  const root = document.querySelector('.storm-tut');
  if (!root) return () => {};
  const chips = Array.from(root.querySelectorAll('.catnav a'));
  const sections = Array.from(root.querySelectorAll('.shead'));
  const handlers = [];
  const apply = (filter) => {
    chips.forEach((c) => c.classList.toggle('on', !!filter && (c.getAttribute('href') || '').slice(1) === filter));
    sections.forEach((shead) => {
      const cards = shead.nextElementSibling;
      const show = !filter || shead.id === filter;
      shead.style.display = show ? '' : 'none';
      if (cards && cards.classList.contains('cards')) cards.style.display = show ? '' : 'none';
    });
  };
  chips.forEach((chip) => {
    const onClick = (e) => {
      e.preventDefault();
      const id = (chip.getAttribute('href') || '').slice(1);
      apply(chip.classList.contains('on') ? '' : id);
    };
    chip.addEventListener('click', onClick);
    handlers.push([chip, onClick]);
  });
  return () => handlers.forEach(([chip, fn]) => chip.removeEventListener('click', fn));
}

export default function Tutorials() {
  useEffect(() => wireTutorialFilters(), []);
  return (
    <>
      <Head>
        <html lang="en" />
        <title>{TITLE}</title>
        <meta name="description" content={DESC} />
        <link rel="canonical" href="https://orm.st/tutorials/" />
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
      <style dangerouslySetInnerHTML={{__html: TUT_CSS}} />
      <div className="storm-tut" dangerouslySetInnerHTML={{__html: BODY}} />
    </>
  );
}
