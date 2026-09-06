# CR-005: TypeScript output

**Status:** Implemented (2026-09-06), phases 1 and 2
**Depends on:** CR-003 (stable ordering; generated `.d.ts` files should be diffable), CR-004 (current toolchain)
**Recommended order:** after CR-004

## Summary

Add a third generator next to the Jsonix mapping (`.js`) and JSON Schema (`.jsonschema`)
generators that emits **TypeScript declarations for the unmarshalled data model** of each module
(`<Module>.d.ts`), so that TypeScript consumers get typed access to what Jsonix produces and
accepts. A second, smaller phase emits the mapping itself as an **ES module** (`.ts`/`.mjs`)
instead of the current UMD wrapper, which is what TypeScript build pipelines actually import.

Nothing of the kind exists today: `jsonix` on npm ships no `types`, there is no `@types/jsonix`,
and upstream closed "Typescript typings generator" (highsource/jsonix#156, 2016) with "too much
effort, would need a TypeScript code model". The alternative tried in that issue, feeding the
generated JSON Schema to `json-schema-to-typescript`, was reported not to work, and for structural
reasons cannot (see "Rejected alternative").

## What the generated types describe

Jsonix unmarshals XML into plain objects whose shape is fully determined by the mapping. From the
runtime (`Jsonix-all.js` 2.2.1, the version the tests use) the representation is:

| Schema construct | Runtime value | TypeScript |
|------------------|---------------|------------|
| complex type `Foo` in mapping `PO` | object with optional discriminant `TYPE_NAME: 'PO.Foo'` and one key per property (`propertyInfo.name`, i.e. the JAXB private name) | `interface Foo extends Base { TYPE_NAME?: 'PO.Foo'; ... }` |
| nested/anonymous type `Items.Item` | class info with scoped local name | `namespace Items { interface Item }` (interface + namespace merging keeps the dotted name literally) |
| global element `purchaseOrder` of type `PurchaseOrderType` | `{ name: QName, value: PurchaseOrderType }` (what `unmarshal*` returns) | `type PurchaseOrderElement = TypedNamedValue<PurchaseOrderType>`; plus `type RootElement = A \| B \| ...` over all element infos |
| property, `required` | always present | non-optional member |
| property, `collection` | array | `T[]` |
| `element` property | value of the type | `T` |
| `elements` property (choice) | one of several types | `A \| B` (array if collection) |
| `elementRef` / `elementRefs` | `{ name: QName, value: T }` objects; DOM nodes if `allowDom`; strings if `mixed` | `TypedNamedValue<A> \| TypedNamedValue<B> [\| Element] [\| string]` |
| `anyElement` | anything, DOM or typed | `any` (or `TypedNamedValue<unknown> \| Element \| string`) |
| `attribute` | value of the type | `T` |
| `anyAttribute` | object keyed by attribute name | `{ [name: string]: string }` |
| `value` (simple content) | value of the type | `T` |
| enumeration with string base | one of the lexical values | `type Color = 'red' \| 'green'` |
| enumeration with non-string base or unsupported values | base type | base type |
| xs:string family, anyURI, ID/IDREF, NMTOKEN, Name, NCName, token, language, normalizedString | string | `string` |
| base64Binary, hexBinary | string (encoded) | `string` |
| integer family (int, long, short, byte, unsigned*, positive*, negative*), decimal, float, double | number | `number` |
| boolean | boolean | `boolean` |
| dateTime, date, time, gYear, gMonth, gDay, gYearMonth, gMonthDay | `Jsonix.XML.Calendar` object (`year`, `month`, `day`, `hour`, `minute`, `second`, `fractionalSecond`, `timezone`, each possibly `NaN`); JS `Date` accepted on marshal | `XmlCalendar` (marshal input: `XmlCalendar \| Date`) |
| duration | Jsonix duration object (verify field set against `Jsonix.Schema.XSD.Duration` at implementation time) | `XmlDuration` |
| QName | `Jsonix.XML.QName` | `XmlQName` (`namespaceURI`, `localPart`, `prefix?`) |
| xs:list / `MList` | array of item type | `T[]` |
| anySimpleType | string | `string` |
| anyType | anything | `any` |
| IDREFS, NMTOKENS, ENTITIES (built-in lists) | array of strings | `string[]` |

Support types (`TypedNamedValue<T>`, `XmlQName`, `XmlCalendar`, `XmlDuration`) are small and
stable; they are emitted once per generated file so each `.d.ts` is self-contained and needs no
`jsonix` typings (there are none). Cross-module references (a mapping whose `dependencies` include
another module) become `import type { ... } from './<other module file>'`.

The `naming` (standard vs compact) affects only the mapping object's keys, not the data, so there
is **one** `.d.ts` per module regardless of how many `jsonix:output`s it has.

Example, for `samples/po` (`PurchaseOrder.d.ts`, abbreviated):

```ts
export interface XmlQName { namespaceURI: string; localPart: string; prefix?: string; }
export interface XmlCalendar { year?: number; month?: number; day?: number; hour?: number;
  minute?: number; second?: number; fractionalSecond?: number; timezone?: number; }
export interface TypedNamedValue<T> { name: XmlQName; value: T; }

export interface USAddress {
  TYPE_NAME?: 'PO.USAddress';
  name: string; street: string; city: string; state: string; zip: number;
  country?: string;
}
export interface Items { TYPE_NAME?: 'PO.Items'; item?: Items.Item[]; }
export namespace Items {
  export interface Item {
    TYPE_NAME?: 'PO.Items.Item';
    productName: string; quantity: number; usPrice: number;
    comment?: string; shipDate?: XmlCalendar; partNum: string;
  }
}
export interface PurchaseOrderType {
  TYPE_NAME?: 'PO.PurchaseOrderType';
  shipTo: USAddress; billTo: USAddress; comment?: string; items: Items; orderDate?: XmlCalendar;
}
export type CommentElement = TypedNamedValue<string>;
export type PurchaseOrderElement = TypedNamedValue<PurchaseOrderType>;
export type RootElement = CommentElement | PurchaseOrderElement;

/** The Jsonix mapping exported by PurchaseOrder.std.js / .cmp.js. */
export declare const PO: JsonixMapping;
```

With that, `const po = (unmarshaller.unmarshalString(xml) as PurchaseOrderElement).value;` gives
`po.shipTo.name` as `string` and flags `po.shipTo.nme` at compile time.

## Design

### Where it plugs in

The existing pipeline (see `CLAUDE.md`) already has two sibling emitters driven from
`execution.JsonixInvoker`: `compilation.mapping.ModulesCompiler` (JS via js-codemodel, written by a
`ProgramWriter`) and `compilation.jsonschema.JsonSchemaModulesGenerator` (javax.json structures,
written by a `JsonStructureWriter`). Both consume the same `definition.Modules` / `Mapping` model,
which since CR-003 yields class, enum and element infos in a stable order. The TypeScript generator
is a third sibling with the same shape:

- `configuration.TypeScriptConfiguration` (`jsonix:typeScript fileName="${module.name}.d.ts"`),
  registered in `ModulesConfigurationUnmarshaller`, `PluginCustomizations` and
  `ModuleConfiguration.build` exactly like `JsonSchemaConfiguration`; default file name pattern
  `${module.name}.d.ts`.
- `settings.Settings`: `-generateTypeScript` / `-Xjsonix-generateTypeScript` (mirrors
  `-generateJsonSchema`); `JsonixInvoker` builds a default `TypeScriptConfiguration` when set.
- `definition.TypeScript` (file name) and `Module.getTypeScripts()`.
- `compilation.typescript.TypeScriptModulesGenerator` → `TypeScriptModuleCompiler` →
  `TypeScriptMappingCompiler` → `typeinfo.*TypeProducer` visitors. Output is a tiny in-memory
  TypeScript AST (`TsType`: primitive, reference, literal, union, array; `TsInterface`,
  `TsTypeAlias`, `TsNamespace`, `TsImport`) with a single `TsWriter` that renders it with stable
  formatting. No TypeScript code model dependency is needed; declarations are a small grammar. This
  answers the upstream objection in jsonix#156.
- A generic `TextFileWriter<T, C>` interface (`writeTextFile(Module, String contents, String fileName)`)
  with `CodeModelTextFileWriter` (XJC `JTextFile`, like `CodeModelJsonStructureWriter`) and
  `TargetDirectoryTextFileWriter` (in `full`). `JsonStructureWriter` could later be reimplemented on
  top of it; not required.

### Rules that need care

- **Identifier hygiene.** Property names come from `MPropertyInfo.getPrivateName()` and are JAXB
  Java identifiers, hence valid TypeScript identifiers, but TypeScript reserved words (`default`,
  `class`, `function`) must be quoted. Type names are JAXB class names; scoped names use the
  namespace trick above.
- **Element type names.** Element infos are named `<LocalPart>Element` (initial upper-cased); scoped
  elements (`scope != null`) are emitted inside the scope's namespace. Collisions after mangling are
  resolved by appending the namespace-URI-derived suffix, deterministically.
- **Inheritance.** `baseTypeInfo` becomes `extends`; properties are not repeated. Abstract types
  are ordinary interfaces.
- **Substitution groups.** An `elementRef` whose element is a substitution head accepts any
  substituting element; type the value as the union of the head's type and all substituting
  elements' types found in the model (the analyzer already has all element infos).
- **Dependencies.** For a mapping depending on another mapping's types, emit
  `import type { X } from './<file>'` using the dependency module's TypeScript file name
  (`Modules` knows mapping → module). If the dependency module has no TypeScript configuration,
  fall back to `any` and log a warning.
- **`TYPE_NAME`** is emitted optional (Jsonix sets it on unmarshal; users need not set it on
  marshal) as a string literal, which makes `interface` unions discriminable with
  `switch (x.TYPE_NAME)`.

### Phase 2: mapping as ES module

Today the mapping file is a UMD wrapper (`define` / `module.exports` / global). Angular and other
bundlers choke on it (jsonix#202 "define cannot be used indirect"). Add an output flavour
`jsonix:output naming="standard" format="esm" fileName="${module.name}.mjs"` (and `format="ts"`
producing `export const PO = { ... } as const;` plus `export default PO;`). Implementation: render
the existing js-codemodel object literal for the mapping into a string and wrap it with the export
statement in `ModuleCompiler`; dependencies need no imports because Jsonix resolves them by name
in `new Jsonix.Context([PO, XLink])`. The `.d.ts` from phase 1 then declares
`export declare const PO: JsonixMapping` and matches the ESM file's exports.

## Rejected alternative: JSON Schema → TypeScript

The `.jsonschema` output models the *XML* structure for validation: element infos as `anyOf` of
`{name, value}` objects, types via `allOf` + `$ref`, Jsonix-specific keys (`elementName`, `scope`,
`typeInfo`) and `#/definitions` refs across files. A generic converter produces unions of
anonymous objects with no `TYPE_NAME` discriminant, no Calendar/QName shapes, and no notion of
`elementRef` wrappers. jsonix#156 reports exactly this ("it didn't work"). Generating from the
`definition` model directly is both simpler and correct.

## Testing

- Unit: compile `basic/zero` and `samples/po` in `compiler` tests and assert on the produced text
  (golden files under `src/test/resources/typescript/`, made feasible by CR-003).
- Type-check: a new `tests/typescript` module (profile `tests`) that runs `jaxb-maven-plugin`
  with `-Xjsonix -Xjsonix-generateTypeScript` on the `po` and `zero` schemas, then
  `exec-maven-plugin` → `npx tsc --noEmit --strict` over the generated `.d.ts` files plus a
  hand-written `usage.ts` that unmarshals `po.xml` through `jsonix` and reads `po.shipTo.name`,
  with `// @ts-expect-error` lines for a misspelt property and a wrong element type. Requires
  `node`/`npm` on the PATH, like the existing `npm` module; skipped when absent
  (`-Dskip.typescript`).
- CI: add the type-check step to the workflow.

## Out of scope

- Typings for the `jsonix` runtime itself (`Jsonix.Context`, `Unmarshaller`). Upstream has a
  hand-written `typescript/src/main/typescript/Jsonix.d.ts` (jsonix#139, #237, unmerged fixes);
  the generated files are deliberately independent of it. A follow-up could publish it under
  `@types/jsonix` or ship it from this fork's npm package.
- Generating TypeScript *classes* with methods, or runtime type guards. Interfaces plus the
  `TYPE_NAME` discriminant cover the use cases in jsonix#139 without runtime code.
- Changing property naming (`-Xjsonix-compact` stays about mapping size; data keys are unchanged).

## Effort

- Phase 1 (declarations, config, writers, unit tests, `tsc` check module, docs): two to three days.
- Phase 2 (ESM/TS mapping output): about one day.

## Decisions (recommended answers to the open questions)

1. **Emit `.d.ts` only.** Interfaces have no runtime; a `.ts` source would only add a compile step
   in the consumer's project under their own `tsconfig`. Declarations beside the `.js` are found by
   module resolution, which makes file naming part of the contract: the default `${module.name}.d.ts`
   pairs with the default `${module.name}.js`, and for every `jsonix:output` whose file name differs
   (e.g. `PurchaseOrder.std.js`) generate a one-line `PurchaseOrder.std.d.ts` that re-exports the
   module declaration file. For phase 2, default to `.mjs` plus that `.d.ts` rather than a `.ts`
   mapping source, so JavaScript and TypeScript consumers are served without compilation.
2. **Date-like properties are `XmlCalendar` only.** Jsonix never yields a `Date` on unmarshal, and
   unmarshalling is the dominant use; a `XmlCalendar | Date` union would force every reader to
   narrow for a case that cannot occur. Marshal callers write `{year: 2026, month: 9, day: 6}`
   instead of passing a `Date`. Separate input/output interfaces per type would double the output
   for that single convenience. If demand appears, add a flag that widens date-like properties to
   accept `Date`; it is purely additive.
3. **Nested namespaces for scoped types.** Interface + namespace merging keeps `Items.Item`
   literally identical to Jsonix's `localName` and to the `TYPE_NAME` discriminant
   (`'PO.Items.Item'`), so errors, `switch` statements and mapping files read alike. Flattening
   needs a mangling rule and a collision policy (`Items.Item` vs a real `Items_Item`). Namespaces
   in declaration files are type-only and erased, so `isolatedModules`-style settings do not
   object; usage is `import type { Items } from './PurchaseOrder'` then `Items.Item`. Lint presets
   that forbid namespaces normally exclude generated declarations.

## Implementation notes (2026-09-06)

Both phases implemented as designed, with these refinements found while type-checking real output:

- **`TYPE_NAME` is a union over the type and its (transitive) subtypes**, e.g.
  `'OWS.DescriptionType' | 'OWS.BasicIdentificationType' | ...`. A literal of only the type's own name
  made `extends` illegal (`Interface X incorrectly extends Y`), and is also wrong at runtime: a value
  typed as the base may carry any subtype's name. Subtypes are collected across all modules.
- **Builtin types are resolved through the property's schema component**, exactly as the mapping
  compiler does (`CollectSimpleTypeNamesVisitor`), because the model's builtin leaf info for every
  date-like type is one `XMLGregorianCalendar`; without this `xs:date` came out as `string`.
- **Enum literals are whitespace-normalised** for the `normalizedString` family, mirroring
  `NormalizedStringTypeInfoCompiler`, so the literal union matches the mapping's `values`.
- Re-export files: `<output>.d.ts` (`export * from './<module>'`) per `.js` output and
  `<output>.d.mts` (`export * from './<module>.js'`) per `.mjs` output; TypeScript's `bundler` and
  `node16` resolution map `.mjs` imports to `.d.mts` and need an explicit extension inside ES modules.
- Phase 2: `jsonix:output format="esm"` (default file names `${module.name}.mjs` /
  `${module.name}.compact.mjs`). `ModuleCompiler.compileEsm` renders the same js-codemodel object
  literal as the UMD output via `CodeWriter.expression`, so the mapping body is identical
  (verified by normalised diff); the module contains only `export const <Mapping> = ...` lines.
  `ModulesCompiler.compile(programWriter, textFileWriter)` routes ESM outputs to the text writer.
- Not done: a flag widening date-like properties to accept `Date`, flattened names, `.ts` sources
  (all rejected in "Decisions"); no typings for the `jsonix` runtime (out of scope).

Verification:

| Check | Result |
|-------|--------|
| `TypeScriptOutputTest` (compiler): golden `PurchaseOrder.d.ts`, re-export files, `.mjs` and `.d.mts`, `zero` schema coverage | green |
| `tests/typescript`: `tsc --strict` over generated `PurchaseOrder.d.ts`/`.d.mts` and `usage.ts` (incl. three `@ts-expect-error` negatives, `.mjs` import under `bundler` resolution) | green |
| `tests/typescript`: Node imports `PurchaseOrder.mjs`, unmarshals `po.xml` via `jsonix` 3.0.0; `TYPE_NAME`, numbers and calendar fields as declared | green |
| Ad-hoc `tsc --strict` over declarations for `basic/zero` and OGC OWS 1.1.0 + XLink (cross-module imports, substitution groups, inheritance) | green |
| Full suite on JDK 17 and 21 | green |

## Follow-ups adopted from the runtime repository (jsonix-CR-001, item 6)

The sibling `jsonix` repository implemented `docs/change-requests/jsonix-CR-001-typescript-consumers.md`
(runtime 3.1.0: generic `unmarshalString<E>()` / `marshalString(E)` typings, an ES-module entry
point, generated fixtures as a contract test). Its item 6 lists three follow-ups for this repository;
they are adopted here, with the action taken for each.

| Follow-up | Action taken (2026-09-06) |
|-----------|---------------------------|
| `tests/typescript` should depend on `@mitre/jsonix` at the version with the generic typings, not upstream `jsonix`, and `usage.ts` should drop the cast | Done. `tests/typescript/package.json` is now generated from `src/main/npm/package.json` with `"@mitre/jsonix": "${jsonix.runtime.dependency}"`. Because 3.1.0 is not on npm yet (latest published: 3.0.11), the property defaults to `file:../../../jsonix/nodejs/scripts` (the sibling checkout) and CI checks out `plutext/jsonix` next to the workspace and passes `-Djsonix.runtime.dependency=file:../../jsonix/nodejs/scripts`. Once 3.1.0 is published, change the default to `^3.1.0` and drop the CI checkout. `usage.ts` now imports `Jsonix` from `@mitre/jsonix`, calls `unmarshalString<PurchaseOrderElement>()` and `marshalString(outgoing)` with no casts, and asserts the generated `XmlCalendar`/`XmlQName` are assignable to `Jsonix.XML.Calendar`/`QName`; `esm-smoke.mjs` uses `import { Jsonix } from '@mitre/jsonix'` instead of `createRequire('jsonix')`. |
| `JsonixMapping` could carry a phantom root-element type parameter so a future generic `Context` can infer the unmarshal result type | Done. `export interface JsonixMapping<R = unknown> { readonly __rootElement?: R; readonly [key: string]: unknown; }` and `export declare const PO: JsonixMapping<RootElement>` (plain `JsonixMapping` when a mapping has no global elements). The optional phantom member is what makes the parameter meaningful to TypeScript (an unused parameter would be erased structurally). The runtime side can infer it as `M extends { __rootElement?: infer R } ? R : unknown`; it is additive and the runtime's current `Mapping = object` accepts it. `usage.ts` checks `NonNullable<typeof PO.__rootElement>` is `RootElement`. **Consequence for the runtime repository:** its committed fixture `tests/typescript/PurchaseOrder.d.ts` differs from the compiler's golden file by these two lines until it is regenerated from this commit. |
| The fork's npm package name (`npm/src/main/npm/package.json` still says `jsonix-schema-compiler`) | **Open, maintainer decision.** Recommendation: `@plutext/jsonix-schema-compiler`, mirroring the scoped `@mitre/jsonix` runtime; the upstream unscoped name cannot be published from this fork (see CR-001 "Publishing target" and `RELEASING.md`). Not changed here because it fixes the public install instructions in both repositories' READMEs at once. |
