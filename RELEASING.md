# Releasing

This replaces the former `release.bat` / `qrelease.bat` / `install.bat` / `full-build-1.6.bat`.
Everything below is plain Maven and works on any OS via the wrapper.

## Prerequisites

- JDK 11 or newer on `JAVA_HOME` (the enforcer rejects older JDKs).
- `npm` on the PATH, logged in (`npm login`) with publish rights to the `@docx4j` scope.
- `node`/`npm` access to the registry for `tests/typescript`, which installs `@docx4j/jsonix`.

## Publishing target

The compiler is published to npm as `@docx4j/jsonix-schema-compiler` (the package carries the full CLI
jar as `lib/jsonix-schema-compiler-full.jar`). The Maven coordinates are `org.docx4j.jsonix:*`, but the
artifacts are **not** deployed to a Maven repository: Maven Central is deferred until someone needs the
XJC plugin from Maven (CR-008). Upstream's `org.hisrc.jsonix:*` and `jsonix-schema-compiler` (2.3.9, the
last `javax` release) belong to the upstream maintainer.

`publishConfig.access` is `public` in `npm/src/main/npm/package.json`, so scoped publishes are public.
npm versions cannot be reused once published (even after an unpublish): a problem found after
publishing ships as the next patch version.

## Steps

```bash
# 1. Set the release version in every module (tests/samples/dist are only reachable via -Pall)
./mvnw -B versions:set -Pall -DnewVersion=3.0.1 -DgenerateBackupPoms=false
# npm/package.json is regenerated from npm/src/main/npm/package.json by the npm module build.

# 2. Full clean build at the release version: unit and integration tests (tests/typescript installs
#    @docx4j/jsonix from npm). -Pall also builds samples/dist, which do not build since the JAXB 4
#    migration (the Ant sample's classpath and Java 1.6 targets); nothing from them is published.
./mvnw -B clean install -Ptests

# 3. Optionally check the package before publishing: npm pack in npm/ and install the tarball
#    into a consumer (e.g. regenerate ../docx4j-generated-objects-ts and run its typecheck)
(cd npm && npm pack --dry-run)

# 4. Commit and tag
git commit -am "Version 3.0.1"
git tag -a 3.0.1 -m "Version 3.0.1"

# 5. Publish to npm: the npm module's deploy phase runs `npm publish .` (its Maven deploy is skipped)
./mvnw -B -pl npm deploy

# 6. Attach full/target/jsonix-schema-compiler-full-3.0.1.jar to a GitHub release for the tag

# 7. Move to the next snapshot
./mvnw -B versions:set -Pall -DnewVersion=3.0.2-SNAPSHOT -DgenerateBackupPoms=false
git commit -am "Version 3.0.2-SNAPSHOT"
git push origin master --tags
```

The `release` profile in the root `pom.xml` attaches javadoc and GPG signatures; it is only needed once
the Maven artifacts are deployed somewhere. Sources jars are attached on every build.
