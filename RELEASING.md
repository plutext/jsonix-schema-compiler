# Releasing

This replaces the former `release.bat` / `qrelease.bat` / `install.bat` / `full-build-1.6.bat`.
Everything below is plain Maven and works on any OS via the wrapper.

## Prerequisites

- JDK 11 or newer on `JAVA_HOME` (the enforcer rejects older JDKs).
- `npm` on the PATH; `tests/typescript` installs `@docx4j/jsonix` from the registry.
- Push rights to `plutext/jsonix-schema-compiler` (to tag and create the GitHub release).

## Publishing target

The compiler is published to npm as `@docx4j/jsonix-schema-compiler` (the package carries the full CLI
jar as `lib/jsonix-schema-compiler-full.jar`), and the full jar is attached to the GitHub release. The
Maven coordinates are `org.docx4j.jsonix:*`, but the artifacts are **not** deployed to a Maven
repository: Maven Central is deferred until someone needs the XJC plugin from Maven (CR-008). Upstream's
`org.hisrc.jsonix:*` and `jsonix-schema-compiler` (2.3.9, the last `javax` release) belong to the
upstream maintainer.

Publishing uses npm **trusted publishing** from GitHub Actions: no npm token is stored anywhere. The
settings of the npm package `@docx4j/jsonix-schema-compiler` (npmjs.com, scope `@docx4j`) name the
publisher GitHub Actions and the GitHub repository allowed to publish it: organization or user `plutext`
(the GitHub owner, not the npm scope), repository `jsonix-schema-compiler`, workflow `push-to-npm.yml`,
no environment. Renaming the workflow file breaks
publishing until the npmjs.com setting is changed to match.

npm versions cannot be reused once published (even after an unpublish): a problem found after
publishing ships as the next patch version.

## Steps

```bash
# 1. Set the release version in every module (tests/samples/dist are only reachable via -Pall)
./mvnw -B versions:set -Pall -DnewVersion=3.0.1 -DgenerateBackupPoms=false
# npm/package.json is regenerated from npm/src/main/npm/package.json by the npm module build.

# 2. Clean build at the release version: unit and integration tests (tests/typescript installs
#    @docx4j/jsonix from npm). -Pall also builds samples/dist, which do not build since the JAXB 4
#    migration (the Ant sample's classpath and Java 1.6 targets); nothing from them is published.
./mvnw -B clean install -Ptests

# 3. Optionally check the package before publishing: npm pack in npm/ and install the tarball
#    into a consumer (e.g. regenerate ../docx4j-generated-objects-ts and run its typecheck)
(cd npm && npm pack --dry-run)

# 4. Commit, tag and push (the tag is the bare version, as the workflow checks it; a leading v is tolerated)
git commit -am "Version 3.0.1"
git tag -a 3.0.1 -m "Version 3.0.1"
git push origin master 3.0.1

# 5. Publish: create a GitHub release for the tag
gh release create 3.0.1 --title "3.0.1" --notes "..."
```

Publishing the release runs `.github/workflows/push-to-npm.yml`, which:

1. builds with `./mvnw clean install -Ptests` on JDK 17 (so the jar is built and tested in CI),
2. fails unless the release tag equals the version in the built `npm/package.json`, and refuses
   `-SNAPSHOT` versions,
3. runs `npm pack` and `npm publish` in `npm/` (with provenance, via OIDC),
4. uploads `full/target/jsonix-schema-compiler-full-<version>.jar` to the release.

If the workflow fails before the publish step, fix the problem, move the tag, and re-run it from the
Actions tab (or delete and recreate the release). If it fails after publishing, only the jar upload is
left: re-run the job's failed steps or upload the jar by hand.

```bash
# 6. Move to the next snapshot
./mvnw -B versions:set -Pall -DnewVersion=3.0.2-SNAPSHOT -DgenerateBackupPoms=false
git commit -am "Version 3.0.2-SNAPSHOT"
git push origin master
```

A local `npm publish` from `npm/` (after step 2) still works as a fallback while token publishing is
allowed on the package; once npmjs.com is set to disallow tokens, CI is the only way to publish.

The `release` profile in the root `pom.xml` attaches javadoc and GPG signatures; it is only needed once
the Maven artifacts are deployed somewhere. Sources jars are attached on every build.
