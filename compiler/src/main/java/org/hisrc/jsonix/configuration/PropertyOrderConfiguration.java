package org.hisrc.jsonix.configuration;

import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;

/**
 * {@code <jsonix:propertyOrder typeInfo="CTLine">vmlId style from to</jsonix:propertyOrder>}:
 * the named properties of the type come first, in the given order, in every
 * output (mapping property infos, JSON Schema, TypeScript members); the
 * remaining properties keep their schema order. Motivated by consumers such
 * as Word that are sensitive to attribute order (CR-007).
 */
@XmlRootElement(name = PropertyOrderConfiguration.LOCAL_ELEMENT_NAME)
public class PropertyOrderConfiguration {

	public static final String LOCAL_ELEMENT_NAME = "propertyOrder";
	public static final QName PROPERTY_ORDER_NAME = new QName(ModulesConfiguration.NAMESPACE_URI,
			LOCAL_ELEMENT_NAME, ModulesConfiguration.DEFAULT_PREFIX);

	private String typeInfo;
	private String value;

	@XmlAttribute(name = "typeInfo")
	public String getTypeInfo() {
		return typeInfo;
	}

	public void setTypeInfo(String typeInfo) {
		this.typeInfo = typeInfo;
	}

	@XmlValue
	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	/** The property names, whitespace-separated in the element content. */
	public List<String> getPropertyNames() {
		final List<String> names = new ArrayList<String>();
		if (value != null) {
			for (String name : value.trim().split("\\s+")) {
				if (!name.isEmpty()) {
					names.add(name);
				}
			}
		}
		return names;
	}
}
