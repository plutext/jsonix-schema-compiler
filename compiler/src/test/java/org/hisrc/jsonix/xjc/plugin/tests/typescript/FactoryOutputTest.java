package org.hisrc.jsonix.xjc.plugin.tests.typescript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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
 * CR-010: element factories. The purchase order schema is compared against
 * golden files; the {@code zero} schema exercises scoped wrappers, global
 * wrappers, reserved-word names and unmapped value types.
 */
public class FactoryOutputTest {

	@Before
	public void setUp() {
		System.setProperty("javax.xml.accessExternalSchema", "all");
	}

	private static void compile(String target, String schema, String binding) throws Exception {
		new File(target).mkdirs();
		final URL schemaUrl = FactoryOutputTest.class.getResource(schema);
		// -generateFactories alone: it implies the TypeScript declarations the factories import.
		final String[] arguments = binding == null
				? new String[] { "-xmlschema", schemaUrl.toExternalForm(), "-d", target, "-extension", "-Xjsonix",
						"-Xjsonix-generateFactories" }
				: new String[] { "-xmlschema", schemaUrl.toExternalForm(), "-b",
						FactoryOutputTest.class.getResource(binding).toExternalForm(), "-d", target, "-extension",
						"-Xjsonix", "-Xjsonix-generateFactories" };
		final Options options = new Options();
		options.parseArguments(arguments);
		final ConsoleErrorReporter receiver = new ConsoleErrorReporter();
		final Model model = ModelLoader.load(options, new JCodeModel(), receiver);
		model.generateCode(options, receiver);
		model.codeModel.build(options.createCodeWriter());
	}

	private static String read(File file) throws IOException {
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	private static String readResource(String name) throws IOException {
		final InputStream is = FactoryOutputTest.class.getResourceAsStream(name);
		try {
			final StringBuilder sb = new StringBuilder();
			final byte[] buffer = new byte[4096];
			int n;
			while ((n = is.read(buffer)) > 0) {
				sb.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
			}
			return sb.toString().replace("\r\n", "\n");
		} finally {
			is.close();
		}
	}

	@Test
	public void purchaseOrderMatchesGoldenFiles() throws Exception {
		final String target = "target/generated-sources/factory/po";
		compile(target, "/typescript/po/purchaseorder.xsd", "/typescript/po/bindings.xjb");
		// The declarations are generated too (implied), unchanged.
		assertEquals(readResource("/typescript/po/PurchaseOrder.d.ts"), read(new File(target, "PurchaseOrder.d.ts")));
		for (String suffix : new String[] { ".factory.mjs", ".factory.d.mts", ".el.mjs", ".el.d.mts" }) {
			assertEquals(suffix, readResource("/typescript/po/PurchaseOrder" + suffix),
					read(new File(target, "PurchaseOrder" + suffix)));
		}
	}

	@Test
	public void zeroSchemaCoversNamingRules() throws Exception {
		final String target = "target/generated-sources/factory/zero";
		compile(target, "/basic/zero/schema.xsd", null);
		final String factory = read(new File(target, "org_hisrc_jsonix_tests_zero.factory.mjs"));
		final String factoryDts = read(new File(target, "org_hisrc_jsonix_tests_zero.factory.d.mts"));
		final String el = read(new File(target, "org_hisrc_jsonix_tests_zero.el.mjs"));
		final String elDts = read(new File(target, "org_hisrc_jsonix_tests_zero.el.d.mts"));

		// creators: TYPE_NAME set, init partial without TYPE_NAME / PARENT
		assertTrue(factory.contains("export function createExtendedType(init) { return Object.assign({ TYPE_NAME: 'org_hisrc_jsonix_tests_zero.ExtendedType' }, init); }"));
		assertTrue(factoryDts.contains("export function createExtendedType(init?: Partial<Omit<M.ExtendedType, 'TYPE_NAME' | 'PARENT'>>): M.ExtendedType;"));
		// the declarations are imported as the namespace M
		assertTrue(factoryDts.contains("import type * as M from './org_hisrc_jsonix_tests_zero.js';"));
		// global wrappers carry the Element suffix (no overload with the creator of the same name)
		assertTrue(factoryDts.contains("export function createElementRefElement(value: M.ElementRefType): M.TypedNamedValue<M.ElementRefType>;"));
		assertTrue(factory.contains("export function createElementRefElement(value) { return __element(__ns0, 'elementRef', value, 'org_hisrc_jsonix_tests_zero.ElementRefType'); }"));
		// scoped wrappers: create + scope + element, by XJC's rule
		assertTrue(factoryDts.contains("export function createElementRefsTypeAlpha(value: M.ValueType): M.TypedNamedValue<M.ValueType>;"));
		assertTrue(factory.contains("export function createElementRefsTypeAlpha(value) { return __element(__ns0, 'alpha', value, 'org_hisrc_jsonix_tests_zero.ValueType'); }"));
		// a builtin-typed element (mixed content value) gets no TYPE_NAME argument
		assertTrue(factory.contains("export function createElementRefMixedTypeValue(value) { return __element(__ns0, 'value', value); }"));
		// no creator for an abstract type, as JAXB's ObjectFactory
		assertFalse(factory.contains("createAbstractBaseType("));
		// support types are imported by name; the module's declarations as M
		assertTrue(factoryDts.contains("import type { XmlQName, XmlCalendar, XmlDuration, XmlDomNode, XmlAnyElement, XmlAnyAttributes } from './org_hisrc_jsonix_tests_zero.js';"));
		assertTrue(factoryDts.contains("export function createQNameEnumElement(value: XmlQName): M.TypedNamedValue<XmlQName>;"));
		// el: one function per local name of the module's namespace; a reserved word is exported with `as`
		assertTrue(elDts.contains("export function elementRef(value: M.ElementRefType): M.TypedNamedValue<M.ElementRefType>;"));
		assertTrue(el.contains("export function elementRef(value) { return __element(__ns0, 'elementRef', value, 'org_hisrc_jsonix_tests_zero.ElementRefType'); }"));
		assertTrue(el.contains("function _string(value) { return __element(__ns0, 'string', value); }\nexport { _string as string };"));
		assertTrue(elDts.contains("declare function _string(value: string): M.TypedNamedValue<string>;\nexport { _string as string };"));
		// nothing untyped by accident
		assertFalse(factoryDts.contains(": undefined"));
		assertFalse(elDts.contains(": undefined"));
	}
}
