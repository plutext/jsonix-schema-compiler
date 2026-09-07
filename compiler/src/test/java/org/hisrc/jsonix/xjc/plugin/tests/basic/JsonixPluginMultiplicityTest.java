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
 * A property is required only if every enclosing group requires it: a
 * branch of a choice, an element in an optional sequence and an optional
 * element in a repeating sequence are optional; an element in a required
 * repeating sequence and the only branch of a required choice stay required.
 */
public class JsonixPluginMultiplicityTest {

	@Before
	public void setUp() {
		System.setProperty("javax.xml.accessExternalSchema", "all");
	}

	@Test
	public void effectiveMultiplicity() throws Exception {
		final String target = "target/generated-sources/basic/multiplicity";
		new File(target).mkdirs();
		final String[] arguments = new String[] { "-xmlschema",
				getClass().getResource("/basic/multiplicity/schema.xsd").toExternalForm(), "-d", target, "-extension",
				"-Xjsonix", "-Xjsonix-generateTypeScript" };
		final Options options = new Options();
		options.parseArguments(arguments);
		final ConsoleErrorReporter receiver = new ConsoleErrorReporter();
		final Model model = ModelLoader.load(options, new JCodeModel(), receiver);
		model.generateCode(options, receiver);
		model.codeModel.build(options.createCodeWriter());

		final String ts = new String(Files.readAllBytes(new File(target, "test_multiplicity.d.ts").toPath()),
				StandardCharsets.UTF_8);
		assertTrue(ts.contains("  always: string;"));
		assertTrue(ts.contains("  xmlData?: string;"));
		assertTrue(ts.contains("  binaryData?: string;"));
		assertTrue(ts.contains("  inOptionalGroup?: string;"));
		assertTrue(ts.contains("  repeatedOptional?: string[];"));
		assertTrue(ts.contains("  repeatedRequired: string[];"));
		assertTrue(ts.contains("  onlyBranch: string;"));
		assertTrue(ts.contains("  mandatory: string;"));
		assertTrue(ts.contains("  optional?: string;"));

		final String mapping = new String(Files.readAllBytes(new File(target, "test_multiplicity.js").toPath()),
				StandardCharsets.UTF_8);
		// the mapping's required flags follow the same rule
		assertTrue(mapping.contains("name: 'always',\n            required: true"));
		assertTrue(!mapping.contains("name: 'xmlData',\n            required: true"));
		assertTrue(mapping.contains("name: 'repeatedRequired',\n            required: true"));
	}
}
