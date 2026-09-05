# Releasing

This replaces the former `release.bat` / `qrelease.bat` / `install.bat` / `full-build-1.6.bat`.
Everything below is plain Maven and works on any OS via the wrapper.

## Prerequisites

- JDK 8 on `JAVA_HOME` (the enforcer rejects anything else until CR-002 lands).
- `npm` on the PATH if you build the `npm` module.
- For signed releases: a GPG key and, in `~/.m2/settings.xml`, a `<server>` entry for whatever
  repository you deploy to.

## Publishing target

The upstream coordinates `org.hisrc.jsonix:*` on Maven Central and the `jsonix-schema-compiler`
npm package belong to the upstream maintainer. Releases from this fork must use their own
`groupId` (root `pom.xml`) and npm package name (`npm/src/main/npm/package.json`), and a
`<distributionManagement>` section pointing at the chosen repository. None of that is configured yet.

## Steps

```bash
# 1. Full clean build including integration tests, samples and dist
./mvnw -B clean install -Pall

# 2. Set the release version in every module (tests/samples/dist are only reachable via -Pall)
./mvnw -B versions:set -Pall -DnewVersion=2.3.10 -DgenerateBackupPoms=false
# npm/package.json is regenerated from npm/src/main/npm/package.json by the npm module build.

# 3. Rebuild and verify at the release version
./mvnw -B clean install -Pall

# 4. Commit and tag
git commit -am "Version 2.3.10"
git tag -a 2.3.10 -m "Version 2.3.10"

# 5. Deploy (attaches sources, javadoc and GPG signatures; also runs `npm publish` from the npm module)
./mvnw -B -Prelease,all clean deploy

# 6. Move to the next snapshot
./mvnw -B versions:set -Pall -DnewVersion=2.3.11-SNAPSHOT -DgenerateBackupPoms=false
git commit -am "Version 2.3.11-SNAPSHOT"
git push origin master --tags
```

The `release` profile is defined in the root `pom.xml`. Sources jars are attached on every build.
