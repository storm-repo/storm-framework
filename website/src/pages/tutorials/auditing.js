import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: auditing with entity callbacks. Storm-way how-to; content verified
// against docs/entity-lifecycle.md (EntityCallback, before* returns the entity,
// withEntityCallback registration, after-callback pre-persist caveat).

const TITLE = 'Auditing with Entity Callbacks';
const DESC =
  'Populate createdAt and updatedAt automatically with EntityCallback: because ' +
  'entities are immutable, before-hooks return a transformed copy, which makes ' +
  'the mechanism explicit, testable, and free of hidden mutation.';

const CODE_CALLBACK = [
  K('data class '), T('Article'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('title: '), T('String'), P(',\n'),
  P('    '), K('val '), P('createdAt: '), T('Instant'), P('? = '), K('null'), P(',\n'),
  P('    '), K('val '), P('updatedAt: '), T('Instant'), P('? = '), K('null'), P(',\n'),
  P(') : '), T('Entity'), P('<'), T('Int'), P('>\n\n'),
  C('// Before-hooks return the entity to persist; copy() is the transformation\n'),
  K('class '), T('AuditCallback'), P(' : '), T('EntityCallback'), P('<'), T('Article'), P('> {\n\n'),
  P('    '), K('override fun '), F('beforeInsert'), P('(entity: '), T('Article'), P('): '), T('Article'), P(' {\n'),
  P('        '), K('val '), P('now = '), T('Instant'), P('.'), F('now'), P('()\n'),
  P('        '), K('return'), P(' entity.'), F('copy'), P('(createdAt = now, updatedAt = now)\n'),
  P('    }\n\n'),
  P('    '), K('override fun '), F('beforeUpdate'), P('(entity: '), T('Article'), P('): '), T('Article'), P(' {\n'),
  P('        '), K('return'), P(' entity.'), F('copy'), P('(updatedAt = '), T('Instant'), P('.'), F('now'), P('())\n'),
  P('    }\n'),
  P('}'),
].join('');

const CODE_REGISTER = [
  C('// Registration is explicit and immutable: a new template, callback applied\n'),
  K('val '), P('orm = dataSource.orm.'), F('withEntityCallback'), P('('), T('AuditCallback'), P('())\n\n'),
  C('// From here, every insert and update flows through the hooks\n'),
  K('val '), P('article = orm '), K('insert '), T('Article'), P('(title = '), S('"Storm 1.11"'), P(')\n'),
  C('// article.createdAt and updatedAt are set'),
].join('');

const CODE_VALIDATION = [
  C('// The same hook enforces invariants before the SQL round trip\n'),
  K('class '), T('ArticleValidation'), P(' : '), T('EntityCallback'), P('<'), T('Article'), P('> {\n'),
  P('    '), K('override fun '), F('beforeInsert'), P('(entity: '), T('Article'), P('): '), T('Article'), P(' {\n'),
  P('        '), F('require'), P('(entity.title.'), F('isNotBlank'), P('()) { '), S('"Title must not be blank"'), P(' }\n'),
  P('        '), K('return'), P(' entity\n'),
  P('    }\n'),
  P('}\n\n'),
  C('// Callbacks chain and fire in registration order\n'),
  K('val '), P('orm = dataSource.orm\n'),
  P('    .'), F('withEntityCallback'), P('('), T('ArticleValidation'), P('())\n'),
  P('    .'), F('withEntityCallback'), P('('), T('AuditCallback'), P('())'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Auditing</div>
  <h1>Auditing with <span class="grad">entity callbacks</span></h1>
  <p class="dek">Every table wants createdAt and updatedAt, and nobody wants to set them by hand. EntityCallback hooks into the write path, and because entities are immutable, the hook returns a transformed copy instead of mutating state behind your back.</p>
  <div class="meta"><span>Series · The Storm way</span><span>4 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>Stamp audit timestamps on every insert and update, enforce a business invariant before data reaches the database, and keep both concerns out of the service code that writes the entities.</p>

  <h2><span class="hno">02</span>The audit callback</h2>
  <p><code>EntityCallback&lt;E&gt;</code> is typed to the entity it applies to, with hooks for before and after each mutation. The before-hooks return the entity that will actually be persisted, which is how transformation works in an immutable world:</p>
  ${editor({file: 'AuditCallback.kt', tag: 'Kotlin · Storm', code: CODE_CALLBACK})}

  <h2><span class="hno">03</span>Registration</h2>
  <p>Callbacks attach to the template, following Storm's immutable configuration pattern: the original template is unchanged, and the returned one applies the callback to every write:</p>
  ${editor({file: 'Setup.kt', tag: 'Kotlin · Storm', code: CODE_REGISTER})}
  <p>One caveat worth knowing: the entity passed to the after-hooks is the pre-persist value, without database-generated fields. For the generated id, use the return value of <code>insertAndFetch</code>.</p>

  <h2><span class="hno">04</span>Validation, same mechanism</h2>
  <p>Because before-hooks run ahead of the SQL, they double as a validation point with domain-specific error messages, and multiple callbacks chain in registration order:</p>
  ${editor({file: 'Callbacks.kt', tag: 'Kotlin · Storm', code: CODE_VALIDATION})}
  <p>For auditing across many entity types, a single global callback with a shared <code>Auditable</code> interface covers them all; the <a class="tlink" href="/docs/entity-lifecycle">lifecycle docs</a> show that pattern, the Spring Boot auto-registration, and how callbacks route for upserts.</p>

  <h2><span class="hno">05</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/entity-lifecycle">Entity Lifecycle</a>
    <a href="/docs/entities">Entities</a>
    <a href="/docs/validation">Validation</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function AuditingTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="auditing" body={BODY} />;
}
