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
| [CR-006](CR-006-parent-pointers-and-deep-copy.md) | Parent pointers and deep copy for unmarshalled objects (docx4j `-Xparent-pointer` / `-Xdocx4j-copy` parity; runtime half is jsonix-CR-002) | 5 | Compiler half implemented 2026-09-07; runtime half pending (jsonix-CR-002) |
| [CR-007](CR-007-officeopenxml-generated-output-adjustments.md) | Equivalents of docx4j's `ModifyGeneratedSources` for the OfficeOpenXML output (`jsonix:propertyOrder`, `defaultValue` override, `inheritance:implements` unions, helper module) | 6 | Implemented 2026-09-07 |
| [CR-008](CR-008-docx4j-identity.md) | Publish under the docx4j identity: `@docx4j/jsonix-schema-compiler` (done), `org.docx4j.jsonix` (planned) | 7 | Draft; npm rename applied |
| [CR-009](CR-009-docx4j-ts-split.md) | Split `OfficeOpenXML/` into its own repository with a docx4j-style facade; renamed 2026-09-09 to `plutext/docx4j-generated-objects-ts` / `@docx4j/generated-objects-ts` | 8 | Implemented 2026-09-07, pushed; renamed 2026-09-09 |
| [CR-010](CR-010-element-factories.md) | Generated element factories per module (`<Module>.factory.mjs` + `.d.mts`): creators with `TYPE_NAME`, `{ name, value }` wrappers named by XJC's rule (`createRT`, `createBodyP`), and an `el` map per QName; docx4j's `ObjectFactory` for the TypeScript model | 9 | Accepted 2026-09-10, in progress |

All findings below were reproduced on 2026-09-06 on this machine (Maven 3.9.16, OpenJDK 8u502,
17 and 21) against commit `a160ac7`. Trial migrations were done in scratch copies; nothing in the
repository was changed except these documents and `CLAUDE.md`.
