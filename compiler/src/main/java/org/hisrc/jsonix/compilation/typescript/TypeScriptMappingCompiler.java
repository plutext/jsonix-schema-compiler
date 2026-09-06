package org.hisrc.jsonix.compilation.typescript;

import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.commons.lang3.Validate;
import org.hisrc.jsonix.JsonixConstants;
import org.hisrc.jsonix.definition.Mapping;
import org.jvnet.jaxb.xml.bind.model.MClassInfo;
import org.jvnet.jaxb.xml.bind.model.MClassTypeInfo;
import org.jvnet.jaxb.xml.bind.model.MElementInfo;
import org.jvnet.jaxb.xml.bind.model.MEnumConstantInfo;
import org.jvnet.jaxb.xml.bind.model.MEnumLeafInfo;
import org.jvnet.jaxb.xml.bind.model.MPropertyInfo;
import org.jvnet.jaxb.xml.bind.model.MTypeInfo;

/**
 * Produces the declarations of one mapping: an interface per class info, a
 * literal-union alias per enum, a {@code TypedNamedValue} alias per element
 * info and a {@code RootElement} union of the global elements.
 */
public class TypeScriptMappingCompiler<T, C extends T> {

	public static final String ELEMENT_SUFFIX = "Element";
	public static final String ROOT_ELEMENT_NAME = "RootElement";

	private final TypeScriptModuleCompiler<T, C> moduleCompiler;
	private final Mapping<T, C> mapping;
	private final CreateTsTypeVisitor<T, C> types;
	private final TsPropertyVisitor<T, C> properties;

	public TypeScriptMappingCompiler(TypeScriptModuleCompiler<T, C> moduleCompiler, Mapping<T, C> mapping) {
		this.moduleCompiler = Validate.notNull(moduleCompiler);
		this.mapping = Validate.notNull(mapping);
		this.types = new CreateTsTypeVisitor<T, C>(moduleCompiler);
		this.properties = new TsPropertyVisitor<T, C>(moduleCompiler);
	}

	public Mapping<T, C> getMapping() {
		return mapping;
	}

	/**
	 * Adds the mapping's declarations to {@code root} and returns the name of
	 * the root element union, or {@code null} if the mapping has no global
	 * elements.
	 */
	public String compile(TsNode root) {
		// Type names are unique per mapping; reserve them first so that element
		// aliases never shadow a type.
		for (MClassInfo<T, C> classInfo : mapping.getClassInfos()) {
			reserve(root, localName(classInfo));
		}
		for (MEnumLeafInfo<T, C> enumLeafInfo : mapping.getEnumLeafInfos()) {
			reserve(root, localName(enumLeafInfo));
		}
		for (MClassInfo<T, C> classInfo : mapping.getClassInfos()) {
			final String localName = localName(classInfo);
			parent(root, localName).add(compileClassInfo(classInfo, simpleName(localName)));
		}
		for (MEnumLeafInfo<T, C> enumLeafInfo : mapping.getEnumLeafInfos()) {
			final String localName = localName(enumLeafInfo);
			parent(root, localName).add(compileEnumLeafInfo(enumLeafInfo, simpleName(localName)));
		}
		final List<String> rootElements = new ArrayList<String>();
		for (MElementInfo<T, C> elementInfo : mapping.getElementInfos()) {
			final MClassInfo<T, C> scope = elementInfo.getScope();
			final TsNode node = scope == null ? root : root.path(localName(scope));
			final String name = node.uniqueName(elementAliasName(elementInfo.getElementName()));
			node.add("export type " + name + " = "
					+ Ts.generic(Ts.TYPED_NAMED_VALUE, types.typeOf(elementInfo, elementInfo.getTypeInfo())) + ";");
			if (scope == null) {
				rootElements.add(name);
			}
		}
		if (!rootElements.isEmpty()) {
			final String name = root.uniqueName(ROOT_ELEMENT_NAME);
			root.add("/** Any global element of this mapping, i.e. what an unmarshaller returns. */\n"
					+ "export type " + name + " = " + Ts.union(rootElements) + ";");
			return name;
		}
		return null;
	}

	private static String localName(MClassTypeInfo<?, ?, ?> typeInfo) {
		return typeInfo.getContainerLocalName(JsonixConstants.DEFAULT_SCOPED_NAME_DELIMITER);
	}

	private static String localName(MEnumLeafInfo<?, ?> typeInfo) {
		return typeInfo.getContainerLocalName(JsonixConstants.DEFAULT_SCOPED_NAME_DELIMITER);
	}

	private static String simpleName(String localName) {
		final int dot = localName.lastIndexOf('.');
		return dot < 0 ? localName : localName.substring(dot + 1);
	}

	private static TsNode parent(TsNode root, String localName) {
		final int dot = localName.lastIndexOf('.');
		return dot < 0 ? root : root.path(localName.substring(0, dot));
	}

	private static void reserve(TsNode root, String localName) {
		parent(root, localName).reserve(simpleName(localName));
	}

	public static String elementAliasName(QName elementName) {
		return Ts.identifier(Ts.upperFirst(elementName.getLocalPart())) + ELEMENT_SUFFIX;
	}

	private String compileClassInfo(MClassInfo<T, C> classInfo, String name) {
		final StringBuilder sb = new StringBuilder();
		sb.append("export interface ").append(name);
		final MClassTypeInfo<T, C, ?> baseTypeInfo = classInfo.getBaseTypeInfo();
		if (baseTypeInfo != null) {
			sb.append(" extends ").append(moduleCompiler.ref(baseTypeInfo));
		}
		sb.append(" {\n");
		sb.append(TsNode.INDENT).append(Ts.TYPE_NAME_PROPERTY).append("?: ")
				.append(moduleCompiler.typeNameLiteral(classInfo)).append(";\n");
		for (MPropertyInfo<T, C> propertyInfo : classInfo.getProperties()) {
			if (mapping.getPropertyInfos().contains(propertyInfo)) {
				sb.append(TsNode.INDENT).append(propertyInfo.acceptPropertyInfoVisitor(properties)).append('\n');
			}
		}
		sb.append("}");
		return sb.toString();
	}

	private String compileEnumLeafInfo(MEnumLeafInfo<T, C> enumLeafInfo, String name) {
		final MTypeInfo<T, C> baseTypeInfo = enumLeafInfo.getBaseTypeInfo();
		final String baseType = types.typeOf(enumLeafInfo, baseTypeInfo);
		final boolean normalized = types.isNormalizedString(enumLeafInfo, baseTypeInfo);
		final List<String> literals = new ArrayList<String>();
		boolean supported = true;
		for (MEnumConstantInfo<T, C> constant : enumLeafInfo.getConstants()) {
			final String lexical = constant.getLexicalValue();
			if (Ts.STRING.equals(baseType)) {
				literals.add(Ts.stringLiteral(normalized ? normalize(lexical) : lexical));
			} else if (Ts.NUMBER.equals(baseType) && isNumericLiteral(lexical)) {
				literals.add(lexical.trim());
			} else if (Ts.BOOLEAN.equals(baseType) && isBooleanLiteral(lexical)) {
				literals.add(booleanLiteral(lexical));
			} else {
				supported = false;
				break;
			}
		}
		final String type = supported && !literals.isEmpty() ? Ts.union(literals) : baseType;
		return "export type " + name + " = " + type + ";";
	}

	/** Same as jakarta.xml.bind.annotation.adapters.NormalizedStringAdapter. */
	private static String normalize(String value) {
		return value == null ? null : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
	}

	private static boolean isNumericLiteral(String value) {
		return value != null && value.trim().matches("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?")
				&& !value.trim().startsWith("+");
	}

	private static boolean isBooleanLiteral(String value) {
		final String v = value == null ? "" : value.trim();
		return "true".equals(v) || "false".equals(v) || "1".equals(v) || "0".equals(v);
	}

	private static String booleanLiteral(String value) {
		final String v = value.trim();
		return "true".equals(v) || "1".equals(v) ? "true" : "false";
	}
}
