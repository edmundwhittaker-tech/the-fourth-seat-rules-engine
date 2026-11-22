# XMage integration — stage 1

This branch starts an independent XMage backend integration. The existing
TypeScript playground remains on `main` and continues to deploy to GitHub Pages.
This integration has its own build workflow and does not deploy to Pages.

## Locked upstream

- Source: https://github.com/magefree/mage
- Release: `xmage_1.4.62V1` (3 October 2026)
- Commit: `eb69d05b88142b5c4eec4869bcb23a0fec13c091`
- Java: Eclipse Temurin `17.0.20`
- Server archive assembly plugin: `3.7.1`
- License: upstream MIT notice is retained in the server artifact.

`upstream.json` is the single source for the engine/toolchain choices above.
We use the upstream release matching the 1.4.62 generation used by Nexus.
Matching version numbers alone does not establish compatibility with Nexus's
fork; that is the next milestone's investigation.

## Browser-only build and inspection

1. Open the repository's **Actions** tab.
2. Open **Build pinned XMage server** and select a run for `integration/xmage`.
3. Inspect the build steps, summary, and artifact list in your browser.
4. The main artifact contains `mage-server.zip`, the upstream license,
   `contents.txt`, `manifest.json`, `upstream.json`, and `SHA256SUMS`.
5. A separate diagnostic artifact contains Maven build logs and tool versions.

Changes to the integration files automatically build this branch. A manual
workflow-dispatch trigger is also defined; GitHub normally exposes the manual
run button after a workflow is present on the default branch. Until then,
push a change to the integration branch to trigger it. No download or local
software is needed to review Actions results.

## What stage 1 establishes

The source commit is checked before the build. Maven compiles and packages the
server plus its runtime dependencies, including native AI and multiplayer
Commander plugins. The resulting ZIP is checked for corruption and required
JARs. Failed compilation or packaging fails the workflow.

The source and Java version are pinned, as are the GitHub Actions revisions
and assembly plugin. Maven's resolved version is recorded from the hosted
runner. This is a repeatable, source-pinned build, not a claim of bit-for-bit
reproducibility: the runner image, Maven dependency repositories, unpinned
upstream build plugins, and upstream build timestamps can still vary.

The upstream test suite is explicitly skipped for this packaging milestone.
Successful compilation does not establish gameplay correctness, successful
server startup, Nexus compatibility, or an AI game. Those have separate gates.

## Next stage: bridge against our own server

Pin and inspect Nexus independently, including its fork changes and protocol.
Start the built XMage server in an isolated environment, connect the bridge to
that private server rather than a public XMage service, and check connection,
seat views, choices and native AI support. Only after that should we verify a
four-player Commander game and connect the Fourth Seat website.
