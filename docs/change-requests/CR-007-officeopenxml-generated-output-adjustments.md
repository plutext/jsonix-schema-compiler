# CR-007: Equivalents of docx4j's `ModifyGeneratedSources` for the OfficeOpenXML output

**Status:** Implemented 2026-09-07
**Depends on:** CR-005 (TypeScript output), CR-006 (`PARENT`; runtime half jsonix-CR-002 for the helper part)
**Recommended order:** after the runtime half of CR-006 lands; items 1 and 3 can go first

## Summary

docx4j runs `docx4j-generated-objects/ModifyGeneratedSources.java` over the Java that XJC
generates from its schemas (`process-sources` phase): fourteen textual patches, each applied once
and marked with a `// docx4j:patched:<id>` comment so the step is idempotent, failing the build if
a search string is not found. This CR classifies every one of those patches by what it means for
the JavaScript mappings and TypeScript declarations in `OfficeOpenXML/`, and proposes the
equivalents where one exists.

Two principles differ from docx4j's approach, on purpose:

- **No textual patching of generated files.** `OfficeOpenXML/generate.sh` reproduces the
  directory byte-identically (CR-003); every adjustment must be expressed as a compiler
  customization in `OfficeOpenXML/bindings.xjb`, which is the single source of "what differs from
  a plain compile". Where the compiler lacks a customization, the compiler gains one (items 1 and 3),
  rather than a patch script.
- **Data objects have no methods.** Jsonix produces plain objects; a Java patch that adds behaviour
  (a lazy getter, a colour table, an equality rule) has no place in generated output. The few
  worth having become a small hand-written helper module (item 5).

## The fourteen patches and their equivalents

| # | docx4j patch (`ModifyGeneratedSources`) | What it does in Java | JavaScript / TypeScript equivalent |
|---|------------------------------------------|----------------------|-------------------------------------|
| 1 | `ArrayListDocx4j<>(this)` in every constructor | collections know their owner | none needed: `PARENT` on the items (CR-006) covers it; arrays are plain |
| 2 | `Document.setBody` sets the body's parent | parent maintained by setters | none needed: unmarshalling sets `PARENT` (jsonix-CR-002); hand-built trees use `Jsonix.Util.setParent` |
| 3 | `CTSection.getSldIdLst` lazily creates the list | convenience getter | none: `sldIdLst?:` is an optional property; consumers write `s.sldIdLst ??= {}` |
| 4 | `SdtRun`/`SdtBlock`/`CTSdtCell`/`CTSdtRow`: `setSdtContent(SdtContent)`, `setSdtPr` set parent, implementing the hand-written `SdtElement` interface | a common interface over four Sdt element types | **item 3**: emit `export type SdtElement = SdtRun \| SdtBlock \| CTSdtCell \| CTSdtRow;` from the schema's `inheritance:implements` customizations; parent setting is CR-006 |
| 5 | WML `ObjectFactory.get()` singleton | Java factory | none: mappings are object literals |
| 6 | DML chart `_UserShapes_QNAME` made public | expose an element QName | none: element names are in the mapping's `elementInfos` and typed as `UserShapesElement` |
| 7 | VML `CTLine`: attributes reordered to `id, style, from, to` (docx4j #469) | **Word is sensitive to attribute order**; XJC emits schema order `from, to, ..., id, style` | **item 1**: the Jsonix marshaller writes attributes in mapping order (`ClassInfo.marshal` iterates `propertyInfos`), and the generated mapping has `from, to` at positions 1 and 2 and `id, style` at 12 and 13, so the same bug exists; add a `jsonix:propertyOrder` customization |
| 8 | `Id.equals`/`hashCode` on `val` | value-based identity | none: JavaScript objects have no overridable equality; `Jsonix.Util.Type.isEqual` compares structurally. Document |
| 9 | `Style.isCustomStyle()` returns `false` when absent (docx4j #641; the schema default is `true`) | override a schema default | **item 2**: the mapping carries `defaultValue: true` from the schema, but the runtime ignores `defaultValue` entirely (verified in `jsonix.js`), so the value is documentation only; correct it via a `jsonix:property defaultValue` customization and document the rule for consumers |
| 10 | `Highlight.setVal` maps `#RRGGBB` to the enumerated colour names, `getHexVal()` | domain helper with a colour table | **item 5** (helper module): `highlightHexValue(val)` / `highlightNameForHex(hex)` |
| 11 | `LatentStyles.LsdException.isQFormat()` falls back to the parent's `defQFormat` | getter using the parent pointer | **item 5** (helper module): `isQFormat(lsdException)` using `PARENT` (needs jsonix-CR-002) |
| 12 | `CTLine`: remove the duplicated `from`/`to`/`id` fields | part of patch 7 | part of item 1 |
| 13 | `ObjectFactory` and QName visibility tweaks (chart) | Java visibility | none |
| 14 | `setSdtPr` parent setting (four classes) | as 2 | as 2 |

Also in the schemas but not in `ModifyGeneratedSources`: `inheritance:implements` for
`org.docx4j.wml.ContentAccessor` (14 classes), `org.docx4j.wml.CTCustomXmlElement` (4),
`org.docx4j.vml.VmlShapeElements` (11), `VmlAllCoreAttributes` (11) and `VmlAllShapeAttributes`
(10). `ContentAccessor` is the interface docx4j code uses most (`getContent()` on anything that
holds block or run content), and `inheritance:extends` for `org.docx4j.wml.SdtContent` (the four
Sdt content types extend that hand-written abstract class, which is what lets docx4j's
`setSdtContent(SdtContent)` accept any of them). Item 3 covers all of them: `implements` and
`extends` alike become union aliases, so `SdtContent` is
`CTSdtContentCell | CTSdtContentRow | CTSdtContentRun | SdtContentBlock`.

## Proposed changes

### 1. `jsonix:propertyOrder` customization (compiler) and its use for `CTLine`

New customization, allowed inside `jsonix:mapping`:

```xml
<jsonix:mapping package="org.docx4j.vml">
  <!-- Word is sensitive to the order of these attributes: https://github.com/plutext/docx4j/issues/469 -->
  <jsonix:propertyOrder typeInfo="CTLine">vmlId style from to</jsonix:propertyOrder>
</jsonix:mapping>
```

Semantics: the named properties (mapping property names, i.e. JAXB private names; `vmlId` is the
`id` attribute) come first in the given order, all others keep their schema order. Applies to the
`propertyInfos` array of the UMD, ES-module and JSON Schema outputs and to the member order of the
TypeScript interface (cosmetic there). Unknown type or property names are errors. Implemented in
`configuration.PropertyOrderConfiguration` and applied in `definition.Mapping` when properties are
read (so all emitters see the same order). Verification: marshal a `CTLine` through Jsonix and
assert the attribute order in the output XML.

### 2. `jsonix:property defaultValue` override and the `customStyle` rule

Extend the existing `jsonix:property` element (today only usable in includes/excludes) with an
optional `defaultValue` attribute usable inside `jsonix:mapping`:

```xml
<jsonix:mapping package="org.docx4j.wml">
  <!-- Absent w:customStyle means a built-in style: https://github.com/plutext/docx4j/issues/641 -->
  <jsonix:property typeInfo="Style" name="customStyle" defaultValue="false"/>
</jsonix:mapping>
```

Because the runtime ignores `defaultValue`, the effect is on the mapping text only, plus an entry in
`OfficeOpenXML/README.md` "Known differences from the schema" telling consumers to treat a missing
`customStyle` as `false`. If the runtime ever starts applying defaults (a possible jsonix-CR), the
mapping is already right. Cheap, and it keeps the docx4j decision where it belongs.

### 3. Interface unions from `inheritance:implements` (compiler, TypeScript output)

`TypeScriptMappingCompiler` reads the jaxb-tools inheritance customizations on each class info
(`urn:jaxb.jvnet.org:plugin:inheritance`, elements `implements` and `extends`, via
`CustomizationUtils.getCustomizations` on the class's origin) and emits, per module, one alias per
interface simple name:

```ts
/** Types that implement org.docx4j.wml.ContentAccessor in docx4j. */
export type ContentAccessor = Body | CTFtnEdn | CTSdtContentBlock | ... | Tc;
export type SdtElement = CTSdtCell | CTSdtRow | SdtBlock | SdtRun;
export type SdtContent = CTSdtContentCell | CTSdtContentRow | CTSdtContentRun | SdtContentBlock;
```

The union is over the implementing classes of that module plus their subtypes, sorted; names are
reserved in the module's root `TsNode` like element aliases (a clash with a type name gets the
numeric suffix). Interfaces implemented by classes in several modules produce one alias per module
(there is no shared file to put them in; `ContentAccessor` happens to be WML-only). Nothing is
emitted for modules without such customizations, so other users' output is unchanged.

This is the TypeScript counterpart of docx4j patch 4 and of the `-Xinheritance` plugin generally:
docx4j code written against `ContentAccessor` or `SdtElement` has a typed equivalent.

### 4. Nothing to do (documented only)

Patches 1, 2, 5, 6, 8, 13, 14: covered by CR-006/jsonix-CR-002 or without JavaScript meaning.
`OfficeOpenXML/README.md` gets a short "Relationship to docx4j's generated Java" section listing
them, so the next person does not re-derive this table.

### 5. Optional helper module `OfficeOpenXML/helpers/wml.ts` (hand-written)

Not generated; a small TypeScript file next to the output, typed against the declarations:

- `highlightHexValue(val: STHighlightColor): string | undefined` and
  `highlightNameForHex(hex: string): STHighlightColor | undefined` with docx4j's colour table
  (patch 10);
- `isQFormat(e: Styles.LatentStyles.LsdException): boolean` using `e.PARENT?.defQFormat` (patch 11;
  needs jsonix-CR-002);
- `isCustomStyle(style: Style): boolean` (`style.customStyle === true`, patch 9).

Kept deliberately tiny; anything larger belongs in a consumer library, not next to generated code.

## Verification

- Items 1 and 2: golden/unit tests in `compiler` for the two customizations (a small schema with an
  attribute order and a default override); `OfficeOpenXML/generate.sh` regenerated with the
  bindings additions, `git diff` shows only `CTLine`'s `propertyInfos` order and `customStyle`'s
  `defaultValue`; a Jsonix marshal of a `CTLine` yields `id style from to`.
- Item 3: `zero` schema gains `inheritance:implements` and `inheritance:extends` customizations
  for the test; the OfficeOpenXML WML declarations contain `ContentAccessor` with 14+ members and
  `SdtElement` and `SdtContent` with four each; `tsc --strict` green over all 188 files.
- Item 5: type-checked with the rest of `OfficeOpenXML/` (add the helper to the `tsc` file list
  in the smoke); `isQFormat` exercised once jsonix-CR-002 is available.

## Out of scope

- Applying schema defaults at unmarshal time in the runtime (would be a jsonix-CR; item 2 only
  corrects what the mapping says).
- Methods on unmarshalled objects (`instanceFactory` classes with `copy()`, `getParent()` and the
  like); CR-006 chose plain objects plus helpers.
- Any change to docx4j's Java pipeline.

## Effort

| Item | Estimate |
|------|----------|
| 1. `jsonix:propertyOrder` + CTLine | half a day |
| 2. `defaultValue` override + README rule | two hours |
| 3. `inheritance:implements` unions | half a day |
| 4. README section | one hour |
| 5. Helper module | two hours (plus jsonix-CR-002 for `isQFormat`) |

## Decisions (2026-09-07, recommendations accepted)

1. `jsonix:propertyOrder` reorders the TypeScript interface members as well as the mapping, so the
   declaration and the mapping read alike.
2. Interface aliases use the Java interface's simple name (`ContentAccessor`), with a numeric
   suffix on collision.
3. The helper module is shipped as `OfficeOpenXML/helpers/wml.ts`.

## Implementation notes (2026-09-07)

Items 1 to 5 implemented as proposed, plus one finding that changed more than planned:

- **Attribute order was never deterministic.** While regenerating `OfficeOpenXML/` after adding
  the customizations, two unrelated modules changed: in DrawingML's `CT_Constraint` one attribute
  group swapped places with another. XSOM keeps attribute-group references in a plain `Set`
  (`AttributesHolder.attGroups`), so the order XJC reports for attributes drawn from several groups
  follows identity hashes; `java -Xshare:off` alone flips it. CR-003's byte-identical regenerations
  had been luck of identical allocation. `Mapping.getProperties(classInfo)` therefore now sorts
  attribute properties by attribute name (namespace URI, then local part) among themselves, in the
  slots they occupy; element properties keep their particle order, which XSOM holds in lists;
  `jsonix:propertyOrder` still takes precedence. Verified: `OfficeOpenXML/` is now identical under
  `-Xshare:off`, `-XX:+UseSerialGC` and `-Xint`. The one-time reorder touched 147 of its files.
  XML attribute order carries no meaning, and the previous order could not be relied on, so no
  compatibility note beyond this one.
- **Item 1**: `configuration.PropertyOrderConfiguration` (`jsonix:propertyOrder typeInfo="..."`
  with whitespace-separated property names), validated against the model
  (`InvalidCustomizationException` for an unknown type or property), applied through
  `Mapping.getProperties`, which the mapping, JSON Schema and TypeScript emitters all use (decision
  1). `OfficeOpenXML/bindings.xjb` orders `CTLine` as `vmlId style from to`; a `v:line` marshalled
  through `@mitre/jsonix` now serialises as `id style from to`.
- **Item 2**: `jsonix:property` gained a `defaultValue` attribute and may appear directly under
  `jsonix:mapping` (`name="Type.property"`); `CreateTypeInfoDelaration` applies the override.
  `Style.customStyle` now says `defaultValue: false` in the WML mapping.
- **Item 3**: `TypeScriptModuleCompiler.inheritanceTypes` reads `inheritance:implements` and
  `inheritance:extends` from the class's XJC customizations (marking them acknowledged) and
  `TypeScriptMappingCompiler` emits one union alias per Java type, simple name (decision 2), over the
  implementing classes and their subtypes. WML gains `ContentAccessor` (17 members), `SdtElement`,
  `SdtContent`, `CTCustomXmlElement`; VML gains `VmlShapeElements`, `VmlAllCoreAttributes`,
  `VmlAllShapeAttributes`. `compiler` has `jaxb-plugins` as a test dependency so the test can run
  with `-Xinheritance`.
- **Item 5**: `OfficeOpenXML/helpers/wml.ts` (decision 3) with `HIGHLIGHT_COLORS`,
  `highlightHexValue`, `highlightNameForColor`, `isQFormat` (uses `PARENT`) and `isCustomStyle`,
  type-checked with the rest of the directory.
- Tests: `JsonixPluginAdjustmentsTest` (order in mapping, JSON Schema and declarations; attribute
  sorting; default override; `Shape`/`Base` aliases from `implements`/`extends`). Full suite green
  on JDK 17. The runtime checks in `tests/typescript`'s smoke now run rather than skip, because the
  sibling checkout has implemented jsonix-CR-002 in the meantime.
