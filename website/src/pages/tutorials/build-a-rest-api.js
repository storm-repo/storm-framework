import React from 'react';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import {
  TutorialPage,
  navHtml,
  FOOT_HTML,
  editor,
  K, T, S, C, F, N, A, P, QK, QC,
} from '../../components/tutorial/tutorialTheme';

// End-to-end tutorial: build a small but complete REST API from an empty
// project to running endpoints and a test. Kotlin + Ktor (the lightest way to
// show the whole loop). Lives as a website tutorial page so it deploys live
// immediately and the /quickstart "Build a real app" button can link to it.
// The install snippet renders the resolved release version from customFields.

const TITLE = 'Build a REST API from scratch';
const DESC =
  'From an empty project to a running, tested REST API with Storm and Ktor: ' +
  'model two entities, load their relationship in a single query, expose CRUD ' +
  'routes, and assert the SQL with a test.';

function buildBody(version) {
  const gradle =
    C('// build.gradle.kts\n') +
    F('plugins') + P(' {\n') +
    P('    ') + F('kotlin') + P('(') + S('"jvm"') + P(') version ') + S('"2.0.21"') + P('\n') +
    P('    ') + F('id') + P('(') + S('"io.ktor.plugin"') + P(') version ') + S('"3.0.3"') + P('\n') +
    P('    ') + F('id') + P('(') + S('"com.google.devtools.ksp"') + P(') version ') + S('"2.0.21-1.0.28"') + P('\n') +
    P('}\n\n') +
    F('dependencies') + P(' {\n') +
    P('    ') + F('implementation') + P('(') + F('platform') + P('(') + S(`"st.orm:storm-bom:${version}"`) + P('))\n') +
    P('    ') + F('implementation') + P('(') + S('"st.orm:storm-kotlin"') + P(')\n') +
    P('    ') + F('implementation') + P('(') + S('"st.orm:storm-ktor"') + P(')\n') +
    P('    ') + F('runtimeOnly') + P('(') + S('"st.orm:storm-core"') + P(')\n') +
    P('    ') + F('runtimeOnly') + P('(') + S('"st.orm:storm-h2"') + P(')\n') +
    P('    ') + F('runtimeOnly') + P('(') + S('"com.h2database:h2:2.2.224"') + P(')\n') +
    P('    ') + F('ksp') + P('(') + S('"st.orm:storm-metamodel-ksp"') + P(')\n') +
    P('    ') + F('kotlinCompilerPluginClasspath') + P('(') + S('"st.orm:storm-compiler-plugin-2.0"') + P(')\n\n') +
    P('    ') + F('implementation') + P('(') + S('"io.ktor:ktor-server-netty"') + P(')\n') +
    P('    ') + F('implementation') + P('(') + S('"io.ktor:ktor-server-content-negotiation"') + P(')\n') +
    P('    ') + F('implementation') + P('(') + S('"io.ktor:ktor-serialization-jackson"') + P(')\n') +
    P('    ') + F('implementation') + P('(') + S('"st.orm:storm-jackson2"') + P(')\n\n') +
    P('    ') + F('testImplementation') + P('(') + S('"st.orm:storm-ktor-test"') + P(')\n') +
    P('    ') + F('testImplementation') + P('(') + S('"com.h2database:h2"') + P(')\n') +
    P('}');

  const schema =
    C('-- src/main/resources/schema.sql\n') +
    K('CREATE TABLE ') + P('folder (\n') +
    P('    id    ') + T('INT') + P(' ') + K('PRIMARY KEY AUTO_INCREMENT') + P(',\n') +
    P('    name  ') + T('VARCHAR') + P('(') + N('100') + P(') ') + K('NOT NULL') + P('\n') +
    P(');\n') +
    K('CREATE TABLE ') + P('bookmark (\n') +
    P('    id         ') + T('INT') + P(' ') + K('PRIMARY KEY AUTO_INCREMENT') + P(',\n') +
    P('    url        ') + T('VARCHAR') + P('(') + N('2000') + P(') ') + K('NOT NULL') + P(',\n') +
    P('    title      ') + T('VARCHAR') + P('(') + N('200') + P(') ') + K('NOT NULL') + P(',\n') +
    P('    folder_id  ') + T('INT') + P(' ') + K('NOT NULL REFERENCES') + P(' folder(id)\n') +
    P(');\n') +
    C('-- one folder to start with\n') +
    K('INSERT INTO ') + P('folder (name) ') + K('VALUES') + P(' (') + S("'Reading'") + P(');');

  const entities =
    C('// Folder.kt\n') +
    K('data class ') + T('Folder') + P('(\n') +
    P('    ') + A('@PK') + P(' ') + K('val ') + P('id: ') + T('Int') + P(' = ') + N('0') + P(',\n') +
    P('    ') + K('val ') + P('name: ') + T('String') + P(',\n') +
    P(') : ') + T('Entity') + P('<') + T('Int') + P('>\n\n') +
    C('// Bookmark.kt — @FK means the folder is loaded in the same query\n') +
    K('data class ') + T('Bookmark') + P('(\n') +
    P('    ') + A('@PK') + P(' ') + K('val ') + P('id: ') + T('Int') + P(' = ') + N('0') + P(',\n') +
    P('    ') + K('val ') + P('url: ') + T('String') + P(',\n') +
    P('    ') + K('val ') + P('title: ') + T('String') + P(',\n') +
    P('    ') + A('@FK') + P(' ') + K('val ') + P('folder: ') + T('Folder') + P(',\n') +
    P(') : ') + T('Entity') + P('<') + T('Int') + P('>');

  const conf =
    C('# src/main/resources/application.conf\n') +
    P('storm {\n') +
    P('    datasource {\n') +
    P('        jdbcUrl = ') + S("\"jdbc:h2:mem:bookmarks;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:schema.sql'\"") + P('\n') +
    P('        username = ') + S('"sa"') + P('\n') +
    P('        password = ') + S('""') + P('\n') +
    P('    }\n') +
    P('    validation { schemaMode = ') + S('"warn"') + P(' }') + P('   ') + C('# log any entity/schema mismatch\n') +
    P('}');

  const repo =
    C('// BookmarkRepository.kt — CRUD is inherited; add only your own queries\n') +
    K('interface ') + T('BookmarkRepository') + P(' : ') + T('EntityRepository') + P('<') + T('Bookmark') + P(', ') + T('Int') + P('> {\n') +
    P('    ') + K('fun ') + F('findByFolderName') + P('(name: ') + T('String') + P('): ') + T('List') + P('<') + T('Bookmark') + P('> =\n') +
    P('        ') + F('findAll') + P('(Bookmark_.folder.name ') + K('eq') + P(' name)\n') +
    P('}');

  const app =
    C('// Application.kt\n') +
    K('fun ') + F('main') + P('() {\n') +
    P('    ') + F('embeddedServer') + P('(') + T('Netty') + P(', port = ') + N('8080') + P(') { ') + F('module') + P('() }.') + F('start') + P('(wait = ') + K('true') + P(')\n') +
    P('}\n\n') +
    K('fun ') + T('Application') + P('.') + F('module') + P('() {\n') +
    P('    ') + F('install') + P('(') + T('Storm') + P(')') + P('                     ') + C('// reads storm.datasource; auto-registers repositories\n') +
    P('    ') + F('install') + P('(') + T('ContentNegotiation') + P(') {\n') +
    P('        ') + F('jackson') + P(' { ') + F('registerModule') + P('(') + T('StormModule') + P('()) }\n') +
    P('    }\n') +
    P('    ') + F('routing') + P(' { ') + F('bookmarkRoutes') + P('() }\n') +
    P('}');

  const routes =
    C('// Routes.kt\n') +
    A('@JsonIgnoreProperties') + P('(ignoreUnknown = ') + K('true') + P(')\n') +
    K('data class ') + T('NewBookmark') + P('(') + K('val ') + P('url: ') + T('String') + P(', ') + K('val ') + P('title: ') + T('String') + P(', ') + K('val ') + P('folderId: ') + T('Int') + P(')\n\n') +
    K('fun ') + T('Route') + P('.') + F('bookmarkRoutes') + P('() {\n') +
    P('    ') + F('get') + P('(') + S('"/bookmarks"') + P(') {\n') +
    P('        call.') + F('respond') + P('(') + F('repository') + P('<') + T('BookmarkRepository') + P('>().') + F('findAll') + P('())\n') +
    P('    }\n\n') +
    P('    ') + F('get') + P('(') + S('"/bookmarks/{id}"') + P(') {\n') +
    P('        ') + K('val ') + P('id = call.parameters.') + F('getOrFail') + P('(') + S('"id"') + P(').') + F('toInt') + P('()\n') +
    P('        ') + K('val ') + P('bookmark = call.orm.') + F('entity') + P('<') + T('Bookmark') + P('>().') + F('findById') + P('(id)\n') +
    P('        call.') + F('respond') + P('(bookmark ?: ') + T('HttpStatusCode') + P('.NotFound)\n') +
    P('    }\n\n') +
    P('    ') + F('post') + P('(') + S('"/bookmarks"') + P(') {\n') +
    P('        ') + K('val ') + P('body = call.') + F('receive') + P('<') + T('NewBookmark') + P('>()\n') +
    P('        ') + K('val ') + P('folder = call.orm.') + F('entity') + P('<') + T('Folder') + P('>().') + F('findById') + P('(body.folderId)\n') +
    P('        ') + K('if ') + P('(folder == ') + K('null') + P(') { call.') + F('respond') + P('(') + T('HttpStatusCode') + P('.BadRequest, ') + S('"unknown folder"') + P('); ') + K('return@post') + P(' }\n') +
    P('        ') + K('val ') + P('created = ') + F('transaction') + P(' {\n') +
    P('            call.orm ') + K('insert ') + T('Bookmark') + P('(url = body.url, title = body.title, folder = folder)\n') +
    P('        }\n') +
    P('        call.') + F('respond') + P('(') + T('HttpStatusCode') + P('.Created, created)\n') +
    P('    }\n\n') +
    P('    ') + F('delete') + P('(') + S('"/bookmarks/{id}"') + P(') {\n') +
    P('        ') + K('val ') + P('id = call.parameters.') + F('getOrFail') + P('(') + S('"id"') + P(').') + F('toInt') + P('()\n') +
    P('        ') + F('transaction') + P(' { call.orm.') + F('entity') + P('<') + T('Bookmark') + P('>().') + F('removeById') + P('(id) }\n') +
    P('        call.') + F('respond') + P('(') + T('HttpStatusCode') + P('.NoContent)\n') +
    P('    }\n') +
    P('}');

  const listCode =
    C('// GET /bookmarks returns every bookmark, each with its folder\n') +
    F('repository') + P('<') + T('BookmarkRepository') + P('>().') + F('findAll') + P('()');
  const listSql =
    QC('-- one query, no N+1: the folder is joined in\n') +
    QK('SELECT') + ' b.id, b.url, b.title, f.id, f.name\n' +
    QK('FROM') + ' bookmark b\n' +
    QK('INNER JOIN') + ' folder f ' + QK('ON') + ' b.folder_id = f.id';

  const run =
    C('# start the app\n') +
    P('./gradlew run\n\n') +
    C('# create a bookmark in folder 1\n') +
    P('curl -s -X POST localhost:8080/bookmarks \\\n') +
    P("  -H 'content-type: application/json' \\\n") +
    P('  -d ') + S('\'{"url":"https://orm.st","title":"Storm","folderId":1}\'') + P('\n\n') +
    C('# list them back — each bookmark includes its folder object\n') +
    P('curl -s localhost:8080/bookmarks');

  const test =
    C('// BookmarkRoutesTest.kt\n') +
    A('@Test') + P('\n') +
    K('fun ') + P('`POST creates a bookmark with a single insert`() = ') + F('testStormApplication') + P('(\n') +
    P('    scripts = ') + F('listOf') + P('(') + S('"/schema.sql"') + P('),\n') +
    P(') { scope ->\n') +
    P('    ') + F('application') + P(' {\n') +
    P('        ') + F('install') + P('(') + T('Storm') + P(') { dataSource = scope.stormDataSource }\n') +
    P('        ') + F('install') + P('(') + T('ContentNegotiation') + P(') { ') + F('jackson') + P(' { ') + F('registerModule') + P('(') + T('StormModule') + P('()) } }\n') +
    P('        ') + F('routing') + P(' { ') + F('bookmarkRoutes') + P('() }\n') +
    P('    }\n\n') +
    P('    scope.stormSqlCapture.') + F('run') + P(' {\n') +
    P('        client.') + F('post') + P('(') + S('"/bookmarks"') + P(') {\n') +
    P('            ') + F('contentType') + P('(') + T('ContentType') + P('.Application.Json)\n') +
    P('            ') + F('setBody') + P('(') + S('"""{"url":"https://orm.st","title":"Storm","folderId":1}"""') + P(')\n') +
    P('        }\n') +
    P('    }\n') +
    P('    ') + F('assertEquals') + P('(') + N('1') + P(', scope.stormSqlCapture.') + F('count') + P('(') + T('Operation') + P('.INSERT))\n') +
    P('}');

  return `
${navHtml('tutorials')}

<div class="art">
  <div class="crumbs"><a href="/">Home</a><span class="sep">/</span><a href="/tutorials/">Tutorials</a><span class="sep">/</span>Build a REST API</div>
  <h1>Build a REST API<br><span class="grad">from scratch.</span></h1>
  <p class="dek">Start with an empty project and finish with a running, tested REST API: two entities, their relationship loaded in a single query, CRUD routes over Ktor, and a test that asserts the exact SQL. About twenty minutes end to end.</p>
  <div class="meta"><span>Kotlin</span><span>Ktor 3</span><span>~20 min</span></div>

  <div class="note">New to Storm? The <a href="/quickstart">five-minute quickstart</a> covers just install, entity, and query. This tutorial builds the whole app around them.</div>

  <h2><span class="hno">1</span>Create the project</h2>
  <p>Start a plain Kotlin/Gradle project and add Ktor and Storm. The Ktor plugin manages the Ktor artifact versions, and the Storm BOM manages Storm's, so most dependencies need no version. We use H2 so there is nothing to install.</p>
  ${editor({file: 'build.gradle.kts', tag: 'Gradle · Kotlin DSL', code: gradle})}

  <h2><span class="hno">2</span>Create the schema</h2>
  <p>Storm maps to an existing schema rather than generating one, which keeps migrations under your control. For this tutorial a small script is enough; H2 runs it on startup, so there is no migration tool to set up yet.</p>
  ${editor({file: 'schema.sql', tag: 'SQL', code: schema})}

  <h2><span class="hno">3</span>Model the domain</h2>
  <p>Two immutable data classes. A <code>Bookmark</code> belongs to a <code>Folder</code>; marking that reference <code>@FK</code> is the whole relationship. Field names map to columns automatically, so <code>folder</code> becomes the <code>folder_id</code> column.</p>
  ${editor({file: 'model.kt', tag: 'Kotlin', code: entities})}

  <h2><span class="hno">4</span>Point Storm at the database</h2>
  <p>The Storm Ktor plugin reads its DataSource from <code>application.conf</code>. No wiring code: <code>install(Storm)</code> builds a connection pool and, because the metamodel processor already indexed them, registers your repositories.</p>
  ${editor({file: 'application.conf', tag: 'HOCON', code: conf})}

  <h2><span class="hno">5</span>Add a repository</h2>
  <p>Extend <code>EntityRepository</code> and every CRUD method comes for free. Add your own one-line queries on top; <code>Bookmark_</code> is the compile-time metamodel, so a typo in a field name fails to compile.</p>
  ${editor({file: 'BookmarkRepository.kt', tag: 'Kotlin', code: repo})}

  <h2><span class="hno">6</span>Wire up the application</h2>
  <p>A standard Ktor <code>main</code> and module. Install Storm, register Storm's Jackson module so entities serialize cleanly, and mount the routes.</p>
  ${editor({file: 'Application.kt', tag: 'Kotlin', code: app})}

  <h2><span class="hno">7</span>Write the routes</h2>
  <p>Read straight from the ORM, write inside <code>transaction { }</code>. Because Ktor and Storm are both coroutine-based, the transaction rides the request's coroutine with no proxies or annotations. <code>call.orm</code> and <code>repository&lt;T&gt;()</code> are extensions available right in the handler.</p>
  ${editor({file: 'Routes.kt', tag: 'Kotlin', code: routes})}
  <p>The list endpoint is where Storm earns its keep. One call, one query: the folder for every bookmark is joined in, so there is no N+1 to discover later. Toggle <b>Show SQL</b> to see exactly what runs.</p>
  ${editor({file: 'BookmarkRepository.kt', tag: 'Kotlin', code: listCode, sql: listSql})}

  <h2><span class="hno">8</span>Run it</h2>
  <p>Start the server and exercise it with curl. The created bookmark comes back with its full folder object, loaded in the same query that fetched the bookmark.</p>
  ${editor({file: 'terminal', tag: 'shell', code: run})}

  <h2><span class="hno">9</span>Test it</h2>
  <p><code>storm-ktor-test</code> spins up an in-memory database from your schema script and captures the SQL. Here we assert the create path is a single INSERT, so an accidental N+1 or extra round-trip fails the build rather than slipping into production.</p>
  ${editor({file: 'BookmarkRoutesTest.kt', tag: 'Kotlin · test', code: test})}

  <h2><span class="hno">✓</span>You built it</h2>
  <p>An empty folder to a running, tested API: two entities, a joined relationship, four routes, and a repository, with no persistence context, no proxies, and no N+1. From here:</p>
  <div class="refs">
    <a href="/docs/relationships">Relationships</a>
    <a href="/docs/repositories">Repositories</a>
    <a href="/docs/ktor-integration">Ktor integration</a>
    <a href="/docs/testing">Testing</a>
    <a href="/examples/kotlin-ktor">The full Ktor example app</a>
  </div>

  <div class="cta">
    <a href="/docs/" class="btn primary">Read the docs</a>
    <a href="/examples/" class="btn">See full example apps</a>
  </div>
</div>

${FOOT_HTML}`;
}

export default function BuildARestApi() {
  const {siteConfig} = useDocusaurusContext();
  const version = siteConfig.customFields?.stormVersion || '0.0.0';
  return (
    <TutorialPage
      title={TITLE}
      description={DESC}
      slug="build-a-rest-api"
      body={buildBody(version)}
    />
  );
}
