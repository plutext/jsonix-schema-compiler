# CR-009: Split `OfficeOpenXML/` into `plutext/docx4j-ts` as `@docx4j/docx4j-ts`

**Status:** Implemented 2026-09-07 (repository created locally; push pending)
**Depends on:** CR-007 (current state of `OfficeOpenXML/`), CR-008 (npm identity), runtime
`@docx4j/jsonix` 3.2.0 on npm (otherwise the package cannot declare its dependency by version)

## Summary

The generated Office Open XML mappings and declarations (`OfficeOpenXML/`, 94 modules, 5.9 MB) are
a product for JavaScript/TypeScript consumers, not part of the compiler. They move to their own
repository `plutext/docx4j-ts`, keeping their history, and are published as `@docx4j/docx4j-ts`
with a dependency on `@docx4j/jsonix` and a small facade that gives them docx4j's names. The
compiler repository keeps the bindings and the generation script as the source of truth for *how*
the output is produced; the new repository holds *what* is produced.

## Split with history

`OfficeOpenXML/` was created in `972fa18` and changed in `0b6a0d9` and `733c427` (all on this
repository's `cr-006-parent-pointers` branch, to be merged to `master` first). From the compiler
repository:

```
git subtree split --prefix=OfficeOpenXML -b docx4j-ts-history     # 3 commits, paths rebased to the root
git clone --no-local <compiler repo> docx4j-ts && cd docx4j-ts
git checkout docx4j-ts-history && git branch -m main
git remote set-url origin git@github.com:plutext/docx4j-ts.git && git push -u origin main
```

Then, in the compiler repository, replace `OfficeOpenXML/` with a `README.md` pointing at the new
repository and keep `OfficeOpenXML/bindings.xjb` and `OfficeOpenXML/generate.sh` (they read
docx4j's schemas and write into a checkout of `docx4j-ts` given as an argument, defaulting to
`../docx4j-ts`). The commit that does this cites the last compiler commit the generated files came
from (`733c427` today), as the runtime repository does for its fixture.

Regeneration workflow afterwards: compiler change → `OfficeOpenXML/generate.sh ../docx4j-ts` →
commit in `docx4j-ts` citing the compiler commit → publish.

## Package layout in `docx4j-ts`

```
package.json            @docx4j/docx4j-ts, "type": "module", "exports" map (below), dependency @docx4j/jsonix ^3.2.0
README.md               from OfficeOpenXML/README.md, plus the facade
generate.md             how the files are regenerated (the compiler repository's generate.sh)
index.mts / index.d.mts the facade (hand-written)
helpers/wml.ts          from OfficeOpenXML/helpers/wml.ts
org_docx4j_wml.mjs, .d.mts, .d.ts, .js ...   generated (unchanged names)
```

`exports`:

```json
{
  ".": { "types": "./index.d.mts", "import": "./index.mjs" },
  "./helpers/wml": { "types": "./helpers/wml.d.ts", "import": "./helpers/wml.js" },
  "./modules/*": { "types": "./*.d.ts", "import": "./*.mjs", "require": "./*.js" }
}
```

so `import { org_docx4j_wml } from '@docx4j/docx4j-ts/modules/org_docx4j_wml'` keeps working for
consumers who want a single module, while the root gives the facade. The `.js` UMD files stay for
`require` users; `.d.ts`/`.d.mts` resolve for both. The package ships `helpers/` compiled by `tsc`
(the only build step) and the generated files as they are.

## The facade (`index.mts`), docx4j's names

```ts
import { Jsonix } from '@docx4j/jsonix';
export { Jsonix };
export type { TypedNamedValue, XmlQName, XmlCalendar } from './org_docx4j_wml.mjs';

const MODULE_NAMES = [ 'org_docx4j_wml', 'org_docx4j_dml', /* ... all 94, generated */ ] as const;

let contextPromise: Promise<Jsonix.Context> | undefined;
/** The context over all Office Open XML mappings, built once; modules are loaded lazily on first use. */
export function getContext(options?: Jsonix.ContextOptions): Promise<Jsonix.Context> {
  return (contextPromise ??= Promise.all(MODULE_NAMES.map((n) => import(`./${n}.mjs`)))
    .then((mods) => new Jsonix.Context(mods.flatMap((m) => Object.values(m)), { parentPointers: true, ...options })));
}

/** docx4j XmlUtils.unmarshalString: parse XML into a typed element. */
export async function unmarshalString<E extends Jsonix.TypedNamedValue = Jsonix.TypedNamedValue>(xml: string): Promise<E> {
  return (await getContext()).createUnmarshaller().unmarshalString<E>(xml);
}
/** docx4j XmlUtils.marshaltoString. */
export async function marshalString(element: Jsonix.TypedNamedValue): Promise<string> {
  return (await getContext()).createMarshaller().marshalString(element);
}
/** docx4j XmlUtils.unwrap: the value of an element. */
export function unwrap<T>(element: Jsonix.TypedNamedValue<T>): T { return element.value; }
/** docx4j XmlUtils.deepCopy: Jsonix.Util.deepCopy, re-exported. */
export const deepCopy: <T>(value: T, parent?: unknown) => T = Jsonix.Util.deepCopy;
```

Design points:

- **Lazy loading** because 94 modules are 5.9 MB; a consumer that only unmarshals a
  `document.xml` still needs most of WML's dependencies, so the context is all-or-nothing, but
  it is built on first use, once, and not at import time. `getContext` accepts options so
  `parentPointers` (on by default here, since docx4j's model always has parents) can be turned off.
- **Synchronous variants** are deliberately absent: dynamic `import()` is asynchronous. A consumer
  needing synchronous calls imports the modules it wants from `./modules/*` and builds its own
  context.
- **Typed results** come from the generated declarations: `unmarshalString<DocumentElement>(xml)`
  with `import type { DocumentElement } from '@docx4j/docx4j-ts/modules/org_docx4j_wml'`.
- `MODULE_NAMES` is generated into `index.mts` by the regeneration step (a line per module) so it
  cannot drift from the directory.

## Tests in `docx4j-ts`

- `tsc --strict` over everything (the current ad-hoc check, made permanent).
- Node: `unmarshalString` of a `word/document.xml` fixture (the docx4j sample used in the compiler's
  smoke), `TYPE_NAME`, `PARENT`, `deepCopy` round trip, `marshalString` and re-parse.
- `v:line` marshals as `id style from to` (CR-007).
- CI on Node 18, 20, 22.

## Compiler repository after the split

- `OfficeOpenXML/` keeps `bindings.xjb`, `generate.sh` (target directory argument) and a short
  README; the `tsc` and Node checks that today run ad hoc against `OfficeOpenXML/` move to
  `docx4j-ts`.
- CR-007's helper module and README sections move with the files.

## Open questions

1. Package name `@docx4j/docx4j-ts` (given) versus `@docx4j/ooxml`: the former matches the
   repository, the latter says what it is. Recommendation: keep `@docx4j/docx4j-ts` as given.
2. Whether to also publish per-module packages (`@docx4j/wml`, ...). Recommendation: no; the
   modules depend on each other and the `exports` subpaths already allow selective imports.
3. `parentPointers` default in the facade: on (docx4j parity) or off (runtime parity).
   Recommendation: on, as sketched.

## Effort

Split and repository setup: two hours. Facade, `exports`, tests, CI: one day.

## Implementation notes (2026-09-07)

- History split with `git subtree split --prefix=OfficeOpenXML` (four commits, `972fa18` to
  `ba24587`) into `../docx4j-ts`, `main`, remote `https://github.com/plutext/docx4j-ts.git`
  (the GitHub repository must be created from the account, `gh` is not installed here; then
  `git push -u origin main`).
- Scaffold committed there (`4450673`): `package.json` (`@docx4j/docx4j-ts` 0.1.0, `"type":
  "module"`, `exports` root / `./helpers/wml` / `./modules/*`, dependency `@docx4j/jsonix ^3.2.0`),
  `index.mts` facade as sketched (`MODULE_NAMES` generated from the directory, `getContext` lazy
  with `parentPointers: true`, `resetContext`, `unmarshalString`, `marshalString`, `unwrap`,
  `deepCopy`, `Jsonix` re-export), `tsconfig.json` / `tsconfig.build.json` (`lib` includes `dom`
  because the runtime typings reference `Node` and `Document`), `test/smoke.mjs` over a small
  hand-written WordprocessingML document (parent pointers, `deepCopy` with the copy's own `PARENT`
  unset, marshal round trip, `v:line` order), a Node 18/20/22 workflow, `generate.md`, README.
  Verified: `npm run typecheck` and the smoke pass with the runtime installed from the sibling
  checkout (`npm install --no-save ../jsonix/nodejs/scripts`, until 3.2.0 is on npm).
- Compiler side: `OfficeOpenXML/` reduced to `bindings.xjb`, `generate.sh` (target directory
  argument, default `../docx4j-ts`) and a pointer README; regeneration into `../docx4j-ts`
  reproduces the committed files with an empty diff. The ad-hoc `tsc`/Node checks over the
  generated files now live in `docx4j-ts` as `npm run typecheck` / `npm test`.
- Decisions taken as recommended: package name `@docx4j/docx4j-ts`; no per-module packages;
  `parentPointers` on by default in the facade.
