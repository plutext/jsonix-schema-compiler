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
Other XJC plugin switches are passed through; the jar bundles the jaxb-tools plugins, so schemas whose
annotations need `-Xinheritance`, `-Xannotate` or `-Xinject-code` compile by adding those switches.
The Office Open XML mappings and declarations generated this way from docx4j's schemas are published as
[`@docx4j/docx4j-ts`](https://github.com/plutext/docx4j-ts); `OfficeOpenXML/` holds their bindings and generation script.

## Using with NPM

From the command line:

```
npm install @docx4j/jsonix-schema-compiler
java -jar node_modules/@docx4j/jsonix-schema-compiler/lib/jsonix-schema-compiler-full.jar schema.xsd
```

Or add `@docx4j/jsonix-schema-compiler` as dependency and invoke in `scripts/prepublish`.

```json
 {
    "name": "mypackage",
    ...
    "dependencies": {
        ...
        "@docx4j/jsonix": "<VERSION>",
        "@docx4j/jsonix-schema-compiler": "<VERSION>"
    },
    "scripts": {
    	...
        "prepublish" : "java -jar node_modules/@docx4j/jsonix-schema-compiler/lib/jsonix-schema-compiler-full.jar schema.xsd"
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

## TypeScript

Add `-generateTypeScript` (`-Xjsonix-generateTypeScript` with XJC/Maven/Ant) to get, next to each
module's mapping file, a declaration file describing the objects Jsonix unmarshals and marshals:

```
java -jar jsonix-schema-compiler-full.jar -generateTypeScript schema.xsd -b bindings.xjb
```

For the purchase order sample this produces `PurchaseOrder.d.ts` (plus one-line
`PurchaseOrder.std.d.ts` / `PurchaseOrder.cmp.d.ts` re-exports so imports of the mapping files resolve):

```ts
export interface USAddress {
  TYPE_NAME?: 'PO.USAddress';
  name: string; street: string; city: string; state: string; zip: number;
  country?: string;
}
export interface PurchaseOrderType {
  TYPE_NAME?: 'PO.PurchaseOrderType';
  shipTo: USAddress; billTo: USAddress; comment?: string; items: Items; orderDate?: XmlCalendar;
}
export type PurchaseOrderElement = TypedNamedValue<PurchaseOrderType>;
export type RootElement = CommentElement | PurchaseOrderElement;
export declare const PO: JsonixMapping<RootElement>;
```

```ts
import type { PurchaseOrderElement } from './PurchaseOrder.std';
const po = (unmarshaller.unmarshalString(xml) as PurchaseOrderElement).value;
po.shipTo.name;   // string
po.orderDate?.year; // number | undefined (dates are Jsonix calendars, not JS Dates)
```

- Required properties are non-optional, collections are arrays, choices are unions, `elementRef`
  properties are `TypedNamedValue<T>` unions (with `string` when mixed), enums are literal unions,
  and `TYPE_NAME` is a literal union over the type and its subtypes, usable as a discriminant.
- `readonly PARENT?: A | B` names the types that can contain the type (through their properties,
  substitution groups and subtypes). The runtime fills it in when unmarshalling with
  `new Jsonix.Context(mappings, { parentPointers: true })` (`@docx4j/jsonix` 3.2.0+, jsonix-CR-002),
  and `Jsonix.Util.deepCopy(value)` copies a subtree and re-links the pointers, like docx4j's
  `-Xparent-pointer` / `-Xdocx4j-copy` model. Types that only occur at the root have no `PARENT`.
- Cross-module references become `import type * as ... from './<other module>'`.
- Java interfaces declared with the jaxb-tools inheritance plugin (`inheritance:implements`,
  `inheritance:extends`) become union aliases, e.g. `export type ContentAccessor = Body | P | ...`.
- Customise the file name with `<jsonix:typeScript fileName="${module.name}.d.ts"/>` inside
  `jsonix:module` (or at the top level of the bindings). One file per module; naming
  (standard/compact) does not affect it.

To emit the mapping itself as an ES module instead of the UMD wrapper (for bundlers, Angular, Vite):

```xml
<jsonix:module name="PurchaseOrder">
  <jsonix:mapping name="PO" package="org.hisrc.jsonix.demos.po"/>
  <jsonix:output naming="standard" format="esm"/>   <!-- PurchaseOrder.mjs, plus PurchaseOrder.d.mts -->
</jsonix:module>
```

```js
import { PO } from './PurchaseOrder.mjs';
const context = new Jsonix.Context([PO]);
```

Two mapping-level customizations shape all outputs (mapping, JSON Schema, declarations):

```xml
<jsonix:mapping package="org.docx4j.vml">
  <jsonix:propertyOrder typeInfo="CTLine">vmlId style from to</jsonix:propertyOrder> <!-- these first, rest in schema order -->
  <jsonix:property name="Style.customStyle" defaultValue="false"/>                   <!-- override the schema default -->
</jsonix:mapping>
```

Attributes are otherwise emitted sorted by name (XSOM does not keep a stable order for attributes
from several attribute groups); elements keep schema order.

Generated declarations are self-contained. With the `@docx4j/jsonix` runtime 3.1.0 or newer, whose
typings are generic, no cast is needed at all:

```ts
import { Jsonix } from '@docx4j/jsonix';
import { PO } from './PurchaseOrder.mjs';
import type { PurchaseOrderElement } from './PurchaseOrder.mjs';
const po = new Jsonix.Context([PO]).createUnmarshaller().unmarshalString<PurchaseOrderElement>(xml).value;
```

`JsonixMapping<RootElement>` carries the mapping's root element union as a phantom type parameter
(`__rootElement`), so a runtime can infer the result type from the mappings passed to `Jsonix.Context`.
