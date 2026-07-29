## What This Changes

<!-- What does this do, and why? If it fixes an issue, link it: Fixes #123 -->

## Verification

<!-- Delete rows that do not apply. See CONTRIBUTING.md for the commands. -->

| Check | Status |
| --- | --- |
| `./gradlew :common:test -Pmc_version=1.21.11` | |
| `verifyCompatibilityManifest verifyCompatibilityStructure verifyBuildGenerationRouting` | |
| Compiles on `1.21` / `1.21.8` / `1.21.10` / `1.21.11` | |
| Compiles on `26.1` / `26.2` (`-p mc26`, both loaders) | |
| Clicked through the affected UI in a dev client | |

If you could not run something locally — no Java 25 for the `26.x` targets, for
instance — say so here rather than leaving it blank. CI covers the full matrix on
every pull request.

## Notes For Review

<!--
Anything that would be hard to see from the diff:
- non-obvious decisions, or an approach you considered and rejected
- behaviour not covered by tests, so a reviewer knows where to look by hand
- ordering or precedence you deliberately preserved
- anything intentionally left out of scope
-->
