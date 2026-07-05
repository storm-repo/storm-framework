// Shared look and building blocks for the tutorial pages under `/tutorials/*`.
//
// The palette, fonts and editor chrome are lifted from the landing page
// (src/pages/index.js) so tutorials read as subpages of the front page rather
// than as docs pages. Pages compose: <Head> + <style>{TUT_CSS}</style> +
// navHtml()/FOOT_HTML + editor() blocks, and call wireSqlToggles() in a
// useEffect to activate the per-block "Show SQL" consoles.

import React, {useEffect} from 'react';
import Head from '@docusaurus/Head';

const esc = (s) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

// Token helpers for hand-highlighted code, same classes/colors as the landing
// editor: K keyword, T type, S string, C comment, F function, N number,
// A annotation, P plain, M muted.
export const K = (x) => `<span class="code-k">${esc(x)}</span>`;
export const T = (x) => `<span class="code-t">${esc(x)}</span>`;
export const S = (x) => `<span class="code-s">${esc(x)}</span>`;
export const C = (x) => `<span class="code-c">${esc(x)}</span>`;
export const F = (x) => `<span class="code-f">${esc(x)}</span>`;
export const N = (x) => `<span class="code-n">${esc(x)}</span>`;
export const A = (x) => `<span class="code-a">${esc(x)}</span>`;
export const P = (x) => `<span class="code-pl">${esc(x)}</span>`;
export const M = (x) => `<span class="code-m">${esc(x)}</span>`;

// SQL console tokens, same classes as the landing SQL panel.
export const QK = (x) => `<span class="sqlk">${esc(x)}</span>`;
export const QQ = (x) => `<span class="sqlq">${esc(x)}</span>`;
export const QC = (x) => `<span class="sqlc">${esc(x)}</span>`;

// An editor-chrome code block. `code` and `sql` are pre-highlighted HTML built
// with the token helpers above. When `sql` is present the block gets a
// "Show SQL" toggle that reveals the generated SQL, like the landing editor.
export function editor({file, tag, code, sql}) {
  // Newlines only occur in the escaped text tokens, never inside tag markup,
  // so the line count can be taken from the HTML string directly.
  const lines = code.split('\n').length;
  let gutter = '';
  for (let i = 1; i <= lines; i++) gutter += `<div>${i}</div>`;
  const sqlBtn = sql
    ? `<span class="sqlbtn"><svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5"/><path d="M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"/></svg><span class="sqlbtntext">Show SQL</span></span>`
    : '';
  const sqlConsole = sql
    ? `<div class="sqlhead"><span class="ar">↳</span> generated sql</div><div class="sqlconsole"><pre class="sqlpanel">${sql}</pre></div>`
    : '';
  return `
<div class="editor">
  <div class="ebar">
    <span class="dot r"></span><span class="dot y"></span><span class="dot g"></span>
    <span class="fname">${esc(file)}</span>
    ${tag ? `<span class="langtag">${esc(tag)}</span>` : '<span class="langtag"></span>'}
    ${sqlBtn}
  </div>
  <div class="codearea">
    <div class="gutter">${gutter}</div>
    <pre class="code">${code}</pre>
  </div>
  ${sqlConsole}
</div>`;
}

// A compact two-pane "at a glance" comparison for one-liner contrasts. Only
// use where both snippets are genuinely short; full examples stay sequential.
export function glance({left, right}) {
  return `
<div class="glance">
  <div class="gpane"><div class="glabel">${esc(left.label)}</div><pre>${left.code}</pre></div>
  <div class="gpane storm"><div class="glabel">${esc(right.label)}</div><pre>${right.code}</pre></div>
</div>`;
}

// Wires up every "Show SQL" button rendered by editor(). Returns a cleanup
// function for the calling useEffect.
export function wireSqlToggles() {
  const handlers = [];
  document.querySelectorAll('.storm-tut .editor').forEach((ed) => {
    const btn = ed.querySelector('.sqlbtn');
    if (!btn) return;
    const txt = btn.querySelector('.sqlbtntext');
    const onClick = () => {
      const on = ed.classList.toggle('show-sql');
      btn.classList.toggle('on', on);
      if (txt) txt.textContent = on ? 'Hide SQL' : 'Show SQL';
    };
    btn.addEventListener('click', onClick);
    handlers.push([btn, onClick]);
  });
  return () => handlers.forEach(([btn, fn]) => btn.removeEventListener('click', fn));
}

// Standard head + shell for a tutorial article page. Keeps the per-page files
// down to content: pages pass the article body (built with editor() and the
// token helpers) and this component renders the chrome around it.
export function TutorialPage({title, description, slug, body}) {
  useEffect(() => wireSqlToggles(), []);
  const url = `https://orm.st/tutorials/${slug}`;
  return (
    <>
      <Head>
        <html lang="en" />
        <title>{`${title} · Storm Tutorials`}</title>
        <meta name="description" content={description} />
        <link rel="canonical" href={url} />
        <meta property="og:type" content="article" />
        <meta property="og:title" content={title} />
        <meta property="og:description" content={description} />
        <meta name="twitter:title" content={title} />
        <meta name="twitter:description" content={description} />
        <script type="application/ld+json">
          {JSON.stringify({
            '@context': 'https://schema.org',
            '@type': 'TechArticle',
            headline: title,
            description,
            url,
            proficiencyLevel: 'Beginner',
            author: {'@type': 'Organization', name: 'Storm', url: 'https://github.com/storm-orm'},
          })}
        </script>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;700&display=swap"
          rel="stylesheet"
        />
      </Head>
      <style dangerouslySetInnerHTML={{__html: TUT_CSS}} />
      <div className="storm-tut" dangerouslySetInnerHTML={{__html: body}} />
    </>
  );
}

export const navHtml = (active) => `
<nav><div class="wrap nav">
  <div class="brand"><a class="bhome" href="/"><img class="logo" src="/img/storm-light.png" alt="Storm" /><span>ST<b>/ORM</b></span></a><span class="tech-tag">Kotlin 2.0–2.4 · Java 21+ · Apache 2.0</span></div>
  <div class="nav-links">
    <a href="/tutorials/"${active === 'tutorials' ? ' class="on"' : ''}>Tutorials</a>
    <a href="/examples/"${active === 'examples' ? ' class="on"' : ''}>Examples</a>
    <a href="/blog/"${active === 'blog' ? ' class="on"' : ''}>Blog</a>
    <a href="/docs/"${active === 'docs' ? ' class="on"' : ''}>Docs</a>
    <a class="gh" href="https://github.com/orgs/storm-orm/repositories">GitHub</a>
    <a href="/docs/getting-started" class="btn primary" style="height:36px">Get started</a>
  </div>
</div></nav>`;

export const FOOT_HTML = `
<footer><div class="wrap foot">
  <div class="brand"><img class="logo" src="/img/storm-light.png" alt="Storm" /></div>
  <div class="links"><a href="/">orm.st</a><a href="/tutorials/">Tutorials</a><a href="/examples/">Examples</a><a href="/blog/">Blog</a><a href="https://github.com/storm-orm/storm-framework">GitHub</a><a href="https://central.sonatype.com/namespace/st.orm">Maven Central</a></div>
</div></footer>`;

export const TUT_CSS = `
  :root{
    --bg:#070709; --panel:#0f0f14; --panel-2:#0b0b0f; --statusbg:#08080b;
    --border:#20202a; --border-soft:#17171f;
    --text:#eaeaf0; --muted:#8a8a96; --faint:#565662; --body:#b7b8c2;
    --accent:#818cf8; --accent-2:#a78bfa; --green:#5eead4;
    --kw:#c4b5fd; --type:#7dd3fc; --str:#86efac; --com:#5a616e; --fn:#f0abfc; --num:#fcd34d; --anno:#fbbf24; --plain:#cfd0d8;
    --mono:"JetBrains Mono",ui-monospace,SFMono-Regular,Menlo,monospace;
    --sans:"Inter",system-ui,-apple-system,Segoe UI,Roboto,sans-serif;
  }
  html{background:var(--bg)}
  body{margin:0;background:var(--bg);color:var(--text);font-family:var(--sans);-webkit-font-smoothing:antialiased;
    background-image:radial-gradient(1100px 540px at 18% -10%,rgba(129,140,248,.14),transparent 62%);}
  .storm-tut a{color:inherit;text-decoration:none}
  .storm-tut *{box-sizing:border-box}
  .storm-tut .wrap{max-width:1120px;margin:0 auto;padding:0 24px}

  .storm-tut nav{position:sticky;top:0;z-index:20;backdrop-filter:blur(12px);background:rgba(7,7,9,.7);border-bottom:1px solid var(--border-soft)}
  .storm-tut .nav{display:flex;align-items:center;justify-content:space-between;height:62px}
  .storm-tut .brand{display:flex;align-items:center;gap:9px;font-weight:600;letter-spacing:-.01em}
  .storm-tut .brand .bhome{display:flex;align-items:center;gap:9px}
  .storm-tut .logo{height:26px;width:auto;display:block;position:relative;top:-2px;filter:drop-shadow(0 0 10px rgba(129,140,248,.35))}
  .storm-tut .brand b{font-family:var(--mono);font-weight:700}
  .storm-tut .tech-tag{font-family:var(--mono);font-size:11px;color:var(--faint);letter-spacing:.02em;border-left:1px solid var(--border);padding-left:12px}
  .storm-tut .nav-links{display:flex;align-items:center;gap:24px;font-size:14px;color:var(--muted)}
  .storm-tut .nav-links a:hover{color:var(--text)}
  .storm-tut .nav-links a.on{color:var(--text)}
  .storm-tut .btn{display:inline-flex;align-items:center;gap:8px;height:40px;padding:0 18px;border-radius:9px;font-size:14.5px;
    font-weight:550;border:1px solid var(--border);background:var(--panel);transition:.16s;cursor:pointer}
  .storm-tut .btn:hover{border-color:#34343d;transform:translateY(-1px)}
  .storm-tut .btn.primary{background:var(--accent);color:#0a0a0f;border-color:var(--accent);font-weight:600}
  .storm-tut .btn.primary:hover{background:#9aa3ff}
  .storm-tut .grad{background:linear-gradient(100deg,#a78bfa,#818cf8 50%,#7dd3fc);-webkit-background-clip:text;background-clip:text;color:transparent}

  /* article shell */
  .storm-tut .art{max-width:860px;margin:0 auto;padding:54px 24px 48px}
  .storm-tut .crumbs{font-family:var(--mono);font-size:12px;color:var(--faint);letter-spacing:.04em}
  .storm-tut .crumbs a:hover{color:var(--muted)}
  .storm-tut .crumbs .sep{margin:0 9px;color:#2c2c36}
  .storm-tut h1{font-size:clamp(34px,5vw,52px);line-height:1.05;letter-spacing:-.035em;font-weight:800;margin:18px 0 0}
  .storm-tut .dek{color:var(--muted);font-size:17.5px;line-height:1.66;margin:20px 0 0;max-width:700px}
  .storm-tut .meta{display:flex;gap:8px;margin-top:24px;flex-wrap:wrap}
  .storm-tut .meta span{font-family:var(--mono);font-size:11.5px;color:var(--faint);border:1px solid var(--border-soft);border-radius:999px;padding:5px 13px}
  .storm-tut h2{font-size:25px;letter-spacing:-.02em;font-weight:700;margin:62px 0 0}
  .storm-tut h2 .hno{font-family:var(--mono);font-size:14px;font-weight:500;color:var(--accent);margin-right:12px}
  .storm-tut p{color:var(--body);font-size:15.5px;line-height:1.75;margin:16px 0 0}
  .storm-tut p b,.storm-tut li b{color:var(--text);font-weight:600}
  .storm-tut ul{color:var(--body);font-size:15.5px;line-height:1.75;margin:14px 0 0;padding-left:22px}
  .storm-tut li{margin-top:6px}
  .storm-tut li::marker{color:var(--faint)}
  .storm-tut p code,.storm-tut li code,.storm-tut td code{font-family:var(--mono);font-size:.86em;background:var(--panel);border:1px solid var(--border-soft);border-radius:6px;padding:1.5px 6px;color:var(--plain);white-space:nowrap}
  .storm-tut .art a.tlink{color:var(--accent)}
  .storm-tut .art a.tlink:hover{text-decoration:underline}

  /* editor chrome (landing look, sized for articles) */
  .storm-tut .editor{margin:26px 0 0;border:1px solid var(--border);border-radius:14px;overflow:hidden;background:var(--panel);
    box-shadow:0 40px 90px -50px rgba(0,0,0,.8),0 0 0 1px rgba(129,140,248,.05)}
  .storm-tut .ebar{display:flex;align-items:center;gap:8px;height:44px;padding:0 16px;border-bottom:1px solid var(--border-soft);background:rgba(255,255,255,.014)}
  .storm-tut .dot{width:11px;height:11px;border-radius:50%;flex:none}.storm-tut .dot.r{background:#ff5f57}.storm-tut .dot.y{background:#febc2e}.storm-tut .dot.g{background:#28c840}
  .storm-tut .fname{margin-left:10px;font-family:var(--mono);font-size:12.5px;color:var(--faint)}
  .storm-tut .langtag{margin-left:auto;font-family:var(--mono);font-size:11px;color:var(--faint);letter-spacing:.05em}
  .storm-tut .sqlbtn{margin-left:14px;display:inline-flex;align-items:center;gap:7px;font-family:var(--mono);font-size:11.5px;
    color:var(--accent);border:1px solid rgba(129,140,248,.3);border-radius:7px;padding:4px 10px;cursor:pointer;transition:.16s;user-select:none}
  .storm-tut .sqlbtn:hover{background:rgba(129,140,248,.12);border-color:rgba(129,140,248,.5)}
  .storm-tut .sqlbtn.on{background:rgba(129,140,248,.16);color:#aab2ff}
  .storm-tut .sqlbtn .ico{width:13px;height:13px;opacity:.9}
  .storm-tut .codearea{display:flex;background:linear-gradient(180deg,var(--panel),var(--panel-2))}
  .storm-tut .gutter{padding:18px 0;width:46px;text-align:right;color:#3b3b46;font-family:var(--mono);font-size:12px;
    line-height:24px;user-select:none;border-right:1px solid var(--border-soft);flex:none}
  .storm-tut .gutter div{padding-right:14px}
  .storm-tut .code{margin:0;padding:18px 22px;font-family:var(--mono);font-size:13.5px;line-height:24px;white-space:pre;overflow-x:auto;flex:1}
  .storm-tut pre{background:transparent;border:0;border-radius:0;box-shadow:none}
  .storm-tut .code-k{color:var(--kw)}.storm-tut .code-t{color:var(--type)}.storm-tut .code-s{color:var(--str)}.storm-tut .code-c{color:var(--com)}
  .storm-tut .code-f{color:var(--fn)}.storm-tut .code-n{color:var(--num)}.storm-tut .code-a{color:var(--anno)}.storm-tut .code-pl{color:var(--plain)}.storm-tut .code-m{color:var(--muted)}

  /* per-block SQL console */
  .storm-tut .sqlhead{display:none;align-items:center;gap:9px;height:34px;padding:0 18px;border-top:1px solid var(--border-soft);background:var(--statusbg);
    font-family:var(--mono);font-size:11px;letter-spacing:.12em;text-transform:uppercase;color:var(--faint)}
  .storm-tut .sqlhead .ar{color:var(--accent)}
  .storm-tut .sqlconsole{display:none;border-top:1px solid var(--border-soft);background:var(--statusbg)}
  .storm-tut .editor.show-sql .sqlconsole,.storm-tut .editor.show-sql .sqlhead{display:flex}
  .storm-tut .sqlpanel{margin:0;padding:14px 18px 18px;font-family:var(--mono);font-size:12.5px;line-height:1.75;white-space:pre;overflow-x:auto;color:var(--plain);flex:1}
  .storm-tut .sqlk{color:var(--accent)}.storm-tut .sqlq{color:var(--num)}.storm-tut .sqlc{color:var(--com)}

  /* at-a-glance two-pane strip */
  .storm-tut .glance{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin:26px 0 0}
  @media(max-width:720px){.storm-tut .glance{grid-template-columns:1fr}}
  .storm-tut .gpane{border:1px solid var(--border-soft);border-radius:12px;background:var(--panel-2);overflow:hidden}
  .storm-tut .gpane .glabel{display:flex;align-items:center;height:34px;padding:0 14px;border-bottom:1px solid var(--border-soft);
    font-family:var(--mono);font-size:11px;letter-spacing:.08em;text-transform:uppercase;color:var(--faint)}
  .storm-tut .gpane.storm .glabel{color:var(--accent)}
  .storm-tut .gpane pre{margin:0;padding:14px 16px;font-family:var(--mono);font-size:13px;line-height:22px;white-space:pre;overflow-x:auto}

  /* callout */
  .storm-tut .note{margin:26px 0 0;border:1px solid var(--border-soft);border-left:3px solid var(--accent);border-radius:10px;
    background:var(--panel-2);padding:15px 18px;font-size:14.5px;line-height:1.7;color:var(--body)}
  .storm-tut .note a{color:var(--accent)}
  .storm-tut .note a:hover{text-decoration:underline}

  /* comparison table */
  .storm-tut table.cmp{width:100%;border-collapse:collapse;margin:26px 0 0;font-size:14px}
  .storm-tut .cmp th{font-family:var(--mono);font-size:11px;letter-spacing:.12em;text-transform:uppercase;color:var(--faint);
    text-align:left;padding:10px 14px;border-bottom:1px solid var(--border)}
  .storm-tut .cmp td{padding:13px 14px;border-bottom:1px solid var(--border-soft);color:var(--body);line-height:1.6;vertical-align:top}
  .storm-tut .cmp td:first-child{color:var(--text);font-weight:550}

  /* cta + doc refs */
  .storm-tut .cta{display:flex;gap:14px;margin-top:36px;flex-wrap:wrap}
  .storm-tut .refs{display:flex;gap:8px;margin-top:18px;flex-wrap:wrap}
  .storm-tut .refs a{font-family:var(--mono);font-size:12px;color:var(--muted);border:1px solid var(--border-soft);border-radius:999px;padding:6px 14px;transition:.16s}
  .storm-tut .refs a:hover{color:var(--accent);border-color:rgba(129,140,248,.4)}

  /* tutorial hub */
  .storm-tut .tuthero{max-width:1080px;margin:0 auto;padding:64px 24px 6px}
  .storm-tut .tuthero .sub{color:var(--muted);font-size:17.5px;line-height:1.66;margin:20px 0 0;max-width:700px}
  .storm-tut .catnav{display:flex;gap:10px;margin-top:28px;flex-wrap:wrap}
  .storm-tut .catnav a{font-family:var(--mono);font-size:12.5px;color:#b1b5da;border:1px solid rgba(129,140,248,.20);border-radius:999px;padding:8px 16px;transition:.16s;background:rgba(129,140,248,.07);cursor:pointer}
  .storm-tut .catnav a:hover{color:#e9eaf7;border-color:rgba(129,140,248,.45);background:rgba(129,140,248,.13)}
  .storm-tut .catnav a.on{color:#0a0a0f;background:var(--accent);border-color:var(--accent);font-weight:600}
  .storm-tut .catnav a b{color:rgba(178,182,220,.55);font-weight:500;margin-left:7px}
  .storm-tut .catnav a.on b{color:rgba(10,10,15,.55)}
  .storm-tut .shead{max-width:1080px;margin:0 auto;padding:52px 24px 0;font-family:var(--mono);font-size:12px;letter-spacing:.16em;text-transform:uppercase;color:var(--text);scroll-margin-top:86px}
  .storm-tut .shead .mark{color:var(--accent);margin-right:10px}
  .storm-tut .shead .sdesc{display:block;margin-top:9px;font-family:var(--sans);font-size:14.5px;letter-spacing:0;text-transform:none;color:var(--muted);line-height:1.6}
  .storm-tut .cards{max-width:1080px;margin:0 auto;padding:20px 24px 6px;display:grid;gap:14px;grid-template-columns:repeat(2,minmax(0,1fr))}
  @media(max-width:840px){.storm-tut .cards{grid-template-columns:1fr}}
  .storm-tut .tcard{display:block;border:1px solid var(--border-soft);border-radius:14px;padding:20px 22px;background:var(--panel-2);transition:.16s}
  .storm-tut .tcard:hover{border-color:rgba(129,140,248,.45);transform:translateY(-1px)}
  .storm-tut .tcard .tt{font-size:16.5px;font-weight:650;letter-spacing:-.01em}
  .storm-tut .tcard .tt .arrow{color:var(--accent);margin-left:8px}
  .storm-tut .tcard .td{color:var(--muted);font-size:13.5px;line-height:1.62;margin-top:8px}
  .storm-tut .tcard .tm{display:flex;gap:8px;margin-top:14px;flex-wrap:wrap}
  .storm-tut .tcard .tm span{font-family:var(--mono);font-size:11px;color:var(--faint);border:1px solid var(--border-soft);border-radius:999px;padding:4px 11px}
  .storm-tut .soon{max-width:1080px;margin:0 auto;padding:30px 24px 70px}
  .storm-tut .soon .lbl{font-family:var(--mono);font-size:11px;letter-spacing:.16em;text-transform:uppercase;color:var(--faint)}
  .storm-tut .soon .chips{display:flex;gap:8px;margin-top:14px;flex-wrap:wrap}
  .storm-tut .soon .chips span{font-family:var(--mono);font-size:12px;color:var(--muted);background:var(--panel-2);border:1px solid var(--border-soft);border-radius:8px;padding:7px 14px}
  .storm-tut .soon p{color:var(--faint);font-size:13.5px;margin-top:16px}
  .storm-tut .soon a{color:var(--muted)}
  .storm-tut .soon a:hover{color:var(--accent)}

  .storm-tut footer{border-top:1px solid var(--border-soft);margin-top:70px;padding:36px 0;color:var(--faint);font-size:13.5px}
  .storm-tut .foot{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:14px}
  .storm-tut .foot .links{display:flex;gap:22px;font-family:var(--mono)}.storm-tut .foot a{color:var(--muted)}.storm-tut .foot a:hover{color:var(--text)}
  @media(max-width:920px){.storm-tut .tech-tag{display:none}}
  @media(max-width:760px){.storm-tut .nav-links a:not(.gh){display:none}}
`;
