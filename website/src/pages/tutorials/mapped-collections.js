import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor, glance,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: mapped collections (@OneToMany/@ManyToMany) vs queried
// associations. This is the page where JPA is conceded to be more convenient
// on the surface; the content explains what the convenience costs and why
// Storm queries the association instead. Patterns match docs/relationships.md.

const TITLE = 'Mapped Collections vs Queried Associations: JPA vs Storm';
const DESC =
  '@OneToMany and @ManyToMany make navigation a property access, and that is ' +
  'genuinely convenient. Storm asks you to query the association instead. This ' +
  'page spells out the trade: one property versus one line, and what each costs.';

const CODE_JPA_COLLECTIONS = [
  A('@Entity'), P('\n'),
  K('class '), T('Owner'), P('(\n'),
  P('    '), A('@Id'), P(' '), A('@GeneratedValue'), P(' '), K('var '), P('id: '), T('Int'), P('? = '), K('null'), P(',\n'),
  P('    '), K('var '), P('name: '), T('String'), P(' = '), S('""'), P(',\n\n'),
  P('    '), A('@OneToMany'), P('(mappedBy = '), S('"owner"'), P(')\n'),
  P('    '), K('var '), P('pets: '), T('MutableList'), P('<'), T('Pet'), P('> = '), F('mutableListOf'), P('(),\n'),
  P(')\n\n'),
  A('@Entity'), P('\n'),
  K('class '), T('User'), P('(\n'),
  P('    '), A('@Id'), P(' '), A('@GeneratedValue'), P(' '), K('var '), P('id: '), T('Int'), P('? = '), K('null'), P(',\n'),
  P('    '), K('var '), P('name: '), T('String'), P(' = '), S('""'), P(',\n\n'),
  P('    '), A('@ManyToMany'), P('\n'),
  P('    '), A('@JoinTable'), P('(name = '), S('"user_role"'), P(')\n'),
  P('    '), K('var '), P('roles: '), T('MutableSet'), P('<'), T('Role'), P('> = '), F('mutableSetOf'), P('(),\n'),
  P(')\n\n'),
  C('// Navigation is a property access. This is genuinely convenient.\n'),
  K('val '), P('pets = owner.pets\n'),
  K('val '), P('roles = user.roles'),
].join('');

const CODE_STORM_ONE_TO_MANY = [
  C('// The many side owns the relation, as it does in the schema\n'),
  K('data class '), T('Pet'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('name: '), T('String'), P(',\n'),
  P('    '), A('@FK'), P(' '), K('val '), P('owner: '), T('Owner'), P(',\n'),
  P(') : '), T('Entity'), P('<'), T('Int'), P('>\n\n'),
  C('// The "collection" is a query: one line, loaded when you ask\n'),
  K('val '), P('pets = orm.'), F('findAll'), P('('), T('Pet_'), P('.owner '), K('eq'), P(' owner)'),
].join('');

const SQL_STORM_ONE_TO_MANY = [
  QC('-- runs when you ask, loads what you asked for'), '\n',
  QK('SELECT'), ' p.id, p.name, o.id, o.name\n',
  QK('FROM'), ' pet p\n',
  QK('INNER JOIN'), ' owner o ', QK('ON'), ' p.owner_id = o.id\n',
  QK('WHERE'), ' p.owner_id = ', QQ('?'),
].join('');

const CODE_STORM_COMPOSE = [
  C('// Because the association is a query, it composes\n'),
  K('val '), P('firstTen = orm.'), F('entity'), P('<'), T('Pet'), P('>().'), F('select'), P('()\n'),
  P('    .'), F('where'), P('('), T('Pet_'), P('.owner '), K('eq'), P(' owner)\n'),
  P('    .'), F('orderBy'), P('('), T('Pet_'), P('.name)\n'),
  P('    .'), F('limit'), P('('), N('10'), P(')\n'),
  P('    .resultList'),
].join('');

const CODE_STORM_MANY_TO_MANY = [
  C('// The join table is a real entity with a composite key\n'),
  K('data class '), T('UserRolePk'), P('('), K('val '), P('userId: '), T('Int'), P(', '), K('val '), P('roleId: '), T('Int'), P(')\n\n'),
  K('data class '), T('UserRole'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('userRolePk: '), T('UserRolePk'), P(',\n'),
  P('    '), A('@FK'), P(' '), A('@Persist'), P('(insertable = '), K('false'), P(', updatable = '), K('false'), P(') '), K('val '), P('user: '), T('User'), P(',\n'),
  P('    '), A('@FK'), P(' '), A('@Persist'), P('(insertable = '), K('false'), P(', updatable = '), K('false'), P(') '), K('val '), P('role: '), T('Role'), P(',\n'),
  P(') : '), T('Entity'), P('<'), T('UserRolePk'), P('>\n\n'),
  C('// Roles of a user, through the join entity ...\n'),
  K('val '), P('roles = orm.'), F('findAll'), P('('), T('UserRole_'), P('.user '), K('eq'), P(' user).'), F('map'), P(' { it.role }\n\n'),
  C('// ... or joined straight onto Role\n'),
  K('val '), P('sameRoles = orm.'), F('entity'), P('<'), T('Role'), P('>().'), F('select'), P('()\n'),
  P('    .'), F('innerJoin'), P('<'), T('UserRole'), P('>().'), F('on'), P('<'), T('Role'), P('>()\n'),
  P('    .'), F('whereAny'), P('('), T('UserRole_'), P('.user '), K('eq'), P(' user)\n'),
  P('    .resultList'),
].join('');

const SQL_STORM_MANY_TO_MANY = [
  QC('-- the second form: one query, no intermediate list'), '\n',
  QK('SELECT'), ' r.id, r.name\n',
  QK('FROM'), ' role r\n',
  QK('INNER JOIN'), ' user_role ur ', QK('ON'), ' ur.role_id = r.id\n',
  QK('WHERE'), ' ur.user_id = ', QQ('?'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Mapped collections</div>
  <h1>Mapped collections vs <span class="grad">queried associations</span></h1>
  <p class="dek">A mapped collection and a queried association are each one line of code. The difference is what the line tells you: Storm's says when it runs, what it loads, and how to filter and page it.</p>
  <div class="meta"><span>Series · JPA to Storm</span><span>6 min read</span><span>Kotlin</span></div>
  ${glance({
    left: {label: 'JPA', code: [K('val '), P('pets = owner.pets')].join('')},
    right: {label: 'Storm', code: [K('val '), P('pets = orm.'), F('findAll'), P('('), T('Pet_'), P('.owner '), K('eq'), P(' owner)')].join('')},
  })}

  <h2><span class="hno">01</span>The task</h2>
  <p>An owner has pets; a user has roles through a join table. Show the pets of an owner and the roles of a user, the two classic association shapes: one-to-many and many-to-many.</p>

  <h2><span class="hno">02</span>The JPA way</h2>
  <p>JPA maps both associations onto the entity, and afterwards navigation is just Kotlin:</p>
  ${editor({file: 'Entities.kt', tag: 'Kotlin · JPA', code: CODE_JPA_COLLECTIONS})}
  <p>No query in sight, and nobody should pretend that is not convenient. The costs arrive later, and they are all variations of one fact: <code>owner.pets</code> is an unbounded query hiding behind a property. It runs on first access, per owner, which is the N+1 pattern in a loop. It always loads the whole collection: showing ten of two thousand pets loads two thousand. The list it returns is a lazy proxy bound to the session, with the familiar failure mode outside it. And in practice, the moment you need the association filtered, sorted, or paged, you write <code>petRepository.findByOwner(owner, pageable)</code> anyway, at which point the mapped collection is no longer carrying the load. The write side adds its own decisions: cascade types and orphan removal now govern what a mutation of the collection means.</p>

  <h2><span class="hno">03</span>The Storm way: one-to-many</h2>
  <p>Storm keeps entities stateless, so there is no collection on the one side. The association lives where it lives in the schema, on the many side, and reading it is a query:</p>
  ${editor({file: 'PetService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_ONE_TO_MANY, sql: SQL_STORM_ONE_TO_MANY})}
  <p>One line instead of one property, and the line buys three things: it runs when you decide, it loads exactly what you asked, and it composes. Filtering, sorting, and paging the association need no second mechanism, because the association already is a query:</p>
  ${editor({file: 'PetService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_COMPOSE})}

  <h2><span class="hno">04</span>The Storm way: many-to-many</h2>
  <p>For many-to-many, Storm models the join table as what it is in the database, an entity with a composite key:</p>
  ${editor({file: 'UserRole.kt', tag: 'Kotlin · Storm', code: CODE_STORM_MANY_TO_MANY, sql: SQL_STORM_MANY_TO_MANY})}
  <p>This is more code than <code>@ManyToMany</code> plus <code>@JoinTable</code>, no argument. Two things pay it back. Writes are explicit inserts and deletes on <code>UserRole</code>, with no cascade semantics to configure or debug. And the join table is a real type from day one, so when it inevitably grows columns, <code>grantedAt</code>, <code>grantedBy</code>, an expiry, you add fields to an entity you already have. JPA teams know that moment: it is when <code>@ManyToMany</code> gets refactored into exactly this shape. Storm starts where mapped collections end up.</p>

  <h2><span class="hno">05</span>The trade, stated plainly</h2>
  <table class="cmp">
    <tr><th></th><th>JPA mapped collections</th><th>Storm queried associations</th></tr>
    <tr><td>Navigation</td><td><code>owner.pets</code>, one property</td><td><code>orm.findAll(Pet_.owner eq owner)</code>, one line</td></tr>
    <tr><td>When it loads</td><td>On first access, the whole collection, per owner</td><td>When you ask, exactly what you asked</td></tr>
    <tr><td>Filter, sort, page</td><td>A separate repository query in practice</td><td>The same query, composed</td></tr>
    <tr><td>Join table</td><td>Hidden until it needs columns, then refactored</td><td>An entity from day one</td></tr>
    <tr><td>Failure modes</td><td>Lazy proxies, session scope, cascade surprises</td><td>None specific; plain queries, plain values</td></tr>
  </table>
  <p>If your associations are small, always loaded whole, and never leave the session, JPA's property is genuinely convenient and stays that way. Storm's position is that associations rarely stay that way, and a query is the shape that survives growth without changing form.</p>

  <h2><span class="hno">06</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/relationships">Relationships</a>
    <a href="/docs/refs">Refs</a>
    <a href="/docs/queries">Queries</a>
    <a href="/docs/pagination-and-scrolling">Pagination and Scrolling</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function MappedCollectionsTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="mapped-collections" body={BODY} />;
}
