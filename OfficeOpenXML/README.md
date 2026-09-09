# Office Open XML generation

The Jsonix mappings and TypeScript declarations generated from docx4j's schemas live in their own
repository, [plutext/docx4j-generated-objects-ts](https://github.com/plutext/docx4j-generated-objects-ts), published as
`@docx4j/generated-objects-ts` (CR-009). They were split out of this directory with their history on
2026-09-07 (last compiler commit with the files: `ba24587`).

This directory keeps the source of truth for *how* they are produced:

- `bindings.xjb`: the Jsonix customizations (outputs and declarations for every module, and the
  docx4j-specific adjustments of CR-007: `v:line` attribute order, `w:customStyle` default);
- `generate.sh`: regenerates into a `docx4j-generated-objects-ts` checkout (default `../docx4j-generated-objects-ts`; into its `modules/` directory when present) from
  `../docx4j/xsd/ROOT.xsd` with `-generateTypeScript -Xinheritance -Xannotate -Xinject-code`.

Regeneration is deterministic (CR-003, CR-007): with the same compiler and schemas the diff in
`docx4j-generated-objects-ts` is empty. After a compiler change, run the script, then `npm run build && npm test`
in `docx4j-generated-objects-ts`, and commit there citing the compiler and docx4j commits.
