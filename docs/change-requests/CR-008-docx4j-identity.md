# CR-008: Publish under the docx4j identity (`org.docx4j.jsonix`, `@docx4j/jsonix-schema-compiler`)

**Status:** Implemented (2026-09-14) at version 3.0.0: npm rename and Maven group change done; Maven
Central publishing deferred (see "Implementation, 2026-09-14")
**Depends on:** CR-001 (release profile, `RELEASING.md`)
**Context:** the runtime is now `@docx4j/jsonix` (repository `plutext/jsonix`, 3.2.0, commit `8be7123`;
`@mitre/jsonix` 3.0.11 is the last MITRE release). The whole family moves under `docx4j`.

## Summary

This fork cannot publish under upstream's coordinates (`org.hisrc.jsonix` on Maven Central,
`jsonix-schema-compiler` on npm; see CR-001 "Publishing target" and CR-005's open decision). The
decision is now made: the compiler becomes `@docx4j/jsonix-schema-compiler` on npm and
`org.docx4j.jsonix` on Maven. The npm part is done in this change; the Maven part is a coordinated
rename across the build and the consumers' instructions, planned here and executed as a single
commit when the first release is prepared.

## Done now

- `npm/src/main/npm/package.json` (the template Maven filters) and the tracked `npm/package.json`:
  `"name": "@docx4j/jsonix-schema-compiler"`, repository, homepage, bugs and licence URLs point at
  `plutext/jsonix-schema-compiler`.
- README "Using with NPM": install and `prepublish` snippets use the scoped names of both packages.
- Every reference to the runtime says `@docx4j/jsonix`. `tests/typescript` depends on
  `@docx4j/jsonix` (default still the sibling checkout via `jsonix.runtime.dependency`; the default
  becomes `^3.2.0` once it is on npm).

## Maven group change: plan

Occurrences of `org.hisrc.jsonix` as a Maven coordinate (15 pom files, 34 `<groupId>` elements),
plus the README Maven snippet, `RELEASING.md`, `CLAUDE.md` and the CI workflow's `dependency:tree`
grep. Java packages (`org.hisrc.jsonix.*`) and the customization namespace
(`http://jsonix.highsource.org/customizations`) are **not** renamed: they are API, referenced by
every user's `.xjb`, and unrelated to publishing.

Steps, one commit, done as the first act of a release:

1. `find . -name pom.xml -o -name project-pom.xml | xargs sed -i 's|<groupId>org.hisrc.jsonix</groupId>|<groupId>org.docx4j.jsonix</groupId>|'`
   (root, modules, `tests/*`, `samples/po`, the `project-pom.xml` template, and the
   `<plugin><groupId>` entries that reference the compiler from `jaxb-maven-plugin` configurations).
2. Root `pom.xml`: `<url>`, `<scm>`, `<issueManagement>` to `plutext/jsonix-schema-compiler`;
   add the fork's maintainer to `<developers>` (keep the upstream author and contributors);
   `<distributionManagement>` for the chosen repository (Central Portal via
   `central-publishing-maven-plugin`, or a GitHub Packages repository); the `release` profile
   from CR-001 already attaches sources, javadoc and signatures.
3. `tests/*/pom.xml` and `samples/po/pom.xml`: the `<plugins><plugin>` reference to the compiler
   inside `jaxb-maven-plugin` uses the new group (covered by step 1, but verify: it is what makes
   `-Xjsonix` resolve).
4. Documentation: README Maven snippet (`org.docx4j.jsonix:jsonix-schema-compiler` with
   `org.jvnet.jaxb:jaxb-maven-plugin`), `RELEASING.md` (drop the "cannot publish" paragraph, add
   the credentials/server ids), `CLAUDE.md` (the "Versioning and release" note), CI workflow grep.
5. Version: start the new identity at `3.0.0` (the runtime is at 3.2.0; the compiler's Jakarta,
   TypeScript and determinism changes are incompatible with the 2.3.x `javax` line, which stays
   `2.3.9` under upstream's name). `mvn versions:set -Pall -DnewVersion=3.0.0`.
6. Verify: `./mvnw -B clean install -Pall`, `mvn dependency:tree` shows only the new group for
   project artifacts, `npm pack` in `npm/` yields `docx4j-jsonix-schema-compiler-3.0.0.tgz` with
   `lib/jsonix-schema-compiler-full.jar`.

Artifact ids stay (`jsonix-schema-compiler`, `-full`, `-plugin`), so the jar names in the README and
in `OfficeOpenXML/generate.sh` do not change.

## Implementation, 2026-09-14

- Steps 1, 3, 4 and 5 as planned: every `<groupId>` and the `samples` assembly includes use
  `org.docx4j.jsonix`; version 3.0.0 (decision: straight to 3.0.0, no release candidate; a problem
  found after publishing ships as 3.0.1).
- Step 2 in part: `<url>`, `<scm>`, `<issueManagement>` point at `plutext/jsonix-schema-compiler` and
  the fork's maintainer is listed in `<developers>`. **No `<distributionManagement>`**: nothing needs
  the artifacts from a Maven repository yet (the TypeScript consumers take the compiler from npm, and
  `OfficeOpenXML/generate.sh` and `tests/*` use the local build), so Maven Central is deferred until an
  outside XJC-plugin user asks. `RELEASING.md` publishes with `./mvnw -pl npm deploy` instead of a
  reactor `deploy`, which would fail without a repository.
- `npm/src/main/npm/package.json`: `"publishConfig": {"access": "public"}` (a scoped package is
  otherwise published restricted), SPDX `license` instead of the deprecated `licenses` array, and the
  duplicated `repository` key removed.
- `@docx4j/jsonix` 3.2.0 is on npm: `tests/typescript` defaults `jsonix.runtime.dependency` to
  `^3.2.0` and CI no longer checks out `plutext/jsonix`.
- `jsonix-scripts` (upstream's `org.hisrc.jsonix:jsonix-scripts:2.2.1` on Maven Central, used by
  `tests/filter`, `wps`, `zero`) keeps upstream's group: it is an external artifact, not ours.
- Verification: `./mvnw clean install -Ptests` (every published module and all integration tests),
  `npm pack --dry-run` in `npm/` gives `docx4j-jsonix-schema-compiler-3.0.0.tgz` with the full jar.
  Step 6's `-Pall` does not pass, for reasons unrelated to this CR: `samples` used the
  `maven-assembly-plugin` goal `attached` (removed in 3.x; now `single`), and the Ant sample then fails
  because `project-build.xml` still names JAXB 2 jars on the `XJCTask` classpath and compiles with
  `source="1.6"`. Follow-up: repair or retire `samples/` and `dist/`.

## Out of scope

- Renaming Java packages or the customization namespace.
- Setting up the publishing account and secrets; `RELEASING.md` documents the steps, the
  maintainer provides the credentials.

## Effort

Two hours for the rename and verification; the publishing setup depends on the chosen repository.
