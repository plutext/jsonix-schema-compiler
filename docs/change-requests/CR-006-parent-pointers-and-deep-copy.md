# CR-006: Parent pointers and deep copy for unmarshalled objects

**Status:** Compiler half implemented 2026-09-07; runtime half (jsonix-CR-002) pending
**Depends on:** CR-005 (TypeScript output). Runtime half: `jsonix-CR-002` in the sibling `jsonix` repository.
**Recommended order:** runtime first (jsonix-CR-002), then this CR's TypeScript part.

## Summary

docx4j's Java object model is generated with two XJC plugins beyond the ones this compiler already
accepts: `-Xparent-pointer` (jaxb-tools' `ParentPointerPlugin`: every generated class implements
`org.jvnet.jaxb.lang.Child` with `getParent()`/`setParent()`, filled in by JAXB's
`afterUnmarshal(Unmarshaller, Object parent)` callback) and `-Xdocx4j-copy`
(`docx4j-xjc-copy`: a generated `copy()`/`copyTo()` deep copy per class that re-links parent
pointers through `CopyUtils.copyObjectAndSetParent`). docx4j code navigates upwards
(`p.getParent()`) and clones subtrees (`XmlUtils.deepCopy`) constantly.

The JavaScript objects Jsonix produces have neither. This CR gives them both, with the same
semantics, so that a TypeScript consumer of the `OfficeOpenXML/` mappings can write what a docx4j
Java consumer writes:

```ts
const paragraph: P = ...;
const cell = paragraph.PARENT;           // Tc | Body | Hdr | ... (typed union of possible parents)
const clone = Jsonix.Util.deepCopy(paragraph); // children re-linked to clone; clone.PARENT unset unless passed as 2nd argument
```

Two halves:

1. **Runtime** (`jsonix` repository, jsonix-CR-002): an unmarshal option that sets a non-enumerable
   `PARENT` on every object a class info produces, and `Jsonix.Util.deepCopy(value, parent?)` that
   copies a value structurally and re-links `PARENT`.
2. **Compiler** (this CR): the generated declarations type `PARENT` as the union of the types that
   can contain the type, computed from the model, and document the option; plus the CLI facts
   below.

## What "support both plugins" means, and what already works

| Plugin | Effect in docx4j's Java | Standalone CLI (`full` jar) today | Jsonix/TypeScript today |
|--------|-------------------------|-----------------------------------|-------------------------|
| `-Xparent-pointer` (jaxb-tools `jaxb-plugins`, bundled since the `OfficeOpenXML/` change) | `Child` interface, `parent` field, `afterUnmarshal` sets it | accepted (`-Xparent-pointer` passes through XJC's option parsing); no Java is written because the CLI only loads the XJC model and never runs code generation | nothing |
| `-Xdocx4j-copy` (`docx4j-xjc-copy`, in the docx4j repository) | `copy()`/`copyTo()` with parent re-linking | accepted when the plugin and `docx4j-copy` jars are on the classpath: `java -cp full.jar:docx4j-xjc-copy-*.jar:docx4j-copy-*.jar org.hisrc.jsonix.JsonixMain -Xparent-pointer -Xdocx4j-copy ...` (verified on `samples/po`); no Java is written for the same reason | nothing |

So "supporting the plugins" in the XJC sense is already true and needs no code: docx4j's own
`docx4j-generated-objects` build (`-Xinject-code -Xparent-pointer -Xdocx4j-copy -Xinheritance
-Xannotate`) can add `-Xjsonix` and get mappings next to its Java. The substantive work is
**semantic parity on the JavaScript side**, which is what the rest of this CR is about. Note that
`docx4j-xjc-copy` generates Java from the XJC *outline*; it cannot and should not be reused for
JavaScript. The JavaScript copy is a runtime function driven by the object graph (and, where
available, the mapping), not generated code.

## Semantics (mirroring docx4j)

From `ParentPointerPlugin`, `CopyPlugin` and `CopyUtils` (read 2026-09-07):

- **Parent** is the containing object, never the element wrapper: for an `elementRef` property the
  runtime holds `{ name, value }`; `value.PARENT` is the object that owns the property, exactly as
  JAXB's `afterUnmarshal` receives the bean, not the `JAXBElement`. Root objects have no parent.
  Collections: each item's parent is the owner.
- **Copy**: value types are shared (strings, numbers, booleans, enum literals, `XmlDuration` in
  docx4j's list); `XmlCalendar` and `XmlQName` are cloned; DOM nodes are `cloneNode(true)`; objects
  and arrays are copied structurally (wrappers are not recognised by shape: a `{ name, value }`
  wrapper cannot be told from a typed object with properties named `name` and `value`, both common);
  `TYPE_NAME` is preserved. `PARENT` is re-linked from a map of original to copy, so every copied
  child points at its copied parent. The copy's own `PARENT` is **unset**, as docx4j's `copy()`
  leaves it; the optional second argument sets it, as `CopyUtils.copyObjectAndSetParent` does for
  a new owner.
- **Unknown properties** (not in the mapping) are copied structurally; the runtime does not need
  the mapping to copy, which keeps `deepCopy` usable on objects built by hand for marshalling.

## Runtime half (jsonix-CR-002, drafted alongside this CR)

- `new Jsonix.Context(mappings, { parentPointers: true })` (default `false`, so existing users see
  no change). When on, `ClassInfo.unmarshal` sets `PARENT` on the object it produces from a stack
  of enclosing results kept on the input, so only typed objects (class-info products) are parented,
  never element wrappers, simplified-style `{ localName: value }` objects, maps, DOM nodes,
  calendars or QNames, whichever mapping style is in use. The property is **non-enumerable and
  writable**, so `for...in`, `JSON.stringify`, `Jsonix.Util.Type.isEqual` and the marshaller ignore
  it and cycles never reach serialisers. This matches what the declarations describe: `PARENT` is
  declared on interfaces only.
- `Jsonix.Util.deepCopy(value, parent?)` with the semantics above; `Jsonix.Util.parentOf(value)`
  is not needed (the property is readable directly) but a `Jsonix.Util.setParent(value, parent)`
  helper keeps the non-enumerable definition in one place for code that builds trees by hand.
- Typings (`types/main.d.ts`): `ContextOptions.parentPointers?: boolean`;
  `deepCopy<T>(value: T, parent?: unknown): T`; `interface Parented<P = unknown> { readonly PARENT?: P }`.
- Tests: the existing `tests/typescript` purchase order round trip with `parentPointers: true`
  (`po.items.item[0].PARENT === po.items`, `po.shipTo.PARENT === po`), a copy whose children point
  at the copy and whose own `PARENT` is unset, marshalling a parented tree produces the same XML as
  an unparented one, and `isEqual(copy, original)` holds.

## Compiler half (this CR)

### Declarations

For every class info, emit

```ts
export interface P extends ... {
  TYPE_NAME?: 'org_docx4j_wml.P' | ...;
  readonly PARENT?: Body | Tc | CTSdtContentBlock | Hdr | Ftr | ... ;
  ...
}
```

where the union is computed from the model: every class info `C` with a property (element,
elements, elementRef, elementRefs, value-less wrappers) whose declared type is `T` or a supertype
of `T`, expanded through substitution groups and subtypes the same way `TsPropertyVisitor` already
expands element refs, plus `C`'s own subtypes (a `Tc` subclass is also a possible parent). Types
from other modules come in through the existing `import type * as Dep_...` mechanism. If nothing
can contain `T` (it only occurs as a global element), the member is omitted: such an object is
always a root. A union that would exceed a size limit (see open question 3) falls back to
`unknown` with a comment, to keep `org_docx4j_wml.d.ts` readable; the purchase order and `zero`
schemas stay fully typed.

`readonly` documents that consumers should not assign it; the runtime helper does. The member is
optional because parent pointers are an unmarshal option and hand-built objects have none.

### Where it goes

- `TypeScriptModuleCompiler`: a reverse index type → containing types, built once from
  `modules.getModelInfo()` (it already builds the subclass and substitution indexes; this is the
  third).
- `TypeScriptMappingCompiler.compileClassInfo`: emit the member after `TYPE_NAME`.
- `Ts.PARENT_PROPERTY = "PARENT"`; the support-types block gets nothing new (the union is inline).
- Option: `-Xjsonix-typeScript-parentUnionLimit=N` is not worth a flag; a constant with a comment
  is enough until someone needs it.
- Golden file and `DeterministicOrderTest`-style ordering: unions are sorted by name.

### Documentation

- README "TypeScript" section: `parentPointers` option and `deepCopy`, with the docx4j-flavoured
  example above.
- `OfficeOpenXML/README.md`: regenerate after this CR (declarations change; mappings do not) and
  show `PARENT` in the usage example.

## Testing

- Compiler: `TypeScriptOutputTest` golden update; new assertions on `zero` (an `ExtendedType`
  contained via an element ref shows both the base and the extension as possible parents; a
  substitution group member appears as a parent through the head).
- `tests/typescript`: `usage.ts` reads `po.items.item[0].PARENT` as `Items`, and `PARENT` on a
  root `PurchaseOrderType` is absent from the type (`@ts-expect-error`); the ESM smoke enables
  `parentPointers` and checks the pointers and a `deepCopy` round trip at runtime (needs the
  runtime half; until then the smoke checks are skipped when `Jsonix.Util.deepCopy` is missing).
- `OfficeOpenXML/`: the `document.xml` smoke gains `paragraph.value.PARENT === body`.

## Out of scope

- Generating JavaScript classes with `getParent()`/`copy()` methods (Jsonix objects are plain
  objects by design; `instanceFactory` users can add methods themselves).
- Running XJC's Java code generation from the standalone CLI.
- A structural `isEqual` that ignores `PARENT`: non-enumerable properties are already invisible to
  `Jsonix.Util.Type.isEqual`.

## Effort

| Part | Estimate |
|------|----------|
| Runtime option, `deepCopy`, typings, tests (jsonix-CR-002) | one day |
| Compiler `PARENT` typing, tests, docs, `OfficeOpenXML/` regeneration | half a day |

## Decisions (2026-09-07, recommendations accepted)

1. The property is named `PARENT`, in the style of `TYPE_NAME`. The compiler reports a schema
   property named `PARENT` or `TYPE_NAME` as an error instead of letting it clash silently.
2. `parentPointers` is opt-in (default `false`); revisiting the default is left to a future major
   version of the runtime.
3. Union size: measured on `org_docx4j_wml` during implementation; the outcome is recorded in the
   implementation notes below. No limit unless the measurement shows a need.

## Implementation notes, compiler half (2026-09-07)

- `HeldTypesCollector` (new) visits each class's properties and records the class types they can
  hold (element, elements, elementRef and elementRefs, the latter two expanded through
  substitution groups; wildcards, attributes and values hold no typed children).
  `TypeScriptModuleCompiler` turns that into a containers index keyed by `TYPE_NAME`, registering a
  container for the held type **and all its subtypes**, since a property declared as `T` accepts
  them.
- `parentType(classInfo)` unions the containers of the class **and of its subtypes** (a value
  typed as the base may be a subtype instance held elsewhere), each expanded by the container's
  own subclasses (which inherit the property); refs are sorted, so output is deterministic and
  cross-module containers arrive through the existing `import type` mechanism. Without the
  subtype part `tsc` rejected `CTEdnDocProps extends CTEdnProps` in WML, the same shape of error
  that shaped `TYPE_NAME` in CR-005.
- `TypeScriptMappingCompiler` emits `readonly PARENT?: <union>;` after `TYPE_NAME` and rejects a
  schema property named `PARENT` or `TYPE_NAME` with an `IllegalStateException` (decision 1).
- Measurement on `OfficeOpenXML/` (decision 3): 1,805 types carry `PARENT`; median union size 1,
  90th percentile 5, maximum 113 (DrawingML `CTOfficeArtExtensionList`), seven unions above 40.
  No limit was introduced; `tsc --strict` handles the file without complaint.
- Tests: golden `PurchaseOrder.d.ts` (`Items`, `USAddress` and `Items.Item` have parents,
  `PurchaseOrderType` has none), `zero` assertions (`ExtendedType` inherits `BaseType`'s container
  `ElementRefType`), `usage.ts` reads `firstItem?.PARENT` as `Items` and gets a compile error on
  the root's `PARENT`. `esm-smoke.mjs` runs the runtime checks (pointers, non-enumerability,
  `deepCopy` re-linking) when `Jsonix.Util.deepCopy` exists and reports them as skipped until
  jsonix-CR-002 lands. `tests/typescript` now sets `forceRegenerate`, because `jaxb-maven-plugin`
  otherwise keeps stale declarations when only the compiler changed.
- `OfficeOpenXML/` regenerated: declaration files changed, no mapping file changed.

Revised 2026-09-07 after the runtime side reviewed jsonix-CR-002 against `jsonix.js` (see its
"Revision notes"): the hook is `ClassInfo.unmarshal` with a stack of enclosing results, `PARENT`
goes only on class-info products, `deepCopy` copies structurally without recognising wrappers by
shape, and a copy's own `PARENT` is unset unless the `parent` argument is given. None of that
changes the compiler; the "Semantics" and "Runtime half" sections above were corrected to match,
and `esm-smoke.mjs` already asserted only what survives (it never asserted the copy's own
`PARENT`).

Still to do when jsonix-CR-002 lands as 3.2.0: bump the runtime dependency of `tests/typescript`,
confirm the smoke's runtime checks run rather than skip, and have the runtime repository regenerate
its fixture `tests/typescript/PurchaseOrder.d.ts` from the commit that contains this compiler half.

Update 2026-09-07: the runtime half landed as `@mitre/jsonix` 3.2.0 (sibling commit `dd26153`);
`tests/typescript`'s smoke now reports "parent pointers and deepCopy behave as declared" instead of
skipping. The dependency is still the sibling checkout (`jsonix.runtime.dependency`) until 3.2.0 is
published to npm.
