# CR-003: Deterministic ordering of generated mappings

**Status:** Proposed
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
