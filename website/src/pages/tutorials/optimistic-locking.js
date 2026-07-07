import React from 'react';
import {
  TutorialPage, navHtml, FOOT_HTML, editor,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../../components/tutorial/tutorialTheme';

// Tutorial: optimistic locking, JPA vs Storm.

const TITLE = 'Optimistic Locking with Immutable Entities: JPA vs Storm';
const DESC =
  'JPA checks @Version at flush time, far from the code that made the change. ' +
  'Storm checks it on the update statement itself and works with immutable ' +
  'values, so conflicts surface immediately and stale state stays comparable.';

const CODE_JPA_VERSION = [
  A('@Entity'), P('\n'),
  K('class '), T('Owner'), P('(\n'),
  P('    '), A('@Id'), P(' '), A('@GeneratedValue'), P(' '), K('var '), P('id: '), T('Int'), P('? = '), K('null'), P(',\n'),
  P('    '), K('var '), P('firstName: '), T('String'), P(' = '), S('""'), P(',\n'),
  P('    '), K('var '), P('lastName: '), T('String'), P(' = '), S('""'), P(',\n'),
  P('    '), A('@Version'), P(' '), K('var '), P('version: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P(')\n\n'),
  A('@Transactional'), P('\n'),
  K('fun '), F('rename'), P('(id: '), T('Int'), P(', lastName: '), T('String'), P(') {\n'),
  P('    '), K('val '), P('owner = ownerRepository.'), F('findById'), P('(id).'), F('orElseThrow'), P('()\n'),
  P('    owner.lastName = lastName\n'),
  P('    '), C('// no save call: the UPDATE happens at flush, the version check\n'),
  P('    '), C('// at commit, and a conflict surfaces far from this line\n'),
  P('}'),
].join('');

const CODE_STORM_VERSION = [
  K('data class '), T('Owner'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('firstName: '), T('String'), P(',\n'),
  P('    '), K('val '), P('lastName: '), T('String'), P(',\n'),
  P('    '), A('@Version'), P(' '), K('val '), P('version: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P(') : '), T('Entity'), P('<'), T('Int'), P('>\n\n'),
  K('val '), P('owners = orm.'), F('entity'), P('<'), T('Owner'), P(', _>()\n\n'),
  K('val '), P('owner = owners.'), F('getById'), P('('), N('1'), P(')\n'),
  K('val '), P('updated = owners.'), F('updateAndFetch'), P('(owner.'), F('copy'), P('(lastName = '), S('"Smith"'), P('))\n'),
  C('// the UPDATE ran here, on this line, and updated.version is incremented'),
].join('');

const SQL_STORM_VERSION = [
  QC('-- updateAndFetch(): the version is part of the WHERE clause'), '\n',
  QK('UPDATE'), ' owner ', QK('SET'), ' first_name = ', QQ('?'), ', last_name = ', QQ('?'), ', version = ', QQ('?'), '\n',
  QK('WHERE'), ' id = ', QQ('?'), ' ', QK('AND'), ' version = ', QQ('?'), '\n',
  QC('-- zero rows matched means another writer won: OptimisticLockException, thrown here'),
].join('');

const CODE_STORM_CONFLICT = [
  K('try'), P(' {\n'),
  P('    owners.'), F('update'), P('(stale.'), F('copy'), P('(lastName = '), S('"Smith"'), P('))\n'),
  P('} '), K('catch'), P(' (exception: '), T('OptimisticLockException'), P(') {\n'),
  P('    '), K('val '), P('current = owners.'), F('getById'), P('(stale.id)\n'),
  P('    '), C('// stale and current are plain values: diff them, merge, retry,\n'),
  P('    '), C('// or surface the conflict to the user\n'),
  P('}'),
].join('');

const CODE_STORM_TIMESTAMP = [
  C('// A timestamp works as the version too\n'),
  K('data class '), T('Visit'), P('(\n'),
  P('    '), A('@PK'), P(' '), K('val '), P('id: '), T('Int'), P(' = '), N('0'), P(',\n'),
  P('    '), K('val '), P('visitDate: '), T('LocalDate'), P(',\n'),
  P('    '), K('val '), P('description: '), T('String'), P('? = '), K('null'), P(',\n'),
  P('    '), A('@Version'), P(' '), K('val '), P('timestamp: '), T('Instant'), P('?,\n'),
  P(') : '), T('Entity'), P('<'), T('Int'), P('>'),
].join('');

const BODY = `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Optimistic locking</div>
  <h1>Optimistic locking with <span class="grad">immutable entities</span></h1>
  <p class="dek">Two users edit the same record; the second save must not silently erase the first. Both JPA and Storm solve this with a version column. The difference is where the check runs and what you hold in your hands when it fails.</p>
  <div class="meta"><span>Series · JPA to Storm</span><span>5 min read</span><span>Kotlin</span></div>

  <h2><span class="hno">01</span>The task</h2>
  <p>An owner record is open in two browser tabs. Both edit, both save. Without a guard, the last write wins and the first edit vanishes without a trace. Optimistic locking detects the collision and turns it into an error you can handle.</p>

  <h2><span class="hno">02</span>The JPA way</h2>
  <p>JPA solves this with <code>@Version</code> on a managed entity. The mechanics work, but notice where everything happens:</p>
  ${editor({file: 'OwnerService.kt', tag: 'Kotlin · JPA', code: CODE_JPA_VERSION})}
  <p>The write is implicit: mutating the managed entity schedules an UPDATE for whenever the persistence context flushes. The version check runs at that flush, so the failure appears at commit time, at the end of the transaction, often wrapped by Spring into <code>ObjectOptimisticLockingFailureException</code> somewhere above the code that made the change. And because the check belongs to the session, handling a conflict across requests means detached entities and <code>merge()</code>, which is its own chapter of subtleties.</p>

  <h2><span class="hno">03</span>The Storm way</h2>
  <p>Storm has no flush and no managed state, so the version check happens in the only place left: the UPDATE statement itself, on the line where you call it:</p>
  ${editor({file: 'OwnerService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_VERSION, sql: SQL_STORM_VERSION})}
  <p>The update is explicit, the exception is immediate, and there is no session for the failure to hide behind. <code>copy()</code> expresses the change; <code>updateAndFetch()</code> returns the new state with the incremented version, ready for the next edit.</p>

  <h2><span class="hno">04</span>Handling the conflict</h2>
  <p>Because entities are immutable values, a conflict leaves you with something unusually useful: the stale copy you tried to write and the current copy from the database, both plain data classes that you can diff field by field:</p>
  ${editor({file: 'OwnerService.kt', tag: 'Kotlin · Storm', code: CODE_STORM_CONFLICT})}
  <p>There is nothing to detach, reattach, or merge. Retry logic is ordinary code operating on ordinary values.</p>

  <h2><span class="hno">05</span>Timestamp versions</h2>
  <p>If the schema tracks a last-modified timestamp anyway, it can serve as the version:</p>
  ${editor({file: 'Visit.kt', tag: 'Kotlin · Storm', code: CODE_STORM_TIMESTAMP})}

  <h2><span class="hno">06</span>Side by side</h2>
  <table class="cmp">
    <tr><th></th><th>JPA with Hibernate</th><th>Storm</th></tr>
    <tr><td>When the check runs</td><td>At flush or commit, wherever that happens to be</td><td>On the update call, on that line</td></tr>
    <tr><td>Failure surfaces as</td><td><code>ObjectOptimisticLockingFailureException</code> at commit</td><td><code>OptimisticLockException</code> at the call site</td></tr>
    <tr><td>State model</td><td>Managed, mutable entities; writes are implicit</td><td>Immutable values; every write is a visible call</td></tr>
    <tr><td>Conflict handling</td><td>Detach, reload, merge</td><td>Stale and current copies coexist as plain values</td></tr>
  </table>

  <h2><span class="hno">07</span>Keep going</h2>
  <p>The reference documentation covers the mechanics in depth:</p>
  <div class="refs">
    <a href="/docs/entities">Entities</a>
    <a href="/docs/error-handling">Error Handling</a>
    <a href="/docs/transactions">Transactions</a>
    <a href="/docs/dirty-checking">Dirty Checking</a>
  </div>
  <div class="cta">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" target="_blank" rel="noopener" class="btn">Star on GitHub</a>
  </div>
</div>

${FOOT_HTML}
`;

export default function OptimisticLockingTutorial() {
  return <TutorialPage title={TITLE} description={DESC} slug="optimistic-locking" body={BODY} />;
}
