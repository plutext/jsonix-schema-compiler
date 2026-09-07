package org.hisrc.jsonix.xjc.plugin.tests.basic;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Before;
import org.junit.Test;

import com.sun.codemodel.JCodeModel;
import com.sun.tools.xjc.ConsoleErrorReporter;
import com.sun.tools.xjc.ModelLoader;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.model.Model;

/**
 * CR-007: jsonix:propertyOrder, jsonix:property defaultValue and union aliases
 * from the inheritance plugin's implements / extends customizations.
 */
public class JsonixPluginAdjustmentsTest {

	@Before
	public void setUp() {
		System.setProperty("javax.xml.accessExternalSchema", "all");
	}

	@Test
	public void appliesOrderDefaultAndInheritanceCustomizations() throws Exception {
		final String target = "target/generated-sources/basic/adjustments";
		new File(target).mkdirs();
		final String[] arguments = new String[] { "-xmlschema",
				getClass().getResource("/basic/adjustments/schema.xsd").toExternalForm(), "-b",
				getClass().getResource("/basic/adjustments/bindings.xjb").toExternalForm(), "-d", target,
				"-extension", "-Xinheritance", "-Xjsonix", "-Xjsonix-generateJsonSchema" };
		final Options options = new Options();
		options.parseArguments(arguments);
		final ConsoleErrorReporter receiver = new ConsoleErrorReporter();
		final Model model = ModelLoader.load(options, new JCodeModel(), receiver);
		model.generateCode(options, receiver);
		model.codeModel.build(options.createCodeWriter());

		final String mapping = read(new File(target, "Adjustments.js"));
		// property order: id, style first, then schema order (from, to)
		final int id = mapping.indexOf("name: 'id'"), style = mapping.indexOf("name: 'style'"),
				from = mapping.indexOf("name: 'from'"), to = mapping.indexOf("name: 'to'");
		assertTrue(id > 0 && style > id && from > style && to > from);
		// attributes are sorted by name for determinism (XSOM attribute groups are hash-ordered); elements keep schema order
		assertOrder(mapping, "name: 'zElement'", "name: 'aElement'", "name: 'a'", "name: 'z'");
		// default value override
		assertTrue(mapping.contains("defaultValue: false"));
		assertTrue(!mapping.contains("defaultValue: true"));
		// JSON Schema follows the same property order
		final String schema = read(new File(target, "Adjustments.jsonschema"));
		assertTrue(schema.indexOf("\"id\"") < schema.indexOf("\"style\"") && schema.indexOf("\"style\"") < schema.indexOf("\"from\""));

		final String ts = read(new File(target, "Adjustments.d.ts"));
		// TypeScript members in the same order
		final int tsId = ts.indexOf("  id?: string;"), tsStyle = ts.indexOf("  style?: string;"), tsFrom = ts.indexOf("  from?: string;");
		assertTrue(tsId > 0 && tsStyle > tsId && tsFrom > tsStyle);
		// union aliases from inheritance customizations (implements and extends)
		assertTrue(ts.contains("export type Shape = LineType | StyleType;"));
		assertTrue(ts.contains("export type Base = StyleType;"));
		assertTrue(!ts.contains("OtherType |") && !ts.contains("| OtherType"));
	}

	private static String read(File file) throws Exception {
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	private static void assertOrder(String s, String... needles) {
		int last = -1;
		for (String needle : needles) {
			final int i = s.indexOf(needle);
			assertTrue("expected " + needle + " after position " + last, i > last);
			last = i;
		}
	}
}
