const fs = require('fs');
const path = require('path');

const pomPath = path.resolve(__dirname, '../../pom.xml');
const pomContent = fs.readFileSync(pomPath, 'utf-8');

// Try to extract <revision> from <properties> first (CI-friendly versions).
const revisionMatch = pomContent.match(
  /<properties>[\s\S]*?<revision>(.*?)<\/revision>[\s\S]*?<\/properties>/
);

let version;
if (revisionMatch) {
  version = revisionMatch[1];
} else {
  // Fall back to extracting the first <version> tag directly under <project>,
  // skipping <version> tags nested inside <parent>, <dependencies>, etc.
  const stripped = pomContent
    .replace(/<parent>[\s\S]*?<\/parent>/g, '')
    .replace(/<dependencies>[\s\S]*?<\/dependencies>/g, '')
    .replace(/<dependencyManagement>[\s\S]*?<\/dependencyManagement>/g, '')
    .replace(/<build>[\s\S]*?<\/build>/g, '')
    .replace(/<profiles>[\s\S]*?<\/profiles>/g, '')
    .replace(/<properties>[\s\S]*?<\/properties>/g, '');
  const versionMatch = stripped.match(/<version>(.*?)<\/version>/);
  version = versionMatch ? versionMatch[1] : '0.0.0';
}

// The published documentation must never render a development/snapshot version
// (e.g. `0.0.0-SNAPSHOT`, which is the pom <revision> between releases). The repo
// itself is allowed to carry a SNAPSHOT revision, but the docs site is deployed
// from `main` on every push — so when the resolved version is a snapshot or the
// `0.0.0` placeholder, fall back to the latest RELEASED docs version from
// versions.json. That keeps install snippets like
// `st.orm:storm-bom:<version>` showing a real, installable version on the site.
if (!version || /snapshot/i.test(version) || /^0\.0\.0\b/.test(version)) {
  try {
    const versions = JSON.parse(
      fs.readFileSync(path.resolve(__dirname, '../versions.json'), 'utf-8')
    );
    if (Array.isArray(versions) && versions.length > 0) {
      version = versions[0]; // versions.json is newest-first
    }
  } catch (e) {
    // No versions.json yet (e.g. before the first release) — keep what we have.
  }
}

module.exports = version;
