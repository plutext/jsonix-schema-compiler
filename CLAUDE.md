# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An XJC (JAXB schema compiler) plugin that generates [Jsonix](https://github.com/highsource/jsonix) JavaScript mappings, and optionally JSON Schema, from XML Schemas. Documentation lives in the [GitHub wiki](https://github.com/highsource/jsonix-schema-compiler/wiki); the README covers command-line, NPM, Ant and Maven usage.

## Toolchain: JDK 11 or newer

The build emits Java 11 bytecode (`maven.compiler.release` 11) and uses JAXB 2.3.9 (`javax.xml.bind`
line; `jaxb-api` and `javax.activation` are declared explicitly because they left the JDK in 11).
`maven-enforcer-plugin` rejects JDK 8 at `validate`. Any of the installed JDKs 17 or 21 work:

```
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
```

Use the wrapper (`./mvnw`, pinned to Maven 3.9.16). All plugin versions are pinned in the root pom's
`pluginManagement`. The `npm` module invokes `npm install` during `compile`, so `npm` must be on the
PATH for a full build (or exclude it with `-pl '!npm'`); it also drops `lib/`, `node_modules/` and
`package-lock.json` into `npm/`, which are not tracked.

Generated output order is deterministic (CR-003): `definition.Mapping` returns class/enum infos sorted
by scoped local name and element infos by (namespace, local part, scope) via `InfoComparators`, so
generated files can be diffed across toolchains. `DeterministicOrderTest` guards this.

## Build and test commands

```
# Default build (compiler, plugin, full, npm). Default goal is install.
./mvnw install

# Skip the npm module
./mvnw install -pl '!npm'

# Compiler unit tests only / a single test class
./mvnw -pl compiler test
./mvnw -pl compiler test -Dtest=JsonixPluginZeroTest -DfailIfNoTests=false

# Unit + integration tests (tests/ module is behind the `tests` profile). This is what CI runs.
./mvnw clean install -Ptests -pl '!npm'

# One integration module only (resolves org.hisrc.jsonix:jsonix-schema-compiler from ~/.m2,
# so the root pom and compiler must have been installed first)
./mvnw -Ptests -pl tests/issues test

# Everything: tests + samples + dist
./mvnw install -Pall
```

Run the standalone CLI from the built shaded jar (exercises the same code path as `full`'s tests):

```
java -jar full/target/jsonix-schema-compiler-full-<version>.jar \
  [-compact] [-generateJsonSchema] [-logLevel TRACE] [-d outdir] schema.xsd [-b bindings.xjb]
```

Compiler tests write generated output under `compiler/target/generated-sources/...`; inspect the `.js` / `.jsonschema` files there when debugging generation.

Migration proposals (Java 11, JAXB 2.3, deterministic output, Jakarta) live in `docs/change-requests/`.

## Module layout

- `compiler/` — all the logic. `JsonixPlugin` is registered as an XJC plugin via `META-INF/services/com.sun.tools.xjc.Plugin`. JAXB/XJC are `provided` scope here.
- `plugin/` — shaded jar of `compiler` (for the Ant/xjc classpath). Contains only a `Dummy` class.
- `full/` — shaded executable jar bundling XJC, the JAXB runtime, `jaxb-api`, `javax.activation` and slf4j-simple. Adds `JsonixMain` (CLI entry point) and the `TargetDirectory*Writer` classes that write files to disk.
- `npm/` — wraps the `full` jar as `lib/jsonix-schema-compiler-full.jar`. `npm/package.json` is *generated* by resource filtering from `npm/src/main/npm/package.json`; edit the latter.
- `tests/` (profile `tests`) — integration tests driven by `maven-jaxb2-plugin` 0.15.3: `zero`, `filter`, `wps`, and `issues` (GitHub issue regressions). `tests/zero` uses the 2010-era `legato-testing` JsUnit runner and needs `--add-opens java.base/java.net` (set in its surefire config).
- `samples/po` (profile `samples`), `dist/` (profile `dist`), `demos/po-npm` (not built by Maven).

## Compilation pipeline (compiler module)

Both entry points (`JsonixPlugin.run` inside XJC, `JsonixMain` standalone) end up in `execution.JsonixInvoker.execute`, which runs these stages in order:

1. **Settings** (`settings.Settings`, args4j). Every option has a plain form and an `-Xjsonix-` alias (`-compact` / `-Xjsonix-compact`, `-logLevel`, `-defaultNaming`, `-generateJsonSchema`, `-d`). `args4j.PartialCmdLineParser` lets the plugin consume only its own options from the XJC arg list.
2. **Configuration** (`configuration.*`). `ModulesConfigurationUnmarshaller` pulls `jsonix:*` binding customizations (namespace `http://jsonix.highsource.org/customizations`) out of the XJC `Model` and unmarshals them with JAXB into `ModulesConfiguration` / `ModuleConfiguration` / `MappingConfiguration` / `OutputConfiguration` / `JsonSchemaConfiguration` (+ includes/excludes). `jsonix:packageMapping` is deprecated and converted to a mapping. Defaults are applied when no customization exists (one output named `${module.name}.js`, and `${module.name}.jsonschema` if JSON Schema generation is on).
3. **Model** — XJC's `Model` is converted to jaxb2-basics' `MModelInfo` via `XJCCMInfoFactory`. Everything downstream is generic over `<T, C extends T>` (`NType`/`NClass` in practice) and works only with `MModelInfo`, never XJC classes.
4. **Analysis** (`analysis.*`). `ModelInfoGraphAnalyzer` builds a JGraphT dependency graph over package/type/property/element vertices. `ModulesConfiguration.build` uses it to resolve includes/excludes and inter-mapping dependencies, producing the immutable `definition.*` objects (`Modules` → `Module` → `Mapping`, plus `Output` and `JsonSchema`). `Modules` enforces that a package is mapped under one mapping name and one schema id.
5. **Mapping compilation** (`compilation.mapping.*`). `ModulesCompiler` → `ModuleCompiler` (emits the UMD-style module wrapper: AMD `define`, `module.exports`, or globals) → `MappingCompiler` → `typeinfo.*Compiler` classes, all building JS via `org.hisrc.jscm` js-codemodel. One `JSProgram` per (module, output) is handed to a `ProgramWriter` (`CodeModelProgramWriter` writes into XJC's `JCodeModel` as a resource; `TargetDirectoryProgramWriter` in `full` writes files).
6. **JSON Schema generation** (`compilation.jsonschema.*`), mirroring step 5: `JsonSchemaModulesGenerator` → `JsonSchemaModuleCompiler` → `JsonSchemaMappingCompiler` → `typeinfo.*Producer`, building `javax.json` structures via `jsonschema.JsonSchemaBuilder` and handing them to a `JsonStructureWriter`.

Practical consequences:

- Built-in XSD types have **two** parallel implementations, `compilation/mapping/typeinfo/builtin/*TypeInfoCompiler` and `compilation/jsonschema/typeinfo/builtin/*TypeInfoProducer`, dispatched from `CreateTypeInfoCompiler` / `CreateTypeInfoProducer`. Changes to type handling usually need both.
- Property names in generated mappings come from `naming.Naming` (`StandardNaming` vs `CompactNaming`); the naming is chosen per `Output`, so one module can emit both `.std.js` and `.cmp.js` (see `samples/po/src/main/resources/bindings.xjb`).
- Logging goes through `context.JsonixContext`, whose `slf4j.Levelled*` wrappers apply `-logLevel` regardless of the SLF4J binding present. `compiler` only has `slf4j-simple` at test scope; `full` ships `simplelogger.properties`.

## Test conventions

- Compiler unit tests (`compiler/src/test`) drive XJC in-process: build an `Options` with `-extension -Xjsonix [...]`, `ModelLoader.load`, `model.generateCode`, then `codeModel.build`. Test schemas live in `compiler/src/test/resources` (OGC schemas, `basic/zero`, `jsonschema/*`).
- Issue regressions go in `tests/issues`: add types to `src/main/resources/schema.xsd` (bindings in `binding.xjb`, package `org.hisrc.jsonix.tests.issues`), let `maven-jaxb2-plugin` generate the Java classes, then assert in an `IssueGHC<N>Test`. Commit messages follow "Fixes #N." / "Issue #N.".

## Versioning and release

See `RELEASING.md`. The version string appears in every module pom and in `npm/src/main/npm/package.json`; bump with `./mvnw versions:set -Pall -DnewVersion=...`. The `release` profile attaches javadoc and GPG signatures. This fork cannot publish to the upstream `org.hisrc.jsonix` coordinates or npm package name; no `distributionManagement` is configured.
