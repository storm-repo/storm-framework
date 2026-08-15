/**
 * Remark plugin that replaces @@STORM_VERSION@@ placeholders in markdown with
 * the Storm version the page documents.
 *
 * A released snapshot states its own version: a reader on /docs/1.12.1 gets
 * install snippets for 1.12.1, matching the banner above them, instead of
 * coordinates for whatever release happens to be current. The version is read
 * from the snapshot's directory name (versioned_docs/version-<version>), which
 * `docusaurus docs:version` names after the release it captures.
 *
 * The current docs (docs/, served at /docs and /docs/next) have no version of
 * their own and fall back to the resolved release version, so their snippets
 * stay installable rather than pointing at an unpublished revision.
 */

// Snapshot directories are named after the release, e.g. version-1.13.1. Only
// a version-shaped name is usable as a coordinate; anything else (a named
// branch of the docs, say) falls back rather than producing an artifact
// version that was never published.
const SNAPSHOT_DIR = /\/versioned_docs\/version-(\d+\.\d+\.\d+[^/]*)\//;

function visitNode(node, replacer) {
  if (node.type === 'text' || node.type === 'code' || node.type === 'inlineCode') {
    if (typeof node.value === 'string') {
      node.value = node.value.replaceAll('@@STORM_VERSION@@', replacer);
    }
  }
  if (node.children) {
    for (const child of node.children) {
      visitNode(child, replacer);
    }
  }
}

function versionFor(file, fallback) {
  const filePath = file?.path ?? file?.history?.[file.history.length - 1] ?? '';
  // Windows separators, so the snapshot is recognized on every platform.
  const match = String(filePath).replaceAll('\\', '/').match(SNAPSHOT_DIR);
  return match ? match[1] : fallback;
}

const plugin = (options) => {
  const version = options?.version || '0.0.0';
  return (tree, file) => {
    visitNode(tree, versionFor(file, version));
  };
};

module.exports = plugin;
