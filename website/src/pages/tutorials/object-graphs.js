import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: persisting object graphs, JPA cascades vs Storm write sets.

const TITLE = 'Persisting Object Graphs: JPA Cascades vs Storm Write Sets';
const DESC =
  'JPA cascades configure, once per mapping, how far every persist travels. A ' +
  'Storm write set makes that decision per call: pass the entities, and unsaved ' +
  'parents are discovered, dependency-ordered, and keyed automatically.';

const CODE_JPA_MODEL = [
  C('// The graph lives in the mapping: collections point down, cascade rides them\n'),
  A('@Entity'), P('\n'),
  K('class '), T('Owner'), P('(\n'),
  P('    '), A('@Id'), P(' '), A('@GeneratedValue'), P(' '), K('var '), P('id: '), T('Long'), P(' = '), N('0'), P(',\n'),
  P('    '), K('var '), P('firstName: '), T('String'), P(',\n'),
  P(') {\n'),
  P('    '), A('@OneToMany'), P('(mappedBy = '), S('"owner"'), P(', cascade = ['), T('CascadeType'), P('.PERSIST])\n'),
  P('    '), K('var '), P('pets: '), T('MutableList'), P('<'), T('Pet'), P('> = '), F('mutableListOf'), P('()\n\n'),
  P('    '), K('fun '), F('addPet'), P('(pet: '), T('Pet'), P(') { pets += pet; pet.owner = '), K('this'), P(' }\n'),
  P('}\n\n'),
  A('@Entity'), P('\n'),
  K('class '), T('Pet'), P('(\n'),
  P('    '), A('@Id'), P(' '), A('@GeneratedValue'), P(' '), K('var '), P('id: '), T('Long'), P(' = '), N('0'), P(',\n'),
  P('    '), K('var '), P('name: '), T('String'), P(',\n'),
  P('    '), A('@ManyToOne'), P(' '), K('var '), P('owner: '), T('Owner'), P('? = '), K('null'), P(',\n'),
  P(')'),
].join('');

const CODE_JPA_PERSIST = [
  C('// One call on the root; the mapping decides how far it travels\n'),
  K('val '), P('owner = '), T('Owner'), P('(firstName = '), S('"Alice"'), P(')\n'),
  P('owner.'), F('addPet'), P('('), T('Pet'), P('(name = '), S('"Wolfie"'), P('))\n'),
  P('owner.'), F('addPet'), P('('), T('Pet'), P('(name = '), S('"Rex"'), P('))\n\n'),
  P('entityManager.'), F('persist'), P('(owner)   '), C('// pets become managed too; SQL runs at flush'),
].join('');

const SQL_JPA_PERSIST = [
  QC('-- at flush time, parents before children'), '\n',
  QK('INSERT INTO'), ' owner (first_name) ', QK('VALUES'), ' (', QQ('?'), ')\n',
  QK('INSERT INTO'), ' pet (name, owner_id) ', QK('VALUES'), ' (', QQ('?'), ', ', QQ('?'), ')\n',
  QK('INSERT INTO'), ' pet (name, owner_id) ', QK('VALUES'), ' (', QQ('?'), ', ', QQ('?'), ')\n\n',
  QC('-- the provider walked owner.pets; every persist that reaches an Owner,'), '\n',
  QC('-- from any call site, cascades exactly the same way'),
].join('');

const CODE_STORM_MODEL = [
  C('// The graph lives in the values: a row names its parents, like the FK column does\n'),
  K('data class '), T('Owner'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('firstName: '), T('String'), P(',\n'),
  P(') : '), T('Entity'), P('<'), T('Int'), P('>\n\n'),
  K('data class '), T('Pet'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('name: '), T('String'), P(',\n'),
  P('    '), A('@FK'), P(' '), K('val '), P('owner: '), T('Owner'), P(',\n'),
  P(') : '), T('Entity'), P('<'), T('Int'), P('>\n\n'),
  K('data class '), T('Visit'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('description: '), T('String'), P(',\n'),
  P('    '), A('@FK'), P(' '), K('val '), P('pet: '), T('Pet'), P(',\n'),
  P(') : '), T('Entity'), P('<'), T('Int'), P('>'),
].join('');

const CODE_STORM_INSERT = [
  C('// Build the graph by holding parent instances, then write it in one call\n'),
  K('val '), P('owner = '), T('Owner'), P('(firstName = '), S('"Alice"'), P(')\n'),
  K('val '), P('wolfie = '), T('Pet'), P('(name = '), S('"Wolfie"'), P(', owner = owner)\n'),
  K('val '), P('rex = '), T('Pet'), P('(name = '), S('"Rex"'), P(', owner = owner)     '), C('// same instance\n'),
  K('val '), P('visit = '), T('Visit'), P('(description = '), S('"Check-up"'), P(', pet = wolfie)\n\n'),
  P('orm.'), F('writeSet'), P('().'), F('insert'), P('(wolfie, rex, visit)'),
].join('');

const SQL_STORM_INSERT = [
  QC('-- one batch per entity type per dependency level'), '\n',
  QK('INSERT INTO'), ' owner (first_name) ', QK('VALUES'), ' (', QQ('?'), ')\n',
  QK('INSERT INTO'), ' pet (name, owner_id) ', QK('VALUES'), ' (', QQ('?'), ', ', QQ('?'), ')   ', QC('-- batch of 2'), '\n',
  QK('INSERT INTO'), ' visit (description, pet_id) ', QK('VALUES'), ' (', QQ('?'), ', ', QQ('?'), ')\n\n',
  QC('-- the owner was never passed: the pet values imply it (insert discovery),'), '\n',
  QC('-- and both pets hold the same instance, so both rows share its generated key'),
].join('');

const CODE_STORM_FETCH = [
  C('// Values never mutate: fetch the persisted graph back, or just its keys\n'),
  K('val '), P('saved: '), T('Visit'), P(' = orm.'), F('writeSet'), P('().'), F('insertAndFetch'), P('(visit)\n'),
  C('// saved.pet.owner.id carries the generated key; visit itself is untouched\n'), P('\n'),
  K('val '), P('ids = orm.'), F('writeSet'), P('().'), F('insertAndFetchIds'), P('(listOf(visit))\n'),
  C('// keys in input order, straight from the insert: no re-read'),
].join('');

const CODE_STORM_REMOVE = [
  C('// remove deletes exactly what you pass, children before parents\n'),
  P('orm.'), F('writeSet'), P('().'), F('remove'), P('(owner, wolfie, visit)\n'),
  C('// executed as: visit, wolfie, owner\n'), P('\n'),
  C('// reactive cleanup of dependents is the schema\'s job:\n'),
  C('// pet.owner_id REFERENCES owner(id) ON DELETE CASCADE'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Object graphs</div>
  <h1>Persisting object graphs: <span class="grad">cascades vs write sets</span></h1>
  <p class="dek">An object graph spanning several tables must reach the database in dependency order, with generated keys flowing from parents to children. JPA and Storm both solve it. They start from opposite ends.</p>
  <div class="meta"><span>Series · JPA to Storm</span><span>6 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>An <code>Owner</code> with two <code>Pet</code>s, one of which has a <code>Visit</code>. Four rows across three tables: the owner first, then the pets carrying the generated owner key, then the visit carrying a pet key. Whatever tool you use has to order the writes and move the keys.</p>

  <h2><span class="hno">02</span>The JPA way</h2>
  <p>In JPA the graph is a class model with associations in both directions, and cascade configuration on those associations decides how far each operation travels:</p>
  ${editor({file: 'Model.kt', tag: 'Kotlin · JPA', code: CODE_JPA_MODEL})}
  <p>Persisting the graph is one call on the root. <code>persist</code> makes the owner managed, propagates along every association marked <code>CascadeType.PERSIST</code>, and the provider writes the statements at flush time, assigning each generated key to the managed instances:</p>
  ${editor({file: 'RegistrationService.kt', tag: 'Kotlin · JPA', code: CODE_JPA_PERSIST, sql: SQL_JPA_PERSIST})}
  <p>Two things carried that graph. The parent-to-child collections, kept consistent with the child-to-parent references by the <code>addPet</code> helper. And the mapping, where the cascade lives: it is declared once and applies to every call site in the application. That is genuinely convenient, and it is also a decision you can no longer make per use case.</p>

  <h2><span class="hno">03</span>The Storm way</h2>
  <p>Storm entities are immutable values that model rows, so they reference only their parents, exactly as the foreign key columns do. No collections, no back-references, no helpers keeping the two sides in sync:</p>
  ${editor({file: 'Model.kt', tag: 'Kotlin · Storm', code: CODE_STORM_MODEL})}
  <p>A write set applies one operation to a heterogeneous collection of entities. Pass the leaves; unsaved parents are discovered through the foreign key fields:</p>
  ${editor({file: 'RegistrationService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_INSERT, sql: SQL_STORM_INSERT})}
  <p>The owner was never passed. <code>insert</code> extends the entities you supply with every unsaved entity reachable through their foreign key fields, the <em>discovered members</em>; Storm calls this <em>insert discovery</em>. Storm orders the result by foreign key dependencies, executes one batch per entity type per level, and propagates generated keys by instance identity: both pets hold the same <code>owner</code> instance, so both rows receive the same key. Nothing on <code>Owner</code> says that pets depend on it; the pet values say so themselves.</p>
  <p>One difference follows from immutability. JPA assigns generated keys to your instances; Storm values never change, so the <code>AndFetch</code> variants return the persisted state:</p>
  ${editor({file: 'RegistrationService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_FETCH})}

  <h2><span class="hno">04</span>Cascades point down, write sets point up</h2>
  <p>The deeper difference is one of direction. A JPA model references in both directions because cascades travel parent to child, along the collections. A Storm value references only upward: a pet names its owner because the pet row holds the foreign key, and that is the only graph there is. Insert discovery therefore needs no configuration, a value declares its own dependencies. And delete discovery deliberately does not exist, because a value never names its children.</p>
  <p>Behind that sits a difference of philosophy. In JPA, persistence is a property of the object model: you configure once how operations travel, and the session derives the writes from state at flush time. In Storm, persistence is an operation on values: a single call whose inputs fully determine what happens. Neither is free. Storm's price is that every call site states its intent; there is no per-association default to set once and rely on. JPA's price is action at a distance: the call site alone does not tell you what will be written, because the mapping and the session state complete the sentence.</p>
  <div class="note">A write set executes several statements and is not atomic by itself. Wrap it in <code>transaction { }</code> when the graph must commit or fail as one. Keys correlate by instance, not by equality: a <code>copy()</code> of an unsaved parent describes a second row.</div>

  <h2><span class="hno">05</span>Updates, deletes, and orphanRemoval</h2>
  <p>The other actions follow the same contract. Where <code>merge</code> with <code>CascadeType.MERGE</code> walks the associations and decides insert-or-update from entity state, Storm splits the decision into explicit actions: <code>update</code> for rows you know exist, <a href="/docs/upserts">upsert</a> when the database should resolve it atomically. Both write exactly the entities you pass, and <code>update</code> rejects unsaved members instead of quietly inserting them.</p>
  <p>That includes the case that trips JPA instincts hardest: an updated owner held inside a pet you pass to <code>update</code> is not written. A keyed referenced entity contributes only its primary key, and there is no session that will flush the owner later. When both changed, name both: <code>update(pet, owner)</code>. Each row is written with its own dirty check, so unchanged members still cost nothing.</p>
  <p>There is no cascade that insert has and update lacks; one rule covers every action: a write set writes the entities you name, plus the entities your values make necessary. An unsaved owner is necessary, the pet row cannot be written without its key, so holding it is unambiguous intent to create it. A keyed owner is never necessary: it already provides the foreign key value, and beyond that it is whatever snapshot was hydrated when the pet was read. Writing it anyway would silently overwrite newer state with a stale copy. JPA can afford to walk the graph on merge because the session identity-maps entities; without a session, a snapshot is not write intent.</p>
  <p><code>remove</code> deletes the entities you name, children before parents. What it does not do is walk the graph looking for dependents:</p>
  ${editor({file: 'CleanupService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_REMOVE})}
  <p><code>orphanRemoval</code> has no counterpart by design: Storm entities hold no child collections, so there is no collection mutation to react to. Declare the reaction where the database can enforce it for every writer, on the constraint, or delete rows explicitly.</p>

  <h2><span class="hno">06</span>Side by side</h2>
  <table class="cmp">
    <tr><th></th><th>JPA cascades</th><th>Storm write sets</th></tr>
    <tr><td>Behavior declared</td><td>On the mapping, for every call site</td><td>At the call site, per operation</td></tr>
    <tr><td>What carries the graph</td><td>Collections and back-references, kept in sync</td><td>FK fields; a value names its parents</td></tr>
    <tr><td>When SQL runs</td><td>At flush time</td><td>At the call, in dependency-ordered batches</td></tr>
    <tr><td>Generated keys</td><td>Assigned to managed instances</td><td>Propagated by instance identity; <code>AndFetch</code> returns keyed values</td></tr>
    <tr><td>What counts as new</td><td>Entity state: transient, managed, detached</td><td>Local test: default id on an auto-generated key</td></tr>
    <tr><td>Cascading deletes</td><td><code>CascadeType.REMOVE</code>, <code>orphanRemoval</code></td><td>Explicit <code>remove</code>; <code>ON DELETE CASCADE</code> in the schema</td></tr>
  </table>

  <h2><span class="hno">07</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/write-sets">Write Sets</a>
    <a href="/docs/jpa-cascades-vs-write-sets">JPA Cascades vs Write Sets</a>
    <a href="/docs/upserts">Upserts</a>
    <a href="/docs/transactions">Transactions</a>
    <a href="/docs/migration-from-jpa">Migration from JPA</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" target="_blank" rel="noopener" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function ObjectGraphsTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="object-graphs" body={BODY} />;
}
