package org.hisrc.jsonix.compilation.typescript;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.lang3.Validate;
import org.hisrc.jsonix.JsonixConstants;
import org.hisrc.jsonix.definition.Mapping;
import org.hisrc.jsonix.definition.Module;
import org.hisrc.jsonix.definition.Modules;
import org.hisrc.jsonix.definition.Output;
import org.hisrc.jsonix.definition.TypeScript;
import org.jvnet.jaxb.xml.bind.model.MClassInfo;
import org.jvnet.jaxb.xml.bind.model.MClassTypeInfo;
import org.jvnet.jaxb.xml.bind.model.MElementInfo;
import org.jvnet.jaxb.xml.bind.model.MPackagedTypeInfo;
import org.jvnet.jaxb.xml.bind.model.MPropertyInfo;
import org.jvnet.jaxb.xml.bind.model.concrete.origin.CMClassInfoOrigin;
import org.w3c.dom.Element;

import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.model.CPluginCustomization;

/**
 * Produces the TypeScript declaration file of a module ({@code <Module>.d.ts})
 * plus one-line re-export files for outputs whose file name differs from the
 * module's, so that {@code import ... from './PurchaseOrder.std'} resolves.
 */
public class TypeScriptModuleCompiler<T, C extends T> {

	/** A generated file: name and contents. */
	public static final class GeneratedFile {
		public final String fileName;
		public final String contents;

		GeneratedFile(String fileName, String contents) {
			this.fileName = fileName;
			this.contents = contents;
		}
	}

	private final Modules<T, C> modules;
	private final Module<T, C> module;
	private final TypeScript typeScript;
	private final boolean multiMapping;
	/** Dependency module → import alias, in first-use order. */
	private final Map<Module<T, C>, String> imports = new LinkedHashMap<Module<T, C>, String>();
	private final Set<String> importAliases = new HashSet<String>();
	/** Substitution group head element name → elements substituting it. */
	private final Map<QName, List<MElementInfo<T, C>>> substitutions = new LinkedHashMap<QName, List<MElementInfo<T, C>>>();
	/** TYPE_NAME of a class → its direct subclasses (across all modules). */
	private final Map<String, List<MClassInfo<T, C>>> subclasses = new LinkedHashMap<String, List<MClassInfo<T, C>>>();
	/** TYPE_NAME of a class → the classes with a property that can hold it (CR-006). */
	private final Map<String, Set<MClassInfo<T, C>>> containers = new LinkedHashMap<String, Set<MClassInfo<T, C>>>();
	/**
	 * Prefix put before references to this module's own types; empty inside
	 * the declarations, {@code M.} inside the factory declarations, which
	 * import the module's declarations as a namespace (CR-010).
	 */
	private String ownQualifier = "";

	public TypeScriptModuleCompiler(Modules<T, C> modules, Module<T, C> module, TypeScript typeScript) {
		this.modules = Validate.notNull(modules);
		this.module = Validate.notNull(module);
		this.typeScript = Validate.notNull(typeScript);
		int nonEmptyMappings = 0;
		for (Mapping<T, C> mapping : module.getMappings()) {
			if (!mapping.isEmpty()) {
				nonEmptyMappings++;
			}
		}
		this.multiMapping = nonEmptyMappings > 1;
		for (MElementInfo<T, C> elementInfo : modules.getModelInfo().getElementInfos()) {
			final QName head = elementInfo.getSubstitutionHead();
			if (head != null) {
				List<MElementInfo<T, C>> list = substitutions.get(head);
				if (list == null) {
					list = new ArrayList<MElementInfo<T, C>>();
					substitutions.put(head, list);
				}
				list.add(elementInfo);
			}
		}
		for (MClassInfo<T, C> classInfo : modules.getModelInfo().getClassInfos()) {
			final MClassTypeInfo<T, C, ?> base = classInfo.getBaseTypeInfo();
			if (base != null) {
				final String baseName = typeName(base);
				List<MClassInfo<T, C>> list = subclasses.get(baseName);
				if (list == null) {
					list = new ArrayList<MClassInfo<T, C>>();
					subclasses.put(baseName, list);
				}
				list.add(classInfo);
			}
		}
		for (MClassInfo<T, C> classInfo : modules.getModelInfo().getClassInfos()) {
			final HeldTypesCollector<T, C> collector = new HeldTypesCollector<T, C>(this);
			for (MPropertyInfo<T, C> propertyInfo : classInfo.getProperties()) {
				propertyInfo.acceptPropertyInfoVisitor(collector);
			}
			for (MClassTypeInfo<T, C, ?> held : collector.getHeldTypes()) {
				// A property declared as T can hold T or any of its subtypes.
				for (String heldName : typeNamesOfSelfAndSubtypes(typeName(held))) {
					Set<MClassInfo<T, C>> set = containers.get(heldName);
					if (set == null) {
						set = new LinkedHashSet<MClassInfo<T, C>>();
						containers.put(heldName, set);
					}
					set.add(classInfo);
				}
			}
		}
	}

	/**
	 * The TypeScript type of the {@code PARENT} property: the union of all
	 * classes that can contain the given class through one of their
	 * properties (including subclasses of those containers, which inherit
	 * the property), or {@code null} if nothing can, i.e. the type only
	 * occurs at the root.
	 */
	public String parentType(MClassInfo<T, C> classInfo) {
		// A value typed as this class may be an instance of any subtype, which may
		// be held in more places; including them also keeps `extends` valid, since
		// the subtype's PARENT union must be assignable to the base's.
		final Set<MClassInfo<T, C>> direct = new LinkedHashSet<MClassInfo<T, C>>();
		for (String name : typeNamesOfSelfAndSubtypes(typeName(classInfo))) {
			final Set<MClassInfo<T, C>> set = containers.get(name);
			if (set != null) {
				direct.addAll(set);
			}
		}
		if (direct.isEmpty()) {
			return null;
		}
		final java.util.SortedSet<String> refs = new java.util.TreeSet<String>();
		for (MClassInfo<T, C> container : direct) {
			refs.add(ref(container));
			for (MClassInfo<T, C> sub : subtypesOf(container)) {
				refs.add(ref(sub));
			}
		}
		return Ts.union(refs);
	}

	private List<String> typeNamesOfSelfAndSubtypes(String typeName) {
		final List<String> names = new ArrayList<String>();
		collectTypeNames(typeName, names, new HashSet<String>());
		return names;
	}

	/** Namespace of the jaxb-tools inheritance plugin customizations (implements / extends). */
	public static final String INHERITANCE_NAMESPACE_URI = "urn:jaxb.jvnet.org:plugin:inheritance";

	/**
	 * The fully qualified Java types the class is customized to implement or
	 * extend ({@code inheritance:implements}, {@code inheritance:extends}), in
	 * document order; empty if none or if the model is not an XJC model.
	 */
	public List<String> inheritanceTypes(MClassInfo<T, C> classInfo) {
		final List<String> result = new ArrayList<String>();
		final Object origin = classInfo.getOrigin();
		if (!(origin instanceof CMClassInfoOrigin)) {
			return result;
		}
		final Object source = ((CMClassInfoOrigin<?, ?, ?>) origin).getSource();
		if (!(source instanceof CClassInfo)) {
			return result;
		}
		for (CPluginCustomization customization : ((CClassInfo) source).getCustomizations()) {
			final Element element = customization.element;
			if (element != null && INHERITANCE_NAMESPACE_URI.equals(element.getNamespaceURI())
					&& ("implements".equals(element.getLocalName()) || "extends".equals(element.getLocalName()))) {
				customization.markAsAcknowledged();
				final String javaType = element.getTextContent() == null ? "" : element.getTextContent().trim();
				if (!javaType.isEmpty()) {
					result.add(javaType);
				}
			}
		}
		return result;
	}

	/** Whether the class is abstract in the schema (XJC model), for which no creator is generated (CR-010). */
	public boolean isAbstract(MClassInfo<T, C> classInfo) {
		final Object origin = classInfo.getOrigin();
		if (!(origin instanceof CMClassInfoOrigin)) {
			return false;
		}
		final Object source = ((CMClassInfoOrigin<?, ?, ?>) origin).getSource();
		return source instanceof CClassInfo && ((CClassInfo) source).isAbstract();
	}

	public List<MClassInfo<T, C>> subtypesOf(MClassInfo<T, C> classInfo) {
		final List<MClassInfo<T, C>> result = new ArrayList<MClassInfo<T, C>>();
		final java.util.Deque<MClassInfo<T, C>> queue = new java.util.ArrayDeque<MClassInfo<T, C>>();
		final Set<String> visited = new HashSet<String>();
		queue.add(classInfo);
		while (!queue.isEmpty()) {
			final MClassInfo<T, C> current = queue.removeFirst();
			final List<MClassInfo<T, C>> subs = subclasses.get(typeName(current));
			if (subs == null) {
				continue;
			}
			for (MClassInfo<T, C> sub : subs) {
				if (visited.add(typeName(sub))) {
					result.add(sub);
					queue.add(sub);
				}
			}
		}
		return result;
	}

	/**
	 * The TypeScript type of the {@code TYPE_NAME} property: the type's own
	 * name or that of any (transitive) subtype, since Jsonix tags each object
	 * with its concrete type.
	 */
	public String typeNameLiteral(MClassInfo<T, C> classInfo) {
		final List<String> names = new ArrayList<String>();
		final Set<String> visited = new HashSet<String>();
		collectTypeNames(typeName(classInfo), names, visited);
		final List<String> sorted = new ArrayList<String>(names.subList(1, names.size()));
		java.util.Collections.sort(sorted);
		final List<String> literals = new ArrayList<String>();
		literals.add(Ts.stringLiteral(names.get(0)));
		for (String name : sorted) {
			literals.add(Ts.stringLiteral(name));
		}
		return Ts.union(literals);
	}

	private void collectTypeNames(String typeName, List<String> names, Set<String> visited) {
		if (!visited.add(typeName)) {
			return;
		}
		names.add(typeName);
		final List<MClassInfo<T, C>> subs = subclasses.get(typeName);
		if (subs != null) {
			for (MClassInfo<T, C> sub : subs) {
				collectTypeNames(typeName(sub), names, visited);
			}
		}
	}

	public Modules<T, C> getModules() {
		return modules;
	}

	public Module<T, C> getModule() {
		return module;
	}

	public List<GeneratedFile> compile() {
		final List<GeneratedFile> files = new ArrayList<GeneratedFile>();
		files.add(new GeneratedFile(typeScript.getFileName(), compileDeclarations()));
		final String moduleBaseName = typeScript.getBaseName();
		if (typeScript.isFactories()) {
			files.addAll(new FactoryCompiler<T, C>(this, typeScript).compile());
		}
		final Set<String> done = new HashSet<String>();
		for (Output output : module.getOutputs()) {
			final String outputFileName = output.getFileName();
			if (outputFileName == null) {
				continue;
			}
			final String reExport;
			final String extension;
			if (outputFileName.endsWith(".mjs")) {
				// Node16/bundler resolution for an .mjs import expects <base>.d.mts;
				// inside an ES module the relative specifier needs an extension.
				extension = ".mjs";
				reExport = "export * from './" + moduleBaseName + ".js';\n";
			} else if (outputFileName.endsWith(".js")) {
				extension = ".js";
				reExport = "export * from './" + moduleBaseName + "';\n";
			} else {
				continue;
			}
			final String outputBaseName = outputFileName.substring(0, outputFileName.length() - extension.length());
			final String declarationFileName = outputBaseName + (".mjs".equals(extension) ? ".d.mts" : ".d.ts");
			if (declarationFileName.equals(typeScript.getFileName()) || !done.add(declarationFileName)) {
				continue;
			}
			files.add(new GeneratedFile(declarationFileName, "// Generated by jsonix-schema-compiler. Do not edit.\n"
					+ reExport));
		}
		return files;
	}

	private String compileDeclarations() {
		// Compile the body first so that cross-module imports are known.
		final StringBuilder body = new StringBuilder();
		final List<String> mappingNames = new ArrayList<String>();
		final Map<String, String> rootElementTypes = new LinkedHashMap<String, String>();
		for (Mapping<T, C> mapping : module.getMappings()) {
			if (mapping.isEmpty()) {
				continue;
			}
			mappingNames.add(mapping.getMappingName());
			final TsNode root = new TsNode();
			final String rootElementName = new TypeScriptMappingCompiler<T, C>(this, mapping).compile(root);
			if (rootElementName != null) {
				rootElementTypes.put(mapping.getMappingName(),
						(multiMapping ? mapping.getMappingName() + "." : "") + rootElementName);
			}
			if (multiMapping) {
				body.append("\nexport namespace ").append(mapping.getMappingName()).append(" {\n");
				root.render(body, TsNode.INDENT);
				body.append("}\n");
			} else {
				body.append('\n');
				root.render(body, "");
			}
		}

		final StringBuilder sb = new StringBuilder();
		sb.append("// Generated by jsonix-schema-compiler: TypeScript declarations for the Jsonix module [")
				.append(module.getName()).append("]. Do not edit.\n");
		sb.append("// Describes the objects Jsonix produces when unmarshalling with this module's mappings.\n");
		for (Map.Entry<Module<T, C>, String> entry : imports.entrySet()) {
			sb.append("import type * as ").append(entry.getValue()).append(" from './")
					.append(entry.getKey().getTypeScripts().get(0).getBaseName()).append("';\n");
		}
		sb.append('\n').append(supportTypes());
		sb.append(body);
		sb.append('\n');
		for (String mappingName : mappingNames) {
			sb.append("/** The Jsonix mapping [").append(mappingName).append("] exported by the module's JavaScript file. */\n");
			final String rootElementType = rootElementTypes.get(mappingName);
			sb.append("export declare const ").append(mappingName).append(": ").append(Ts.JSONIX_MAPPING)
					.append(rootElementType == null ? "" : "<" + rootElementType + ">").append(";\n");
		}
		return sb.toString();
	}

	private static String supportTypes() {
		return "" //
				+ "/** Jsonix.XML.QName */\n" //
				+ "export interface " + Ts.XML_QNAME + " { namespaceURI: string; localPart: string; prefix?: string; key?: string; string?: string; }\n" //
				+ "/** Jsonix.XML.Calendar: the representation of date, time and dateTime values; unset fields are NaN. */\n" //
				+ "export interface " + Ts.XML_CALENDAR + " { year?: number; month?: number; day?: number; hour?: number; minute?: number; second?: number; fractionalSecond?: number; timezone?: number; }\n" //
				+ "/** The representation of xs:duration values. */\n" //
				+ "export interface " + Ts.XML_DURATION + " { sign?: number; years?: number; months?: number; days?: number; hours?: number; minutes?: number; seconds?: number; }\n" //
				+ "/** An element as returned by an unmarshaller or held by an elementRef property. */\n" //
				+ "export interface " + Ts.TYPED_NAMED_VALUE + "<T> { name: " + Ts.XML_QNAME + "; value: T; }\n" //
				+ "/** A DOM node, allowed where the mapping permits DOM content (allowDom). */\n" //
				+ "export type " + Ts.XML_DOM_NODE + " = any;\n" //
				+ "/** The value of an anyElement property: a typed element, a DOM node or (if mixed) text. */\n" //
				+ "export type " + Ts.XML_ANY_ELEMENT + " = any;\n" //
				+ "/** The value of an anyAttribute property. */\n" //
				+ "export interface " + Ts.XML_ANY_ATTRIBUTES + " { [attributeName: string]: string; }\n" //
				+ "/** A Jsonix mapping object as consumed by new Jsonix.Context([...]); R is the union of its global element types. */\n" //
				+ "export interface " + Ts.JSONIX_MAPPING + "<R = unknown> { readonly " + Ts.ROOT_ELEMENT_MEMBER
				+ "?: R; readonly [key: string]: unknown; }\n";
	}

	/**
	 * The {@code TYPE_NAME} Jsonix assigns to instances of the type:
	 * {@code <mapping name>.<scoped local name>}.
	 */
	public String typeName(MPackagedTypeInfo<T, C> typeInfo) {
		final String mappingName = modules.getMappingName(typeInfo.getPackageInfo().getPackageName());
		final String localName = typeInfo.getContainerLocalName(JsonixConstants.DEFAULT_SCOPED_NAME_DELIMITER);
		return (mappingName == null ? "" : mappingName + ".") + localName;
	}

	/** Whether the type belongs to a mapping, i.e. whether Jsonix tags its instances with a TYPE_NAME. */
	public boolean isMapped(MPackagedTypeInfo<T, C> typeInfo) {
		return modules.getMappingName(typeInfo.getPackageInfo().getPackageName()) != null;
	}

	/** The dependency modules referenced so far and their import aliases, in first-use order. */
	public Map<Module<T, C>, String> getImports() {
		return imports;
	}

	/** See {@link #ownQualifier}. */
	public void setOwnQualifier(String ownQualifier) {
		this.ownQualifier = ownQualifier == null ? "" : ownQualifier;
	}

	/**
	 * A TypeScript reference to the declaration of the given type, qualified
	 * with the mapping namespace and/or an import alias as needed.
	 */
	public String ref(MPackagedTypeInfo<T, C> typeInfo) {
		final String packageName = typeInfo.getPackageInfo().getPackageName();
		final String mappingName = modules.getMappingName(packageName);
		if (mappingName == null) {
			return Ts.ANY;
		}
		final Module<T, C> targetModule = moduleOf(mappingName);
		if (targetModule == null) {
			return Ts.ANY;
		}
		final String localName = typeInfo.getContainerLocalName(JsonixConstants.DEFAULT_SCOPED_NAME_DELIMITER);
		final StringBuilder sb = new StringBuilder();
		if (targetModule != this.module) {
			if (targetModule.getTypeScripts().isEmpty()) {
				return Ts.ANY;
			}
			sb.append(importAlias(targetModule)).append('.');
		} else {
			sb.append(ownQualifier);
		}
		if (hasMultipleMappings(targetModule)) {
			sb.append(mappingName).append('.');
		}
		sb.append(localName);
		return sb.toString();
	}

	/** The elements that (transitively) substitute the given head element. */
	public List<MElementInfo<T, C>> substitutingElements(QName head) {
		final List<MElementInfo<T, C>> result = new ArrayList<MElementInfo<T, C>>();
		collectSubstitutingElements(head, result, new HashSet<QName>());
		return result;
	}

	private void collectSubstitutingElements(QName head, List<MElementInfo<T, C>> result, Set<QName> visited) {
		if (head == null || !visited.add(head)) {
			return;
		}
		final List<MElementInfo<T, C>> substituting = substitutions.get(head);
		if (substituting == null) {
			return;
		}
		for (MElementInfo<T, C> elementInfo : substituting) {
			result.add(elementInfo);
			collectSubstitutingElements(elementInfo.getElementName(), result, visited);
		}
	}

	private Module<T, C> moduleOf(String mappingName) {
		for (Module<T, C> candidate : modules.getModules()) {
			for (Mapping<T, C> mapping : candidate.getMappings()) {
				if (mappingName.equals(mapping.getMappingName())) {
					return candidate;
				}
			}
		}
		return null;
	}

	private boolean hasMultipleMappings(Module<T, C> aModule) {
		if (aModule == this.module) {
			return multiMapping;
		}
		int count = 0;
		for (Mapping<T, C> mapping : aModule.getMappings()) {
			if (!mapping.isEmpty()) {
				count++;
			}
		}
		return count > 1;
	}

	private String importAlias(Module<T, C> dependency) {
		String alias = imports.get(dependency);
		if (alias == null) {
			final String base = "Dep_" + Ts.identifier(dependency.getName());
			alias = base;
			for (int i = 2; !importAliases.add(alias); i++) {
				alias = base + i;
			}
			imports.put(dependency, alias);
		}
		return alias;
	}
}
