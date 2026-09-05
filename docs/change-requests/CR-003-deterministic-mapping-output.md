# CR-003: Deterministic ordering of generated mappings

**Status:** Implemented (2026-09-06)
**Depends on:** nothing (best done together with CR-002, before or after)

## Summary

The order of `typeInfos` and `elementInfos` in generated mapping files (and of definitions in the
generated JSON Schema) is not stable across JDK or JAXB versions. Make it deterministic so that
generated files can be diffed, committed and used as golden files in tests.

## Motivation (evidence)

Compiling `samples/po/src/main/resources/purchaseorder.xsd` three times on one toolchain yields
identical output each time, but different toolchains yield different orders:

| Toolchain | `typeInfos` order |
|-----------|-------------------|
| jar built with JAXB 2.2.11, run on JDK 8 | `Items`, `USAddress`, `PurchaseOrderType` |
| jar built with JAXB 2.3.9, run on JDK 8 | `USAddress`, `PurchaseOrderType`, `Items` |
| jar built with JAXB 2.3.9, run on JDK 17 | `PurchaseOrderType`, `Items`, `USAddress` |

`elementInfos` reorder likewise. Cause: `definition.Mapping` and `definition.MappingDependency`
hold `classInfos`, `enumLeafInfos`, `elementInfos`, `propertyInfos` and `infoVertices` in
`HashSet`s, and `analysis.ModelInfoGraphAnalyzer` builds `HashMap`s keyed by model objects.
Iteration order therefore depends on the hash codes of XJC/jaxb2-basics model objects, which are
not stable across library versions.

Consequences:

- CR-002 cannot be verified by diffing output; only by eyeballing.
- Users who commit generated mappings see spurious diffs when they change JDK.
- Jsonix runtime behaviour does not depend on order, so this is not a correctness bug.

## Proposed change

- In `definition.Mapping` / `MappingDependency`, replace the `HashSet`s with `LinkedHashSet`s **and**
  sort at emission time: in `compilation.mapping.MappingCompiler` and
  `compilation.jsonschema.JsonSchemaMappingCompiler`, iterate class/enum infos sorted by local name
  and element infos sorted by `(namespace, localPart)`. Sorting at emission is the minimal change;
  the `LinkedHashSet` swap just removes the remaining hash-order dependence in dependency analysis.
- Do not reorder `propertyInfos` within a type; they already follow schema order (which Jsonix
  relies on for `elements`/`choice` handling) and the trial diff shows them unchanged.
- Add a unit test in `compiler` that compiles `basic/zero/schema.xsd` and asserts the emitted
  `typeInfos` local names are in sorted order.

## Verification

- Same schema compiled on JDK 8/JAXB 2.2.11 and JDK 17/JAXB 2.3.9 produces byte-identical `.js`
  and `.jsonschema` files.
- Existing `tests/zero` JS unit tests still pass (they exercise the runtime with the generated
  mapping, so they would catch any accidental change to property order).

## Risks

Low. The change is in emission order only. One visible effect: users' committed mapping files will
reorder once, on the first regeneration after this change.

## Effort

Two to three hours.

## Implementation notes (2026-09-06)

Implemented slightly differently from the proposal: the sort lives in the **definition layer**
rather than in each emitter, so the JS mapping compiler and the JSON Schema compiler share it
without any change of their own.

- New `definition.InfoComparators`: `PACKAGED_TYPE_INFO` (scoped local name, then fully
  qualified name) and `ELEMENT_INFO` (namespace URI, local part, then scope's scoped local name;
  global elements first).
- `definition.Mapping`: `getClassInfos()`, `getEnumLeafInfos()` and `getElementInfos()` return
  sorted, unmodifiable lists; `getDirectDependencies()` is keyed by package name in a `TreeMap`,
  so the `dependencies` array is stable too; all `HashSet`/`HashMap` fields became linked
  variants. `MappingDependency` likewise.
- Property order within a type is untouched (still schema order).
- New unit test `xjc.plugin.tests.ordering.DeterministicOrderTest` compiles `basic/zero` and
  asserts the JSON Schema `definitions` keys are the 18 class infos sorted followed by the 7 enum
  infos sorted, and that the 64 `anyOf` element infos are sorted by
  (namespace URI, local part, scope).

Verification: the same `full` jar run on JDK 17 and JDK 21 now produces byte-identical
`.js` and `.jsonschema` files for `samples/po` and for OGC OWS 1.1.0 (`full/src/test/resources`),
where it previously differed. Full suite green on JDK 17 and 21 (29 + 4 unit tests,
`filter`/`wps`/`zero`/`issues`).

Visible effect for users: committed mapping files reorder once on the first regeneration.
