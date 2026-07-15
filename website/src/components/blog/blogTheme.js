import React from 'react';
import Head from '@docusaurus/Head';
import {TUT_CSS, navHtml, FOOT_HTML} from '../tutorial/tutorialTheme';

// Shared chrome for the blog articles under `/blog/*`. Reuses the tutorial /
// landing look (see tutorialTheme.js) so the blog reads as part of the
// marketing site rather than as docs. Each article file supplies the article
// body (the `<div class="art">...`); this component wraps it with the nav,
// footer, fonts, and SEO metadata (including a BlogPosting datePublished so the
// post dates show up in search results).

export function BlogPage({title, description, slug, dateISO, body}) {
  const url = `https://orm.st/blog/${slug}`;
  return (
    <>
      <Head>
        <html lang="en" />
        <title>{`${title} · ST/ORM Blog`}</title>
        <meta name="description" content={description} />
        <link rel="canonical" href={url} />
        <meta property="og:type" content="article" />
        <meta property="og:title" content={title} />
        <meta property="og:description" content={description} />
        <meta property="article:published_time" content={dateISO} />
        <meta name="twitter:title" content={title} />
        <meta name="twitter:description" content={description} />
        <script type="application/ld+json">
          {JSON.stringify({
            '@context': 'https://schema.org',
            '@type': 'BlogPosting',
            headline: title,
            description,
            url,
            mainEntityOfPage: {'@type': 'WebPage', '@id': url},
            image: 'https://orm.st/img/storm.png',
            datePublished: dateISO,
            dateModified: dateISO,
            author: {'@type': 'Organization', name: 'Storm', url: 'https://github.com/storm-orm'},
            publisher: {
              '@type': 'Organization',
              name: 'Storm',
              url: 'https://orm.st',
              logo: {'@type': 'ImageObject', url: 'https://orm.st/img/storm-dark.png'},
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
      <style dangerouslySetInnerHTML={{__html: TUT_CSS}} />
      <div
        className="storm-tut"
        dangerouslySetInnerHTML={{__html: navHtml('blog') + body + FOOT_HTML}}
      />
    </>
  );
}
