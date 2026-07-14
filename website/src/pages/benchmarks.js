import React, {useEffect} from 'react';
import Head from '@docusaurus/Head';
import {
  TUT_CSS, navHtml, FOOT_HTML, wireSqlToggles, editor,
  K, T, S, C, F, N, A, P, QK, QQ,
} from '../components/tutorial/tutorialTheme';

const TITLE = 'Benchmarks · Storm ORM vs Hibernate, jOOQ, Exposed and Jimmer';
const DESC = 'Reproducible JMH benchmarks of Storm against JDBC, Hibernate, jOOQ, Exposed and Jimmer on PostgreSQL 17, with the entity and query code behind every number.';

// Results from the reproducible suite: one tuned PostgreSQL 17 container over TCP, JMH, 2 forks,
// 5x3s measured iterations, single thread. Values are mean us/op with the reported error.
// Rows are same-session comparisons; the raw JDBC single round trip measured ~158 us.
const LIBS = {
  jdbc: {name: 'JDBC', cls: 'jdbc'},
  storm: {name: 'Storm', cls: 'storm'},
  hibernate: {name: 'Hibernate', cls: ''},
  jooq: {name: 'jOOQ', cls: ''},
  exposed: {name: 'Exposed', cls: ''},
  exposedDao: {name: 'Exposed DAO', cls: ''},
  jimmer: {name: 'Jimmer', cls: ''},
};

const WORKLOADS = [
  {
    id: 'singleRowById',
    title: 'Primary key lookup',
    desc: 'Load one visit by primary key. The purest round-trip test: one query, one row.',
    results: {jdbc: [171.9, 5.7], hibernate: [180.7, 5.1], storm: [182.3, 7.1], jooq: [184.2, 3.8], jimmer: [184.4, 7.9], exposed: [369.5, 9.1], exposedDao: [377.3, 5.8]},
  },
  {
    id: 'joinWithMapping10',
    title: 'Three-table join · 10 rows',
    desc: 'Load pets with owner and city hydrated through a single three-table join.',
    results: {jdbc: [388.4, 6.8], storm: [443.7, 9.4], hibernate: [447.6, 8.4], jooq: [476.4, 11.4], exposed: [579.1, 5.5], jimmer: [605.7, 20.5], exposedDao: [797.5, 21.9]},
  },
  {
    id: 'joinWithMapping100',
    title: 'Three-table join · 100 rows',
    desc: 'The same join at 100 rows. Hydration cost starts to separate the field.',
    results: {jdbc: [545.4, 9.9], storm: [803.7, 31.7], exposed: [814.4, 7.8], jimmer: [981.3, 15.0], jooq: [1043.2, 110.8], hibernate: [1172.4, 241.6], exposedDao: [1311.8, 343.9]},
  },
  {
    id: 'joinWithMapping1000',
    title: 'Three-table join · 1,000 rows',
    desc: 'The same join at 1,000 rows. Row mapping now dominates the round trip.',
    results: {jdbc: [1440.9, 102.0], exposed: [2792.4, 215.9], storm: [3163.7, 156.8], hibernate: [3665.2, 132.7], jooq: [3791.3, 76.3], exposedDao: [8220.8, 783.0], jimmer: [8446.6, 3990.8]},
  },
  {
    id: 'projection',
    title: 'Projection',
    desc: 'Three columns across three tables into a flat DTO, 100 rows.',
    results: {jdbc: [729.0, 16.9], hibernate: [757.1, 25.6], jimmer: [758.3, 14.7], storm: [786.4, 18.1], jooq: [788.4, 17.5], exposed: [1011.3, 16.8], exposedDao: [1032.2, 18.4]},
  },
  {
    id: 'batchInsert',
    title: 'Batch insert',
    desc: 'Insert 100 visits atomically and fetch the generated keys.',
    results: {jdbc: [2404.2, 32.4], jooq: [2436.0, 287.3], storm: [2720.7, 131.3], exposed: [3165.3, 260.8], hibernate: [3535.7, 194.5], jimmer: [3739.3, 170.3], exposedDao: [3986.1, 451.4]},
  },
  {
    id: 'updateById',
    title: 'Read, modify, update',
    desc: 'Read one owner, change one field, persist atomically. Every implementation reads a lazy association shape and writes only the changed column.',
    results: {jdbc: [538.4, 4.8], exposed: [553.0, 18.8], hibernate: [557.2, 8.6], exposedDao: [564.0, 16.9], storm: [571.9, 12.6], jooq: [726.2, 21.2], jimmer: [901.0, 21.9]},
  },
  {
    id: 'objectGraph',
    title: 'Object graph',
    desc: 'Load the owners of a city, each with their list of pets. The one-to-many shape every application has.',
    results: {jdbc: [854.6, 42.5], jooq: [971.9, 106.6], storm: [1191.2, 59.1], exposed: [1328.0, 25.9], hibernate: [1329.4, 162.3], exposedDao: [1380.3, 120.2], jimmer: [1406.9, 25.5]},
  },
];

function fmt(v) {
  return v >= 1000 ? (v / 1000).toFixed(2) + ' ms' : Math.round(v) + ' µs';
}

function chartHtml(w) {
  const entries = Object.entries(w.results).sort((a, b) => a[1][0] - b[1][0]);
  const max = entries[entries.length - 1][1][0];
  const rows = entries.map(([lib, [mean, err]]) => {
    const meta = LIBS[lib];
    const width = Math.max(1.5, (mean / max) * 100);
    return `<div class="bm-row ${meta.cls}">
      <span class="bm-name">${meta.name}</span>
      <span class="bm-track"><span class="bm-bar" style="width:${width.toFixed(1)}%"></span></span>
      <span class="bm-val">${fmt(mean)} <em>± ${err >= 1000 ? (err / 1000).toFixed(1) + ' ms' : Math.round(err) + ' µs'}</em></span>
    </div>`;
  }).join('');
  return `<div class="bm-card">
    <h3>${w.title}</h3>
    <p class="bm-desc">${w.desc}</p>
    ${rows}
    ${w.note ? `<p class="bm-note">${w.note}</p>` : ''}
  </div>`;
}

// Hand-written lines for the complete suite: benchmark class, entity or table model, row mappers and
// result DTOs. Non-blank, non-comment, non-import lines; generated code excluded on both sides that
// use it (Storm's metamodel, jOOQ's table classes).
const LOC = [
  ['storm', 100, []],
  ['hibernate', 123, ['string queries']],
  ['exposedDao', 124, []],
  ['exposed', 128, ['hand-mapped rows']],
  ['jooq', 131, ['hand-mapped rows']],
  ['jimmer', 144, []],
  ['jdbc', 257, ['hand-mapped rows', 'string queries']],
];

// The five-table model alone, same counting rule; result DTOs and Storm's optimized write shape excluded.
const MODEL_LOC = [
  ['storm', 29], ['exposed', 51], ['jimmer', 55], ['exposedDao', 69], ['hibernate', 105],
];

function modelLocHtml() {
  const max = Math.max(...MODEL_LOC.map(([, n]) => n));
  const rows = MODEL_LOC.map(([lib, n]) => `<div class="bm-row ${LIBS[lib].cls}">
      <span class="bm-name">${LIBS[lib].name}</span>
      <span class="bm-track"><span class="bm-bar" style="width:${((n / max) * 100).toFixed(1)}%"></span></span>
      <span class="bm-val">${n} lines</span>
    </div>`).join('');
  return `<div class="bm-card bm-loc">
    <h3>Entities LOC</h3>
    <p class="bm-desc">The entity or table definitions by the same counting rule, result DTOs and Storm's optimized write shape excluded. This is the code you write first and read forever.</p>
    ${rows}
    <p class="bm-note">JDBC and jOOQ have no hand-written model: JDBC maps rows by hand and jOOQ generates its table classes, so their cost appears in the suite total above instead.</p>
  </div>`;
}

function locHtml() {
  const max = Math.max(...LOC.map(([, n]) => n));
  const rows = LOC.map(([lib, n, tags]) => `<div class="bm-row ${LIBS[lib].cls}">
      <span class="bm-name">${LIBS[lib].name}${tags.map((t) => `<i class="bm-tag">${t}</i>`).join('')}</span>
      <span class="bm-track"><span class="bm-bar" style="width:${((n / max) * 100).toFixed(1)}%"></span></span>
      <span class="bm-val">${n} lines</span>
    </div>`).join('');
  return `<div class="bm-card bm-loc">
    <h3>Queries LOC</h3>
    <p class="bm-desc">Everything above the model that the eight workloads need: query code, row mappers, result DTOs and Storm's optimized write shape. Generated code is excluded for both libraries that use it: Storm's metamodel and jOOQ's table classes.</p>
    ${rows}
    <p class="bm-note">Storm implements all eight workloads in the fewest lines; every other implementation needs 23% to 157% more. Beyond the line count, the labels show what a low number can leave unsaid: hand-mapped rows are written and maintained by hand, and string queries are not compile-checked.</p>
  </div>`;
}

// ---- Code snippets: the operative lines of each workload, trimmed of JMH plumbing. ----

const CODE_ENTITIES = [
  `${K('data class')} ${T('City')}(`,
  `    ${A('@PK')} ${K('val')} id: ${T('Long')} = ${N('0')},`,
  `    ${K('val')} name: ${T('String')},`,
  `) : ${T('Entity')}&lt;${T('Long')}&gt;`,
  ``,
  `${K('data class')} ${T('Owner')}(`,
  `    ${A('@PK')} ${K('val')} id: ${T('Long')} = ${N('0')},`,
  `    ${K('val')} firstName: ${T('String')},`,
  `    ${K('val')} lastName: ${T('String')},`,
  `    ${K('val')} address: ${T('String')},`,
  `    ${K('val')} telephone: ${T('String')},`,
  `    ${A('@FK')} ${K('val')} city: ${T('City')},`,
  `) : ${T('Entity')}&lt;${T('Long')}&gt;`,
  ``,
  `${K('data class')} ${T('Pet')}(`,
  `    ${A('@PK')} ${K('val')} id: ${T('Long')} = ${N('0')},`,
  `    ${K('val')} name: ${T('String')},`,
  `    ${K('val')} birthDate: ${T('LocalDate')},`,
  `    ${A('@FK')} ${K('val')} type: ${T('Ref')}&lt;${T('PetType')}&gt;,`,
  `    ${A('@FK')} ${K('val')} owner: ${T('Owner')},`,
  `) : ${T('Entity')}&lt;${T('Long')}&gt;`,
].join('\n');

const CODE_SINGLE = [
  `${K('val')} visit = visits.${F('getById')}(id)`,
].join('\n');
const SQL_SINGLE = [
  `${QK('SELECT')} v.id, v.pet_id, v.visit_date, v.description`,
  `${QK('FROM')} visit v`,
  `${QK('WHERE')} v.id = ${QQ('?')}`,
].join('\n');

const CODE_JOIN = [
  `${K('val')} result = pets.${F('select')}()`,
  `    .${F('where')}((${T('Pet_')}.id ${F('greater')} base) ${K('and')} (${T('Pet_')}.id ${F('lessEq')} base + rows))`,
  `    .resultList  ${C('// Pet, Owner and City hydrated from one query')}`,
].join('\n');
const SQL_JOIN = [
  `${QK('SELECT')} p.id, p.name, p.birth_date, p.type_id, p.owner_id,`,
  `       o.first_name, o.last_name, o.address, o.telephone, o.city_id, c.name`,
  `${QK('FROM')} pet p`,
  `${QK('INNER JOIN')} owner o ${QK('ON')} p.owner_id = o.id`,
  `${QK('INNER JOIN')} city c ${QK('ON')} o.city_id = c.id`,
  `${QK('WHERE')} p.id > ${QQ('?')} ${QK('AND')} p.id &lt;= ${QQ('?')}`,
].join('\n');

const CODE_PROJECTION = [
  `${K('data class')} ${T('PetRow')}(${K('val')} petName: ${T('String')}, ${K('val')} ownerLastName: ${T('String')}, ${K('val')} cityName: ${T('String')})`,
  ``,
  `${K('val')} rows = orm.${F('selectFrom')}&lt;${T('Pet')}, ${T('PetRow')}&gt; { ${S('"${Pet_.name}, ${Pet_.owner.lastName}, ${Pet_.owner.city.name}"')} }`,
  `    .${F('where')}(${T('Pet_')}.owner.city.id ${F('eq')} cityId)`,
  `    .resultList`,
].join('\n');
const SQL_PROJECTION = [
  `${QK('SELECT')} p.name, o.last_name, c.name`,
  `${QK('FROM')} pet p`,
  `${QK('INNER JOIN')} owner o ${QK('ON')} p.owner_id = o.id`,
  `${QK('INNER JOIN')} city c ${QK('ON')} o.city_id = c.id`,
  `${QK('WHERE')} o.city_id = ${QQ('?')}`,
].join('\n');

const CODE_BATCH = [
  `${K('val')} ids = ${F('transaction')} {`,
  `    visits.${F('insertAndFetchIds')}(newVisits)  ${C('// 100 visits, one atomic batch')}`,
  `}`,
].join('\n');
const SQL_BATCH = [
  `${QK('INSERT INTO')} visit (pet_id, visit_date, description)`,
  `${QK('VALUES')} (${QQ('?')}, ${QQ('?')}, ${QQ('?')})`,
].join('\n');

const CODE_UPDATE = [
  `${A('@DbTable')}(${S('"owner"')})  ${C('// in-place optimization for writing: city as lazy ref')}`,
  `${A('@DynamicUpdate')}(${T('UpdateMode')}.FIELD)`,
  `${K('data class')} ${T('OwnerCityRef')}(`,
  `    ${A('@PK')} ${K('val')} id: ${T('Long')} = ${N('0')},`,
  `    ${K('val')} firstName: ${T('String')},`,
  `    ${K('val')} lastName: ${T('String')},`,
  `    ${K('val')} address: ${T('String')},`,
  `    ${K('val')} telephone: ${T('String')},`,
  `    ${A('@FK')} ${K('val')} city: ${T('Ref')}&lt;${T('City')}&gt;,`,
  `) : ${T('Entity')}&lt;${T('Long')}&gt;`,
  ``,
  `${F('transaction')} {`,
  `    ${K('val')} owner = owners.${F('getById')}(id)`,
  `    owners.${F('update')}(owner.${F('copy')}(telephone = newTelephone))`,
  `}`,
].join('\n');
const SQL_UPDATE = [
  `${QK('SELECT')} ocr.id, ocr.first_name, ocr.last_name, ocr.address, ocr.telephone, ocr.city_id`,
  `${QK('FROM')} owner ocr`,
  `${QK('WHERE')} ocr.id = ${QQ('?')}`,
  ``,
  `${QK('UPDATE')} owner`,
  `${QK('SET')} telephone = ${QQ('?')}`,
  `${QK('WHERE')} id = ${QQ('?')}`,
].join('\n');

const CODE_GRAPH_STORM = [
  `${K('val')} result = pets.${F('select')}()`,
  `    .${F('where')}(${T('Pet_')}.owner.city.id ${F('eq')} cityId)`,
  `    .${F('orderBy')}(${T('Pet_')}.owner)`,
  `    .${F('resultGroupedBy')}(${T('Pet_')}.owner)`,
  `    .${F('map')} { (owner, pets) -> ${T('OwnerWithPets')}(owner, pets) }`,
].join('\n');
const SQL_GRAPH = [
  `${QK('SELECT')} p.id, p.name, p.birth_date, p.type_id, p.owner_id,`,
  `       o.first_name, o.last_name, o.address, o.telephone, o.city_id, c.name`,
  `${QK('FROM')} pet p`,
  `${QK('INNER JOIN')} owner o ${QK('ON')} p.owner_id = o.id`,
  `${QK('INNER JOIN')} city c ${QK('ON')} o.city_id = c.id`,
  `${QK('WHERE')} o.city_id = ${QQ('?')}`,
  `${QK('ORDER BY')} p.owner_id`,
].join('\n');

const CODE_GRAPH_HIBERNATE = [
  `${T('List')}&lt;${T('Owner')}&gt; owners = sessionFactory.${F('fromSession')}(session -> session`,
  `    .${F('createSelectionQuery')}(`,
  `        ${S('"select distinct o from Owner o join fetch o.pets join fetch o.city where o.city.id = :cityId"')},`,
  `        ${T('Owner')}.${K('class')})`,
  `    .${F('setParameter')}(${S('"cityId"')}, cityId)`,
  `    .${F('getResultList')}());`,
  ``,
  `${C('// plus the @OneToMany(mappedBy = "owner") collection on the 126-line entity model')}`,
].join('\n');

const CODE_GRAPH_JOOQ = [
  `${T('List')}&lt;${T('OwnerWithPets')}&gt; result = ctx.${F('select')}(`,
  `            OWNER.ID, OWNER.FIRST_NAME, OWNER.LAST_NAME, OWNER.ADDRESS, OWNER.TELEPHONE,`,
  `            CITY.ID, CITY.NAME,`,
  `            ${F('multiset')}(`,
  `                    ${F('select')}(PET.ID, PET.NAME, PET.BIRTH_DATE, PET.TYPE_ID)`,
  `                            .${F('from')}(PET)`,
  `                            .${F('where')}(PET.OWNER_ID.${F('eq')}(OWNER.ID))))`,
  `    .${F('from')}(OWNER)`,
  `    .${F('join')}(CITY).${F('on')}(OWNER.CITY_ID.${F('eq')}(CITY.ID))`,
  `    .${F('where')}(OWNER.CITY_ID.${F('eq')}(cityId))`,
  `    .${F('orderBy')}(OWNER.ID)`,
  `    .${F('fetch')}(record -> {`,
  `        ${T('Owner')} owner = ${K('new')} ${T('Owner')}(record.${F('value1')}(), record.${F('value2')}(), record.${F('value3')}(),`,
  `                record.${F('value4')}(), record.${F('value5')}(), ${K('new')} ${T('City')}(record.${F('value6')}(), record.${F('value7')}()));`,
  `        ${T('List')}&lt;${T('Pet')}&gt; pets = record.${F('value8')}()`,
  `                .${F('map')}(pet -> ${K('new')} ${T('Pet')}(pet.${F('value1')}(), pet.${F('value2')}(), pet.${F('value3')}(), pet.${F('value4')}(), owner));`,
  `        ${K('return')} ${K('new')} ${T('OwnerWithPets')}(owner, pets);`,
  `    });`,
].join('\n');

const MATRIX_LIBS = ['jdbc', 'storm', 'hibernate', 'jooq', 'exposed', 'exposedDao', 'jimmer'];

// Green while close to the fastest framework in the row, then yellow, orange, red as the gap grows.
const HEAT_STOPS = [
  [1.0, [74, 222, 128]],
  [1.15, [74, 222, 128]],
  [1.6, [253, 224, 71]],
  [2.2, [251, 146, 60]],
  [3.0, [248, 113, 113]],
];

function heatStyle(ratio) {
  const r = Math.min(3.0, ratio);
  let lo = HEAT_STOPS[0], hi = HEAT_STOPS[HEAT_STOPS.length - 1];
  for (let i = 0; i < HEAT_STOPS.length - 1; i++) {
    if (r >= HEAT_STOPS[i][0] && r <= HEAT_STOPS[i + 1][0]) { lo = HEAT_STOPS[i]; hi = HEAT_STOPS[i + 1]; break; }
  }
  const span = hi[0] - lo[0] || 1;
  const t = (r - lo[0]) / span;
  const mix = lo[1].map((v, i) => Math.round(v + (hi[1][i] - v) * t));
  const alpha = 0.22 + 0.16 * Math.min(1, (r - 1) / 2);
  return `background:rgba(${mix[0]},${mix[1]},${mix[2]},${alpha.toFixed(2)})`;
}

function matrixHtml() {
  const head = MATRIX_LIBS.map((lib) =>
    `<th class="${LIBS[lib].cls}">${LIBS[lib].name}</th>`).join('');
  const rows = WORKLOADS.map((w) => {
    const frameworkBest = Math.min(...MATRIX_LIBS.filter((l) => l !== 'jdbc').map((l) => w.results[l][0]));
    const jdbcMean = w.results.jdbc[0];
    const cells = MATRIX_LIBS.map((lib) => {
      const mean = w.results[lib][0];
      if (lib === 'jdbc') return `<td class="bm-floor">${fmt(mean)}</td>`;
      const overJdbc = `<span class="bm-ratio">+${Math.round((mean / jdbcMean - 1) * 100)}%</span>`;
      return `<td style="${heatStyle(mean / frameworkBest)}">${fmt(mean)}${overJdbc}</td>`;
    }).join('');
    return `<tr><th>${w.title}</th>${cells}</tr>`;
  }).join('\n');
  // Average overhead over raw JDBC per library, across all workloads.
  const avgRatio = (lib) => WORKLOADS.reduce((sum, w) => sum + w.results[lib][0] / w.results.jdbc[0], 0) / WORKLOADS.length;
  const bestAvg = Math.min(...MATRIX_LIBS.filter((l) => l !== 'jdbc').map(avgRatio));
  const avgCells = MATRIX_LIBS.map((lib) => {
    if (lib === 'jdbc') return `<td class="bm-floor">baseline</td>`;
    const avg = avgRatio(lib);
    return `<td style="${heatStyle(avg / bestAvg)}"><b>+${Math.round((avg - 1) * 100)}%</b></td>`;
  }).join('');
  return `<div class="bm-matrix-wrap"><table class="bm-matrix">
    <thead><tr><th></th>${head}</tr></thead>
    <tbody>${rows}<tr class="bm-gap" aria-hidden="true"><td colspan="8"></td></tr></tbody>
    <tfoot><tr><th>Average over JDBC</th>${avgCells}</tr></tfoot>
  </table></div>`;
}

const BM_CSS = `
  .bm-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:22px;margin:26px 0 10px}
  @media(max-width:900px){.bm-grid{grid-template-columns:1fr}}
  .bm-card{border:1px solid var(--border);background:var(--panel);border-radius:14px;padding:28px 30px 24px}
  .storm-tut .art .bm-card h3{margin:0 0 6px;font-size:15.5px;letter-spacing:.01em}
  .bm-desc{margin:0 0 24px;color:var(--muted);font-size:13px;line-height:1.55}
  .bm-row{display:grid;grid-template-columns:96px 1fr 118px;align-items:center;gap:12px;margin:11px 0}
  .bm-name{font-family:var(--mono);font-size:12px;color:var(--muted);white-space:nowrap}
  .bm-track{height:9px;background:var(--panel-2);border-radius:5px;overflow:hidden;border:1px solid var(--border-soft)}
  .bm-bar{display:block;height:100%;background:#414150;border-radius:4px}
  .bm-row.jdbc .bm-name{color:var(--muted)}
  .bm-row.jdbc .bm-bar{background:repeating-linear-gradient(45deg,#2b2b35,#2b2b35 4px,#20202a 4px,#20202a 8px)}
  .bm-row.storm .bm-name{color:var(--text);font-weight:600}
  .bm-row.storm .bm-bar{background:linear-gradient(90deg,#a78bfa,#818cf8 55%,#7dd3fc)}
  .bm-val{font-family:var(--mono);font-size:11.5px;color:var(--body);text-align:right;white-space:nowrap}
  .bm-row.storm .bm-val{color:var(--text)}
  .bm-val em{font-style:normal;color:var(--faint);opacity:.75}
  .bm-note{margin:10px 0 0;color:var(--faint);font-size:12px}
  .bm-stats{display:grid;grid-template-columns:repeat(3,1fr);gap:18px;margin:28px 0 6px}
  @media(max-width:900px){.bm-stats{grid-template-columns:1fr}}
  .bm-stat{border:1px solid var(--border);background:var(--panel);border-radius:14px;padding:16px 18px}
  .bm-stat b{display:block;font-size:22px;margin-bottom:4px}
  .bm-stat span{color:var(--muted);font-size:13px;line-height:1.5}
  .bm-meta{color:var(--faint);font-size:12.5px;font-family:var(--mono);margin:8px 0 0}
  .storm-tut .art h2{margin:52px 0 14px}
  .storm-tut .art h3{margin:44px 0 10px}
  .storm-tut .art h3 + p{margin-top:0}
  .bm-stat b{background:linear-gradient(105deg,#c98d0a 0%,#e6a817 28%,#ffc93c 52%,#ffe08a 78%,#fff7cc 100%);-webkit-background-clip:text;background-clip:text;color:transparent}
  .bm-matrix-wrap{overflow-x:auto;border:1px solid var(--border);border-radius:14px;background:var(--panel);margin:22px 0 10px;padding:10px 12px}
  .art .bm-matrix,.art .bm-matrix thead,.art .bm-matrix tbody{background:none}
  .art .bm-matrix{width:100%;border-collapse:separate;border-spacing:3px;font-family:var(--mono);font-size:12px;margin:0}
  .art .bm-matrix th,.art .bm-matrix td{border:none;background:none;padding:9px 12px;text-align:right;white-space:nowrap}
  .art .bm-matrix thead th{color:var(--muted);font-weight:600;padding:4px 12px;font-size:10.5px;letter-spacing:.04em}
  .art .bm-matrix thead th.storm{background:linear-gradient(100deg,#a78bfa,#818cf8 55%,#7dd3fc);-webkit-background-clip:text;background-clip:text;color:transparent}
  .art .bm-matrix thead th.jdbc{color:var(--faint);font-style:italic}
  .art .bm-matrix tbody th{text-align:left;color:var(--body);font-family:var(--sans);font-size:12.5px;font-weight:500}
  .art .bm-matrix td{color:var(--text);border-radius:7px}
  .art .bm-matrix td .bm-ratio{display:block;font-size:10px;line-height:1.4;color:var(--text);opacity:.55}
  .art .bm-matrix td.bm-floor{color:var(--faint);font-style:italic}
  .art .bm-matrix tr.bm-gap td{padding:2px;background:none}
  .art .bm-matrix tfoot th{text-align:left;color:var(--text);font-family:var(--sans);font-size:12.5px;font-weight:600}
  .art .bm-matrix tfoot td{font-size:12.5px;border-radius:7px}
  .art .bm-matrix tfoot td b{font-weight:700}
  .bm-matrix-read{color:var(--muted);font-size:13.5px}
  .bm-details{margin:20px 0 8px;border:1px solid var(--border);border-radius:14px;background:var(--panel-2)}
  .bm-details summary{cursor:pointer;padding:14px 18px;font-size:14px;font-weight:600;color:var(--body);list-style:none;display:flex;align-items:center;gap:10px;user-select:none}
  .bm-details summary::-webkit-details-marker{display:none}
  .bm-details summary::before{content:'▸';color:var(--accent);font-size:12px;transition:transform .15s ease}
  .bm-details[open] summary::before{transform:rotate(90deg)}
  .bm-details > .bm-desc{margin:0 18px 6px}
  .bm-details .bm-grid{padding:0 16px 16px;margin:8px 0 0}
  .bm-loc{margin:22px 0 8px}
  .bm-loc .bm-desc{margin-bottom:20px}
  .bm-loc .bm-row{grid-template-columns:250px 1fr 90px;margin:9px 0}
  .bm-loc .bm-note{margin-top:16px}
  .bm-loc .bm-name{white-space:normal}
  .bm-tag{display:inline-block;font-style:normal;font-size:9.5px;line-height:1;padding:3px 7px;margin-left:6px;border:1px solid var(--border);border-radius:999px;color:var(--muted);white-space:nowrap;vertical-align:1px}
`;

function buildBody() {
  const charts = WORKLOADS.map(chartHtml).join('\n');
  return `
${navHtml('benchmarks')}

<div class="art">
  <h1>Concise by design.<br><span class="grad">Fast by measurement.</span></h1>
  <p class="dek">Storm set out to be the most enjoyable ORM to work with, entities as plain records, queries that read like the SQL they produce. This page shows that the same design keeps the hot path lean, measured against six alternatives on identical workloads, with the code behind every number.</p>
  <p class="bm-meta">PostgreSQL 17 over TCP · JMH · Storm 1.13.0 · measured 2026-07-14</p>

  <div class="bm-stats">
    <div class="bm-stat"><b>+10 µs</b><span>is all Storm adds to a 172 µs primary key lookup over raw JDBC. The abstraction is nearly free.</span></div>
    <div class="bm-stat"><b>26% less</b><span>overhead over raw JDBC than the closest alternative, averaged across all eight workloads.</span></div>
    <div class="bm-stat"><b>72% less</b><span>entity code than JPA: the five-table model is 29 lines in Storm, 105 as JPA entities.</span></div>
  </div>

  <h2>At a glance</h2>
  <p>Seven implementations, one database, one discipline: same schema, same data, same transaction boundaries, every score a real network round trip away from PostgreSQL. Mean latency per operation, lower is better. Cells are tinted by distance from the fastest framework in the row, green through red. Percentages are overhead over raw JDBC. Raw JDBC is the reference floor.</p>
  ${matrixHtml()}
  <p class="bm-matrix-read">Every library has strong rows, but Storm's is the only column that never runs hot. On every workload Storm is either the fastest framework or close behind it, while every alternative has at least one workload where it costs a third more than the best, and most cost far more than that. A real network round trip sits inside every score, so the pure mapping gap is larger still. Absolute times depend on the hardware they were measured on; the relative comparisons are the point.</p>

  <details class="bm-details">
    <summary>Per-workload charts: the same numbers with their reported error</summary>
    <p class="bm-desc">Compare within a chart; each chart is a same-session comparison.</p>
    <div class="bm-grid">
${charts}
    </div>
  </details>

  <h2>The code being measured</h2>
  <p>Numbers without code invite tuned-benchmark suspicion, so here is what each workload runs, trimmed of harness plumbing. Toggle <i>Show SQL</i> to see the exact statement Storm puts on the wire. The full sources for all seven implementations are in the benchmark repository.</p>
  ${modelLocHtml()}
  ${locHtml()}

  <h3>The model</h3>
  <p>Plain data classes. Nullability, keys and relations live in the type: <code>@FK val owner: Owner</code> hydrates through a join, <code>Ref&lt;PetType&gt;</code> stays a lazy reference until asked. There are no proxies, no session lifecycle, and nothing to configure.</p>
  ${editor({file: 'Entities.kt', tag: 'Kotlin', code: CODE_ENTITIES})}

  <h3>Primary key lookup</h3>
  ${editor({file: 'singleRowById', tag: 'Kotlin', code: CODE_SINGLE, sql: SQL_SINGLE})}

  <h3>Three-table join</h3>
  <p>No fetch joins to spell out and no N+1 to dodge: the entity graph declares what a Pet is, so selecting pets hydrates owner and city from one query.</p>
  ${editor({file: 'joinWithMapping', tag: 'Kotlin', code: CODE_JOIN, sql: SQL_JOIN})}

  <h3>Projection</h3>
  <p>A template picks three columns across the graph; the metamodel keeps every path compile-checked.</p>
  ${editor({file: 'projection', tag: 'Kotlin', code: CODE_PROJECTION, sql: SQL_PROJECTION})}

  <h3>Batch insert</h3>
  ${editor({file: 'batchInsert', tag: 'Kotlin', code: CODE_BATCH, sql: SQL_BATCH})}

  <h3>Read, modify, update</h3>
  <p>Storm's regular <code>Owner</code> is an aggregate: reading one loads its city through a join. Every other library declares that association lazy and reads the owner row alone, so to keep the read side of this workload identical for everyone, the benchmark uses a dedicated shape of the same table where city stays a lazy <code>Ref</code>. That shape is one record; declaring it is Storm's equivalent of the <code>FetchType.LAZY</code> the others put on their entities, and its ten lines are counted against Storm in the Queries LOC table above. On the write side, <code>@DynamicUpdate(FIELD)</code> writes only the column that changed. Entities are immutable; an update is a <code>copy</code>.</p>
  ${editor({file: 'updateById', tag: 'Kotlin', code: CODE_UPDATE, sql: SQL_UPDATE})}

  <h3>Object graph</h3>
  <p>One query, grouped during hydration. Repeated owners deduplicate to the same instance, so grouping is an identity operation, not a hash of every field. Switch the variant to see the code the other libraries needed for the same result.</p>
  ${editor({file: 'objectGraph', tag: 'Kotlin', sql: SQL_GRAPH, variants: [
    {label: 'Storm', code: CODE_GRAPH_STORM, selected: true},
    {label: 'Hibernate', code: CODE_GRAPH_HIBERNATE},
    {label: 'jOOQ', code: CODE_GRAPH_JOOQ},
  ]})}

  <h2>Method and fairness</h2>
  <p>The suite is built to be argued with. Everything below is enforced in code, not prose.</p>
  <ul>
    <li><b>Real round trips.</b> One tuned PostgreSQL 17 container, reached over TCP. The raw JDBC single round trip measured about 158 µs, and every score includes it. That compresses relative differences; the mapping-heavy workloads are where library differences show.</li>
    <li><b>JMH, properly.</b> Two forks, five 3-second measurement iterations after warmup, single thread: latency, not throughput. Sanity checks run every workload once per trial and verify row counts before anything is timed.</li>
    <li><b>Same work for everyone.</b> Identical schema and data, identical transaction boundaries on writes, and update values derived from the value just read, so change-detecting libraries can never silently skip a write. On the update workload every implementation writes only the changed column and reads a lazy association shape.</li>
    <li><b>Idiomatic code for everyone.</b> Each library is written the way its documentation recommends: Hibernate with <code>join fetch</code> and <code>@DynamicUpdate</code>, jOOQ with generated records and <code>MULTISET</code>, Jimmer with fetchers, Exposed in both DSL and DAO flavors.</li>
    <li><b>Rows are the unit of comparison.</b> Libraries within one chart ran in the same session under the same conditions. Comparing across charts, or treating values as absolute costs, carries environment drift that comparing within a chart does not.</li>
  </ul>
  <p>Versions: Storm 1.13.0, Hibernate 7.4.5, jOOQ 3.21.6, Exposed 1.3.1, Jimmer 0.11.0, PostgreSQL 17, JDK 21.</p>
  <p>Reproduce it: <code>git clone https://github.com/storm-orm/storm-benchmarks &amp;&amp; scripts/run.sh</code>. The repository contains the full methodology, the statement-log auditing tools used to verify round-trip counts, and every implementation in full.</p>
</div>

${FOOT_HTML}
`;
}

export default function Benchmarks() {
  useEffect(() => wireSqlToggles(), []);
  const url = 'https://orm.st/benchmarks';
  return (
    <>
      <Head>
        <html lang="en" />
        <title>{TITLE}</title>
        <meta name="description" content={DESC} />
        <link rel="canonical" href={url} />
        <meta property="og:type" content="website" />
        <meta property="og:title" content={TITLE} />
        <meta property="og:description" content={DESC} />
        <meta property="og:url" content={url} />
        <meta name="twitter:card" content="summary" />
        <meta name="twitter:title" content={TITLE} />
        <meta name="twitter:description" content={DESC} />
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;700&display=swap" rel="stylesheet" />
        <script type="application/ld+json">{JSON.stringify({
          '@context': 'https://schema.org',
          '@type': 'WebPage',
          name: TITLE,
          description: DESC,
          url,
        })}</script>
      </Head>
      <style dangerouslySetInnerHTML={{__html: TUT_CSS + BM_CSS}} />
      <div className="storm-tut" dangerouslySetInnerHTML={{__html: buildBody()}} />
    </>
  );
}
