# CR-004: Jakarta XML Binding / JAXB 4 / jaxb-tools 4.x

**Status:** Implemented (2026-09-06)
**Depends on:** CR-001, CR-002, CR-003 (all implemented)
**Recommended order:** next

## Summary

Move from the end-of-life `javax.xml.bind` line (JAXB 2.3.9, `maven-jaxb2-plugin` 0.15.3) to
Jakarta XML Binding 4 (JAXB 4.0.9) and its tooling successor `org.jvnet.jaxb:jaxb-tools` 4.0.16
(`jaxb-maven-plugin`, `jaxb-plugins-*`). A complete trial in a scratch copy shows this is a
**mechanical** change: coordinate renames, package renames, one library substitution, and a
two-line workaround for a Jakarta bug. The full unit and integration suite passes, and the CLI
output is byte-identical to the JAXB 2.3.9 build.

The original estimate of two to four days and the "deferred" status were wrong. The trial took
about two hours including diagnosis.

## Why do it

- `org.jvnet.jaxb2.maven2:maven-jaxb2-plugin` 0.15.3 (2022) is the last release of that line. Every
  actively maintained Maven build that runs XJC today uses `org.jvnet.jaxb:jaxb-maven-plugin` 4.x
  with Jakarta JAXB, and an XJC plugin compiled against `javax.xml.bind` cannot be loaded there.
- JAXB 2.3.x itself is in maintenance only. JDK 11+ users get Jakarta JAXB from every framework
  (Jakarta EE 10, Spring Boot 3, docx4j 11).
- The trial proves the whole `jaxb2-basics` model API this project is built on
  (`MModelInfo`, `XJCCMInfoFactory`, `CustomizationUtils`, the visitors and origins) survives
  unchanged in `jaxb-plugins-runtime`/`jaxb-plugins-tools` 4.0.16 under the package `org.jvnet.jaxb`,
  compiled for Java 11.

## Trial results (JDK 17)

| Check | Result |
|-------|--------|
| `compiler` compiles after renames | yes, zero code edits needed beyond those listed below |
| `./mvnw clean install -Ptests -pl '!npm'` | green: 29 + 4 unit tests, `filter`, `wps`, `zero`, `issues` |
| `full` jar on JDK 17 and 21 against `samples/po` | all three files produced |
| Output vs the JAXB 2.3.9 build (same jar inputs) | `.std.js`, `.cmp.js`, `.jsonschema` byte-identical |
| `full` jar contents | 126 `jakarta.xml.bind` classes, 0 `javax.xml.bind`; 5.6 MB (was 7.5 MB) |

## What actually changes (from the trial diff: 110 files)

### Coordinates (root `pom.xml` and 8 module poms)

| Old | New |
|-----|-----|
| `org.glassfish.jaxb:*` 2.3.9 | same artifacts, **4.0.9** |
| `javax.xml.bind:jaxb-api` 2.3.1 | `jakarta.xml.bind:jakarta.xml.bind-api` **4.0.2** |
| `com.sun.activation:javax.activation` 1.2.0 | `jakarta.activation:jakarta.activation-api` **2.1.3** (runtime impl `angus-activation` arrives transitively) |
| `org.jvnet.jaxb2_commons:jaxb2-basics-runtime/-tools/-ant/jaxb2-basics` 0.11.0 | `org.jvnet.jaxb:jaxb-plugins-runtime/-tools/-ant/jaxb-plugins` **4.0.16** |
| `org.jvnet.jaxb2.maven2:maven-jaxb2-plugin` 0.15.3 | `org.jvnet.jaxb:jaxb-maven-plugin` **4.0.16** |
| `org.jvnet.jaxb2.maven2:maven-jaxb2-plugin-testing` | `org.jvnet.jaxb:jaxb-maven-plugin-testing` 4.0.16 |
| (transitive) | `org.apache.commons:commons-lang3` 3.20.0, **declared explicitly** |

`commons-lang3` is used directly by 30+ classes (`Validate`, `StringUtils`) but was never declared;
it arrived via `jaxb2-basics-tools`. `jaxb-plugins-tools` no longer brings it. Declaring it is
correct regardless of this CR and could be done today.

### Packages (73 Java files, imports only)

- `javax.xml.bind` → `jakarta.xml.bind`
- `org.jvnet.jaxb2_commons` → `org.jvnet.jaxb`

Two of the 73 files show large diffs in the trial only because the rewrite normalised their CRLF
line endings; ignoring CR they are import-only. `com.sun.tools.xjc`, `com.sun.codemodel`,
`com.sun.xml.xsom` and `com.sun.tools.rngdatatype` are unchanged in JAXB 4.

### Real code changes (2 files, 2 lines each)

`Base64BinaryTypeInfoCompiler` and `Base64BinaryTypeInfoProducer` call
`DatatypeConverter.parseBase64Binary`. The Jakarta 4.0.2 implementation throws
`IllegalArgumentException: base64 text invalid` for valid one-byte inputs such as `QQ==` (verified in
isolation; the javax 2.3.1 implementation accepts them). Replace with
`java.util.Base64.getMimeDecoder().decode(item)`. This also removes a JAXB-implementation dependency
from the compiler and can be done today.

### Test helper (1 file)

`tests/zero/.../RunZeroPlugin` extends `org.jvnet.jaxb2.maven2.test.RunXJC2Mojo`; in 4.x this is
`org.jvnet.jaxb.maven.test.RunXJCMojo` with `configureMojo(AbstractXJCMojo)`. Four-line change.

### Binding files (28 `.xsd`/`.xjb` in `compiler`, `full`, `tests`, `samples`)

JAXB 4's XJC **silently ignores** customizations in the legacy namespace
`http://java.sun.com/xml/ns/jaxb`. In the trial this made every `jaxb:schemaBindings`/`jaxb:package`
disappear, so generated classes landed in package `generated` and tests failed with
`getClassInfo("test.E01")` returning null. All binding files must move to
`xmlns:jaxb="https://jakarta.ee/xml/ns/jaxb"` with `jaxb:version="3.0"` / `<jaxb:bindings version="3.0">`.
The vendor-extension namespace `http://java.sun.com/xml/ns/jaxb/xjc` **stays as is** (JAXB 4 rejects
a `jakarta.ee` form of it with "Unsupported binding namespace"). The `jsonix:` customization
namespace is ours and does not change.

This is the one item with a **user-facing** consequence: consumers' `.xjb` files need the same
namespace change. Anyone already on `jaxb-maven-plugin` 4.x has done it; anyone still on
`maven-jaxb2-plugin` 0.15.x cannot use the new release at all (see "Consumers" below).

### Not changed

- `javax.json` / `org.glassfish:javax.json` 1.0.4: unrelated to JAXB, works on Java 11+. Could move to
  `jakarta.json` separately; not needed.
- `jgrapht-core` 0.9.0, `js-codemodel` 1.1, `args4j` 2.0.29, `slf4j` 1.7.7: untouched.
- `maven.compiler.release` stays 11 (all 4.x artifacts involved are Java 11 class files).

## Consumers

| Consumer | Before this CR | After this CR |
|----------|----------------|---------------|
| CLI jar (`full`, npm) | JDK 11+ | JDK 11+, unchanged usage; bindings must use the Jakarta namespace |
| Maven, `org.jvnet.jaxb:jaxb-maven-plugin` 4.x | not possible | supported (this is the point of the CR) |
| Maven, `org.jvnet.jaxb2.maven2:maven-jaxb2-plugin` 0.14+ | supported | **no longer possible** |
| Ant `xjc` task from JAXB 2.3.x | supported | **no longer possible**; JAXB 4 `xjc` task works |

If both audiences matter, the alternative is a second Maven module (`compiler-javax`, built from the
same sources with the reverse package rewrite at build time). Not recommended: it doubles the test
matrix for a line that is already end-of-life. Cut a final `javax` release from the current branch
first and point old-toolchain users at it.

## Plan

1. Pre-work on the current line (no behaviour change, could ship before this CR):
   declare `commons-lang3`; switch the two base64 decoders to `java.util.Base64`.
2. Tag and release the last `javax` version.
3. Apply the coordinate table above; run the two package renames over `compiler`, `full`, `tests`;
   port `RunZeroPlugin`; rewrite the 28 binding files (namespace + version only).
4. README: Requirements section becomes "JDK 11+, Jakarta XML Binding 4 / `jaxb-maven-plugin`
   4.0.x; bindings use `https://jakarta.ee/xml/ns/jaxb`"; replace the Maven snippet with the
   `org.jvnet.jaxb` one.
5. CI unchanged (11/17/21).

## Verification

Exactly the trial: suite green on 17 and 21; CLI jar output for `samples/po` and the OWS 1.1.0
fixture byte-identical to the last `javax` release (CR-003 makes this comparison meaningful); a
throwaway consumer project using `jaxb-maven-plugin` 4.0.16 with `-Xjsonix` generates mappings.

## Risks

- Low for the code (proved by the trial).
- The Jakarta `DatatypeConverter` base64 bug suggests re-checking the other converter uses
  (`parseHexBinary`, `parseQName`); both passed the existing tests, which cover hex and QName
  enumerations in the `zero` schema.
- Users who miss the binding-namespace change get silently wrong output (customizations ignored,
  default package). Worth a loud note in the release notes and README.

## Effort

Half a day, plus whatever the final `javax` release takes.

## Implementation notes (2026-09-06)

Implemented as planned in steps 1, 3 and 4 (1 and 3 in one change). No separate `javax` release
was cut; that is the maintainer's call and would be made from commit `b47683a` or earlier.
Additional touches beyond the trial:

- Root pom property names renamed to match the artifacts (`jakarta.xml.bind-api.version`,
  `jakarta.activation-api.version`, `jaxb-plugins.version`, `jaxb-maven-plugin.version`,
  `commons-lang3.version`).
- `samples/po/project-pom.xml`, `samples/po/project-build.xml` (Ant task class
  `org.jvnet.jaxb.xjc.XJCTask`, jar name) and `samples/src/main/assembly/ant-src.xml` updated to the
  new coordinates. The samples profile was not exercised.
- README: Requirements section rewritten (Jakarta 4, binding namespace warning); Maven snippet uses
  `org.jvnet.jaxb:jaxb-maven-plugin` 4.0.16.

Verification:

| Check | Result |
|-------|--------|
| `./mvnw clean install -Ptests -pl '!npm'` on JDK 17 | green: 29 + 4 unit tests, `filter`/`wps`/`zero`/`issues` |
| same on JDK 21 | green |
| `full` jar, `samples/po`, vs the JAXB 2.3.9 build (CR-003 state) | `.std.js`, `.cmp.js`, `.jsonschema` byte-identical |
| `full` jar, OGC OWS 1.1.0 + XLink, vs the JAXB 2.3.9 build | all four files byte-identical |
| JDK 17 vs 21 output | identical |
| `full` jar contents | 126 `jakarta.xml.bind` classes, 47 `jakarta.activation`, 0 `javax.xml.bind`; 5.6 MB |
