# CR-010: Generated element factories (docx4j's `ObjectFactory` for the TypeScript model)

**Status:** Implemented 2026-09-10 (review fixes in section 8, implementation notes in section 9)
**Depends on:** CR-005 (TypeScript output; the `.d.mts` / `.mjs` emitter this extends), CR-007
(the OfficeOpenXML adjustments; the factories read the same model), CR-009 (the objects
repository that ships the output)
**Requested by:** `plutext/docx4j-core-ts` CR-002 section 3.2, which decided that the wrappers
should be generated rather than hand-written
**Recommended order:** after CR-009

## 1. Summary

The TypeScript model puts every item of a content list into a `{ name, value }` pair
(`TypedNamedValue`): `w:p` in a body is `{ name: { namespaceURI: W, localPart: 'p' }, value: P }`.
Consumers write those qualified names by hand today:

```ts
body.content.push({ name: { namespaceURI: W, localPart: 'p' }, value: { content: [{ name: { namespaceURI: W, localPart: 'r' }, value: { ... } }] } });
```

docx4j's Java model has `org.docx4j.wml.ObjectFactory` for this: 834 `JAXBElement` wrapper
methods (`createRT(Text)` for `w:t` in a run, `createBodyPermStart`, `createSettings` for a root
element) and 375 no-argument creators (`createP()`). This CR adds a fourth generator that
emits the equivalent per module, from the same model the mapping is compiled from:

- **element wrappers**, one per element declaration of the module (scoped ones named by XJC's
  rule, global ones with an `Element` suffix), returning the `TypedNamedValue` with the right
  QName;
- **creators**, one per class info, that return a typed object with `TYPE_NAME` set;
- an **`el` module** of per-local-name wrappers for the module's own namespace, with union
  value types where one QName has several types, the compact form for hand-written and
  LLM-written code.

Everything is derived; nothing is hand-maintained. The output is small, tree-shakeable ES
modules with declarations, next to the mapping.

## 2. What the model already knows

Reproduced 2026-09-10 on `org_docx4j_wml.mjs` (objects package 0.1.0):

| | Count | Where in the mapping |
|---|---|---|
| Element declarations | 897 | `elementInfos`: `{ typeInfo, elementName, scope? }` |
| of which scoped (JAXB `@XmlElementDecl(scope = R.class)`) | 860 | `scope: '.R'`, `'.CTRPrChange.RPr'` |
| of which global (roots: `document`, `styles`, `settings`, ...) | 37 | no `scope` |
| Container-to-element entries on content properties | 976 | `propertyInfos[].elementTypeInfos[]` of `elementRef`, `elementRefs` and `elements` properties (34 such properties) |
| Distinct local names | 200 | |
| Local names mapping to more than one type | 5 | `r`, `rPr`, `t`, `customXml`, `sdt` |

So the (scope, element name, type) triples XJC used for `ObjectFactory` are all present. The
wrapper set is exactly the module's **element declarations** (`elementInfos`, 897 in WML: 860
scoped and 37 global), one wrapper per declaration. The 976 property-level `elementTypeInfos`
entries are not the set: several properties hold the same declaration, and some hold
declarations that belong to other modules (`wp:inline` inside `Drawing` is declared in the
DrawingML module), whose wrappers come from *that* module's factory. Properties of type
`element` hold their value directly and need nothing.

The five local names with more than one type are of two kinds, and the design must
distinguish them: `sdt` and `customXml` are **one QName with four types by scope**
(`SdtBlock`, `SdtRun`, `CTSdtRow`, `CTSdtCell`); `r`, `rPr` and `t` are **two QNames**, `w:r`
and `m:r` (and so on), because `RunTrackChange`, `RunIns`, `RunDel` and
`CTMathRunTrackChange` hold math runs and WML therefore declares the math elements in those
scopes.

## 3. Output

Per module, two ES modules beside the mapping, each with declarations: `<Module>.factory.mjs`
(+ `.factory.d.mts`) for creators and wrappers, and `<Module>.el.mjs` (+ `.el.d.mts`) for the
compact per-name form (the `.js` UMD form is not needed: factories are for TypeScript and
bundlers). For `org_docx4j_wml`:

```ts
import type { P, R, Text, Body, CTSettings, TypedNamedValue } from './org_docx4j_wml';
const W = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main';

// 3.1 creators: one per class info, TYPE_NAME set, optional initialiser (docx4j createP())
export function createP(init?: Omit<P, 'TYPE_NAME' | 'PARENT'>): P;
export function createR(init?: ...): R;
export function createText(init?: ...): Text;
// ... 375 in WML

// 3.2 element wrappers: one per element declaration; scoped ones by XJC's rule (docx4j createRT, createBodyPermStart)
export function createRT(value: Text): TypedNamedValue<Text>;                 // scope R, element w:t
export function createBodyP(value: P): TypedNamedValue<P>;                     // scope Body, element w:p (no JAXBElement in Java; needed here)
export function createPR(value: R): TypedNamedValue<R>;                        // scope P, element w:r
export function createRunInsR(value: Dep_org_docx4j_math.CTR): TypedNamedValue<Dep_org_docx4j_math.CTR>;   // scope RunIns, element m:r
// global ones with an Element suffix, matching the PElement / SettingsElement aliases of the declarations
export function createSettingsElement(value: CTSettings): SettingsElement;    // global element w:settings
export function createPElement(value: P): PElement;                           // global element w:p
// ... 897 in WML

// 3.3 org_docx4j_wml.el.mjs: per-local-name wrappers for the module's own namespace; `import * as el`
export function p(value: P): TypedNamedValue<P>;
export function t(value: Text): TypedNamedValue<Text>;
export function sdt(value: SdtBlock | SdtRun | CTSdtRow | CTSdtCell): TypedNamedValue<SdtBlock | SdtRun | CTSdtRow | CTSdtCell>;  // one QName, four types
// no `r` for m:r: foreign-namespace declarations are reached through their scoped wrapper (createRunInsR) or the math module's el
// ... one per local name of the module's namespace (197 in WML)
```

Each wrapper sets `TYPE_NAME` on the value when it is absent (a literal) **where the
declaration determines the type**: every scoped and global wrapper, and every `el` function
for a name with one type. An `el` function for a name with several types (`sdt`, `customXml`)
sets the QName only and leaves `TYPE_NAME` alone; the scoped wrappers (`createBodySdt`,
`createPSdt`) exist for the precise form. Content built with the factories then answers
`TYPE_NAME` navigation like unmarshalled content. `PARENT` is not set: that is the container's
job (docx4j's `addObject`, core-ts CR-002 `insertElement`), as in Java where `ObjectFactory`
does not link parents either.

### Naming

- Creators: `create` + class name (`createP`, `createCTSettings`, `createSectPrPgSz` for the
  nested `SectPr.PgSz`), the JAXB names docx4j users know.
- Scoped wrappers: `create` + scope class name + element local name capitalised (`createRT`,
  `createBodyP`, `createCTSdtContentRunR`), XJC's rule verbatim. A nested scope
  (`'.CTRPrChange.RPr'`) contributes its outer and inner names (`createCTRPrChangeRPrB`).
- Global wrappers: `create` + element local name capitalised + `Element` (`createSettingsElement`,
  `createDocumentElement`), matching the `SettingsElement` / `DocumentElement` type aliases
  the declarations already emit. In Java the global wrapper overloads the creator
  (`createComments()` and `createComments(Comments)`); that is not available here, because
  28 of WML's 37 global elements share a name with a class and the creators take an optional
  `init`, so both forms have one argument and nothing can dispatch between them. The suffix
  removes the overload entirely.
- `el`: a module of named exports, one per local name of the module's own namespace, used as
  `import * as el` and then `el.p(...)`, `el.delText(...)`. A local name that is a reserved
  word is exported with `as` (`export { _delete as delete }`) and used as `el.delete(...)`.
  Element declarations in a foreign namespace (the math ones in WML) are not in `el`; keying
  on the local name would conflate `w:r` and `m:r`, which are different QNames.
- Any remaining collision between generated names (a class named like a scoped wrapper) is
  detected by the generator and fails the build with the two sources named; XJC-style numeric
  suffixes are not used, because a silent rename would change a public name later.
- A prefix option in the bindings (`jsonix:factory prefix="make"`) exists for consumers who
  want no `create` verb; default `create`.

### Size

WML: 375 creators + 897 wrappers + 197 `el` functions, one line each, about 90 KB of `.mjs`
and 120 KB of `.d.mts` before compression, against the 1.9 MB `org_docx4j_wml.mjs` mapping.
Every function, in both files, is a named export of an ES module with no side effects, so a
bundle that uses `createBodyP` and `el.t` carries two of them; this is why `el` is a module
and not an object literal, which a bundler would have to keep whole.

## 4. Implementation

- `TypeScriptModulesGenerator` gains a sibling `FactoryModulesGenerator` (or a mode of the
  same), enabled by a compiler option `-generateFactories` (alias `-Xjsonix-generateFactories`,
  the pattern of every other option in `Settings`) or the bindings customization
  `jsonix:factories`; off by default so existing consumers see no new files. It requires the
  TypeScript output, whose declarations the factory declarations import.
- Source of truth: the same `Module` / `Mapping` definition objects CR-005 walks, which keep
  `elementInfos` (with scope) and every property's `elementTypeInfos`; no second pass over
  XJC is needed. Ordering follows CR-003 (deterministic, so regeneration with unchanged
  inputs is an empty diff).
- The declaration emitter reuses `Ts` / `TsNode` and the type-name resolution of
  `CreateTsTypeVisitor` (cross-module types are imported from the dependency's declarations,
  as `Dep_org_docx4j_math.CTR` is today).
- `OfficeOpenXML/generate.sh` passes the option, so the objects repository receives
  `modules/<Module>.factory.mjs` + `.factory.d.mts` and `modules/<Module>.el.mjs` +
  `.el.d.mts`. Its `exports` map resolves `./modules/*` types to `.d.ts`, which these files do
  not have, so it gains `./factory/*` (to `./modules/*.factory.mjs` / `.factory.d.mts`) and
  `./el/*` (to `./modules/*.el.mjs` / `.el.d.mts`). Its `MODULE_NAMES` list is unaffected
  (factories are not mappings and do not enter the context). Its README says that a literal
  remains the stricter form: a creator's `init` is partial, so a literal is what checks
  required properties at compile time.

## 5. Tests

- `tests/typescript`: a compile test that builds a paragraph three ways (creators plus scoped
  wrappers, `el`, and a literal), marshals each through the runtime with the docx4j prefix
  table, and asserts the same XML:
  `<w:p><w:r><w:t xml:space="preserve">Hello</w:t></w:r></w:p>`; that a wrong value type is a
  compile error (`createRT(createP())`); and that `TYPE_NAME` is set on the wrapped value.
- Determinism: generate twice, diff empty (CR-003's harness).
- The objects repository's smoke imports `factory/org_docx4j_wml` and `el/org_docx4j_wml`
  and checks `createBodyP(createP()).name.localPart === 'p'`, `TYPE_NAME` on the wrapped
  value, and that `el.sdt` leaves `TYPE_NAME` unset.

## 6. Phases and effort

| Phase | Content | Effort |
|---|---|---|
| A | Generator for creators and scoped/global wrappers, `.mjs` + `.d.mts`, option, determinism test | 2 days |
| B | `el` module with union typing for one-QName-several-types names; the compile test; `generate.sh` and the objects repository's `exports` | 1 day |

## 7. Open questions

1. Whether to emit factories for all 94 OfficeOpenXML modules or only those with content
   lists. Recommendation: all, for uniformity; the small modules cost a few lines each.
2. `init` on creators: `Partial<T>` (any subset) or the exact required-properties type.
   Recommendation: `Omit<T, 'TYPE_NAME' | 'PARENT'>` made partial, so `createP()` with no
   arguments compiles even for types with required attributes; the marshaller reports what
   is missing, as it does for literals today.
3. Whether `el` should also be scoped (`el.body.p`) for `sdt` and `customXml` instead of
   union-typed. Recommendation: union-typed; the scoped `create*` names exist for precision.
4. A `text(s)` convenience that returns `el.t({ value: s, space: 'preserve' when needed })`
   belongs to the hand-written `builders/wml` of the objects repository, not here: the
   generator emits only what the model states.

## 8. Review and decisions (2026-09-10)

Review findings folded into the sections above:

1. Global wrappers are `create<Name>Element(value)`, not overloads of the creators: 28 of
   WML's 37 global element names are class names, and with an optional `init` on creators an
   `arguments.length` dispatch cannot work (section 3, Naming).
2. `el` covers the module's own namespace only. The `r` / `rPr` / `t` ambiguity in WML is
   `w:r` versus `m:r`, two QNames, not a scope ambiguity; keying on the local name would
   conflate them (section 2, section 3.3). `sdt` / `customXml` (one QName, four types) stay
   union-typed, and their `el` function does not set `TYPE_NAME`.
3. `el` is a module of named exports, so it tree-shakes (section 3, Size).
4. The option is `-generateFactories` / `-Xjsonix-generateFactories`; the objects package gets
   `./factory/*` and `./el/*` export entries because `./modules/*` resolves types to `.d.ts`
   (section 4).
5. The wrapper set is the module's element declarations (897 in WML), not the property-level
   `elementTypeInfos` (976, with duplicates and foreign declarations) (section 2).
6. A creator's partial `init` loses the compile-time required-property check; the objects
   README says a literal remains the stricter form (section 4). Open questions 1 to 4 are
   decided as recommended.

## 9. Implementation notes (2026-09-10)

- `compilation.typescript.FactoryCompiler`, invoked from `TypeScriptModuleCompiler.compile()` when
  the module's `TypeScript` definition has `factories` set: `-generateFactories` /
  `-Xjsonix-generateFactories` (`Settings`; implies the TypeScript output and also switches
  `factories` on for `jsonix:typeScript` customizations from the bindings) or
  `<jsonix:typeScript factories="true"/>` (`TypeScriptConfiguration`). Four files per module:
  `<base>.factory.mjs`, `<base>.factory.d.mts`, `<base>.el.mjs`, `<base>.el.d.mts`, through the
  existing `TextFileWriter`.
- **Names come from XJC itself.** A scoped or global wrapper is `create` + the element's XJC
  *squeezed name* (`CElementInfo.getSqueezedName()`, reached through the element info's
  `CMElementInfoOrigin`), which is what XJC's `ObjectFactory` uses: for a scoped element it
  already starts with the scope class (`RT` for `w:t` in `R`), and it carries `jaxb:class`
  customizations on element refs, which is how docx4j distinguishes `w:rPr` and `m:rPr` in
  `CTMathRunTrackChange` (`createCTMathRunTrackChangeRPr` and `createCTMathRunTrackChangeRPrMath`).
  Section 3's `createBodyP` does not exist: `w:p` has only its global declaration (`P` is a root
  element in docx4j), so its wrapper is `createPElement`, as is `createRElement` for `w:r`; `el.p`
  and `el.r` are the compact forms. Global wrappers take the `Element` suffix (section 8 item 1).
  A scoped wrapper whose XJC name a creator already holds (a nested class named after the element
  in the same scope: `SdtPr.Alias` and `w:alias` in `SdtPr`, which Java overloads) takes the
  `Element` suffix too: `createSdtPrAlias(init?)` creates, `createSdtPrAliasElement(value)`
  wraps. Any other collision fails the build naming both sources.
- Creators are emitted for non-abstract classes only (`CClassInfo.isAbstract()`), as JAXB's
  `ObjectFactory`. `init` is `Partial<Omit<T, 'TYPE_NAME' | 'PARENT'>>`.
- The factory `.d.mts` imports the module's declarations as `import type * as M` plus the six
  support types by name (`XmlQName`, ...); `TypeScriptModuleCompiler.setOwnQualifier("M.")` makes
  `ref()` qualify the module's own types while the factory is compiled, so `CreateTsTypeVisitor`
  is reused unchanged and cross-module types keep their `Dep_<module>` aliases.
- `el` covers the element declarations in the mapping's `defaultElementNamespaceURI` only. In WML
  that is 170 of 200 local names: the 30 others are math (`m:r`, `m:t`, ...) and Word 2010
  (`w14:glow`, `w14:ligatures`, ...) declarations scoped in WML classes, reached through their
  scoped wrappers or the owning module's `el`. A reserved-word name is exported with `as`
  (`el.object` in WML). One QName with several types (`sdt`, `customXml`) is union-typed and sets
  no `TYPE_NAME`.
- Tests: `FactoryOutputTest` (golden files `compiler/src/test/resources/typescript/po/PurchaseOrder.{factory,el}.{mjs,d.mts}`;
  naming rules on the `zero` schema), `tests/typescript` (`factories.ts` type-checks the three ways
  of building a purchase order and the `@ts-expect-error` cases; `esm-smoke.mjs` marshals them to
  identical XML and checks `TYPE_NAME` behaviour). `OfficeOpenXML/generate.sh` passes the option;
  the objects repository gained 376 files (3.1 MB, 94 modules × 4; WML: 1,282 factory exports,
  170 `el` exports), `./factory/*` and `./el/*` export entries, smoke checks and a README section.
