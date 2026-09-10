#!/usr/bin/env sh
# Regenerates the Office Open XML Jsonix mappings and TypeScript declarations from docx4j's schemas
# into a checkout of https://github.com/plutext/docx4j-generated-objects-ts (CR-009). Run from the repository root:
#
#   OfficeOpenXML/generate.sh [path/to/docx4j/xsd/ROOT.xsd] [path/to/docx4j-generated-objects-ts]
#
# Defaults: ../docx4j/xsd/ROOT.xsd and ../docx4j-generated-objects-ts. Requires JDK 11+ and a built full jar
# (./mvnw -pl .,compiler,full install -DskipTests). Then, in docx4j-generated-objects-ts: npm run build && npm test,
# and commit there citing the compiler and docx4j commits used.
set -eu
ROOT_XSD="${1:-../docx4j/xsd/ROOT.xsd}"
TARGET="${2:-../docx4j-generated-objects-ts}"
[ -d "$TARGET" ] || { echo "target directory not found: $TARGET" >&2; exit 1; }
# docx4j-generated-objects-ts keeps the generated modules and the reference bindings.xjb under modules/.
MODULES="$TARGET"; [ -d "$TARGET/modules" ] && MODULES="$TARGET/modules"
JAR="$(ls full/target/jsonix-schema-compiler-full-*.jar | grep -v -- '-sources\|original-' | head -1)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
# -Xinheritance/-Xannotate/-Xinject-code: docx4j's schemas carry customizations for those XJC
# plugins (they shape the generated Java, not the XML model); XJC refuses them unless enabled.
java -jar "$JAR" -d "$OUT" -generateTypeScript -generateFactories -Xinheritance -Xannotate -Xinject-code \
  "$ROOT_XSD" -b OfficeOpenXML/bindings.xjb
# XJC also writes Java sources (org/...) into the target directory; keep only the mappings and declarations.
rm -f "$MODULES"/org_*.js "$MODULES"/org_*.mjs "$MODULES"/org_*.d.ts "$MODULES"/org_*.d.mts
cp "$OUT"/*.js "$OUT"/*.mjs "$OUT"/*.d.ts "$OUT"/*.d.mts "$MODULES"/   # includes <Module>.factory.* and <Module>.el.* (CR-010)
cp OfficeOpenXML/bindings.xjb "$MODULES"/bindings.xjb
echo "generated $(ls "$MODULES"/org_*.js | wc -l) modules into $MODULES"
