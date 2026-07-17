import React, {useEffect} from 'react';
import Head from '@docusaurus/Head';
import {
  TUT_CSS, navHtml, FOOT_HTML, wireSqlToggles, editor, clonebar,
  K, T, S, C, F, N, A, P, QK, QQ, QC,
} from '../components/tutorial/tutorialTheme';

const TITLE = 'Benchmarks · ST/ORM vs Hibernate, jOOQ, Exposed and Jimmer';
const DESC = 'Reproducible JMH benchmarks of Storm against JDBC, Hibernate, jOOQ, Exposed and Jimmer on PostgreSQL 17, with the entity and query code behind every number.';

// Results from the reproducible suite: one tuned PostgreSQL 17 container over TCP, JMH, 2 forks,
// 5x3s measured iterations, single thread. Values are mean us/op with the reported error.
// Rows are same-session comparisons; the raw JDBC single round trip measured ~155-172 us across sessions.
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
    results: {jdbc: [172.4, 3.3], hibernate: [180.8, 3.2], storm: [182.7, 7.7], jimmer: [182.7, 6.9], jooq: [184.7, 8.2], exposed: [369.4, 9.5], exposedDao: [374.8, 17.4]},
  },
  {
    id: 'joinWithMapping10',
    title: 'Three-table join · 10 rows',
    desc: 'Load pets with owner and city hydrated through a single three-table join.',
    results: {jdbc: [395.6, 15.2], storm: [464.1, 80.9], hibernate: [472.3, 28.2], jooq: [490.4, 20.5], exposed: [581.2, 14.6], jimmer: [600.0, 19.7], exposedDao: [798.1, 26.8]},
  },
  {
    id: 'joinWithMapping100',
    title: 'Three-table join · 100 rows',
    desc: 'The same join at 100 rows. Hydration cost starts to separate the field.',
    results: {jdbc: [569.2, 12.9], storm: [792.9, 16.6], exposed: [813.5, 15.4], jimmer: [1005.0, 152.3], jooq: [1315.3, 161.5], hibernate: [1489.9, 246.6], exposedDao: [1995.6, 290.6]},
  },
  {
    id: 'joinWithMapping1000',
    title: 'Three-table join · 1,000 rows',
    desc: 'The same join at 1,000 rows. Row mapping now dominates the round trip.',
    results: {jdbc: [1737.3, 317.0], storm: [2242.0, 288.8], exposed: [2730.4, 299.8], hibernate: [3463.7, 378.8], jooq: [3536.6, 261.3], exposedDao: [7575.4, 2485.6], jimmer: [10227.4, 4670.0]},
  },
  {
    id: 'projection',
    title: 'Projection',
    desc: 'Three columns across three tables into a flat DTO, 100 rows.',
    results: {jdbc: [778.3, 24.4], storm: [787.5, 29.4], hibernate: [795.1, 23.4], jimmer: [823.7, 31.1], jooq: [825.8, 27.4], exposed: [1039.5, 31.4], exposedDao: [1042.7, 29.4]},
  },
  {
    id: 'batchInsert',
    title: 'Batch insert',
    desc: 'Insert 100 visits atomically and fetch the generated keys.',
    results: {jdbc: [2546.7, 96.9], jooq: [2842.1, 449.2], storm: [3000.1, 238.6], exposed: [3499.0, 294.2], hibernate: [3911.8, 396.1], jimmer: [4049.7, 226.7], exposedDao: [4301.3, 429.5]},
  },
  {
    id: 'updateById',
    title: 'Read, modify, update',
    desc: 'Read one owner, change one field, persist atomically. Every implementation reads a lazy association shape and writes only the changed column.',
    results: {jdbc: [531.6, 19.2], hibernate: [557.2, 13.3], exposed: [557.6, 19.4], storm: [564.4, 13.5], exposedDao: [568.6, 16.3], jooq: [727.9, 24.9], jimmer: [888.6, 12.7]},
  },
  {
    id: 'objectGraph',
    title: 'Object graph',
    desc: 'Load the owners of a city, each with their list of pets. The one-to-many shape every application has.',
    results: {jdbc: [973.5, 29.9], storm: [1191.3, 182.7], exposed: [1378.0, 43.6], jooq: [1386.7, 197.3], jimmer: [1401.2, 68.9], exposedDao: [1609.3, 151.0], hibernate: [1677.7, 356.4]},
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
    <p class="bm-desc">The entity or table definitions by the same counting rule, result DTOs and Storm's optimized write shape excluded. This is the model code developers write and maintain.</p>
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
const CODE_MODEL_JDBC = [
  `${C('// No ORM model. Rows are hand-mapped into plain records:')}`,
  `${K('record')} ${T('City')}(${K('long')} id, ${T('String')} name) {}`,
  ``,
  `${K('record')} ${T('Owner')}(${K('long')} id, ${T('String')} firstName, ${T('String')} lastName,`,
  `               ${T('String')} address, ${T('String')} telephone, ${T('City')} city) {}`,
  ``,
  `${K('record')} ${T('Pet')}(${K('long')} id, ${T('String')} name, ${T('LocalDate')} birthDate,`,
  `             ${K('long')} typeId, ${T('Owner')} owner) {}`,
].join('\n');
const CODE_MODEL_HIBERNATE = [
  `${C('// Private fields, field access. Getters/setters added only where the app calls them.')}`,
  `${A('@Entity')} ${A('@Table')}(name = ${S('"city"')})`,
  `${K('class')} ${T('City')} {`,
  `    ${A('@Id')} ${A('@GeneratedValue')}(strategy = IDENTITY) ${T('Long')} id;`,
  `    ${A('@Column')}(name = ${S('"name"')}) ${T('String')} name;`,
  `}`,
  ``,
  `${A('@Entity')} ${A('@Table')}(name = ${S('"owner"')}) ${A('@DynamicUpdate')}`,
  `${K('class')} ${T('Owner')} {`,
  `    ${A('@Id')} ${A('@GeneratedValue')}(strategy = IDENTITY) ${T('Long')} id;`,
  `    ${A('@Column')}(name = ${S('"first_name"')}) ${T('String')} firstName;`,
  `    ${A('@Column')}(name = ${S('"last_name"')}) ${T('String')} lastName;`,
  `    ${A('@Column')}(name = ${S('"address"')}) ${T('String')} address;`,
  `    ${A('@Column')}(name = ${S('"telephone"')}) ${T('String')} telephone;`,
  `    ${A('@ManyToOne')}(fetch = LAZY) ${A('@JoinColumn')}(name = ${S('"city_id"')}) ${T('City')} city;`,
  `    ${A('@OneToMany')}(mappedBy = ${S('"owner"')}) ${T('List')}&lt;${T('Pet')}&gt; pets;`,
  `}`,
  ``,
  `${A('@Entity')} ${A('@Table')}(name = ${S('"pet"')})`,
  `${K('class')} ${T('Pet')} {`,
  `    ${A('@Id')} ${A('@GeneratedValue')}(strategy = IDENTITY) ${T('Long')} id;`,
  `    ${A('@Column')}(name = ${S('"name"')}) ${T('String')} name;`,
  `    ${A('@Column')}(name = ${S('"birth_date"')}) ${T('LocalDate')} birthDate;`,
  `    ${A('@ManyToOne')}(fetch = LAZY) ${A('@JoinColumn')}(name = ${S('"type_id"')}) ${T('PetType')} type;`,
  `    ${A('@ManyToOne')}(fetch = LAZY) ${A('@JoinColumn')}(name = ${S('"owner_id"')}) ${T('Owner')} owner;`,
  `}`,
].join('\n');
const CODE_MODEL_JOOQ = [
  `${C('// jOOQ generates the table classes from the schema; there is no entity model.')}`,
  `${C('// You still write the result records it maps into, shared here with JDBC:')}`,
  `${K('record')} ${T('City')}(${K('long')} id, ${T('String')} name) {}`,
  ``,
  `${K('record')} ${T('Owner')}(${K('long')} id, ${T('String')} firstName, ${T('String')} lastName,`,
  `               ${T('String')} address, ${T('String')} telephone, ${T('City')} city) {}`,
  ``,
  `${K('record')} ${T('Pet')}(${K('long')} id, ${T('String')} name, ${T('LocalDate')} birthDate,`,
  `             ${K('long')} typeId, ${T('Owner')} owner) {}`,
].join('\n');
const CODE_MODEL_EXPOSED = [
  `${K('object')} ${T('Cities')} : ${T('Table')}(${S('"city"')}) {`,
  `    ${K('val')} id = ${F('long')}(${S('"id"')}).${F('autoIncrement')}()`,
  `    ${K('val')} name = ${F('varchar')}(${S('"name"')}, ${N('100')})`,
  `    ${K('override val')} primaryKey = ${T('PrimaryKey')}(id)`,
  `}`,
  ``,
  `${K('object')} ${T('Owners')} : ${T('Table')}(${S('"owner"')}) {`,
  `    ${K('val')} id = ${F('long')}(${S('"id"')}).${F('autoIncrement')}()`,
  `    ${K('val')} firstName = ${F('varchar')}(${S('"first_name"')}, ${N('50')})`,
  `    ${K('val')} lastName = ${F('varchar')}(${S('"last_name"')}, ${N('50')})`,
  `    ${K('val')} address = ${F('varchar')}(${S('"address"')}, ${N('120')})`,
  `    ${K('val')} telephone = ${F('varchar')}(${S('"telephone"')}, ${N('20')})`,
  `    ${K('val')} cityId = ${F('long')}(${S('"city_id"')}).${F('references')}(${T('Cities')}.id)`,
  `    ${K('override val')} primaryKey = ${T('PrimaryKey')}(id)`,
  `}`,
  ``,
  `${K('object')} ${T('Pets')} : ${T('Table')}(${S('"pet"')}) {`,
  `    ${K('val')} id = ${F('long')}(${S('"id"')}).${F('autoIncrement')}()`,
  `    ${K('val')} name = ${F('varchar')}(${S('"name"')}, ${N('50')})`,
  `    ${K('val')} birthDate = ${F('date')}(${S('"birth_date"')})`,
  `    ${K('val')} typeId = ${F('long')}(${S('"type_id"')}).${F('references')}(${T('PetTypes')}.id)`,
  `    ${K('val')} ownerId = ${F('long')}(${S('"owner_id"')}).${F('references')}(${T('Owners')}.id)`,
  `    ${K('override val')} primaryKey = ${T('PrimaryKey')}(id)`,
  `}`,
].join('\n');
const CODE_MODEL_EXPOSED_DAO = [
  `${K('object')} ${T('Cities')} : ${T('LongIdTable')}(${S('"city"')}) { ${K('val')} name = ${F('varchar')}(${S('"name"')}, ${N('100')}) }`,
  ``,
  `${K('object')} ${T('Owners')} : ${T('LongIdTable')}(${S('"owner"')}) {`,
  `    ${K('val')} firstName = ${F('varchar')}(${S('"first_name"')}, ${N('50')})`,
  `    ${K('val')} lastName = ${F('varchar')}(${S('"last_name"')}, ${N('50')})`,
  `    ${K('val')} address = ${F('varchar')}(${S('"address"')}, ${N('120')})`,
  `    ${K('val')} telephone = ${F('varchar')}(${S('"telephone"')}, ${N('20')})`,
  `    ${K('val')} cityId = ${F('reference')}(${S('"city_id"')}, ${T('Cities')})`,
  `}`,
  ``,
  `${K('object')} ${T('Pets')} : ${T('LongIdTable')}(${S('"pet"')}) {`,
  `    ${K('val')} name = ${F('varchar')}(${S('"name"')}, ${N('50')})`,
  `    ${K('val')} birthDate = ${F('date')}(${S('"birth_date"')})`,
  `    ${K('val')} typeId = ${F('reference')}(${S('"type_id"')}, ${T('PetTypes')})`,
  `    ${K('val')} ownerId = ${F('reference')}(${S('"owner_id"')}, ${T('Owners')})`,
  `}`,
  ``,
  `${K('class')} ${T('CityDao')}(id: ${T('EntityID')}&lt;${T('Long')}&gt;) : ${T('LongEntity')}(id) {`,
  `    ${K('companion object')} : ${T('LongEntityClass')}&lt;${T('CityDao')}&gt;(${T('Cities')})`,
  `    ${K('var')} name ${K('by')} ${T('Cities')}.name`,
  `}`,
  ``,
  `${K('class')} ${T('OwnerDao')}(id: ${T('EntityID')}&lt;${T('Long')}&gt;) : ${T('LongEntity')}(id) {`,
  `    ${K('companion object')} : ${T('LongEntityClass')}&lt;${T('OwnerDao')}&gt;(${T('Owners')})`,
  `    ${K('var')} firstName ${K('by')} ${T('Owners')}.firstName`,
  `    ${K('var')} lastName ${K('by')} ${T('Owners')}.lastName`,
  `    ${K('var')} address ${K('by')} ${T('Owners')}.address`,
  `    ${K('var')} telephone ${K('by')} ${T('Owners')}.telephone`,
  `    ${K('var')} city ${K('by')} ${T('CityDao')} ${F('referencedOn')} ${T('Owners')}.cityId`,
  `    ${K('val')} pets ${K('by')} ${T('PetDao')} ${F('referrersOn')} ${T('Pets')}.ownerId`,
  `}`,
  ``,
  `${K('class')} ${T('PetDao')}(id: ${T('EntityID')}&lt;${T('Long')}&gt;) : ${T('LongEntity')}(id) {`,
  `    ${K('companion object')} : ${T('LongEntityClass')}&lt;${T('PetDao')}&gt;(${T('Pets')})`,
  `    ${K('var')} name ${K('by')} ${T('Pets')}.name`,
  `    ${K('var')} birthDate ${K('by')} ${T('Pets')}.birthDate`,
  `    ${K('var')} typeId ${K('by')} ${T('Pets')}.typeId`,
  `    ${K('var')} owner ${K('by')} ${T('OwnerDao')} ${F('referencedOn')} ${T('Pets')}.ownerId`,
  `}`,
].join('\n');
const CODE_MODEL_JIMMER = [
  `${A('@Entity')} ${A('@Table')}(name = ${S('"city"')})`,
  `${K('interface')} ${T('City')} {`,
  `    ${A('@Id')} ${K('long')} ${F('id')}();`,
  `    ${T('String')} ${F('name')}();`,
  `}`,
  ``,
  `${A('@Entity')} ${A('@Table')}(name = ${S('"owner"')})`,
  `${K('interface')} ${T('Owner')} {`,
  `    ${A('@Id')} ${K('long')} ${F('id')}();`,
  `    ${T('String')} ${F('firstName')}();`,
  `    ${T('String')} ${F('lastName')}();`,
  `    ${T('String')} ${F('address')}();`,
  `    ${T('String')} ${F('telephone')}();`,
  `    ${A('@ManyToOne')} ${A('@JoinColumn')}(name = ${S('"city_id"')}) ${T('City')} ${F('city')}();`,
  `    ${A('@OneToMany')}(mappedBy = ${S('"owner"')}) ${T('List')}&lt;${T('Pet')}&gt; ${F('pets')}();`,
  `}`,
  ``,
  `${A('@Entity')} ${A('@Table')}(name = ${S('"pet"')})`,
  `${K('interface')} ${T('Pet')} {`,
  `    ${A('@Id')} ${K('long')} ${F('id')}();`,
  `    ${T('String')} ${F('name')}();`,
  `    ${T('LocalDate')} ${F('birthDate')}();`,
  `    ${A('@ManyToOne')} ${A('@JoinColumn')}(name = ${S('"type_id"')}) ${T('PetType')} ${F('type')}();`,
  `    ${A('@ManyToOne')} ${A('@JoinColumn')}(name = ${S('"owner_id"')}) ${T('Owner')} ${F('owner')}();`,
  `}`,
].join('\n');

const CODE_SINGLE = [
  `${K('val')} visit = visits.${F('getById')}(id)`,
].join('\n');
const SQL_SINGLE = [
  `${QK('SELECT')} v.id, v.pet_id, v.visit_date, v.description`,
  `${QK('FROM')} visit v`,
  `${QK('WHERE')} v.id = ${QQ('?')}`,
].join('\n');
const CODE_SINGLE_JDBC = [
  `${K('try')} (${K('var')} ps = connection.${F('prepareStatement')}(`,
  `        ${S('"SELECT id, pet_id, visit_date, description FROM visit WHERE id = ?"')})) {`,
  `    ps.${F('setLong')}(${N('1')}, id);`,
  `    ${K('try')} (${K('var')} rs = ps.${F('executeQuery')}()) {`,
  `        rs.${F('next')}();`,
  `        ${K('return')} ${K('new')} ${T('Visit')}(rs.${F('getLong')}(${N('1')}), rs.${F('getLong')}(${N('2')}),`,
  `                rs.${F('getObject')}(${N('3')}, ${T('LocalDate')}.${K('class')}), rs.${F('getString')}(${N('4')}));`,
  `    }`,
  `}`,
].join('\n');
const CODE_SINGLE_HIBERNATE = [
  `${K('return')} sessionFactory.${F('fromSession')}(session -> session.${F('find')}(${T('Visit')}.${K('class')}, id));`,
].join('\n');
const CODE_SINGLE_JOOQ = [
  `${K('return')} ctx.${F('select')}(${T('VISIT')}.ID, ${T('VISIT')}.PET_ID, ${T('VISIT')}.VISIT_DATE, ${T('VISIT')}.DESCRIPTION)`,
  `        .${F('from')}(${T('VISIT')})`,
  `        .${F('where')}(${T('VISIT')}.ID.${F('eq')}(id))`,
  `        .${F('fetchOne')}(${T('Records')}.${F('mapping')}(${T('Visit')}::new));`,
].join('\n');
const CODE_SINGLE_EXPOSED = [
  `${F('transaction')}(database) {`,
  `    ${T('Visits')}.${F('selectAll')}().${F('where')} { ${T('Visits')}.id ${F('eq')} id }.${F('single')}().${F('toVisit')}()`,
  `}`,
].join('\n');
const CODE_SINGLE_EXPOSED_DAO = [
  `${F('transaction')}(database) {`,
  `    ${T('VisitDao')}.${F('findById')}(id)!!.${F('toVisit')}()`,
  `}`,
].join('\n');
const CODE_SINGLE_JIMMER = [
  `${K('return')} sqlClient.${F('getEntities')}().${F('findById')}(${T('Visit')}.${K('class')}, id);`,
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
const CODE_JOIN_JDBC = [
  `${K('try')} (${K('var')} ps = connection.${F('prepareStatement')}(`,
  `        ${S('"SELECT p.id, p.name, ..., c.id, c.name FROM pet p"')}`,
  `        + ${S('" JOIN owner o ON p.owner_id = o.id JOIN city c ON o.city_id = c.id"')}`,
  `        + ${S('" WHERE p.id > ? AND p.id <= ?"')})) {`,
  `    ps.${F('setLong')}(${N('1')}, base); ps.${F('setLong')}(${N('2')}, base + rows);`,
  `    ${K('try')} (${K('var')} rs = ps.${F('executeQuery')}()) {`,
  `        ${T('List')}&lt;${T('Pet')}&gt; pets = ${K('new')} ${T('ArrayList')}&lt;&gt;();`,
  `        ${K('while')} (rs.${F('next')}()) pets.${F('add')}(${F('mapPet')}(rs));  ${C('// hand-map each row -> Pet, Owner, City')}`,
  `        ${K('return')} pets;`,
  `    }`,
  `}`,
].join('\n');
const CODE_JOIN_HIBERNATE = [
  `${K('return')} sessionFactory.${F('fromSession')}(session -> session`,
  `    .${F('createSelectionQuery')}(`,
  `        ${S('"from Pet p join fetch p.owner o join fetch o.city where p.id > :base and p.id <= :top"')},`,
  `        ${T('Pet')}.${K('class')})`,
  `    .${F('setParameter')}(${S('"base"')}, base)`,
  `    .${F('setParameter')}(${S('"top"')}, base + rows)`,
  `    .${F('getResultList')}());`,
].join('\n');
const CODE_JOIN_JOOQ = [
  `${K('return')} ctx.${F('select')}(`,
  `        ${T('PET')}.ID, ${T('PET')}.NAME, ${T('PET')}.BIRTH_DATE, ${T('PET')}.TYPE_ID,`,
  `        ${F('row')}(${T('OWNER')}.ID, ${T('OWNER')}.FIRST_NAME, ${T('OWNER')}.LAST_NAME, ${T('OWNER')}.ADDRESS, ${T('OWNER')}.TELEPHONE,`,
  `                ${F('row')}(${T('CITY')}.ID, ${T('CITY')}.NAME).${F('mapping')}(${T('City')}::new)).${F('mapping')}(${T('Owner')}::new))`,
  `    .${F('from')}(${T('PET')})`,
  `    .${F('join')}(${T('OWNER')}).${F('on')}(${T('PET')}.OWNER_ID.${F('eq')}(${T('OWNER')}.ID))`,
  `    .${F('join')}(${T('CITY')}).${F('on')}(${T('OWNER')}.CITY_ID.${F('eq')}(${T('CITY')}.ID))`,
  `    .${F('where')}(${T('PET')}.ID.${F('gt')}(base).${F('and')}(${T('PET')}.ID.${F('le')}(base + rows)))`,
  `    .${F('fetch')}(${T('Records')}.${F('mapping')}(${T('Pet')}::new));`,
].join('\n');
const CODE_JOIN_EXPOSED = [
  `${F('transaction')}(database) {`,
  `    (${T('Pets')} ${F('innerJoin')} ${T('Owners')} ${F('innerJoin')} ${T('Cities')})`,
  `        .${F('selectAll')}()`,
  `        .${F('where')} { (${T('Pets')}.id ${F('greater')} base) ${K('and')} (${T('Pets')}.id ${F('lessEq')} base + rows) }`,
  `        .${F('map')} { it.${F('toPet')}() }`,
  `}`,
].join('\n');
const CODE_JOIN_EXPOSED_DAO = [
  `${F('transaction')}(database) {`,
  `    ${T('PetDao')}.${F('wrapRows')}(`,
  `        ${T('Pets')}.${F('selectAll')}()`,
  `            .${F('where')} { (${T('Pets')}.id ${F('greater')} base) ${K('and')} (${T('Pets')}.id ${F('lessEq')} base + rows) })`,
  `        .${F('with')}(${T('PetDao')}::owner, ${T('OwnerDao')}::city)  ${C('// 2 extra batched SELECT ... IN queries')}`,
  `        .${F('map')} { it.${F('toPet')}() }`,
  `}`,
].join('\n');
const CODE_JOIN_JIMMER = [
  `${T('PetTable')} table = ${T('PetTable')}.$;`,
  `${K('return')} sqlClient.${F('createQuery')}(table)`,
  `    .${F('where')}(table.${F('id')}().${F('gt')}(base)).${F('where')}(table.${F('id')}().${F('le')}(base + rows))`,
  `    .${F('select')}(table.${F('fetch')}(`,
  `        ${T('PetFetcher')}.$.${F('allScalarFields')}()`,
  `            .${F('owner')}(${T('OwnerFetcher')}.$.${F('allScalarFields')}()`,
  `                .${F('city')}(${T('CityFetcher')}.$.${F('allScalarFields')}()))))  ${C('// fetchers -> 2 batched loads')}`,
  `    .${F('execute')}();`,
].join('\n');
const SQL_JOIN_EXPOSED_DAO = [
  `${QC('-- 1) main pet query')}`,
  `${QK('SELECT')} p.id, p.name, p.birth_date, p.type_id, p.owner_id`,
  `${QK('FROM')} pet ${QK('WHERE')} p.id > ${QQ('?')} ${QK('AND')} p.id &lt;= ${QQ('?')}`,
  ``,
  `${QC('-- 2) batched owners')}`,
  `${QK('SELECT')} o.id, o.first_name, o.last_name, o.address, o.telephone, o.city_id`,
  `${QK('FROM')} owner ${QK('WHERE')} o.id ${QK('IN')} (${QQ('?')}, ${QQ('?')}, ...)`,
  ``,
  `${QC('-- 3) batched cities')}`,
  `${QK('SELECT')} c.id, c.name ${QK('FROM')} city ${QK('WHERE')} c.id ${QK('IN')} (${QQ('?')}, ${QQ('?')}, ...)`,
].join('\n');
const SQL_JOIN_JIMMER = [
  `${QC('-- 1) main pet query (owner_id kept as FK for the batch load)')}`,
  `${QK('SELECT')} p.id, p.name, p.birth_date, p.owner_id`,
  `${QK('FROM')} pet ${QK('WHERE')} p.id > ${QQ('?')} ${QK('AND')} p.id &lt;= ${QQ('?')}`,
  ``,
  `${QC('-- 2) batched owners')}`,
  `${QK('SELECT')} o.id, o.first_name, o.last_name, o.address, o.telephone, o.city_id`,
  `${QK('FROM')} owner ${QK('WHERE')} o.id ${QK('IN')} (${QQ('?')}, ${QQ('?')}, ...)`,
  ``,
  `${QC('-- 3) batched cities')}`,
  `${QK('SELECT')} c.id, c.name ${QK('FROM')} city ${QK('WHERE')} c.id ${QK('IN')} (${QQ('?')}, ${QQ('?')}, ...)`,
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
const CODE_PROJECTION_JDBC = [
  `${K('try')} (${K('var')} ps = connection.${F('prepareStatement')}(`,
  `        ${S('"SELECT p.name, o.last_name, c.name FROM pet p"')}`,
  `        + ${S('" JOIN owner o ON p.owner_id = o.id JOIN city c ON o.city_id = c.id"')}`,
  `        + ${S('" WHERE o.city_id = ?"')})) {`,
  `    ps.${F('setLong')}(${N('1')}, cityId);`,
  `    ${K('try')} (${K('var')} rs = ps.${F('executeQuery')}()) {`,
  `        ${T('List')}&lt;${T('PetRow')}&gt; rows = ${K('new')} ${T('ArrayList')}&lt;&gt;();`,
  `        ${K('while')} (rs.${F('next')}()) rows.${F('add')}(${K('new')} ${T('PetRow')}(rs.${F('getString')}(${N('1')}), rs.${F('getString')}(${N('2')}), rs.${F('getString')}(${N('3')})));`,
  `        ${K('return')} rows;`,
  `    }`,
  `}`,
].join('\n');
const CODE_PROJECTION_HIBERNATE = [
  `${K('return')} sessionFactory.${F('fromSession')}(session -> session`,
  `    .${F('createSelectionQuery')}(`,
  `        ${S('"select p.name, o.lastName, c.name from Pet p join p.owner o join o.city c where c.id = :cityId"')},`,
  `        ${T('PetRow')}.${K('class')})`,
  `    .${F('setParameter')}(${S('"cityId"')}, cityId)`,
  `    .${F('getResultList')}());`,
].join('\n');
const CODE_PROJECTION_JOOQ = [
  `${K('return')} ctx.${F('select')}(${T('PET')}.NAME, ${T('OWNER')}.LAST_NAME, ${T('CITY')}.NAME)`,
  `    .${F('from')}(${T('PET')})`,
  `    .${F('join')}(${T('OWNER')}).${F('on')}(${T('PET')}.OWNER_ID.${F('eq')}(${T('OWNER')}.ID))`,
  `    .${F('join')}(${T('CITY')}).${F('on')}(${T('OWNER')}.CITY_ID.${F('eq')}(${T('CITY')}.ID))`,
  `    .${F('where')}(${T('OWNER')}.CITY_ID.${F('eq')}(cityId))`,
  `    .${F('fetch')}(${T('Records')}.${F('mapping')}(${T('PetRow')}::new));`,
].join('\n');
const CODE_PROJECTION_EXPOSED = [
  `${F('transaction')}(database) {`,
  `    (${T('Pets')} ${F('innerJoin')} ${T('Owners')} ${F('innerJoin')} ${T('Cities')})`,
  `        .${F('select')}(${T('Pets')}.name, ${T('Owners')}.lastName, ${T('Cities')}.name)`,
  `        .${F('where')} { ${T('Owners')}.cityId ${F('eq')} cityId }`,
  `        .${F('map')} { ${T('PetRow')}(it[${T('Pets')}.name], it[${T('Owners')}.lastName], it[${T('Cities')}.name]) }`,
  `}`,
].join('\n');
const CODE_PROJECTION_EXPOSED_DAO = [
  `${C('// Exposed DAO drops to the same DSL query for projections')}`,
  `${F('transaction')}(database) {`,
  `    (${T('Pets')} ${F('innerJoin')} ${T('Owners')} ${F('innerJoin')} ${T('Cities')})`,
  `        .${F('select')}(${T('Pets')}.name, ${T('Owners')}.lastName, ${T('Cities')}.name)`,
  `        .${F('where')} { ${T('Owners')}.cityId ${F('eq')} ${T('EntityID')}(cityId, ${T('Cities')}) }`,
  `        .${F('map')} { ${T('PetRow')}(it[${T('Pets')}.name], it[${T('Owners')}.lastName], it[${T('Cities')}.name]) }`,
  `}`,
].join('\n');
const CODE_PROJECTION_JIMMER = [
  `${T('PetTable')} table = ${T('PetTable')}.$;`,
  `${K('return')} sqlClient.${F('createQuery')}(table)`,
  `    .${F('where')}(table.${F('owner')}().${F('city')}().${F('id')}().${F('eq')}(cityId))`,
  `    .${F('select')}(table.${F('name')}(), table.${F('owner')}().${F('lastName')}(), table.${F('owner')}().${F('city')}().${F('name')}())`,
  `    .${F('execute')}();`,
].join('\n');

const CODE_BATCH = [
  `${K('val')} ids = ${F('transaction')} {`,
  `    visits.${F('insertAndFetchIds')}(newVisits)  ${C('// 100 visits, one prepared INSERT, batched')}`,
  `}`,
].join('\n');
const CODE_BATCH_JDBC = [
  `${K('try')} (${K('var')} ps = connection.${F('prepareStatement')}(`,
  `        ${S('"INSERT INTO visit (pet_id, visit_date, description) VALUES (?, ?, ?)"')},`,
  `        ${T('Statement')}.RETURN_GENERATED_KEYS)) {`,
  `    ${K('for')} (${K('int')} i = ${N('0')}; i &lt; ${T('BATCH_SIZE')}; i++) {`,
  `        ps.${F('setLong')}(${N('1')}, ...); ps.${F('setObject')}(${N('2')}, ...); ps.${F('setString')}(${N('3')}, ...);`,
  `        ps.${F('addBatch')}();`,
  `    }`,
  `    ps.${F('executeBatch')}();`,
  `    ${K('try')} (${K('var')} keys = ps.${F('getGeneratedKeys')}()) {`,
  `        ${K('while')} (keys.${F('next')}()) ids.${F('add')}(keys.${F('getLong')}(${N('1')}));`,
  `    }`,
  `}`,
].join('\n');
const CODE_BATCH_HIBERNATE = [
  `sessionFactory.${F('fromTransaction')}(session -> {`,
  `    ${K('for')} (${K('int')} i = ${N('0')}; i &lt; ${T('BATCH_SIZE')}; i++) {`,
  `        ${T('Pet')} pet = session.${F('getReference')}(${T('Pet')}.${K('class')}, ...);  ${C('// proxy, no SELECT')}`,
  `        session.${F('persist')}(${K('new')} ${T('Visit')}(pet, ..., ...));`,
  `    }`,
  `    session.${F('flush')}();  ${C('// ids pre-fetched from visit_seq')}`,
  `    ${K('return')} visits.${F('stream')}().${F('map')}(${T('Visit')}::getId).${F('toList')}();`,
  `});`,
].join('\n');
const CODE_BATCH_JOOQ = [
  `${K('var')} insert = ctx.${F('insertInto')}(${T('VISIT')}, ${T('VISIT')}.PET_ID, ${T('VISIT')}.VISIT_DATE, ${T('VISIT')}.DESCRIPTION);`,
  `${K('for')} (${K('int')} i = ${N('0')}; i &lt; ${T('BATCH_SIZE')}; i++) {`,
  `    insert = insert.${F('values')}(..., ..., ...);  ${C('// one VALUES tuple appended per row')}`,
  `}`,
  `${K('return')} insert.${F('returning')}(${T('VISIT')}.ID).${F('fetch')}().${F('map')}(r -> r.${F('get')}(${T('VISIT')}.ID));`,
].join('\n');
const CODE_BATCH_EXPOSED = [
  `${T('Visits')}.${F('batchInsert')}(${N('0')} ${K('until')} ${T('BATCH_SIZE')}, shouldReturnGeneratedValues = ${K('true')}) { i ->`,
  `    ${K('this')}[${T('Visits')}.petId] = ...`,
  `    ${K('this')}[${T('Visits')}.visitDate] = ...`,
  `    ${K('this')}[${T('Visits')}.description] = ...`,
  `}.${F('map')} { it[${T('Visits')}.id] }  ${C('// Exposed batchInsert = JDBC addBatch, not multi-row VALUES')}`,
].join('\n');
const CODE_BATCH_EXPOSED_DAO = [
  `${K('val')} daos = (${N('0')} ${K('until')} ${T('BATCH_SIZE')}).${F('map')} {`,
  `    ${T('VisitDao')}.${F('new')} { petId = ...; visitDate = ...; description = ... }`,
  `}`,
  `daos.${F('map')} { it.id.value }  ${C('// reading ids flushes the pending inserts as a batch')}`,
].join('\n');
const CODE_BATCH_JIMMER = [
  `${T('List')}&lt;${T('Visit')}&gt; drafts = ...;  ${C('// 100x VisitDraft.$.produce(d -> { d.setPet(makeIdOnly(..)); .. })')}`,
  `sqlClient.${F('getEntities')}()`,
  `    .${F('saveEntitiesCommand')}(drafts)`,
  `    .${F('setMode')}(${T('SaveMode')}.INSERT_ONLY)`,
  `    .${F('execute')}(connection);  ${C('// JDBC batch, ids from IDENTITY')}`,
].join('\n');
const SQL_BATCH = [
  `${QK('INSERT INTO')} visit (pet_id, visit_date, description)`,
  `${QK('VALUES')} (${QQ('?')}, ${QQ('?')}, ${QQ('?')})  ${QC('-- one prepared statement, addBatch/executeBatch x100')}`,
].join('\n');
const SQL_BATCH_JOOQ = [
  `${QK('INSERT INTO')} visit (pet_id, visit_date, description)`,
  `${QK('VALUES')} (${QQ('?')}, ${QQ('?')}, ${QQ('?')}), (${QQ('?')}, ${QQ('?')}, ${QQ('?')}), ...  ${QC('-- 100 tuples, one statement')}`,
  `${QK('RETURNING')} visit.id`,
].join('\n');
const SQL_BATCH_HIBERNATE = [
  `${QK('SELECT')} nextval('visit_seq')  ${QC('-- pooled sequence, ~2 calls for 100 rows')}`,
  ``,
  `${QK('INSERT INTO')} visit (pet_id, visit_date, description, id)`,
  `${QK('VALUES')} (${QQ('?')}, ${QQ('?')}, ${QQ('?')}, ${QQ('?')})  ${QC('-- batched x100, id assigned client-side')}`,
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
  `    owners.${F('update')}(owner.${F('copy')}(telephone = newTelephone))  ${C('// only the changed column is written')}`,
  `}`,
].join('\n');
const SQL_UPDATE = [
  `${QK('SELECT')} o.id, o.first_name, o.last_name, o.address, o.telephone, o.city_id`,
  `${QK('FROM')} owner o`,
  `${QK('WHERE')} o.id = ${QQ('?')}`,
  ``,
  `${QK('UPDATE')} owner`,
  `${QK('SET')} telephone = ${QQ('?')}`,
  `${QK('WHERE')} id = ${QQ('?')}`,
].join('\n');
const CODE_UPDATE_JDBC = [
  `connection.${F('setAutoCommit')}(${K('false')});`,
  `${T('String')} phone;`,
  `${K('try')} (${K('var')} ps = connection.${F('prepareStatement')}(`,
  `        ${S('"SELECT id, first_name, last_name, address, telephone FROM owner WHERE id = ?"')})) {`,
  `    ps.${F('setLong')}(${N('1')}, id);`,
  `    ${K('var')} rs = ps.${F('executeQuery')}(); rs.${F('next')}();`,
  `    phone = ${T('Params')}.${F('toggleTelephone')}(rs.${F('getString')}(${N('5')}));`,
  `    owner = ${K('new')} ${T('Owner')}(rs.${F('getLong')}(${N('1')}), ..., phone, ${K('null')});`,
  `}`,
  `${K('try')} (${K('var')} ps = connection.${F('prepareStatement')}(${S('"UPDATE owner SET telephone = ? WHERE id = ?"')})) {`,
  `    ps.${F('setString')}(${N('1')}, phone); ps.${F('setLong')}(${N('2')}, id); ps.${F('executeUpdate')}();`,
  `}`,
  `connection.${F('commit')}();`,
].join('\n');
const CODE_UPDATE_HIBERNATE = [
  `${A('@Entity')} ${A('@Table')}(name = ${S('"owner"')}) ${A('@DynamicUpdate')}  ${C('// write only the changed columns')}`,
  `${K('class')} ${T('Owner')} {`,
  `    ${A('@Id')} ${A('@GeneratedValue')}(strategy = IDENTITY) ${K('private')} ${T('Long')} id;`,
  `    ${A('@Column')}(name = ${S('"first_name"')}) ${K('private')} ${T('String')} firstName;`,
  `    ${A('@Column')}(name = ${S('"last_name"')}) ${K('private')} ${T('String')} lastName;`,
  `    ${A('@Column')}(name = ${S('"address"')}) ${K('private')} ${T('String')} address;`,
  `    ${A('@Column')}(name = ${S('"telephone"')}) ${K('private')} ${T('String')} telephone;`,
  `    ${A('@ManyToOne')}(fetch = LAZY) ${A('@JoinColumn')}(name = ${S('"city_id"')}) ${K('private')} ${T('City')} city;  ${C('// lazy: city not read')}`,
  `    ${A('@OneToMany')}(mappedBy = ${S('"owner"')}) ${K('private')} ${T('List')}&lt;${T('Pet')}&gt; pets;`,
  ``,
  `    ${K('public')} ${T('String')} ${F('getTelephone')}() { ${K('return')} telephone; }`,
  `    ${K('public')} ${K('void')} ${F('setTelephone')}(${T('String')} telephone) { ${K('this')}.telephone = telephone; }`,
  `    ${C('// Hibernate maps via field access; getId()/getPets() added where the app needs them')}`,
  `}`,
  ``,
  `sessionFactory.${F('fromTransaction')}(session -> {`,
  `    ${T('Owner')} owner = session.${F('find')}(${T('Owner')}.${K('class')}, id);`,
  `    owner.${F('setTelephone')}(${T('Params')}.${F('toggleTelephone')}(owner.${F('getTelephone')}()));`,
  `    ${K('return')} owner;  ${C('// dirty-checking flushes only telephone')}`,
  `});`,
].join('\n');
const CODE_UPDATE_JOOQ = [
  `${K('return')} ctx.${F('transactionResult')}(tx -> {`,
  `    ${K('var')} record = ${T('DSL')}.${F('using')}(tx).${F('fetchOne')}(${T('OWNER')}, ${T('OWNER')}.ID.${F('eq')}(id));`,
  `    record.${F('setTelephone')}(${T('Params')}.${F('toggleTelephone')}(record.${F('getTelephone')}()));`,
  `    record.${F('store')}();  ${C('// UpdatableRecord.store() updates only the changed field')}`,
  `    ${K('return')} record.${F('getId')}();`,
  `});`,
].join('\n');
const CODE_UPDATE_EXPOSED = [
  `${F('transaction')}(database) {`,
  `    ${K('val')} row = ${T('Owners')}.${F('selectAll')}().${F('where')} { ${T('Owners')}.id ${F('eq')} id }.${F('single')}()`,
  `    ${K('val')} phone = ${T('Params')}.${F('toggleTelephone')}(row[${T('Owners')}.telephone])`,
  `    ${T('Owners')}.${F('update')}({ ${T('Owners')}.id ${F('eq')} id }) { it[${T('Owners')}.telephone] = phone }`,
  `}`,
].join('\n');
const CODE_UPDATE_EXPOSED_DAO = [
  `${F('transaction')}(database) {`,
  `    ${K('val')} dao = ${T('OwnerDao')}.${F('findById')}(id) ?: ${F('error')}(${S('"owner not found"')})`,
  `    dao.telephone = ${T('Params')}.${F('toggleTelephone')}(dao.telephone)  ${C('// dirty tracking flushes only telephone')}`,
  `}`,
].join('\n');
const CODE_UPDATE_JIMMER = [
  `${T('Owner')} owner = sqlClient.${F('createQuery')}(${T('OwnerTable')}.$)`,
  `    .${F('where')}(${T('OwnerTable')}.$.${F('id')}().${F('eq')}(id)).${F('select')}(${T('OwnerTable')}.$).${F('execute')}(connection).${F('getFirst')}();`,
  `${T('Owner')} updated = ${T('OwnerDraft')}.$.${F('produce')}(owner, d -> d.${F('setTelephone')}(${T('Params')}.${F('toggleTelephone')}(owner.${F('telephone')}())));`,
  `sqlClient.${F('getEntities')}().${F('saveCommand')}(updated)`,
  `    .${F('setMode')}(${T('SaveMode')}.UPDATE_ONLY)  ${C('// only the draft-modified column')}`,
  `    .${F('execute')}(connection);`,
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

const CODE_GRAPH_JDBC = [
  `${T('Map')}&lt;${T('Long')}, ${T('Owner')}&gt; owners = ${K('new')} ${T('LinkedHashMap')}&lt;&gt;();`,
  `${T('Map')}&lt;${T('Long')}, ${T('List')}&lt;${T('Pet')}&gt;&gt; pets = ${K('new')} ${T('LinkedHashMap')}&lt;&gt;();`,
  `${K('while')} (rs.${F('next')}()) {`,
  `    ${K('long')} ownerId = rs.${F('getLong')}(${N('1')});`,
  `    ${T('Owner')} owner = owners.${F('get')}(ownerId);`,
  `    ${K('if')} (owner == ${K('null')}) {`,
  `        owner = ${K('new')} ${T('Owner')}(ownerId, ..., ${K('new')} ${T('City')}(rs.${F('getLong')}(${N('6')}), rs.${F('getString')}(${N('7')})));`,
  `        owners.${F('put')}(ownerId, owner); pets.${F('put')}(ownerId, ${K('new')} ${T('ArrayList')}&lt;&gt;());`,
  `    }`,
  `    pets.${F('get')}(ownerId).${F('add')}(${K('new')} ${T('Pet')}(rs.${F('getLong')}(${N('8')}), ..., owner));`,
  `}`,
  `${C('// then zip owners + pets into List<OwnerWithPets>')}`,
].join('\n');
const CODE_GRAPH_EXPOSED = [
  `${F('transaction')}(database) {`,
  `    (${T('Owners')} ${F('innerJoin')} ${T('Cities')} ${F('innerJoin')} ${T('Pets')})`,
  `        .${F('selectAll')}()`,
  `        .${F('where')} { ${T('Owners')}.cityId ${F('eq')} cityId }`,
  `        .${F('orderBy')}(${T('Owners')}.id)`,
  `        .${F('groupIntoOwners')}()  ${C('// in-memory LinkedHashMap grouping')}`,
  `}`,
].join('\n');
const CODE_GRAPH_EXPOSED_DAO = [
  `${F('transaction')}(database) {`,
  `    ${T('OwnerDao')}.${F('find')} { ${T('Owners')}.cityId ${F('eq')} ${T('EntityID')}(cityId, ${T('Cities')}) }`,
  `        .${F('orderBy')}(${T('Owners')}.id ${K('to')} ${T('SortOrder')}.ASC)`,
  `        .${F('with')}(${T('OwnerDao')}::city, ${T('OwnerDao')}::pets)  ${C('// pets = batched SELECT ... WHERE owner_id IN (...)')}`,
  `        .${F('map')} { ${T('OwnerWithPets')}(it.${F('toOwner')}(), it.pets.${F('map')} { p -> p.${F('toPet')}() }) }`,
  `}`,
].join('\n');
const CODE_GRAPH_JIMMER = [
  `${T('OwnerTable')} table = ${T('OwnerTable')}.$;`,
  `${K('return')} sqlClient.${F('createQuery')}(table)`,
  `    .${F('where')}(table.${F('city')}().${F('id')}().${F('eq')}(cityId))`,
  `    .${F('orderBy')}(table.${F('id')}().${F('asc')}())`,
  `    .${F('select')}(table.${F('fetch')}(`,
  `        ${T('OwnerFetcher')}.$.${F('allScalarFields')}()`,
  `            .${F('city')}(${T('CityFetcher')}.$.${F('allScalarFields')}())`,
  `            .${F('pets')}(${T('PetFetcher')}.$.${F('allScalarFields')}())))  ${C('// pets = batched WHERE owner_id IN (...)')}`,
  `    .${F('execute')}();`,
].join('\n');
const SQL_GRAPH_HIBERNATE = [
  `${QK('SELECT DISTINCT')}`,
  `       o.id, o.first_name, o.last_name, o.address, o.telephone,`,
  `       p.owner_id, p.id, p.birth_date, p.name, p.type_id, c.id, c.name`,
  `${QK('FROM')} owner o`,
  `${QK('JOIN')} pet  p ${QK('ON')} o.id = p.owner_id`,
  `${QK('JOIN')} city c ${QK('ON')} c.id = o.city_id`,
  `${QK('WHERE')} o.city_id = ${QQ('?')}  ${QC('-- DISTINCT collapses the owner x pet cartesian, no ORDER BY')}`,
].join('\n');
const SQL_GRAPH_JOOQ = [
  `${QK('SELECT')} owner.id, owner.first_name, owner.last_name, owner.address, owner.telephone,`,
  `       city.id, city.name,`,
  `       (${QK('SELECT')} jsonb_agg(jsonb_build_array(p.id, p.name, p.birth_date, p.type_id))`,
  `        ${QK('FROM')} pet p ${QK('WHERE')} p.owner_id = owner.id) ${QK('AS')} pets  ${QC('-- correlated MULTISET, no pet join')}`,
  `${QK('FROM')} owner`,
  `${QK('JOIN')} city ${QK('ON')} owner.city_id = city.id`,
  `${QK('WHERE')} owner.city_id = ${QQ('?')}`,
  `${QK('ORDER BY')} owner.id`,
].join('\n');
const SQL_GRAPH_EXPOSED_DAO = [
  `${QC('-- 1) main owners query')}`,
  `${QK('SELECT')} owner.id, ..., owner.city_id ${QK('FROM')} owner ${QK('WHERE')} owner.city_id = ${QQ('?')} ${QK('ORDER BY')} owner.id`,
  ``,
  `${QC('-- 2) batched cities')}`,
  `${QK('SELECT')} city.id, city.name ${QK('FROM')} city ${QK('WHERE')} city.id ${QK('IN')} (${QQ('?')}, ...)`,
  ``,
  `${QC('-- 3) batched pets (the collection)')}`,
  `${QK('SELECT')} pet.id, pet.name, pet.birth_date, pet.type_id, pet.owner_id`,
  `${QK('FROM')} pet ${QK('WHERE')} pet.owner_id ${QK('IN')} (${QQ('?')}, ${QQ('?')}, ...)`,
].join('\n');
const SQL_GRAPH_JIMMER = [
  `${QC('-- 1) main owners query (city().id() folds to owner.city_id)')}`,
  `${QK('SELECT')} o.id, o.first_name, o.last_name, o.address, o.telephone, o.city_id`,
  `${QK('FROM')} owner o ${QK('WHERE')} o.city_id = ${QQ('?')} ${QK('ORDER BY')} o.id`,
  ``,
  `${QC('-- 2) batched cities')}`,
  `${QK('SELECT')} c.id, c.name ${QK('FROM')} city c ${QK('WHERE')} c.id ${QK('IN')} (${QQ('?')}, ...)`,
  ``,
  `${QC('-- 3) batched pets collection (the OneToMany)')}`,
  `${QK('SELECT')} p.owner_id, p.id, p.name, p.birth_date, p.type_id`,
  `${QK('FROM')} pet p ${QK('WHERE')} p.owner_id ${QK('IN')} (${QQ('?')}, ${QQ('?')}, ...)`,
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
  return `<div class="bm-matrix-wrap"><table class="bm-matrix">
    <thead><tr><th></th>${head}</tr></thead>
    <tbody>${rows}</tbody>
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
  .bm-stat b{background:linear-gradient(100deg,#feeeb0,#fbbf24 55%,#f59e0b);-webkit-background-clip:text;background-clip:text;color:transparent}
  .storm-tut .getit{display:flex;gap:12px;margin-top:26px;flex-wrap:wrap;align-items:stretch}
  .storm-tut .clonebar{display:flex;align-items:center;gap:10px;font-family:var(--mono);font-size:13px;color:var(--plain);
    background:var(--panel);border:1px solid var(--border);border-radius:10px;padding:0 18px;min-height:44px;overflow-x:auto;white-space:nowrap}
  .storm-tut .clonebar .dollar{color:var(--green);user-select:none}
  .bm-matrix-wrap{overflow-x:auto;border:1px solid var(--border);border-radius:14px;background:var(--panel);margin:22px 0 10px;padding:10px 12px}
  .art .bm-matrix,.art .bm-matrix thead,.art .bm-matrix tbody{background:none}
  .art .bm-matrix{width:100%;border-collapse:separate;border-spacing:3px;font-family:var(--mono);font-size:12px;margin:0}
  .art .bm-matrix th,.art .bm-matrix td{border:none;background:none;padding:9px 12px;text-align:right;white-space:nowrap}
  .art .bm-matrix thead th{color:var(--muted);font-weight:600;padding:4px 12px;font-size:10.5px;letter-spacing:.04em;text-align:center}
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
  .bm-limits{border:1px solid var(--border);border-left:3px solid var(--accent);border-radius:12px;background:var(--panel-2);padding:16px 20px;margin:8px 0 24px}
  .storm-tut .art .bm-limits h3{margin:0 0 6px;font-size:15px}
  .bm-limits p{margin:0;color:var(--muted);font-size:13.5px;line-height:1.6}
  .bm-cta{border:1px solid var(--border);border-radius:16px;background:var(--panel);padding:30px 32px;margin:46px 0 10px}
  .storm-tut .art .bm-cta h2{margin:0 0 8px}
  .bm-cta p{margin:0;color:var(--muted);max-width:640px}
  .bm-cta .getit{margin-top:20px}
`;

// One workload: Storm's implementation shows by default; the other six are a
// selector away and the SQL a toggle away, both living in the editor's own
// title bar, so a workload adds a single editor rather than extra panels or
// toggle links. Per-library SQL divergences ride on their variant.
function codeBlock({title, desc, file, storm, others, sql, sqlExtras}) {
  const divergent = new Map(sqlExtras || []);
  const variants = [
    {label: 'Storm', tag: 'Kotlin', code: storm, selected: true},
    ...others.map(({selected, ...v}) => (divergent.has(v.label) ? {...v, sql: divergent.get(v.label)} : v)),
  ];
  return `
  <h3>${title}</h3>
  ${desc ? `<p>${desc}</p>` : ''}
  ${editor({file, tag: 'Kotlin', sql, variants})}`;
}

function buildBody() {
  const charts = WORKLOADS.map(chartHtml).join('\n');
  return `
${navHtml('benchmarks')}

<div class="art">
  <h1>Concise by design.<br><span class="grad">Fast by measurement.</span></h1>
  <p class="dek">Storm was designed around plain entities and queries that closely resemble the SQL they produce. These benchmarks show that the same design also keeps runtime overhead low.</p>
  <p class="dek">Seven implementations run against the same PostgreSQL database, using the same schema, data and transaction boundaries. Every result includes a real TCP round trip, and the code behind every number is available to inspect and reproduce.</p>
  <p class="bm-meta">PostgreSQL 17 over TCP · JMH · Storm 1.13.0 · measured 2026-07-16</p>

  <div class="bm-stats">
    <div class="bm-stat"><b>5 of 8</b><span>workloads where Storm is the fastest framework above JDBC.</span></div>
    <div class="bm-stat"><b>5.6%</b><span>is the most Storm trails the fastest framework on any workload.</span></div>
    <div class="bm-stat"><b>72% less</b><span>entity code than JPA: 29 lines in Storm, 105 as JPA entities.</span></div>
  </div>

  <h2>Performance results</h2>
  <p>The workloads cover common data-access paths: point reads, joined entity hydration, projections, batch writes, change-aware updates and one-to-many object graphs.</p>
  <p>Seven implementations, one database, one discipline: same schema, same data, same transaction boundaries, every score a real network round trip away from PostgreSQL. Mean latency per operation, lower is better. Cells are tinted by distance from the fastest framework in the row, green through red. Percentages are overhead over raw JDBC. Raw JDBC is the baseline.</p>
  ${matrixHtml()}
  <p class="bm-matrix-read">On most workloads Storm is the fastest framework; where it isn't, another library edges it narrowly: Hibernate on the single-row lookup and the read-modify-update, jOOQ on the batch insert, where its single multi-row statement beats the batched single-row insert the others send. Even then, Storm stays within about six percent of the fastest framework. Because every result includes a real network round trip, framework overhead represents only part of the reported latency. Absolute times depend on the hardware they were measured on; the relative comparisons are the point.</p>

  <details class="bm-details">
    <summary>Per-workload charts: the same numbers with their reported error</summary>
    <p class="bm-desc">Compare within a chart; each chart is a same-session comparison.</p>
    <div class="bm-grid">
${charts}
    </div>
  </details>

  <h2>Code comparison</h2>
  <p>Numbers without code invite tuned-benchmark suspicion, so the counts below, and the workloads that follow, show exactly what each library runs, trimmed of harness plumbing. The full sources for all seven implementations are in the benchmark repository.</p>
  ${modelLocHtml()}
  ${locHtml()}
  <p class="bm-note">LOC is presented as an illustration of these benchmark implementations, rather than as a universal measure of framework complexity.</p>

  <h2>Inspect each workload</h2>
  <p>Each workload shows Storm's implementation. Pick another library from the selector to compare it, or toggle <i>Show SQL</i> for the exact statement on the wire.</p>

  ${codeBlock({
    title: 'The model',
    file: 'Entities.kt',
    desc: `Storm's model is plain data classes. Nullability, keys and relations live in the type: <code>@FK val owner: Owner</code> hydrates through a join, <code>Ref&lt;PetType&gt;</code> stays a lazy reference until asked. No proxies, no session lifecycle, nothing to configure. Compare it against the JPA entities, table objects and interfaces the other libraries declare for the same five tables.`,
    storm: CODE_ENTITIES,
    others: [
      {label: 'JDBC', file: 'Models.java', tag: 'Java', code: CODE_MODEL_JDBC, selected: true},
      {label: 'Hibernate', file: 'Entities.java', tag: 'Java', code: CODE_MODEL_HIBERNATE},
      {label: 'jOOQ', file: 'Models.java', tag: 'Java', code: CODE_MODEL_JOOQ},
      {label: 'Exposed', file: 'Tables.kt', tag: 'Kotlin', code: CODE_MODEL_EXPOSED},
      {label: 'Exposed DAO', file: 'Entities.kt', tag: 'Kotlin', code: CODE_MODEL_EXPOSED_DAO},
      {label: 'Jimmer', file: 'Entities.java', tag: 'Java', code: CODE_MODEL_JIMMER},
    ],
  })}

  ${codeBlock({
    title: 'Primary key lookup',
    file: 'singleRowById',
    storm: CODE_SINGLE,
    sql: SQL_SINGLE,
    others: [
      {label: 'JDBC', tag: 'Java', code: CODE_SINGLE_JDBC, selected: true},
      {label: 'Hibernate', tag: 'Java', code: CODE_SINGLE_HIBERNATE},
      {label: 'jOOQ', tag: 'Java', code: CODE_SINGLE_JOOQ},
      {label: 'Exposed', tag: 'Kotlin', code: CODE_SINGLE_EXPOSED},
      {label: 'Exposed DAO', tag: 'Kotlin', code: CODE_SINGLE_EXPOSED_DAO},
      {label: 'Jimmer', tag: 'Java', code: CODE_SINGLE_JIMMER},
    ],
  })}

  ${codeBlock({
    title: 'Three-table join',
    file: 'joinWithMapping',
    desc: 'No fetch joins to spell out and no N+1 to dodge: the entity graph declares what a Pet is, so selecting pets hydrates owner and city from one query.',
    storm: CODE_JOIN,
    sql: SQL_JOIN,
    sqlExtras: [['Exposed DAO', SQL_JOIN_EXPOSED_DAO], ['Jimmer', SQL_JOIN_JIMMER]],
    others: [
      {label: 'JDBC', tag: 'Java', code: CODE_JOIN_JDBC, selected: true},
      {label: 'Hibernate', tag: 'Java', code: CODE_JOIN_HIBERNATE},
      {label: 'jOOQ', tag: 'Java', code: CODE_JOIN_JOOQ},
      {label: 'Exposed', tag: 'Kotlin', code: CODE_JOIN_EXPOSED},
      {label: 'Exposed DAO', tag: 'Kotlin', code: CODE_JOIN_EXPOSED_DAO},
      {label: 'Jimmer', tag: 'Java', code: CODE_JOIN_JIMMER},
    ],
  })}

  ${codeBlock({
    title: 'Projection',
    file: 'projection',
    desc: 'A template picks three columns across the graph; the metamodel keeps every path compile-checked.',
    storm: CODE_PROJECTION,
    sql: SQL_PROJECTION,
    others: [
      {label: 'JDBC', tag: 'Java', code: CODE_PROJECTION_JDBC, selected: true},
      {label: 'Hibernate', tag: 'Java', code: CODE_PROJECTION_HIBERNATE},
      {label: 'jOOQ', tag: 'Java', code: CODE_PROJECTION_JOOQ},
      {label: 'Exposed', tag: 'Kotlin', code: CODE_PROJECTION_EXPOSED},
      {label: 'Exposed DAO', tag: 'Kotlin', code: CODE_PROJECTION_EXPOSED_DAO},
      {label: 'Jimmer', tag: 'Java', code: CODE_PROJECTION_JIMMER},
    ],
  })}

  ${codeBlock({
    title: 'Batch insert',
    file: 'batchInsert',
    storm: CODE_BATCH,
    sql: SQL_BATCH,
    sqlExtras: [['Hibernate', SQL_BATCH_HIBERNATE], ['jOOQ', SQL_BATCH_JOOQ]],
    others: [
      {label: 'JDBC', tag: 'Java', code: CODE_BATCH_JDBC, selected: true},
      {label: 'Hibernate', tag: 'Java', code: CODE_BATCH_HIBERNATE},
      {label: 'jOOQ', tag: 'Java', code: CODE_BATCH_JOOQ},
      {label: 'Exposed', tag: 'Kotlin', code: CODE_BATCH_EXPOSED},
      {label: 'Exposed DAO', tag: 'Kotlin', code: CODE_BATCH_EXPOSED_DAO},
      {label: 'Jimmer', tag: 'Java', code: CODE_BATCH_JIMMER},
    ],
  })}

  ${codeBlock({
    title: 'Read, modify, update',
    file: 'updateById',
    desc: `Storm's regular <code>Owner</code> is an aggregate: reading one loads its city through a join. Every other library declares that association lazy and reads the owner row alone, so to keep the read side of this workload identical for everyone, the benchmark uses a dedicated shape of the same table where city stays a lazy <code>Ref</code>. That shape is one record; declaring it is Storm's equivalent of the <code>FetchType.LAZY</code> the others put on their entities, and its ten lines are counted against Storm in the Queries LOC table above. On the write side, <code>@DynamicUpdate(FIELD)</code> writes only the column that changed. Entities are immutable; an update is a <code>copy</code>.`,
    storm: CODE_UPDATE,
    sql: SQL_UPDATE,
    others: [
      {label: 'JDBC', tag: 'Java', code: CODE_UPDATE_JDBC, selected: true},
      {label: 'Hibernate', tag: 'Java', code: CODE_UPDATE_HIBERNATE},
      {label: 'jOOQ', tag: 'Java', code: CODE_UPDATE_JOOQ},
      {label: 'Exposed', tag: 'Kotlin', code: CODE_UPDATE_EXPOSED},
      {label: 'Exposed DAO', tag: 'Kotlin', code: CODE_UPDATE_EXPOSED_DAO},
      {label: 'Jimmer', tag: 'Java', code: CODE_UPDATE_JIMMER},
    ],
  })}

  ${codeBlock({
    title: 'Object graph',
    file: 'objectGraph',
    desc: 'One query, grouped during hydration. Repeated owners deduplicate to the same instance, so grouping is an identity operation, not a hash of every field.',
    storm: CODE_GRAPH_STORM,
    sql: SQL_GRAPH,
    sqlExtras: [['Hibernate', SQL_GRAPH_HIBERNATE], ['jOOQ', SQL_GRAPH_JOOQ], ['Exposed DAO', SQL_GRAPH_EXPOSED_DAO], ['Jimmer', SQL_GRAPH_JIMMER]],
    others: [
      {label: 'JDBC', tag: 'Java', code: CODE_GRAPH_JDBC, selected: true},
      {label: 'Hibernate', tag: 'Java', code: CODE_GRAPH_HIBERNATE},
      {label: 'jOOQ', tag: 'Java', code: CODE_GRAPH_JOOQ},
      {label: 'Exposed', tag: 'Kotlin', code: CODE_GRAPH_EXPOSED},
      {label: 'Exposed DAO', tag: 'Kotlin', code: CODE_GRAPH_EXPOSED_DAO},
      {label: 'Jimmer', tag: 'Java', code: CODE_GRAPH_JIMMER},
    ],
  })}

  <h2>Methodology and reproduction</h2>

  <div class="bm-limits">
    <h3>Scope and limitations</h3>
    <p>These benchmarks measure single-threaded operation latency on PostgreSQL. They do not measure application throughput, connection-pool contention, startup time, memory use, native-image performance or behaviour on other databases.</p>
  </div>

  <p>The suite is built to be argued with. Everything below is enforced in code, not prose.</p>
  <ul>
    <li><b>Real round trips.</b> One tuned PostgreSQL 17 container, reached over TCP. Depending on the benchmark session, the JDBC single-row round trip measured roughly 155&ndash;172 µs, and every score includes it. That compresses relative differences; the mapping-heavy workloads are where library differences show.</li>
    <li><b>JMH, properly.</b> Two forks, five 3-second measurement iterations after warmup, single thread: latency, not throughput. Sanity checks run every workload once per trial and verify row counts before anything is timed.</li>
    <li><b>Same work for everyone.</b> Identical schema and data, identical transaction boundaries on writes, and update values derived from the value just read, so change-detecting libraries can never silently skip a write. On the update workload every implementation writes only the changed column and reads a lazy association shape.</li>
    <li><b>Idiomatic code for everyone.</b> Each library is written the way its documentation recommends: Hibernate with <code>join fetch</code> and <code>@DynamicUpdate</code>, jOOQ with generated records and <code>MULTISET</code>, Jimmer with fetchers, Exposed in both DSL and DAO flavors.</li>
    <li><b>Rows are the unit of comparison.</b> Libraries within one chart ran in the same session under the same conditions. Comparing across charts, or treating values as absolute costs, carries environment drift that comparing within a chart does not.</li>
  </ul>
  <p>Versions: Storm 1.13.0, Hibernate 7.4.5, jOOQ 3.21.6, Exposed 1.3.1, Jimmer 0.11.0, PostgreSQL 17, JDK 21.</p>
  <p>The repository contains the full methodology, the statement-log auditing tools used to verify round-trip counts, and every implementation in full; <code>scripts/run.sh</code> reproduces the numbers.</p>
  <div class="getit">
    ${clonebar('git clone https://github.com/storm-orm/storm-benchmarks.git')}
    <a class="btn" href="https://github.com/storm-orm/storm-benchmarks" target="_blank" rel="noopener">View on GitHub →</a>
  </div>

  <div class="bm-cta">
    <h2>See how the API feels</h2>
    <p>Performance matters most when the programming model also fits your application. Explore the Storm documentation or build the example application yourself.</p>
    <div class="getit">
      <a class="btn primary" href="/quickstart">Get started</a>
      <a class="btn" href="https://github.com/storm-orm/storm-benchmarks" target="_blank" rel="noopener">View benchmark source</a>
    </div>
  </div>
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
