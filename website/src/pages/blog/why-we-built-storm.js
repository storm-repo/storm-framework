import React from 'react';
import {BlogPage} from '../../components/blog/blogTheme';

const TITLE = 'Why we built ST/ORM';
const DESC =
  'We used JPA and Hibernate for years and mostly liked them. This is the ' +
  'quieter story of why we still built ST/ORM: a backend we had to replace, an ' +
  'entity model that pushed us toward DTOs, and an idea, sparked by Java ' +
  'String Templates, for a model we actually wanted to program against.';
const SLUG = 'why-we-built-storm';
const DATE = '2025-09-02';

const BODY = `
<div class="art">
  <div class="crumbs"><a href="/blog/">Blog</a><span class="sep">/</span>Why we built ST/ORM</div>
  <h1>Why we built <span class="grad">ST/ORM</span></h1>
  <p class="dek"><em>&ldquo;We set out to build the model we would enjoy programming against.&rdquo;</em></p>
  <div class="meta"><span>September 2, 2025</span><span>Origin</span><span>6 min read</span></div>

  <h2>We used to reach for Hibernate by default</h2>
  <p>We still like Hibernate, actually. It served us well for a long time, and it has had a huge impact on the JVM ecosystem. It solved real problems and helped shape the way many applications are built.</p>
  <p>We also liked where the ecosystem had gone. Modern JPA and Spring Data improved a great deal on what they inherited: a derived repository method turns a simple query into a method name, most of the old boilerplate is gone, and the day-to-day is genuinely pleasant. JPQL we never really liked, though it has its merits. Ask us at the time and we would have said the tools were good, and mostly they were.</p>
  <p>At the same time, no tool is perfect. As our projects grew larger, Hibernate started to get in our way a bit too often. For the type of systems we were building, the trade-offs became more visible.</p>
  <p>At some point, one of our teams even chose JdbcTemplate for a new project. That says something. When plain SQL and manual mapping start to feel like a relief, you know the abstraction is costing you more than expected. It feels refreshingly simple at first. Then the application grows, and you quickly remember why higher-level tools were invented in the first place.</p>
  <p>But none of that was the thing we kept fighting. What wore on us was not the queries, or the boilerplate, or the SQL. It was one level down, in the way the entities themselves behaved, and it only came fully into focus the day we had to start over.</p>

  <h2>The backend we had to replace</h2>
  <p>That day arrived when we set out to replace a backend that had been with the company since we were still a startup, the kind of application that grows one requirement at a time until it simply has to be rebuilt properly.</p>
  <p>The scale alone was daunting. Modeling more than 300 tables as entities is a large, careful job, and we had watched the previous model grow heavier over the years. The more the entities referenced one another, the harder it became to stop a simple read from fanning out into many queries or pulling in more of the graph than we wanted. Doing all of that again, from scratch, was not encouraging.</p>
  <p>But effort was not the real question. A rebuild is the moment you get to ask whether you want to make the same decisions again, and one of them is a question every JPA codebase meets eventually: do you pass entities between your internal components, or convert everything into DTOs?</p>
  <p>At the edges, for an external API, DTOs make sense on their own. Inside the application you often write them for a narrower reason. An entity is a managed, session-bound instance, and it is not always safe to hand around freely, so you copy it into a DTO to keep that persistence concern from leaking into the rest of your code. That is a lot of packing and unpacking, and we had done it once already. The real question was whether we wanted to do it again.</p>

  <h2>Designed today, it would look different</h2>
  <p>Underneath all of this, I had been turning over a different question for years. The ORM most of us reach for was designed more than two decades ago, and the languages have moved a long way since. So I kept coming back to the same thought experiment: if you designed it today, from a blank page, what would it look like?</p>
  <p>Back then you more or less had to build on mutable, managed beans, because that is what the language gave you. Today the language hands you immutable value types for free: Java records and Kotlin data classes, cheap to create, equal by their contents, and safe to pass to any layer or thread without worrying what becomes of them next. With those in hand, a managed, session-bound entity looks less like the natural choice and more like an answer to a constraint the language no longer has. If I were starting today, I would build on the records.</p>

  <h2>The idea, and the model I wanted</h2>
  <p>The thought experiment turned into a plan when Java String Templates arrived as a preview feature. That was the spark. They let you write a real string, SQL in our case, with typed values embedded directly in it and checked by the compiler. Seeing that, the first piece fell into place: a SQL template engine with row mapping, where your records go into the query and the same records come back out, with nothing to hand-map on either side. String Templates were the catalyst, not a dependency: once we had the shape, we kept it, and it does not require the preview feature at all.</p>
  <p>And once records go in and come out, a second thing follows that I wanted just as much. You can describe the whole database in its most concise form: small records that map one to one onto your tables, and nothing more. The records describe the mapping, so there is nothing to configure on the side and no second copy of the schema living in a file somewhere. A model that small is a pleasure to work with, and that was part of what we were after.</p>
  <p>Because you are not laying an abstraction over the database, the record stays true to the actual shape of your tables. And because it was never shaped for one particular query or screen, it is not tied to a single use, so the same record serves across the application. Everything you asked for is already in it, so there is no lazy loading to reason about later.</p>
  <p>It also carries no session and no persistence state. The database stays in the layer that talks to it, instead of leaking into the rest of your code. That leak was the thing the DTOs had been guarding against, and now there was nothing to guard.</p>
  <p>The part that had made the rebuild feel so daunting became easier too. Getting all those tables accurate still takes real time, and a wrong type or a missed nullability tends to surface later, at runtime. But an entity is only a plain record, so there is little to get wrong, and you can validate each one against the live schema to confirm it lines up. What had been a slow, careful job became one we could trust.</p>
  <p>That template engine and its row mapping were where it all started, not where it stopped. We already had plain records and a metamodel that described their columns, and those pieces pointed at the next step on their own. A repository layer followed: CRUD and type-safe queries, checked by the compiler through that same metamodel. It did not feel like adding a feature. It felt like following the model to where it already led.</p>
  <p>Most of the time you are not writing SQL at all. You are calling type-safe methods over your records, and the real SQL sits underneath, backing every one of them and ready the moment you want to take full control. So the end product is not a SQL API. It is an ORM backed by SQL.</p>
  <p>So that is how it happened. We did not set out to build another ORM. We set out to build the model we would enjoy programming against, and building the ORM was the natural consequence. We called it ST/ORM.</p>

  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="/blog/" class="btn">More from the blog</a>
  </div>
</div>`;

export default function Page() {
  return <BlogPage title={TITLE} description={DESC} slug={SLUG} dateISO={DATE} body={BODY} />;
}
