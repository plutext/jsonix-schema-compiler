# CR-004: Jakarta XML Binding / JAXB 4 / jaxb-tools 4.x

**Status:** Deferred (not recommended until CR-001 to CR-003 are done and there is a concrete consumer need)
**Depends on:** CR-002

## Summary

CR-002 stays on the `javax.xml.bind` line (JAXB 2.3.9), which is end-of-life but works on any JDK.
The ecosystem has moved to `jakarta.xml.bind` (JAXB 3.x/4.x) and the successor of
`maven-jaxb2-plugin`, `org.jvnet.jaxb:jaxb-maven-plugin` 4.0.x with `org.jvnet.jaxb:jaxb-basics`
4.0.x. This CR records what a move would involve so it can be costed later.

## Why not now

- 21 source files in `compiler` import `javax.xml.bind.*`; 58 distinct `org.jvnet.jaxb2_commons.*`
  imports (jaxb2-basics 0.11 API); 32 files in `compiler`/`full` use `com.sun.tools.xjc`,
  `com.sun.xml.xsom` or `com.sun.codemodel`. All of these change package or artifact coordinates
  under Jakarta/jaxb-tools 4 (`org.jvnet.jaxb.*`, `org.glassfish.jaxb:jaxb-xjc:4.x`, codemodel
  moved to `org.glassfish.jaxb:codemodel:4.x`).
- The XJC plugin SPI (`com.sun.tools.xjc.Plugin`) is the same shape in 4.x, but the surrounding
  model classes (`CPluginCustomization`, `Model`, `NType`/`NClass`) must be re-verified.
- The `full` jar would then require JDK 11+ regardless of `release` level (JAXB 4 is compiled for 11).
- Consumers using Ant/`xjc` from an older JAXB would be cut off entirely, whereas after CR-002 they
  keep working with JAXB 2.3.1+.

## Outline of the work

1. Bump `jaxb.version` to 4.0.x, `jaxb2-basics` → `org.jvnet.jaxb:jaxb-basics` 4.0.x, plugin →
   `org.jvnet.jaxb:jaxb-maven-plugin` 4.0.x, `javax.json` → `jakarta.json` 2.x.
2. Mechanical package renames: `javax.xml.bind` → `jakarta.xml.bind`, `org.jvnet.jaxb2_commons` →
   `org.jvnet.jaxb`.
3. Re-run the whole suite; expect model-API drift in `analysis/*` and `xml/xsom/*`.
4. Publish under a new `groupId`/npm name (this fork cannot release as `org.hisrc.jsonix`).

## Effort

Two to four days, most of it in re-verifying `MModelInfo`-based analysis against jaxb-basics 4.
