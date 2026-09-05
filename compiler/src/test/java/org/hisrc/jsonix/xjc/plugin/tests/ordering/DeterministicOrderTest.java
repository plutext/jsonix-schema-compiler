package org.hisrc.jsonix.xjc.plugin.tests.ordering;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.junit.Before;
import org.junit.Test;

import com.sun.codemodel.JCodeModel;
import com.sun.tools.xjc.ConsoleErrorReporter;
import com.sun.tools.xjc.ModelLoader;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.model.Model;

/**
 * CR-003: the order of type infos and element infos in generated output must
 * not depend on hash iteration order (which differs between JDK and JAXB
 * versions). Class infos and enum leaf infos are emitted sorted by scoped local
 * name, element infos by (namespace URI, local part, scope).
 */
public class DeterministicOrderTest {

	private static final String TARGET = "target/generated-sources/ordering/zero";

	@Before
	public void setUp() {
		System.setProperty("javax.xml.accessExternalSchema", "all");
	}

	@Test
	public void emitsTypeInfosAndElementInfosInStableOrder() throws Exception {
		new File(TARGET).mkdirs();
		final String[] arguments = new String[] { "-xmlschema",
				getClass().getResource("/basic/zero/schema.xsd").toExternalForm(), "-d", TARGET, "-extension",
				"-Xjsonix", "-Xjsonix-generateJsonSchema" };
		final Options options = new Options();
		options.parseArguments(arguments);
		final ConsoleErrorReporter receiver = new ConsoleErrorReporter();
		final Model model = ModelLoader.load(options, new JCodeModel(), receiver);
		model.generateCode(options, receiver);
		model.codeModel.build(options.createCodeWriter());

		final JsonObject schema = readJson(new File(TARGET, "org_hisrc_jsonix_tests_zero.jsonschema"));

		// Class infos (sorted) followed by enum leaf infos (sorted).
		final List<String> expectedDefinitions = Arrays.asList(
				// class infos
				"AbstractBaseType", "AnyAttributeType", "AnyElementLaxType", "AnyElementSkipType",
				"AnyElementStrictType", "AttributeType", "BaseType", "ElementRefMixedType", "ElementRefType",
				"ElementRefsType", "ElementType", "ElementsType", "ExtendedExtendedType", "ExtendedMixedType",
				"ExtendedType", "SimpleEnumTypesType", "SimpleTypesType", "ValueType",
				// enum leaf infos
				"ENTITYEnumType", "LanguageEnumType", "NCNameEnumType", "NameEnumType", "NormalizedStringEnumType",
				"StringEnumType", "TokenEnumType");
		assertEquals(expectedDefinitions, new ArrayList<String>(schema.getJsonObject("definitions").keySet()));

		// Element infos sorted by (namespaceURI, localPart, scope).
		final List<String> elementKeys = new ArrayList<String>();
		final JsonArray anyOf = schema.getJsonArray("anyOf");
		for (int i = 0; i < anyOf.size(); i++) {
			final JsonObject elementInfo = anyOf.getJsonObject(i);
			final JsonObject elementName = elementInfo.getJsonObject("elementName");
			final String scope = elementInfo.containsKey("scope") ? elementInfo.get("scope").toString() : "";
			elementKeys.add(elementName.getString("namespaceURI") + " " + elementName.getString("localPart")
					+ " " + scope);
		}
		final List<String> sortedElementKeys = new ArrayList<String>(elementKeys);
		Collections.sort(sortedElementKeys);
		assertEquals(sortedElementKeys, elementKeys);
		assertEquals(64, elementKeys.size());
	}

	private static JsonObject readJson(File file) throws Exception {
		final InputStream is = new FileInputStream(file);
		try {
			final JsonReader reader = Json.createReader(is);
			return reader.readObject();
		} finally {
			is.close();
		}
	}
}
