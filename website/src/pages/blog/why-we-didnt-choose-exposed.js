import React from 'react';
import {BlogPage} from '../../components/blog/blogTheme';

const TITLE = "Why we didn't choose Exposed for ST/ORM";
const DESC =
  "We build in Kotlin, so why didn't we build ST/ORM on JetBrains Exposed? A " +
  'respectful comparison of Exposed, schema-first design, DAO entities, and ' +
  "ST/ORM's different boundary.";
const SLUG = 'why-we-didnt-choose-exposed';
const DATE = '2026-04-14';

const BODY = `
<div class="art">
  <div class="crumbs"><a href="/blog/">Blog</a><span class="sep">/</span>Why we didn't choose Exposed</div>
  <h1>Why we didn't choose <span class="grad">Exposed</span></h1>
  <p class="dek">We build in Kotlin, so there is a fair question we owe an answer to. JetBrains already makes <a class="tlink" href="https://www.jetbrains.com/help/exposed/" target="_blank" rel="noopener">JetBrains Exposed</a>, a strong and well-liked Kotlin SQL library for exactly our language. Why did we not simply build ST/ORM on that?</p>
  <div class="meta"><span>April 14, 2026</span><span>Comparison</span><span>4 min read</span></div>

  <h2>A question of boundaries</h2>
  <p>The answer is less about missing features and more about boundaries. Exposed gives you a strong Kotlin way to model and work with a database. ST/ORM starts from the belief that the data model belongs in the database, and focuses on making that model available to application logic as plain, typed data. It is the same idea behind <a class="tlink" href="/blog/why-we-built-storm">why we built ST/ORM</a> in the first place.</p>

  <h2>Exposed starts from the database</h2>
  <p>Exposed and the thing we were building start from different first principles, and Exposed's fit the Kotlin story very well. Its center of gravity is the database itself. With its table DSL you describe a whole schema in Kotlin: the tables, the columns, the keys and constraints, and you can drive the database from that description. At modeling a database this way, Exposed is genuinely excellent. It is hard to beat. If your goal is to describe and command a database in idiomatic Kotlin, it is a superb answer.</p>

  <h2>Exposed aims to be the Kotlin way to talk to SQL</h2>
  <p>That strength comes with a wide aim. Exposed sets out to be the complete Kotlin way to talk to a database, and defining the schema is the part it optimizes for. Writing an application against that schema, day to day, is a slightly different concern, and there the fit is looser. The DSL gives you a type-safe way to write queries, but it hands the results back as rows for you to map into your own types. Across a handful of tables that is fine. Across a few hundred, it is a lot of mapping to write and keep correct by hand.</p>

  <h2>The DAO API solves part of the mapping problem</h2>
  <p>Exposed does have an answer for the mapping. Its DAO API turns each row into an entity object you work with directly, and for many projects it closes the gap well. What gave us pause was the kind of abstraction it is. A <a class="tlink" href="/blog/three-abstractions">repository</a> abstracts access: it groups the operations for reading and writing a row, and hands the row back as the same plain record it always was. The DAO abstracts representation: on top of the table it puts a second version of your data, a mutable, transaction-bound entity with lazy proxies and updates that flush on commit. That is a parallel model to keep in sync, and it carries the managed lifecycle we had just spent a rebuild moving away from. Both are useful. Only one adds another model of your data to learn.</p>

  <h2>ST/ORM draws the boundary differently</h2>
  <p>We come at this from the other side, though not in the way it might sound. Exposed is schema-first. ST/ORM is database-respecting, but application-facing. We agree with Exposed on the thing that matters most: the real data model lives in the database, not in your code. Where we part is that Exposed also gives you the tools to define that model from Kotlin, and we did not want to.</p>
  <p>We take the schema as the database hands it to us, and put all of our effort into a narrower goal: making that model available to application code as plain, typed records, the same <a class="tlink" href="/blog/entities-should-be-values">values rather than managed objects</a> that shape the rest of ST/ORM. It follows straight from <a class="tlink" href="/blog/data-layer-first-principles">our first-principles approach to the data layer</a>. Exposed lets you model the database beautifully and then reach for the data. We leave the database to define itself, and reach only for the data, as plain records that go into <a class="tlink" href="/blog/stop-hiding-my-sql">real SQL</a> and come back unchanged.</p>

  <h2>Kotlin-first, enterprise JVM second</h2>
  <p>It is genuinely good that Exposed, after many years, is now fully embraced by JetBrains, with a 1.0 release and real investment behind it. It is their Kotlin-native data library, and it sits close to where JetBrains is heading, which is Kotlin everywhere, multiplatform included. ST/ORM is aimed at a different room. It lives in the enterprise JVM, where Kotlin is a first-class citizen rather than the only one. It became a Kotlin-first platform for a plain reason: we want the Java variant to feel natural in Java too, and the language is still catching up in a few places that matter to an API shaped like this one.</p>

  <h2>A fit decision, not a verdict</h2>
  <p>So none of this is a case against Exposed. We admire it, enough that our own transactions follow the same programmatic, coroutine-friendly shape JetBrains Exposed made popular. Our decision was a fit decision, not a verdict. Exposed is excellent when you want to model and command a database in Kotlin. ST/ORM optimizes for a different boundary. Once you have committed to that, you are no longer looking for a library to adopt. You are looking at the thing you probably have to build yourself. Both of those can be true at once.</p>

  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="/blog/" class="btn">More from the blog</a>
  </div>
</div>`;

export default function Page() {
  return <BlogPage title={TITLE} description={DESC} slug={SLUG} dateISO={DATE} body={BODY} />;
}
