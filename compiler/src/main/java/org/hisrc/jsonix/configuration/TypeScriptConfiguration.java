package org.hisrc.jsonix.configuration;

import javax.xml.namespace.QName;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import org.apache.commons.lang3.Validate;
import org.hisrc.jsonix.definition.TypeScript;

/**
 * Configuration of the TypeScript declarations output of a module
 * ({@code <jsonix:typeScript fileName="${module.name}.d.ts"/>}).
 */
@XmlRootElement(name = TypeScriptConfiguration.LOCAL_ELEMENT_NAME)
@XmlType(propOrder = {})
public class TypeScriptConfiguration {

	public static final String LOCAL_ELEMENT_NAME = "typeScript";
	public static final String STANDARD_FILE_NAME_PATTERN = ModuleConfiguration.MODULE_NAME_PROPERTY
			+ ".d.ts";

	private String fileName = STANDARD_FILE_NAME_PATTERN;
	public static final QName TYPE_SCRIPT_NAME = new QName(
			ModulesConfiguration.NAMESPACE_URI, LOCAL_ELEMENT_NAME,
			ModulesConfiguration.DEFAULT_PREFIX);

	public TypeScriptConfiguration() {

	}

	public TypeScriptConfiguration(String fileName) {
		Validate.notNull(fileName);
		this.fileName = fileName;
	}

	@XmlAttribute(name = "fileName")
	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		Validate.notNull(fileName);
		this.fileName = fileName;
	}

	public TypeScript build(String moduleName) {
		Validate.notNull(moduleName);
		final String fileName = getFileName().replace(
				ModuleConfiguration.MODULE_NAME_PROPERTY, moduleName);
		return new TypeScript(fileName);
	}
}
