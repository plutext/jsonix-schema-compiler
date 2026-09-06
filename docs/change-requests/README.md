# Change requests

Numbered proposals for changes to the build and toolchain of this fork
(`plutext/jsonix-schema-compiler`, forked from `highsource/jsonix-schema-compiler`,
whose last upstream commit is from November 2018).

| CR | Title | Recommended order | Status |
|----|-------|-------------------|--------|
| [CR-001](CR-001-maven-build-modernisation.md) | Pin and modernise the Maven build | 1 | Implemented 2026-09-06 |
| [CR-002](CR-002-java-11-baseline.md) | Move to a Java 11 baseline (JAXB 2.3.x) | 2 | Implemented 2026-09-06 |
| [CR-003](CR-003-deterministic-mapping-output.md) | Deterministic ordering of generated mappings | 2 (alongside CR-002) | Implemented 2026-09-06 |
| [CR-004](CR-004-jakarta-jaxb4.md) | Jakarta XML Binding / JAXB 4 / jaxb-tools 4.x | 3 | Implemented 2026-09-06 |
| [CR-005](CR-005-typescript-output.md) | TypeScript output (`.d.ts` data model typings; ES-module mappings) | 4 | Implemented 2026-09-06 |

All findings below were reproduced on 2026-09-06 on this machine (Maven 3.9.16, OpenJDK 8u502,
17 and 21) against commit `a160ac7`. Trial migrations were done in scratch copies; nothing in the
repository was changed except these documents and `CLAUDE.md`.
