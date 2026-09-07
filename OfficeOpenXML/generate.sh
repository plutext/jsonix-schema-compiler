#!/usr/bin/env sh
# Regenerates the Office Open XML Jsonix mappings and TypeScript declarations from docx4j's schemas
# into a checkout of https://github.com/plutext/docx4j-ts (CR-009). Run from the repository root:
#
#   OfficeOpenXML/generate.sh [path/to/docx4j/xsd/ROOT.xsd] [path/to/docx4j-ts]
#
# Defaults: ../docx4j/xsd/ROOT.xsd and ../docx4j-ts. Requires JDK 11+ and a built full jar
# (./mvnw -pl .,compiler,full install -DskipTests). Then, in docx4j-ts: npm run build && npm test,
# and commit there citing the compiler and docx4j commits used.
set -eu
ROOT_XSD="${1:-../docx4j/xsd/ROOT.xsd}"
TARGET="${2:-../docx4j-ts}"
[ -d "$TARGET" ] || { echo "target directory not found: $TARGET" >&2; exit 1; }
JAR="$(ls full/target/jsonix-schema-compiler-full-*.jar | grep -v -- '-sources\|original-' | head -1)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
# -Xinheritance/-Xannotate/-Xinject-code: docx4j's schemas carry customizations for those XJC
# plugins (they shape the generated Java, not the XML model); XJC refuses them unless enabled.
java -jar "$JAR" -d "$OUT" -generateTypeScript -Xinheritance -Xannotate -Xinject-code \
  "$ROOT_XSD" -b OfficeOpenXML/bindings.xjb
# XJC also writes Java sources (org/...) into the target directory; keep only the mappings and declarations.
rm -f "$TARGET"/org_*.js "$TARGET"/org_*.mjs "$TARGET"/org_*.d.ts "$TARGET"/org_*.d.mts
cp "$OUT"/*.js "$OUT"/*.mjs "$OUT"/*.d.ts "$OUT"/*.d.mts "$TARGET"/
cp OfficeOpenXML/bindings.xjb "$TARGET"/bindings.xjb
echo "generated $(ls "$TARGET"/org_*.js | wc -l) modules into $TARGET"
