import React, {useEffect} from 'react';
import Head from '@docusaurus/Head';

// The Storm landing page. The markup/CSS/JS are kept verbatim from the
// hand-built design (landing-drafts/2-live.html) and mounted here so it serves
// at `/`, in front of the docs (which now live under `/docs`). The <style> is
// rendered inside the component so it only applies while this page is mounted
// and never leaks into the docs theme.

const CSS = `
  :root{
    --bg:#070709; --panel:#0f0f14; --panel-2:#0b0b0f; --statusbg:#08080b;
    --border:#20202a; --border-soft:#17171f;
    --text:#eaeaf0; --muted:#8a8a96; --faint:#565662;
    --accent:#818cf8; --accent-2:#a78bfa; --green:#5eead4;
    --kw:#c4b5fd; --type:#7dd3fc; --str:#86efac; --com:#5a616e; --fn:#f0abfc; --num:#fcd34d; --anno:#fbbf24; --plain:#cfd0d8;
    --mono:"JetBrains Mono",ui-monospace,SFMono-Regular,Menlo,monospace;
    --sans:"Inter",system-ui,-apple-system,Segoe UI,Roboto,sans-serif;
  }
  html{background:var(--bg)}
  body{margin:0;background:var(--bg);color:var(--text);font-family:var(--sans);-webkit-font-smoothing:antialiased;
    background-image:radial-gradient(1100px 540px at 18% -10%,rgba(129,140,248,.14),transparent 62%);}
  .storm-home a{color:inherit;text-decoration:none}
  .storm-home *{box-sizing:border-box}
  .storm-home .wrap{max-width:1120px;margin:0 auto;padding:0 24px}

  .storm-home nav{position:sticky;top:0;z-index:20;backdrop-filter:blur(12px);background:rgba(7,7,9,.7);border-bottom:1px solid var(--border-soft)}
  .storm-home .nav{display:flex;align-items:center;justify-content:space-between;height:62px}
  .storm-home .brand{display:flex;align-items:center;gap:9px;font-weight:600;letter-spacing:-.01em}
  .storm-home .logo{height:26px;width:auto;display:block;position:relative;top:-2px;filter:drop-shadow(0 0 10px rgba(129,140,248,.35))}
  .storm-home .brand b{font-family:var(--mono);font-weight:700}
  .storm-home .tech-tag{font-family:var(--mono);font-size:11px;color:var(--faint);letter-spacing:.02em;border-left:1px solid var(--border);padding-left:12px}
  .storm-home .nav-links{display:flex;align-items:center;gap:24px;font-size:14px;color:var(--muted)}
  .storm-home .nav-links a:hover{color:var(--text)}
  .storm-home .btn{display:inline-flex;align-items:center;gap:8px;height:40px;padding:0 18px;border-radius:9px;font-size:14.5px;
    font-weight:550;border:1px solid var(--border);background:var(--panel);transition:.16s;cursor:pointer}
  .storm-home .btn:hover{border-color:#34343d;transform:translateY(-1px)}
  .storm-home .btn.primary{background:var(--accent);color:#0a0a0f;border-color:var(--accent);font-weight:600}
  .storm-home .btn.primary:hover{background:#9aa3ff}

  /* hero — left aligned */
  .storm-home header{padding:46px 0 34px}
  .storm-home h1{font-size:clamp(42px,7vw,78px);line-height:.97;letter-spacing:-.04em;font-weight:800;margin:0}
  .storm-home .grad{background:linear-gradient(100deg,#a78bfa,#818cf8 50%,#7dd3fc);-webkit-background-clip:text;background-clip:text;color:transparent}
  /* Rotating hero line (replaces the static "Predictable Persistence"): all four
     principles live in the DOM, stacked in one grid cell so the line never
     reflows; the JS toggles the .in class to fade one in at a time. nowrap keeps
     each principle on a single line, and the font scales with the viewport so
     the longest one still fits. */
  .storm-home .hero-rot{display:inline-grid;justify-items:start;vertical-align:top}
  .storm-home .hero-rot span{grid-area:1/1;white-space:nowrap;font-size:clamp(30px,7vw,78px);font-weight:800;letter-spacing:-.04em;line-height:1.2;padding-bottom:.14em;
    opacity:0;transform:translateY(10px);transition:opacity .55s ease,transform .55s ease;pointer-events:none}
  .storm-home .hero-rot span.in{opacity:1;transform:none}
  @media(prefers-reduced-motion:reduce){.storm-home .hero-rot span{transition:none}}
  /* On small screens the h1 min (42px) is too large for the longer principles to
     stay on one line, so scale the rotating line down a little below the hero. */
  @media(max-width:600px){.storm-home .hero-rot span{font-size:clamp(20px,6vw,30px)}}
  .storm-home .sub{max-width:600px;margin:24px 0 0;color:var(--muted);font-size:18px;line-height:1.62}
  .storm-home .cta{display:flex;gap:14px;margin-top:32px;flex-wrap:wrap}

  /* editor */
  .storm-home .stage{margin:54px 0 0;max-width:880px}
  .storm-home .editor{border:1px solid var(--border);border-radius:16px;overflow:hidden;background:var(--panel);
    box-shadow:0 50px 110px -45px rgba(0,0,0,.85),0 0 0 1px rgba(129,140,248,.06),0 0 80px -30px rgba(129,140,248,.22)}
  .storm-home .ebar{display:flex;align-items:center;gap:8px;height:46px;padding:0 16px;border-bottom:1px solid var(--border-soft);background:rgba(255,255,255,.014)}
  .storm-home .dot{width:11px;height:11px;border-radius:50%}.storm-home .dot.r{background:#ff5f57}.storm-home .dot.y{background:#febc2e}.storm-home .dot.g{background:#28c840}
  .storm-home .fname{margin-left:10px;font-family:var(--mono);font-size:12.5px;color:var(--faint)}
  .storm-home .langtag{margin-left:auto;font-family:var(--mono);font-size:11px;color:var(--faint);letter-spacing:.05em}
  .storm-home .sqlbtn{margin-left:auto;display:inline-flex;align-items:center;gap:7px;font-family:var(--mono);font-size:11.5px;
    color:var(--accent);border:1px solid rgba(129,140,248,.3);border-radius:7px;padding:4px 10px;cursor:pointer;transition:.16s;user-select:none}
  .storm-home .sqlbtn:hover{background:rgba(129,140,248,.12);border-color:rgba(129,140,248,.5)}
  .storm-home .sqlbtn.on{background:rgba(129,140,248,.16);color:#aab2ff}
  .storm-home .sqlbtn .ico{width:13px;height:13px;opacity:.9}
  .storm-home .sqlconsole{display:none;border-top:1px solid var(--border-soft);background:var(--statusbg)}
  .storm-home .editor.show-sql .sqlconsole{display:block}
  .storm-home #sqlpanel{margin:0;padding:14px 18px 18px;font-family:var(--mono);font-size:12.5px;line-height:1.75;white-space:pre;overflow-x:auto;color:var(--plain)}
  .storm-home .statusbar .sqllabel{display:none;align-items:center;gap:9px;font-family:var(--mono);font-size:11px;letter-spacing:.12em;text-transform:uppercase;color:var(--faint)}
  .storm-home .statusbar .sqllabel .ar{color:var(--accent)}
  .storm-home .editor.show-sql .statusbar #status,
  .storm-home .editor.show-sql .statusbar .right{display:none}
  .storm-home .editor.show-sql .statusbar .sqllabel{display:inline-flex}
  .storm-home .sqlk{color:var(--accent)}
  .storm-home .sqlq{color:var(--num)}
  .storm-home .sqlc{color:var(--com)}

  .storm-home .codearea{display:flex;min-height:460px;background:linear-gradient(180deg,var(--panel),var(--panel-2))}
  .storm-home .gutter{padding:22px 0;width:48px;text-align:right;color:#3b3b46;font-family:var(--mono);font-size:13px;
    line-height:26px;user-select:none;border-right:1px solid var(--border-soft);flex:none}
  .storm-home .gutter div{padding-right:15px}
  .storm-home #code{margin:0;padding:22px 24px;font-family:var(--mono);font-size:14px;line-height:26px;white-space:pre;overflow-x:auto;flex:1}
  /* Docusaurus styles bare <pre> with a light code-block background (and rounded box) in
     light color mode; neutralize it so the landing editor keeps its dark chrome regardless
     of the active Docusaurus theme. */
  .storm-home pre{background:transparent;border:0;border-radius:0;box-shadow:none}
  .storm-home #benefits{display:none}
  .storm-home .codearea.show-benefits #code,.storm-home .codearea.show-benefits .gutter{display:none}
  .storm-home .codearea.show-benefits #benefits{display:grid;flex:1}
  .storm-home .bgrid{grid-template-columns:repeat(3,1fr);gap:12px;padding:22px 24px;align-content:start}
  .storm-home .bcell{border:1px solid var(--border-soft);border-radius:11px;padding:15px 16px;background:var(--panel-2);opacity:0;transform:translateY(8px);transition:opacity .3s ease,transform .3s ease}
  .storm-home .bcell.in{opacity:1;transform:none}
  .storm-home .bcell .bt{display:block;font-size:13.5px;font-weight:600;letter-spacing:-.01em;color:var(--text);margin-bottom:5px}
  .storm-home .bcell .bd{display:block;font-size:12px;line-height:1.5;color:var(--muted)}
  .storm-home .cursor{display:inline-block;width:8px;height:1.05em;background:var(--accent);vertical-align:text-bottom;
    margin-bottom:1px;animation:storm-blink 1s steps(2,start) infinite;border-radius:1px}
  @keyframes storm-blink{50%{opacity:0}}

  .storm-home .statusbar{display:flex;align-items:center;justify-content:space-between;height:38px;padding:0 18px;
    border-top:1px solid var(--border-soft);background:var(--statusbg);font-family:var(--mono);font-size:12px;color:var(--muted)}
  .storm-home #status{display:inline-flex;align-items:center;gap:9px;opacity:0;transform:translateY(2px);transition:opacity .4s ease,transform .4s ease}
  .storm-home #status.show{opacity:1;transform:none}
  .storm-home #status .ck{width:14px;height:14px;color:var(--green)}
  .storm-home .statusbar .right{color:var(--faint);letter-spacing:.05em}

  .storm-home .scenes{display:flex;gap:8px;margin-top:22px;flex-wrap:wrap}
  .storm-home .scenes .s{font-family:var(--mono);font-size:11.5px;color:var(--faint);border:1px solid var(--border-soft);
    border-radius:999px;padding:5px 13px;transition:.2s;cursor:pointer}
  .storm-home .scenes .s:hover{color:var(--muted);border-color:var(--border)}
  .storm-home .scenes .s.on{color:var(--accent);border-color:rgba(129,140,248,.4);background:rgba(129,140,248,.08)}

  .storm-home .code-k{color:var(--kw)}.storm-home .code-t{color:var(--type)}.storm-home .code-s{color:var(--str)}.storm-home .code-c{color:var(--com)}
  .storm-home .code-f{color:var(--fn)}.storm-home .code-n{color:var(--num)}.storm-home .code-a{color:var(--anno)}.storm-home .code-pl{color:var(--plain)}.storm-home .code-m{color:var(--muted)}

  .storm-home section{padding:92px 0}
  .storm-home .dbstrip{padding:30px 0}
  .storm-home .db-label{text-align:center;font-family:var(--mono);font-size:11px;letter-spacing:.16em;text-transform:uppercase;color:var(--faint);margin-bottom:18px}
  .storm-home .dbs{display:flex;flex-wrap:wrap;gap:10px;justify-content:center}
  .storm-home .dbs span{font-family:var(--mono);font-size:13px;color:var(--muted);background:var(--panel-2);border:1px solid var(--border-soft);border-radius:8px;padding:7px 14px}
  .storm-home .strips{display:flex;flex-wrap:wrap;justify-content:center;align-items:flex-start;gap:34px 64px}
  .storm-home .strip-col{display:flex;flex-direction:column;align-items:center}
  .storm-home .three{display:grid;grid-template-columns:repeat(3,1fr);gap:20px}
  .storm-home .card{border:1px solid var(--border-soft);border-radius:14px;padding:26px;background:var(--panel-2)}
  .storm-home .card h3{margin:0 0 9px;font-size:17px;font-weight:600;letter-spacing:-.01em}
  .storm-home .card p{margin:0;color:var(--muted);font-size:14.5px;line-height:1.65}
  .storm-home .card .ic{width:34px;height:34px;border-radius:9px;display:grid;place-items:center;color:var(--accent);
    background:rgba(129,140,248,.1);border:1px solid rgba(129,140,248,.2);margin-bottom:16px}
  .storm-home .endcta{text-align:center}
  .storm-home .endcta h2{font-size:clamp(32px,5vw,54px);letter-spacing:-.035em;font-weight:800;margin:0 0 18px}
  .storm-home footer{border-top:1px solid var(--border-soft);padding:36px 0;color:var(--faint);font-size:13.5px}
  .storm-home .foot{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:14px}
  .storm-home .foot .links{display:flex;gap:22px;font-family:var(--mono)}.storm-home .foot a{color:var(--muted)}.storm-home .foot a:hover{color:var(--text)}
  @media(max-width:920px){.storm-home .tech-tag{display:none}}
  @media(max-width:760px){.storm-home .three{grid-template-columns:1fr}.storm-home .nav-links a:not(.gh){display:none}.storm-home .bgrid{grid-template-columns:repeat(2,1fr)}}
`;

const BODY = `
<nav><div class="wrap nav">
  <div class="brand"><img class="logo" src="/img/storm-light.png" alt="Storm" /><span>ST<b>/ORM</b></span><span class="tech-tag">Kotlin 2.0–2.4 · Java 21+ · Apache 2.0</span></div>
  <div class="nav-links">
    <a href="/docs/">Docs</a>
    <a class="gh" href="https://github.com/storm-orm/storm-framework">GitHub</a>
    <a href="/docs/getting-started" class="btn primary" style="height:36px">Get started</a>
  </div>
</div></nav>

<header><div class="wrap">
  <h1>Radically Simple.<br><span class="hero-rot" id="valuesRotator">
    <span class="grad in">Predictability over magic.</span>
    <span class="grad">Stateless over sessions.</span>
    <span class="grad">Immutable over managed.</span>
    <span class="grad">Explicit over surprises.</span>
    <span class="grad">Intent over ceremony.</span>
  </span></h1>
  <p class="sub" style="max-width:940px">A clear mapping between your data model and database keeps entities reusable and repositories easy to extend. Your persistence layer remains small, expressive, and fully capable as your application grows.</p>

  <div class="stage">
    <div class="editor">
      <div class="ebar">
        <span class="dot r"></span><span class="dot y"></span><span class="dot g"></span>
        <span class="fname" id="fname">Entities.kt</span>
        <span class="sqlbtn" id="sqlbtn"><svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5"/><path d="M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"/></svg><span id="sqlbtntext">Show SQL</span></span>
      </div>
      <div class="codearea">
        <div class="gutter" id="gutter"></div>
        <pre id="code"></pre>
        <div id="benefits" class="bgrid"></div>
      </div>
      <div class="statusbar">
        <span id="status"><svg class="ck" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M20 6 9 17l-5-5"/></svg><span id="statustext"></span></span>
        <span class="sqllabel"><span class="ar">↳</span> generated sql</span>
      </div>
      <div class="sqlconsole">
        <pre id="sqlpanel"></pre>
      </div>
    </div>
    <div class="scenes" id="scenes"></div>
  </div>
</div></header>

<section style="padding-top:48px;padding-bottom:30px"><div class="wrap">
  <div class="three">
    <div class="card">
      <div class="ic"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 12h18M3 6h18M3 18h18"/></svg></div>
      <h3>Direct database control</h3>
      <p>Every query is explicit and predictable. Nested predicates and entity graphs compile to a single efficient query, eliminating hidden queries and N+1.</p>
    </div>
    <div class="card">
      <div class="ic"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 6 9 17l-5-5"/></svg></div>
      <h3>Stateless and Immutable</h3>
      <p>Immutable data classes. No proxies, no flush, no hidden state. What you see is what you get. Safe to cache, share, and serialize across every layer.</p>
    </div>
    <div class="card">
      <div class="ic"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2 4 6v6c0 5 3.5 8 8 10 4.5-2 8-5 8-10V6z"/></svg></div>
      <h3>Type-safe and Injection-safe</h3>
      <p>Compile-time detection of column and type errors. Automatic conversion of interpolated values into bind parameters.</p>
    </div>
  </div>
</div></section>

<div class="wrap dbstrip">
  <div class="strips">
    <div class="strip-col">
      <div class="db-label">Optimized for</div>
      <div class="dbs">
        <span>PostgreSQL</span><span>MySQL</span><span>MariaDB</span><span>Oracle</span><span>SQL&nbsp;Server</span><span>SQLite</span><span>H2</span>
      </div>
    </div>
    <div class="strip-col">
      <div class="db-label">Integrates with</div>
      <div class="dbs">
        <span>Ktor</span><span>Spring&nbsp;Boot&nbsp;3.x</span><span>Spring&nbsp;Boot&nbsp;4.x</span>
      </div>
    </div>
  </div>
</div>

<section class="endcta" style="padding-top:64px"><div class="wrap">
  <h2>Write code worth reading.</h2>
  <p class="sub" style="margin:0 auto 30px;text-align:center;max-width:940px">Concise entities and one-line queries keep you productive. Immutable records simplify your architecture by letting the same types flow through your application layers. Storm is built for engineers who care about beautiful code.</p>
  <div class="cta" style="justify-content:center;margin-top:56px">
    <a href="/docs/getting-started" class="btn primary">Get started →</a>
    <a href="https://github.com/storm-orm/storm-framework" class="btn">Star on GitHub</a>
  </div>
</div></section>

<footer><div class="wrap foot">
  <div class="brand"><img class="logo" src="/img/storm-light.png" alt="Storm" /></div>
  <div class="links"><a href="/">orm.st</a><a href="https://github.com/storm-orm/storm-framework">GitHub</a><a href="https://central.sonatype.com/namespace/st.orm">Maven Central</a></div>
</div></footer>
`;

export default function Home() {
  useEffect(() => {
    const K=(x)=>({x,c:'code-k'}),T=(x)=>({x,c:'code-t'}),S=(x)=>({x,c:'code-s'}),C=(x)=>({x,c:'code-c'}),
          F=(x)=>({x,c:'code-f'}),N=(x)=>({x,c:'code-n'}),A=(x)=>({x,c:'code-a'}),P=(x)=>({x,c:'code-pl'});

    const SCENES=[
      { name:'1 · entities', file:'Entities.kt',
        caption:"the most concise way to define your entities",
        code:[ C("// Records mirror your schema.\n"),
          K("data class "),T("City"),P("(\n"),
          P("    "),A("@PK"),P(" "),K("val "),P("id: "),T("Int"),P(" = "),N("0"),P(",\n"),
          P("    "),K("val "),P("name: "),T("String"),P(",\n"),
          P("    "),K("val "),P("population: "),T("Int"),P(",\n"),
          P("    "),K("val "),P("country: "),T("String"),P(",\n"),
          P(") : "),T("Entity"),P("<"),T("Int"),P(">\n\n"),
          C("// Foreign entities are available in queries and results.\n"),
          K("data class "),T("User"),P("(\n"),
          P("    "),A("@PK"),P(" "),K("val "),P("id: "),T("Int"),P(" = "),N("0"),P(",\n"),
          P("    "),K("val "),P("email: "),T("String"),P(",\n"),
          P("    "),K("val "),P("name: "),T("String"),P(",\n"),
          P("    "),A("@FK"),P(" "),K("val "),P("city: "),T("City"),P(",\n"),
          P(") : "),T("Entity"),P("<"),T("Int"),P(">") ] },

      { name:'2 · query', file:'UserService.kt',
        caption:"one-line queries to get all the data you need · no N+1",
        code:[ C("// A user's city is loaded in the same query.\n"),
          K("val "),P("user = userRepository."),F("getById"),P("("),N("1"),P(")\n"),
          K("val "),P("cityName = user.city.name"),P("   "),C('// already loaded — no N+1, no lazy-init\n\n'),
          C("// Filter across the graph, fully type-safe using the static metamodel.\n"),
          K("val "),P("users = userRepository."),F("findAll"),P("(User_.city.name "),K("eq "),S('"Sunnyvale"'),P(")") ] },

      { name:'3 · repository', file:'UserRepository.kt',
        caption:"your own type-safe queries · CRUD inherited",
        code:[ C("// Custom return types are just records — define them in-place.\n"),
          K("data class "),T("CityCount"),P("("),K("val "),P("city: "),T("City"),P(", "),K("val "),P("count: "),T("Long"),P(")\n\n"),
          C("// Extend EntityRepository — all CRUD comes for free.\n"),
          K("interface "),T("UserRepository"),P(" : "),T("EntityRepository"),P("<"),T("User"),P(", "),T("Int"),P("> {\n"),
          P("    "),K("fun "),F("findByCity"),P("(city: "),T("City"),P(") = "),F("findAll"),P("(User_.city "),K("eq "),P("city)\n\n"),
          C("    // Query builder with SQL templates for the aggregate.\n"),
          P("    "),K("fun "),F("usersPerCity"),P("(country: "),T("String"),P(") =\n"),
          P("        "),F("select"),P("("),T("CityCount"),P("::"),K("class"),P(") { "),S('"${City::class}, COUNT(*)"'),P(" }\n"),
          P("            ."),F("where"),P("(User_.city.country "),K("eq "),P("country)\n"),
          P("            ."),F("groupBy"),P("(User_.city)\n"),
          P("            .resultList\n"),
          P("}") ] },

      { name:'4 · transactions', file:'Transactions.kt',
        caption:"full control with programmatic tx · Spring's declarative tx also supported",
        code:[ C("// Writes are explicit. One transaction.\n"),
          F("transaction"),P(" {\n"),
          P("    "),K("val "),P("city = orm "),K("insert "),T("City"),P("(name = "),S('"Sunnyvale"'),P(", population = "),N("161_884"),P(", country = "),S('"US"'),P(")\n"),
          P("    orm "),K("insert "),T("User"),P("(email = "),S('"bob@acme.io"'),P(", name = "),S('"Bob"'),P(", city = city)\n"),
          P("}\n"),
          P("\n"),
          C("// Full control when you need it: propagation, isolation, timeout — plus post-tx hooks.\n"),
          F("transaction"),P("(propagation = "),T("REQUIRES_NEW"),P(", isolation = "),T("REPEATABLE_READ"),P(", timeoutSeconds = "),N("5"),P(") {\n"),
          P("    "),K("val "),P("city = orm "),K("insert "),T("City"),P("(name = "),S('"San Jose"'),P(", population = "),N("1_013_240"),P(", country = "),S('"US"'),P(")\n"),
          P("    "),K("val "),P("user = orm "),K("insert "),T("User"),P("(email = "),S('"alice@acme.io"'),P(", name = "),S('"Alice"'),P(", city = city)\n"),
          P("\n"),
          P("    "),F("onCommit"),P(" { events."),F("publish"),P("("),T("UserCreated"),P("(user)) }"),P("   "),C("// runs only after successful commit\n"),
          P("}") ] },

      { name:'5 · sql', file:'UserService.kt',
        caption:"full SQL when you want it — never locked in",
        code:[ C("// Need full control of SQL? Plain SQL works — rows map to any data class.\n"),
          K("data class "),T("RankedCity"),P("("),K("val "),P("name: "),T("String"),P(", "),K("val "),P("population: "),T("Int"),P(", "),K("val "),P("rank: "),T("Long"),P(")\n\n"),
          K("val "),P("ranked = orm."),F("query"),P(" { "),S('"""'),P("\n"),
          P("    "),K("SELECT "),P("name, population, RANK() "),K("OVER"),P(" ("),K("ORDER BY "),P("population "),K("DESC"),P(")\n"),
          P("    "),K("FROM "),P("city\n"),
          P("    "),K("WHERE "),P("country = "),T("$country"),P("   "),C("-- bind variable\n"),
          S('"""'),P(" }."),F("resultList"),P("<"),T("RankedCity"),P(">()\n\n"),
          C("// Or use the powerful template engine behind the ORM.\n"),
          K("val "),P("users = orm."),F("query"),P(" { "),S('"""'),P("\n"),
          P("    "),K("SELECT "),T("${User::class}"),P("\n"),
          P("    "),K("FROM "),T("${User::class}"),P("\n"),
          P("    "),K("WHERE "),T("${User_.city.name}"),P(" = "),T("$city"),P("   "),C("-- bind variable\n"),
          S('"""'),P(" }."),F("resultList"),P("<"),T("User"),P(">()") ] },

      { name:'6 · principles', file:'Core Principles',
        caption:"the core principles",
        grid:[
          { t:"Enjoyable", d:"Write code that's a pleasure to read and maintain." },
          { t:"Minimalist", d:"Concise entities and one-line queries. No ceremony." },
          { t:"Modern", d:"Keeps pace with modern language features, e.g. coroutine-aware tx." },
          { t:"Predictable", d:"All database calls are explicit. No surprises like hidden N+1 queries." },
          { t:"Type-Safe", d:"Columns and types verified at compile time." },
          { t:"Secure", d:"Interpolated values become bind variables, preventing SQL injection." },
          { t:"Immutable", d:"Plain data classes and records, safe to share." },
          { t:"Stateless", d:"No session, flush, or transaction-bound proxies." },
          { t:"Portable", d:"Database specifics offered in a portable way, across all major databases." },
          { t:"Flexible", d:"From a simple DSL to full SQL templates when you need them." },
          { t:"Fast", d:"Optimized for performance and memory, e.g. generated code instead of reflection." },
          { t:"Efficient", d:"No heavyweight runtime, no external dependencies." },
        ] },
    ];

    // Generated SQL per scene (index-aligned with SCENES). Shown via the "Show SQL" toggle.
    const SQL=[
      null, // entities — no query, so the Show SQL button is hidden for this scene

      '<span class="sqlc">-- getById(1) — joins the city graph, no N+1</span>\n'+
      '<span class="sqlk">SELECT</span> u.id, u.email, u.name, c.id, c.name, c.population, c.country\n'+
      '<span class="sqlk">FROM</span> "user" u\n'+
      '<span class="sqlk">INNER JOIN</span> city c <span class="sqlk">ON</span> c.id = u.city_id\n'+
      '<span class="sqlk">WHERE</span> u.id = <span class="sqlq">?</span>\n\n'+
      '<span class="sqlc">-- findAll(User_.city.name eq "Sunnyvale")</span>\n'+
      '<span class="sqlk">SELECT</span> u.id, u.email, u.name, c.id, c.name, c.population, c.country\n'+
      '<span class="sqlk">FROM</span> "user" u\n'+
      '<span class="sqlk">INNER JOIN</span> city c <span class="sqlk">ON</span> c.id = u.city_id\n'+
      '<span class="sqlk">WHERE</span> c.name = <span class="sqlq">?</span>',

      '<span class="sqlc">-- findByCity(city)</span>\n'+
      '<span class="sqlk">SELECT</span> u.id, u.email, u.name, c.id, c.name, c.population, c.country\n'+
      '<span class="sqlk">FROM</span> "user" u\n'+
      '<span class="sqlk">INNER JOIN</span> city c <span class="sqlk">ON</span> c.id = u.city_id\n'+
      '<span class="sqlk">WHERE</span> u.city_id = <span class="sqlq">?</span>\n\n'+
      '<span class="sqlc">-- usersPerCity(country)</span>\n'+
      '<span class="sqlk">SELECT</span> c.id, c.name, c.population, c.country, <span class="sqlk">COUNT</span>(*)\n'+
      '<span class="sqlk">FROM</span> "user" u\n'+
      '<span class="sqlk">INNER JOIN</span> city c <span class="sqlk">ON</span> c.id = u.city_id\n'+
      '<span class="sqlk">WHERE</span> c.country = <span class="sqlq">?</span>\n'+
      '<span class="sqlk">GROUP BY</span> u.city_id',

      '<span class="sqlc">-- the second block wraps these two inserts in one configured transaction:</span>\n'+
      '<span class="sqlc">-- transaction(REQUIRES_NEW, REPEATABLE_READ, timeoutSeconds = 5)</span>\n'+
      '<span class="sqlk">SET TRANSACTION ISOLATION LEVEL REPEATABLE READ</span>\n'+
      '<span class="sqlk">BEGIN</span>\n'+
      '<span class="sqlk">INSERT INTO</span> city (name, population, country) <span class="sqlk">VALUES</span> (<span class="sqlq">?</span>, <span class="sqlq">?</span>, <span class="sqlq">?</span>)\n'+
      '<span class="sqlk">INSERT INTO</span> "user" (email, name, city_id) <span class="sqlk">VALUES</span> (<span class="sqlq">?</span>, <span class="sqlq">?</span>, <span class="sqlq">?</span>)\n'+
      '<span class="sqlk">COMMIT</span>\n'+
      '<span class="sqlc">-- onCommit hook runs here, only after COMMIT succeeds</span>',

      '<span class="sqlc">-- plain SQL passes through · $country becomes ?</span>\n'+
      '<span class="sqlk">SELECT</span> name, population, <span class="sqlk">RANK</span>() <span class="sqlk">OVER</span> (<span class="sqlk">ORDER BY</span> population <span class="sqlk">DESC</span>)\n'+
      '<span class="sqlk">FROM</span> city\n'+
      '<span class="sqlk">WHERE</span> country = <span class="sqlq">?</span>\n\n'+
      '<span class="sqlc">-- ${User::class} expands to columns · $city becomes ?</span>\n'+
      '<span class="sqlk">SELECT</span> u.id, u.email, u.name, c.id, c.name, c.population, c.country\n'+
      '<span class="sqlk">FROM</span> "user" u\n'+
      '<span class="sqlk">INNER JOIN</span> city c <span class="sqlk">ON</span> c.id = u.city_id\n'+
      '<span class="sqlk">WHERE</span> c.name = <span class="sqlq">?</span>',
    ];

    const esc=s=>s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
    const codeEl=document.getElementById('code'), gutEl=document.getElementById('gutter'),
          fnameEl=document.getElementById('fname'), scenesEl=document.getElementById('scenes'),
          statusEl=document.getElementById('status'), statusTextEl=document.getElementById('statustext'),
          benefitsEl=document.getElementById('benefits'), codeareaEl=document.querySelector('.storm-home .codearea');
    if(!codeEl) return;

    // "Show SQL" toggle — reveals the generated SQL for the current scene.
    const editorEl=document.querySelector('.storm-home .editor'), sqlBtn=document.getElementById('sqlbtn'),
          sqlBtnText=document.getElementById('sqlbtntext'), sqlPanel=document.getElementById('sqlpanel');
    let showSql=false, curIdx=0;
    function renderSql(){ sqlPanel.innerHTML=SQL[curIdx]||'<span class="sqlc">-- no query</span>'; }
    function onSqlClick(){
      showSql=!showSql;
      editorEl.classList.toggle('show-sql',showSql);
      sqlBtn.classList.toggle('on',showSql);
      sqlBtnText.textContent=showSql?'Hide SQL':'Show SQL';
      if(showSql) renderSql();
    }
    sqlBtn.addEventListener('click',onSqlClick);

    SCENES.forEach((s,i)=>{const d=document.createElement('span');d.className='s';d.textContent=s.name;
      d.addEventListener('click',()=>runFrom(i,true));scenesEl.appendChild(d);});
    const tabs=[...scenesEl.children];

    const fullText=(t)=>t.map(x=>x.x).join('');
    function render(tokens,n,cursor){
      let out='',count=0,done=false;
      for(const tk of tokens){
        if(done)break;
        const len=tk.x.length;
        if(count+len<=n){out+='<span class="'+tk.c+'">'+esc(tk.x)+'</span>';count+=len;}
        else{const rem=n-count;if(rem>0)out+='<span class="'+tk.c+'">'+esc(tk.x.slice(0,rem))+'</span>';done=true;}
      }
      if(cursor)out+='<span class="cursor"></span>';
      return out;
    }
    function setGutter(text){const lines=text.split('\n').length;let g='';for(let i=1;i<=lines;i++)g+='<div>'+i+'</div>';gutEl.innerHTML=g;}

    const reduce=window.matchMedia&&window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let timer=null, gen=0;
    const wait=(ms)=>new Promise(res=>{const g=gen;timer=setTimeout(()=>{if(g===gen)res();},ms);});

    // Length-independent fast write (~500ms) driven by rAF, so a tab click
    // fills the snippet in a fixed time regardless of how long it is.
    function typeFast(tokens,text,duration){
      return new Promise(resolve=>{
        const g=gen, start=performance.now();
        (function frame(now){
          if(g!==gen){resolve();return;}
          const t=Math.min(1,(now-start)/duration);
          codeEl.innerHTML=render(tokens,Math.round(t*text.length),true);
          if(t<1) requestAnimationFrame(frame); else resolve();
        })(performance.now());
      });
    }

    async function play(idx,fast){
      const myGen=gen;
      const sc=SCENES[idx];
      curIdx=idx;
      const hasSql=!!SQL[idx];
      sqlBtn.style.display=hasSql?'':'none';
      sqlBtn.classList.toggle('on',showSql);
      sqlBtnText.textContent=showSql?'Hide SQL':'Show SQL';
      editorEl.classList.toggle('show-sql',hasSql&&showSql);
      if(hasSql&&showSql) renderSql();
      tabs.forEach((t,i)=>t.classList.toggle('on',i===idx));
      fnameEl.textContent=sc.file;
      statusEl.classList.remove('show');

      // Benefits scene — render the core-benefits grid instead of typed code.
      if(sc.grid){
        codeareaEl.classList.add('show-benefits');
        benefitsEl.innerHTML=sc.grid.map(b=>'<div class="bcell"><span class="bt">'+b.t+'</span><span class="bd">'+b.d+'</span></div>').join('');
        const cells=[...benefitsEl.children];
        if(reduce){
          cells.forEach(c=>c.classList.add('in'));
        } else {
          // Reveal the tiles one by one, top-left to bottom-right (DOM order = row-major).
          for(const cell of cells){
            if(myGen!==gen) return;
            cell.classList.add('in');
            await wait(fast?70:120);
          }
        }
        if(myGen!==gen) return;
        statusTextEl.textContent=sc.caption;statusEl.classList.add('show');
        await wait(fast?30000:8000);
        return;
      }
      codeareaEl.classList.remove('show-benefits');

      const text=fullText(sc.code);
      setGutter(text);

      if(reduce){
        codeEl.innerHTML=render(sc.code,text.length,false);
        statusTextEl.textContent=sc.caption;statusEl.classList.add('show');
        return;
      }

      if(fast){
        await typeFast(sc.code,text,500);
      } else {
        for(let n=0;n<=text.length;n++){
          codeEl.innerHTML=render(sc.code,n,true);
          const ch=text[n-1];
          let d=12+Math.random()*22;
          if(ch==='\n')d=120; else if(ch===' ')d=8; else if('(){}.,'.includes(ch))d=46;
          await wait(d);
        }
      }
      if(myGen!==gen) return;
      codeEl.innerHTML=render(sc.code,text.length,false);
      await wait(fast?160:360);
      if(myGen!==gen) return;
      statusTextEl.textContent=sc.caption;statusEl.classList.add('show');
      await wait(fast?30000:4800); // 30s dwell on a clicked scene, else normal auto-advance
    }

    async function runFrom(start,fast){
      const g=++gen; clearTimeout(timer);
      let i=start, first=true;
      while(g===gen){ await play(i, fast&&first); if(g!==gen) return; first=false; i=(i+1)%SCENES.length; }
    }
    runFrom(0);

    // Rotate the "Choose Storm if you value …" principles, one at a time.
    const valuesRotator = document.getElementById('valuesRotator');
    let valuesTimer = null;
    if (valuesRotator) {
      const valueItems = valuesRotator.querySelectorAll('span');
      let valueIndex = 0;
      valuesTimer = setInterval(() => {
        valueItems[valueIndex].classList.remove('in');
        valueIndex = (valueIndex + 1) % valueItems.length;
        valueItems[valueIndex].classList.add('in');
      }, 4500);
    }

    // Stop the loop and undo DOM mutations when the page unmounts.
    return () => {
      gen++;
      clearTimeout(timer);
      clearInterval(valuesTimer);
      if(scenesEl) scenesEl.innerHTML='';
      if(sqlBtn) sqlBtn.removeEventListener('click',onSqlClick);
    };
  }, []);

  return (
    <>
      <Head>
        <html lang="en" />
        <title>Storm — Type-safe ORM for Kotlin & Java 21+</title>
        <meta
          name="description"
          content="Type-safe, SQL-first ORM for Kotlin 2.0+ and Java 21+. Concise entities, one-line queries, immutable records — every query explicit, no proxies, no N+1."
        />
        {/* Open Graph / Twitter: default og:title is just the site name
            ("Storm Framework") and og:description is absent, so set the
            keyword-rich title + description used when the page is shared. */}
        <meta property="og:type" content="website" />
        <meta
          property="og:title"
          content={'Storm — Type-safe ORM for Kotlin & Java 21+'}
        />
        <meta
          property="og:description"
          content="Type-safe, SQL-first ORM for Kotlin 2.0+ and Java 21+. Concise entities, one-line queries, immutable records — every query explicit, no proxies, no N+1."
        />
        <meta
          name="twitter:title"
          content={'Storm — Type-safe ORM for Kotlin & Java 21+'}
        />
        <meta
          name="twitter:description"
          content="Type-safe, SQL-first ORM for Kotlin 2.0+ and Java 21+. Concise entities, one-line queries, immutable records — every query explicit, no proxies, no N+1."
        />
        {/* Structured data so search engines can identify Storm as a
            developer tool for Kotlin/Java and consolidate it with its GitHub
            and Maven Central listings (helps knowledge-graph + rich results). */}
        <script type="application/ld+json">
          {JSON.stringify({
            '@context': 'https://schema.org',
            '@type': 'SoftwareApplication',
            name: 'Storm',
            alternateName: 'Storm ORM',
            applicationCategory: 'DeveloperApplication',
            operatingSystem: 'JVM (Kotlin, Java)',
            description:
              'Storm is a type-safe, SQL-first ORM for Kotlin 2.0+ and Java 21+. Define concise, immutable entities and write one-line queries — nested predicates and entity graphs compile to a single efficient query, eliminating hidden queries and N+1. Drop to full SQL templates whenever you want; never locked in.',
            featureList: [
              'Direct database control — every query explicit, no hidden N+1',
              'Stateless, immutable records — no proxies, no flush, no hidden state',
              'Type-safe and injection-safe — compile-time column and type checks, automatic bind parameters',
              'One-line queries with an optional full SQL template engine',
              'Works with PostgreSQL, MySQL, MariaDB, Oracle, SQL Server, SQLite and H2',
              'Integrates with Spring Boot 3.x/4.x and Ktor',
            ],
            url: 'https://orm.st',
            sameAs: [
              'https://github.com/storm-orm/storm-framework',
              'https://central.sonatype.com/namespace/st.orm',
            ],
            offers: {'@type': 'Offer', price: '0', priceCurrency: 'USD'},
            author: {
              '@type': 'Organization',
              name: 'Storm',
              url: 'https://github.com/storm-orm',
            },
          })}
        </script>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;700&display=swap"
          rel="stylesheet"
        />
      </Head>
      <style dangerouslySetInnerHTML={{__html: CSS}} />
      <div className="storm-home" dangerouslySetInnerHTML={{__html: BODY}} />
    </>
  );
}
