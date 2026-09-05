# Jsonix Schema Compiler #

Generates [Jsonix](https://github.com/highsource/jsonix) mappings for XML Schemas.

Please refer to [Wiki](https://github.com/highsource/jsonix-schema-compiler/wiki) for documentation.

## Requirements

- **Java 11 or newer** to run the compiler (CLI jar, Ant, Maven or NPM usage).
- **Jakarta XML Binding 4** (`jakarta.xml.bind`, JAXB 4.0.x). As an XJC plugin use it with
  `org.jvnet.jaxb:jaxb-maven-plugin` 4.0.x or the JAXB 4 `xjc` Ant task. It does not work with the
  legacy `javax.xml.bind` toolchain (`org.jvnet.jaxb2.maven2:maven-jaxb2-plugin`, JAXB 2.x); the last
  release for that toolchain is 2.3.9.
- **Binding files must use the Jakarta namespace**: `xmlns:jaxb="https://jakarta.ee/xml/ns/jaxb"` with
  `version="3.0"`. JAXB 4 silently ignores customizations in the old `http://java.sun.com/xml/ns/jaxb`
  namespace, which makes `jsonix:` customizations and package bindings disappear without an error.
  The `xjc` extension namespace stays `http://java.sun.com/xml/ns/jaxb/xjc`.
- Building from source: JDK 11+ and the bundled Maven wrapper (`./mvnw`).

## Using in command-line

Download `jsonix-schema-compiler-full-<VERSION>.jar` from [releases](https://github.com/highsource/jsonix-schema-compiler/releases) and run it with `java -jar` from the command line:

```
java -jar jsonix-schema-compiler-full-<VERSION>.jar
  [-compact -logLevel TRACE]
  schema.xsd
  [-b bindings.xjb]
```

See [Command-Line Usage](https://github.com/highsource/jsonix-schema-compiler/wiki/Command-Line-Usage).

## Using with NPM

From the command line:

```
npm install jsonix-schema-compiler
java -jar node_modules/jsonix-schema-compiler/lib/jsonix-schema-compiler-full.jar schema.xsd
```

Or add `jsonix-schema-compiler` as dependency and invoke in `scripts/prepublish`.

```json
 {
    "name": "mypackage",
    ...
    "dependencies": {
        ...
        "jsonix": "<VERSION>",
        "jsonix-schema-compiler": "<VERSION>"
    },
    "scripts": {
    	...
        "prepublish" : "java -jar node_modules/jsonix/lib/jsonix-schema-compiler-full.jar schema.xsd"
    }
}
```

See [NPM Usage](https://github.com/highsource/jsonix-schema-compiler/wiki/NPM-Usage).

## Using with Ant

* Include `jsonix-schema-compiler-plugin-<VERSION>.jar` into `xjc/classpath`.
* Include `-Xjsonix` and further `-Xjsonix-...` [[command-line options|Command-Line usage]] into `arg/@line`.

```xml
<xjc destdir="${basedir}/target/generated-sources/xjc" extension="true">
  <arg line="-Xjsonix -Xjsonix-compact"/>
  <binding dir="${basedir}/src/main/resources">
     <include name="**/*.xjb"/>
  </binding>
  <schema dir="${basedir}/src/main/resources">
     <include name="**/*.xsd"/>
  </schema>
  <!-- Plugins -->
  <classpath>
    <fileset dir="${basedir}/lib">
      <include name="jsonix-*.jar"/>
    </fileset>
  </classpath>
</xjc>
```

See [Ant Usage](https://github.com/highsource/jsonix-schema-compiler/wiki/Ant-Usage).

## Using with Maven

```xml
<plugin>
	<groupId>org.jvnet.jaxb</groupId>
	<artifactId>jaxb-maven-plugin</artifactId>
	<version>4.0.16</version>
	<configuration>
		<extension>true</extension>
		<args>
			<arg>-Xjsonix</arg>
			<arg>-Xjsonix-compact</arg>
		</args>
		<plugins>
			<plugin>
				<groupId>org.hisrc.jsonix</groupId>
				<artifactId>jsonix-schema-compiler</artifactId>
				<version>${jsonix-schema-compiler.version}</version>
			</plugin>
		</plugins>
	</configuration>
</plugin>
```

See [Maven Usage](https://github.com/highsource/jsonix-schema-compiler/wiki/Maven-Usage).
