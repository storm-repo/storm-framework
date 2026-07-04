import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: sealed entity hierarchies (single-table inheritance). Storm-way
// how-to; content verified against docs/polymorphism.md (sealed interface +
// @Discriminator, dtype column, per-subtype INSERT columns, exhaustive when).

const TITLE = 'Sealed Entity Hierarchies';
const DESC =
  'Map a Kotlin sealed interface to a table: subtypes are data classes, the ' +
  'discriminator column picks the type on read, and when expressions over ' +
  'results are exhaustive, checked by the compiler.';

const CODE_SEALED = [
  C('// The sealed interface is the entity; subtypes are plain data classes\n'),
  A('@Discriminator'), P('\n'),
  K('sealed interface '), T('Pet'), P(' : '), T('Entity'), P('<'), T('Int'), P('>\n\n'),
  K('data class '), T('Cat'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('name: '), T('String'), P(',\n'),
  P('    '), K('val '), P('indoor: '), T('Boolean'), P(',\n'),
  P(') : '), T('Pet'), P('\n\n'),
  K('data class '), T('Dog'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('name: '), T('String'), P(',\n'),
  P('    '), K('val '), P('weight: '), T('Int'), P(',\n'),
  P(') : '), T('Pet'), P(''),
].join('');

const CODE_CRUD = [
  K('val '), P('pets = orm.'), F('entity'), P('<'), T('Pet'), P('>()\n\n'),
  C('// Reads return the concrete subtypes; when is exhaustive by construction\n'),
  K('for'), P(' (pet '), K('in'), P(' pets.'), F('findAll'), P('()) {\n'),
  P('    '), K('when'), P(' (pet) {\n'),
  P('        '), K('is '), T('Cat'), P(' -> '), F('render'), P('('), S('"${pet.name}, indoor=${pet.indoor}"'), P(')\n'),
  P('        '), K('is '), T('Dog'), P(' -> '), F('render'), P('('), S('"${pet.name}, ${pet.weight}kg"'), P(')\n'),
  P('    }   '), C('// add a Bird subtype and this stops compiling until handled\n'),
  P('}\n\n'),
  C('// Writes go through the same repository\n'),
  P('pets.'), F('insert'), P('('), T('Cat'), P('(name = '), S('"Bella"'), P(', indoor = '), K('true'), P('))\n'),
  P('pets.'), F('update'), P('('), T('Cat'), P('(id = '), N('1'), P(', name = '), S('"Sir Whiskers"'), P(', indoor = '), K('true'), P('))'),
].join('');

const SQL_CRUD = [
  QC('-- one table; the discriminator column picks the subtype on read'), '\n',
  QK('SELECT'), ' p.id, p.dtype, p.name, p.indoor, p.weight ', QK('FROM'), ' pet p\n\n',
  QC('-- inserts write only the columns of the concrete subtype'), '\n',
  QK('INSERT INTO'), ' pet (dtype, name, indoor) ', QK('VALUES'), ' (', QQ("'Cat'"), ', ', QQ('?'), ', ', QQ('?'), ')\n',
  QK('INSERT INTO'), ' pet (dtype, name, weight) ', QK('VALUES'), ' (', QQ("'Dog'"), ', ', QQ('?'), ', ', QQ('?'), ')',
].join('');

const CODE_FILTER = [
  C('// Query the hierarchy like any other entity\n'),
  K('val '), P('indoorCats = orm.'), F('entity'), P('<'), T('Pet'), P('>().'), F('select'), P('()\n'),
  P('    .'), F('where'), P('('), T('Pet_'), P('.name '), K('like'), P(' '), S('"B%"'), P(')\n'),
  P('    .resultList\n'),
  P('    .'), F('filterIsInstance'), P('<'), T('Cat'), P('>()\n'),
  P('    .'), F('filter'), P(' { it.indoor }'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Sealed entities</div>
  <h1>Sealed entity <span class="grad">hierarchies</span></h1>
  <p class="dek">Kotlin already has the perfect tool for closed type hierarchies: sealed interfaces with exhaustive when. Storm maps them straight onto a table, so the compiler's exhaustiveness checking extends to your database rows.</p>
  <div class="meta"><span>Series · The Storm way</span><span>4 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>A <code>pet</code> table holds cats and dogs with some shared columns and some subtype-specific ones. The code should work with real <code>Cat</code> and <code>Dog</code> types, not a Pet with nullable everything, and adding a new subtype should be impossible to half-do.</p>

  <h2><span class="hno">02</span>The model</h2>
  <p>Declare the hierarchy exactly as you would in domain code. The sealed interface is the entity; the data classes are the subtypes; <code>@Discriminator</code> marks the column that tells rows apart:</p>
  ${editor({file: 'Pet.kt', tag: 'Kotlin · Storm', code: CODE_SEALED})}
  <p>All subtypes share the <code>pet</code> table. Shared fields like <code>name</code> live alongside subtype-specific ones like <code>indoor</code> and <code>weight</code>, which are simply NULL for rows of other subtypes.</p>

  <h2><span class="hno">03</span>Reads and writes</h2>
  <p>The repository works on the sealed interface, and results come back as concrete subtypes. That means <code>when</code> over a result is exhaustive: introduce a <code>Bird</code> and every unhandled branch in the codebase becomes a compile error:</p>
  ${editor({file: 'PetService.kt', tag: 'Kotlin · Storm', code: CODE_CRUD, sql: SQL_CRUD})}
  <p>On insert and update Storm inspects the runtime class, writes the discriminator, and includes only that subtype's columns. On select it reads the discriminator and constructs the right type. No casting, no manual type column handling.</p>

  <h2><span class="hno">04</span>Querying the hierarchy</h2>
  <p>Shared fields query through the metamodel as usual; subtype refinement is ordinary Kotlin on the results:</p>
  ${editor({file: 'PetService.kt', tag: 'Kotlin · Storm', code: CODE_FILTER})}
  <p>This page covers the single-table strategy, the default. When subtypes have many disjoint fields, the joined-table strategy (<code>@Polymorphic(JOINED)</code>) puts each subtype's columns in its own table; see <a class="tlink" href="/docs/polymorphism">Polymorphism</a> for the full decision guide, discriminator customization, and polymorphic foreign keys.</p>

  <h2><span class="hno">05</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/polymorphism">Polymorphism</a>
    <a href="/docs/entities">Entities</a>
    <a href="/docs/metamodel">Metamodel</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function SealedEntitiesTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="sealed-entities" body={BODY} />;
}
