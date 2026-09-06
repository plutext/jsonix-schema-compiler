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
 * NaN, INF and -INF defaults on float/double properties must become the
 * JavaScript globals in mappings (and not crash the compiler).
 */
public class JsonixPluginSpecialFloatsTest {

	@Before
	public void setUp() {
		System.setProperty("javax.xml.accessExternalSchema", "all");
	}

	@Test
	public void compilesSpecialFloatDefaults() throws Exception {
		final String target = "target/generated-sources/basic/specialfloats";
		new File(target).mkdirs();
		final String[] arguments = new String[] { "-xmlschema",
				getClass().getResource("/basic/specialfloats/schema.xsd").toExternalForm(), "-d", target,
				"-extension", "-Xjsonix", "-Xjsonix-generateJsonSchema" };
		final Options options = new Options();
		options.parseArguments(arguments);
		final ConsoleErrorReporter receiver = new ConsoleErrorReporter();
		final Model model = ModelLoader.load(options, new JCodeModel(), receiver);
		model.generateCode(options, receiver);
		model.codeModel.build(options.createCodeWriter());

		final String mapping = new String(Files.readAllBytes(new File(target, "test_specialfloats.js").toPath()),
				StandardCharsets.UTF_8);
		assertTrue(mapping.contains("defaultValue: NaN"));
		assertTrue(mapping.contains("defaultValue: Infinity"));
		assertTrue(mapping.contains("defaultValue: -Infinity"));
		// The JSON Schema generator does not emit default values; it must simply not fail on them.
		assertTrue(new File(target, "test_specialfloats.jsonschema").isFile());
	}
}
