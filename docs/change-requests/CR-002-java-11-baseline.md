# CR-002: Move to a Java 11 baseline (JAXB 2.3.x)

**Status:** Proposed
**Depends on:** CR-001 (needed to verify this change with the integration suite)
**Recommended order:** second

## Summary

Raise the build to require JDK 11 or newer, compile with `--release 11`, and move from JAXB 2.2.11
to JAXB 2.3.9. This is a small change (three import lines and POM edits) with a large payoff: the
shipped CLI jar and the XJC plugin both start working with current JDKs and current
`maven-jaxb2-plugin` releases. It was trialled end-to-end in a scratch copy on JDK 17 and all unit
and integration tests pass.

## Motivation (evidence)

1. **The CLI jar cannot run on any JDK newer than 8.** With the current `full` jar built on JDK 8:

   ```
   $ java17 -jar jsonix-schema-compiler-full-2.3.10-SNAPSHOT.jar purchaseorder.xsd -b bindings.xjb
   Exception in thread "main" java.lang.NoClassDefFoundError: javax/activation/DataSource
   ```

   Same on JDK 21. `javax.activation` and `javax.xml.bind` were removed from the JDK in Java 11 and
   the jar does not bundle them. This is the artifact distributed via npm and GitHub releases, so
   every user on a modern JDK is affected.

2. **The XJC plugin is incompatible with `maven-jaxb2-plugin` 0.14.0 and later.** JAXB 2.3.1
   renamed the RELAX NG datatype API from `org.relaxng.datatype` (artifact
   `relaxngDatatype:relaxngDatatype`) to `com.sun.tools.rngdatatype` (artifact
   `com.sun.xml.bind.external:relaxng-datatype`). Three classes in this project import the old
   package:

   - `compiler/.../compilation/mapping/CreateTypeInfoDelaration.java`
   - `compiler/.../compilation/mapping/typeinfo/builtin/QNameTypeInfoCompiler.java`
   - `compiler/.../compilation/jsonschema/typeinfo/builtin/QNameTypeInfoProducer.java`

   Loading them under a JAXB 2.3.1+ XJC gives `NoClassDefFoundError: org/relaxng/datatype/ValidationContext`.
   This is exactly the failure the repository's own `tests/filter` module shows today (CR-001, item 1),
   and it is what any consumer following the README's Maven instructions with a current plugin hits.

3. **The build itself needs a JDK 8 toolchain.** `source`/`target` 1.6 is rejected by JDK 12+, and the
   JAXB 2.2.11 POMs declare all of their transitive dependencies inside a JDK-activated profile: on
   JDK 17 `mvn dependency:tree` shows `jaxb-xjc:2.2.11` with **no** children, so `com.sun.xml.xsom`,
   `codemodel` etc. vanish from the classpath and nothing compiles. Upgrading JAXB fixes this.

4. Java 8 left Oracle premium support in 2022 and JDK 11 is itself in extended support; a
   baseline below 11 blocks every other dependency upgrade.

## Trial results

A scratch copy with the changes below was built with JDK 17 (JDK 11 is not installed here; 11 is
strictly less restrictive than 17 for everything encountered):

| Check | Result |
|-------|--------|
| `mvn install -pl .,compiler,plugin,full` on JDK 17 | green, 28 + 4 tests pass |
| `mvn install -Ptests -pl '!npm'` on JDK 17 | `filter`, `wps`, `issues` green; `zero` green with `--add-opens` (see below) |
| Resulting `full` jar run on JDK 8 | generates `.std.js`, `.cmp.js`, `.jsonschema` |
| Resulting `full` jar run on JDK 17 | same |
| Resulting `full` jar run on JDK 21 | same |
| `tests/issues` with `maven-jaxb2-plugin` 0.15.3 (JAXB 2.3.7) | green |

The generated mapping files are semantically identical to the JDK 8 / JAXB 2.2.11 output but the
**order** of `typeInfos` and `elementInfos` differs, because `definition.Mapping` stores them in
`HashSet`s and iteration order depends on the JDK and on the JAXB model objects. Output is stable
across runs on a given toolchain. See CR-003 for making it stable across toolchains, which is what
allows this CR to be verified by a byte-for-byte diff of generated mappings.

## Proposed changes (concrete)

### Root `pom.xml`

- Replace `<source>1.6</source><target>1.6</target>` with `<maven.compiler.release>11</maven.compiler.release>`.
  (The trial used target 1.8 to prove the jar still runs on JDK 8. If keeping JDK 8 *runtime*
  support for CLI users matters, use `release` 8 instead; both compile the same sources.)
- `jaxb.version` 2.2.11 → **2.3.9** (last of the `javax.xml.bind` line).
- Add to `dependencyManagement`:
  - `javax.xml.bind:jaxb-api:2.3.1`
  - `com.sun.activation:javax.activation:1.2.0`
- `maven-jaxb2-plugin.version` 0.13.1 → **0.15.3** (last release of `org.jvnet.jaxb2.maven2`; uses JAXB 2.3.x).
- Enforcer range (from CR-001) → `[11,)`.
- Leave `jaxb2-basics` at 0.11.0, `jgrapht-core` at 0.9.0, `js-codemodel` at 1.1, `args4j` at 2.0.29.
  All worked unchanged in the trial. `jgrapht` 1.x removes the `DirectedGraph` interface this code
  uses, so it must not be bumped casually.

### `compiler/pom.xml`

- Add `javax.xml.bind:jaxb-api` and `com.sun.activation:javax.activation` with `provided` scope
  (XJC hosts supply them at runtime).

### `full/pom.xml`

- Add the same two with `compile` scope so the shaded jar bundles them.

### Source (three files listed above)

- `import org.relaxng.datatype.ValidationContext;` → `import com.sun.tools.rngdatatype.ValidationContext;`
  (`sed 's/org\.relaxng\.datatype/com.sun.tools.rngdatatype/'` is sufficient; nothing else changes.)

### `tests/*/pom.xml`

- `tests/issues`: add `javax.xml.bind:jaxb-api` and `org.glassfish.jaxb:jaxb-runtime` dependencies
  (generated code imports `javax.xml.bind.annotation.*`). `filter`, `wps`, `zero` already declare
  `jaxb-runtime`.
- `tests/zero`: `legato-testing` 1.1.1 (an HtmlUnit-based JsUnit runner from 2010) reflects into
  `java.net.URL.handlers`. Works on JDK 11 with an illegal-access warning; on JDK 17+ it needs
  surefire `<argLine>--add-opens java.base/java.net=ALL-UNNAMED</argLine>` (verified). Longer term
  that test should move to a Node-based runner like `samples/po/src/test/javascript`.

### `CLAUDE.md`, `README.md`

- Replace the "Java 8 is required" section with "JDK 11+ required; CI builds on 11, 17, 21".
- README "Using with Maven": note the minimum `maven-jaxb2-plugin` version.

### CI (from CR-001)

- Matrix over JDK 11, 17, 21; run the `full` jar against `samples/po` as a smoke test on each.

## Out of scope

- Jakarta packages / JAXB 3+ / `org.jvnet.jaxb:jaxb-maven-plugin` 4.x (CR-004). JAXB 2.3.9 is the
  end of the `javax` line and is only in maintenance; this CR buys time, not a future.
- Sorting generated output (CR-003).
- Upgrading `jaxb2-basics` to 0.12.0/0.13.1. Optional; `maven-jaxb2-plugin` 0.15.3 itself ships
  0.12.0 and coexisted with our 0.11.0 in the trial.

## Verification

1. Apply CR-001 first and confirm the JDK 8 suite is green; keep the generated mappings from
   `tests/*` and `samples/po` as the reference.
2. Apply this CR; `./mvnw -B install -Ptests -pl '!npm'` on JDK 11, 17 and 21.
3. Run the `full` jar on JDK 8 (if `release` 8 was chosen), 11, 17 and 21 against
   `samples/po/src/main/resources/purchaseorder.xsd`; all three output files must be produced.
4. Diff generated mappings against the reference. Without CR-003 expect reordering only; with
   CR-003 expect no diff.
5. Consumer check: a throwaway project using `org.jvnet.jaxb2.maven2:maven-jaxb2-plugin:0.15.3`
   with `-Xjsonix` and this plugin as a dependency generates mappings.

## Risks

- Low for the code: three import lines, no logic change.
- Medium for consumers of the **`plugin`** jar on Ant/XJC classpaths that still ship JAXB 2.2.x:
  after this change the plugin needs JAXB 2.3.1+. State this in release notes.
- `release` 11 drops the ability to run the CLI on JDK 8. Choose `release` 8 if that matters.

## Effort

Half a day including CI matrix and README updates; the trial migration itself took under an hour.
