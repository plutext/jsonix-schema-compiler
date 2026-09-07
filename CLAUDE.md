# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An XJC (JAXB schema compiler) plugin that generates [Jsonix](https://github.com/highsource/jsonix) JavaScript mappings, and optionally JSON Schema, from XML Schemas. Documentation lives in the [GitHub wiki](https://github.com/highsource/jsonix-schema-compiler/wiki); the README covers command-line, NPM, Ant and Maven usage.

## Toolchain: JDK 11 or newer

The build emits Java 11 bytecode (`maven.compiler.release` 11) and uses Jakarta XML Binding 4 (JAXB
4.0.9, `jakarta.xml.bind`) with the jaxb-tools 4.0.16 family (`org.jvnet.jaxb:jaxb-plugins-*`,
`jaxb-maven-plugin`). Binding files (`.xsd` annotations, `.xjb`) must use
`https://jakarta.ee/xml/ns/jaxb` version 3.0; the `xjc` namespace stays `http://java.sun.com/xml/ns/jaxb/xjc`.
`maven-enforcer-plugin` rejects JDK 8 at `validate`. Any of the installed JDKs 17 or 21 work:

```
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
```

Use the wrapper (`./mvnw`, pinned to Maven 3.9.16). All plugin versions are pinned in the root pom's
`pluginManagement`. The `npm` module invokes `npm install` during `compile`, so `npm` must be on the
PATH for a full build (or exclude it with `-pl '!npm'`); it also drops `lib/`, `node_modules/` and
`package-lock.json` into `npm/`, which are not tracked. `tests/typescript` (profile `tests`) also needs
`node`/`npm` (it runs `tsc` and a Node smoke test) and the `@docx4j/jsonix` runtime 3.2.0 (plutext/jsonix), which is
not on npm yet: its `package.json` is generated from `src/main/npm/package.json` with the dependency
taken from `-Djsonix.runtime.dependency` (default `file:../../../jsonix/nodejs/scripts`, i.e. the
sibling `plutext/jsonix` checkout next to this repository; CI checks it out into `jsonix/`). Skip the
module with `-Dskip.typescript=true`.

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
  [-compact] [-generateJsonSchema] [-generateTypeScript] [-logLevel TRACE] [-d outdir] schema.xsd [-b bindings.xjb]
```

Compiler tests write generated output under `compiler/target/generated-sources/...`; inspect the `.js` / `.jsonschema` files there when debugging generation.

Migration proposals (Java 11, JAXB 2.3, deterministic output, Jakarta) live in `docs/change-requests/`.

## Module layout

- `compiler/` — all the logic. `JsonixPlugin` is registered as an XJC plugin via `META-INF/services/com.sun.tools.xjc.Plugin`. JAXB/XJC are `provided` scope here, as are the Jakarta binding and activation APIs.
- `plugin/` — shaded jar of `compiler` (for the Ant/xjc classpath). Contains only a `Dummy` class.
- `full/` — shaded executable jar bundling XJC, the JAXB 4 runtime, the Jakarta APIs, slf4j-simple and the jaxb-tools XJC plugins (`-Xinheritance`, `-Xannotate`, `-XtoString`, ... plus XJC's own `-Xinject-code`), so schemas carrying those customizations (docx4j's, see `OfficeOpenXML/`) compile from the command line. Adds `JsonixMain` (CLI entry point) and the `TargetDirectory*Writer` classes that write files to disk.
- `npm/` — wraps the `full` jar as `lib/jsonix-schema-compiler-full.jar`. `npm/package.json` is *generated* by resource filtering from `npm/src/main/npm/package.json`; edit the latter.
- `tests/` (profile `tests`) — integration tests driven by `org.jvnet.jaxb:jaxb-maven-plugin` 4.0.16: `zero`, `filter`, `wps`, `issues` (GitHub issue regressions) and `typescript` (generates declarations and an ES module for the purchase order schema, type-checks them with `tsc` against `src/test/typescript/usage.ts`, and unmarshals `po.xml` through the Jsonix npm runtime from the `.mjs` mapping). `tests/zero` uses the 2010-era `legato-testing` JsUnit runner and needs `--add-opens java.base/java.net` (set in its surefire config).
- `samples/po` (profile `samples`), `dist/` (profile `dist`), `demos/po-npm` (not built by Maven).

## Compilation pipeline (compiler module)

Both entry points (`JsonixPlugin.run` inside XJC, `JsonixMain` standalone) end up in `execution.JsonixInvoker.execute`, which runs these stages in order:

1. **Settings** (`settings.Settings`, args4j). Every option has a plain form and an `-Xjsonix-` alias (`-compact` / `-Xjsonix-compact`, `-logLevel`, `-defaultNaming`, `-generateJsonSchema`, `-d`). `args4j.PartialCmdLineParser` lets the plugin consume only its own options from the XJC arg list.
2. **Configuration** (`configuration.*`). `ModulesConfigurationUnmarshaller` pulls `jsonix:*` binding customizations (namespace `http://jsonix.highsource.org/customizations`) out of the XJC `Model` and unmarshals them with JAXB into `ModulesConfiguration` / `ModuleConfiguration` / `MappingConfiguration` / `OutputConfiguration` / `JsonSchemaConfiguration` (+ includes/excludes). `jsonix:packageMapping` is deprecated and converted to a mapping. Defaults are applied when no customization exists (one output named `${module.name}.js`, and `${module.name}.jsonschema` if JSON Schema generation is on).
3. **Model** — XJC's `Model` is converted to jaxb-plugins' (formerly jaxb2-basics) `MModelInfo` via `XJCCMInfoFactory`. Everything downstream is generic over `<T, C extends T>` (`NType`/`NClass` in practice) and works only with `MModelInfo`, never XJC classes.
4. **Analysis** (`analysis.*`). `ModelInfoGraphAnalyzer` builds a JGraphT dependency graph over package/type/property/element vertices. `ModulesConfiguration.build` uses it to resolve includes/excludes and inter-mapping dependencies, producing the immutable `definition.*` objects (`Modules` → `Module` → `Mapping`, plus `Output` and `JsonSchema`). `Modules` enforces that a package is mapped under one mapping name and one schema id.
5. **Mapping compilation** (`compilation.mapping.*`). `ModulesCompiler` → `ModuleCompiler` (emits the UMD-style module wrapper: AMD `define`, `module.exports`, or globals) → `MappingCompiler` → `typeinfo.*Compiler` classes, all building JS via `org.hisrc.jscm` js-codemodel. One `JSProgram` per (module, output) is handed to a `ProgramWriter` (`CodeModelProgramWriter` writes into XJC's `JCodeModel` as a resource; `TargetDirectoryProgramWriter` in `full` writes files).
6. **JSON Schema generation** (`compilation.jsonschema.*`), mirroring step 5: `JsonSchemaModulesGenerator` → `JsonSchemaModuleCompiler` → `JsonSchemaMappingCompiler` → `typeinfo.*Producer`, building `javax.json` structures via `jsonschema.JsonSchemaBuilder` and handing them to a `JsonStructureWriter`.
7. **TypeScript declarations** (`compilation.typescript.*`, CR-005), enabled by `-generateTypeScript` or `jsonix:typeScript`: `TypeScriptModulesGenerator` → `TypeScriptModuleCompiler` (support types, cross-module `import type`, `TYPE_NAME` unions over subtypes, re-export files per output) → `TypeScriptMappingCompiler` (interfaces, enum literal unions, element aliases, `RootElement`) with `CreateTsTypeVisitor` (type info → TS type, resolving builtin XSD types through the property's schema component like the mapping compiler does) and `TsPropertyVisitor` (property kind → member). `HeldTypesCollector` indexes which classes can hold which (CR-006), from which `parentType` derives the `readonly PARENT?` union (containers of the type and of its subtypes, plus subclasses of those containers). Declarations are plain strings assembled in a `TsNode` namespace tree; there is no TypeScript code model. Output goes through `TextFileWriter` (`CodeModelTextFileWriter` in XJC, `TargetDirectoryTextFileWriter` in `full`). The same writer serves `jsonix:output format="esm"`, for which `ModuleCompiler.compileEsm` renders the mapping literal as `export const <Mapping> = {...};`.

Practical consequences:

- Built-in XSD types have **two** parallel implementations, `compilation/mapping/typeinfo/builtin/*TypeInfoCompiler` and `compilation/jsonschema/typeinfo/builtin/*TypeInfoProducer`, dispatched from `CreateTypeInfoCompiler` / `CreateTypeInfoProducer`. Changes to type handling usually need both.
- The TypeScript generator's golden file lives at `compiler/src/test/resources/typescript/po/PurchaseOrder.d.ts`; regenerate it from the compiler output when the emitted format changes deliberately. The sibling runtime repository (`../jsonix`, `docs/change-requests/jsonix-CR-001-typescript-consumers.md`) commits the same generated files as a contract test and must regenerate them after such a change.
- Property order comes from `definition.Mapping.getProperties(classInfo)`, which every emitter must use: attributes sorted by name (XSOM hash-orders attribute groups, CR-007), `jsonix:propertyOrder` first, elements in particle order. `jsonix:property defaultValue` overrides live in `Mapping` too and are applied by `CreateTypeInfoDelaration`.
- Property names in generated mappings come from `naming.Naming` (`StandardNaming` vs `CompactNaming`); the naming is chosen per `Output`, so one module can emit both `.std.js` and `.cmp.js` (see `samples/po/src/main/resources/bindings.xjb`).
- Logging goes through `context.JsonixContext`, whose `slf4j.Levelled*` wrappers apply `-logLevel` regardless of the SLF4J binding present. `compiler` only has `slf4j-simple` at test scope; `full` ships `simplelogger.properties`.

## Test conventions

- Compiler unit tests (`compiler/src/test`) drive XJC in-process: build an `Options` with `-extension -Xjsonix [...]`, `ModelLoader.load`, `model.generateCode`, then `codeModel.build`. Test schemas live in `compiler/src/test/resources` (OGC schemas, `basic/zero`, `jsonschema/*`).
- Issue regressions go in `tests/issues`: add types to `src/main/resources/schema.xsd` (bindings in `binding.xjb`, package `org.hisrc.jsonix.tests.issues`), let `maven-jaxb2-plugin` generate the Java classes, then assert in an `IssueGHC<N>Test`. Commit messages follow "Fixes #N." / "Issue #N.".

## Versioning and release

See `RELEASING.md`. The version string appears in every module pom and in `npm/src/main/npm/package.json`; bump with `./mvnw versions:set -Pall -DnewVersion=...`. The `release` profile attaches javadoc and GPG signatures. This fork cannot publish to the upstream `org.hisrc.jsonix` coordinates or npm package name; no `distributionManagement` is configured.
