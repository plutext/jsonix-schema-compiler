# CR-001: Pin and modernise the Maven build

**Status:** Implemented (2026-09-06)
**Depends on:** nothing
**Recommended order:** first (before CR-002)

## Summary

The project is already a multi-module Maven build, so there is no "migration to Maven" to do.
What it needs is hygiene: the build is not reproducible, the integration test suite is currently
broken because of an unpinned plugin version, the parent POM points at a decommissioned repository,
and the build can only be run from a Windows batch file with a hand-set `JAVA6_HOME`.

This CR makes the existing JDK 8 build green, reproducible and runnable from any OS and from CI,
**without** changing the Java level. CR-002 then builds on that.

## Motivation (evidence)

1. **Integration tests fail today, even on JDK 8.**
   `mvn install -Ptests -pl '!npm'` with JDK 8 fails in `tests/filter`:

   ```
   Failed to execute goal org.jvnet.jaxb2.maven2:maven-jaxb2-plugin:0.15.3:generate ...
   A required class was missing ... org/relaxng/datatype/ValidationContext
   ```

   Root cause: `tests/pom.xml` re-declares `maven-jaxb2-plugin` in `pluginManagement` without a
   `<version>`, which overrides the root's `${maven-jaxb2-plugin.version}` (0.13.1). Maven then
   resolves the *latest release* from repository metadata, currently 0.15.3, which bundles
   JAXB 2.3.7 where the RELAX NG datatype package was renamed (see CR-002). Adding
   `<version>${maven-jaxb2-plugin.version}</version>` to that entry makes all four test modules
   (`filter`, `wps`, `zero`, `issues`) pass on JDK 8. This was verified in a scratch copy.

2. **Parent POM `org.sonatype.oss:oss-parent:9` is obsolete.** It injects
   `https://oss.sonatype.org/content/repositories/snapshots` as a repository; OSSRH was shut down in
   2025. Any build that misses an artifact locally waits on a dead host (seen during the trial:
   `Could not find artifact ... in sonatype-nexus-snapshots`). Its release profile
   (`sonatype-oss-release`) targets a publishing flow that no longer exists.

3. **Most plugin versions are unpinned or ancient.** `maven-compiler-plugin` and
   `maven-surefire-plugin` have no version at all (Maven 3.9 silently picked 3.15.0 and 3.5.4);
   `maven-antrun-plugin` 1.6 declares a `system`-scoped `tools.jar` dependency that no longer exists
   on JDK 9+; `maven-shade-plugin` 2.3, `maven-assembly-plugin` 2.2.1, `maven-dependency-plugin`
   2.1/2.8 are 10+ years old.

4. **Release tooling is Windows-only and manual.** `release.bat`, `qrelease.bat`, `install.bat`
   and `full-build-1.6.bat` all `set JAVA_HOME=%JAVA6_HOME%` and push to `origin master` interactively.
   None of them can be used from Linux/macOS or CI. The `npm` module additionally shells out to
   `npm install`/`npm publish` from Maven.

5. **No CI.** There is no GitHub Actions (or other) workflow, so none of the above is caught.

## Scope

In scope:

- Pin `maven-jaxb2-plugin` in `tests/pom.xml` (one line; restores the test suite).
- Replace `oss-parent:9` with no parent (inline the few things it provided: source/javadoc jars on
  release, GPG signing profile) or with a maintained parent of your choosing.
- Pin every plugin under `<pluginManagement>` to a current version:
  compiler 3.13+, surefire 3.5+, jar 3.4+, source 3.3+, javadoc 3.10+, shade 3.6+, assembly 3.7+,
  dependency 3.8+, resources 3.3+, deploy 3.1+, enforcer 3.5+, exec 3.5+, build-helper 3.6+,
  antrun 3.1+ (and drop its `tools.jar` / `ant-optional` / `jslint4java` system dependencies, which
  only `samples/po` used).
- Add `maven-enforcer-plugin` with `requireJavaVersion` `[1.8,1.9)` and `requireMavenVersion`
  `[3.6,)` so the JDK 21 failure ("Source option 6 is no longer supported") becomes a clear message.
  CR-002 later changes this range to `[11,)`.
- Add the Maven Wrapper (`mvnw`, `.mvn/wrapper`) pinned to Maven 3.9.x.
- Add `.github/workflows/build.yml` running `./mvnw -B install -Ptests -pl '!npm'` on JDK 8
  (CR-002 widens the matrix to 11/17/21).
- Replace the four `.bat` files with a short `RELEASING.md` describing
  `mvn versions:set -Pall` / `mvn -Pall clean install` / tag, and a `release` profile that attaches
  sources/javadoc and signs. Note that this fork cannot publish to `org.hisrc.jsonix` on Maven
  Central; a release from this fork needs its own `groupId` (and its own npm package name), which is a
  decision for the maintainer, not this CR.
- Delete the checked-in Eclipse metadata in `tests/zero` (`.classpath`, `.project`, `.settings/`);
  the root `.gitignore` already excludes them.

Out of scope:

- Any change to Java source, Java level, or JAXB version (CR-002).
- The `samples/po` and `dist` profiles beyond making their POMs parse with the new plugin versions.
  `samples/po` drives Ant, `maven-invoker` and `npm` and was not exercised in this analysis.

## Proposed changes (concrete)

| File | Change |
|------|--------|
| `tests/pom.xml` | add `<version>${maven-jaxb2-plugin.version}</version>` to the `maven-jaxb2-plugin` entry in `pluginManagement` |
| `pom.xml` | remove `<parent>`; add `<properties>` for every plugin version; add enforcer, wrapper; remove `sun.jdk:tools` system dep from antrun; add `release` profile (source, javadoc, gpg) |
| `pom.xml` | keep `<source>1.6</source>/<target>1.6</target>` for now so this CR is behaviour-neutral |
| `*.bat` | delete; replace with `RELEASING.md` |
| `.mvn/wrapper/*`, `mvnw`, `mvnw.cmd` | add |
| `.github/workflows/build.yml` | add |
| `tests/zero/.classpath`, `.project`, `.settings/` | delete |

## Verification

- `JAVA_HOME=<jdk8> ./mvnw -B clean install -Ptests -pl '!npm'` is green (baseline today: green for
  `compiler`, `plugin`, `full`; red for `tests/*` until the pin is applied).
- `./mvnw -B install` on JDK 21 fails fast with the enforcer message instead of a compiler error.
- `full/target/jsonix-schema-compiler-full-*.jar` still generates `PurchaseOrder.std.js` /
  `PurchaseOrder.cmp.js` from `samples/po/src/main/resources` on JDK 8, byte-identical to before.
- `mvn dependency:tree` shows no `system`-scoped artifacts.

## Risks

Low. Every change is to build metadata. The only behavioural change is that the test suite starts
running against the plugin version the POM already claims to use (0.13.1, JAXB 2.2.11).

## Effort

Half a day, including CI setup.

## Implementation notes (2026-09-06)

Implemented as proposed, with these deviations and additions:

- **JUnit 4.11 → 4.13.2** (test scope). Surefire 3.6 refuses to run JUnit 4.11.
- **`maven-source-plugin` goal `jar` → `jar-no-fork`.** `jar` forks the lifecycle, which re-ran
  `maven-jaxb2-plugin:generate` in every test module. Sources jars are still attached.
- **`samples/pom.xml`: `<tasks>` → `<target>`** in the five antrun executions (antrun 3.x dropped
  `tasks`). They only use core Ant (`mkdir`, `unzip`, `ant`), so antrun needs no extra dependencies.
- **Eclipse metadata removed at the root and in `tests/` as well as `tests/zero/`**; all three were
  tracked despite `.gitignore`.
- **`maven-release-plugin` dropped** rather than re-pointed; `RELEASING.md` uses `versions:set`, as
  the batch files did.
- `mvn dependency:tree` still shows `com.sun:tools:jar:1.6:system` twice. That is transitive from
  JAXB 2.2.11's `istack-commons-tools`, not from this project, and disappears with CR-002. The CI
  check excludes it.
- The `npm` module was verified to build with the pinned plugin versions; `samples`/`dist` were
  verified to `validate` under `-Pall` only.

Verification results: `./mvnw -B clean install -Ptests -pl '!npm'` on JDK 8 is green (28 compiler
tests, 4 in `full`, `filter`/`wps`/`zero`/`issues` all pass); JDK 21 fails at `validate` with the
enforcer message; the `full` jar's output for `samples/po` is byte-identical to the pre-change jar.
