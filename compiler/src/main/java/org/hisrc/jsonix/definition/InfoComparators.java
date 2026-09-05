package org.hisrc.jsonix.definition;

import java.util.Comparator;

import javax.xml.namespace.QName;

import org.hisrc.jsonix.JsonixConstants;
import org.jvnet.jaxb.xml.bind.model.MClassInfo;
import org.jvnet.jaxb.xml.bind.model.MElementInfo;
import org.jvnet.jaxb.xml.bind.model.MPackagedTypeInfo;

/**
 * Stable orderings for model infos, so that generated mappings and JSON
 * Schemas do not depend on the iteration order of hash-based collections
 * (which differs between JDK and JAXB versions).
 */
public final class InfoComparators {

	private InfoComparators() {
	}

	/**
	 * Orders packaged type infos (class infos, enum leaf infos) by their scoped
	 * local name (e.g. {@code Items.Item}), which is the name emitted as
	 * {@code localName} in mappings, falling back to the fully qualified name.
	 */
	public static final Comparator<MPackagedTypeInfo<?, ?>> PACKAGED_TYPE_INFO = new Comparator<MPackagedTypeInfo<?, ?>>() {
		@Override
		public int compare(MPackagedTypeInfo<?, ?> a, MPackagedTypeInfo<?, ?> b) {
			final int byLocalName = compareNullsFirst(
					a.getContainerLocalName(JsonixConstants.DEFAULT_SCOPED_NAME_DELIMITER),
					b.getContainerLocalName(JsonixConstants.DEFAULT_SCOPED_NAME_DELIMITER));
			return byLocalName != 0 ? byLocalName : compareNullsFirst(a.getName(), b.getName());
		}
	};

	/**
	 * Orders element infos by namespace URI, then local part, then the scoped
	 * local name of the scope class (global elements first).
	 */
	public static final Comparator<MElementInfo<?, ?>> ELEMENT_INFO = new Comparator<MElementInfo<?, ?>>() {
		@Override
		public int compare(MElementInfo<?, ?> a, MElementInfo<?, ?> b) {
			final QName an = a.getElementName();
			final QName bn = b.getElementName();
			int result = compareNullsFirst(an == null ? null : an.getNamespaceURI(),
					bn == null ? null : bn.getNamespaceURI());
			if (result != 0) {
				return result;
			}
			result = compareNullsFirst(an == null ? null : an.getLocalPart(), bn == null ? null : bn.getLocalPart());
			if (result != 0) {
				return result;
			}
			return compareNullsFirst(scopeName(a.getScope()), scopeName(b.getScope()));
		}
	};

	private static String scopeName(MClassInfo<?, ?> scope) {
		return scope == null ? null : scope.getContainerLocalName(JsonixConstants.DEFAULT_SCOPED_NAME_DELIMITER);
	}

	private static int compareNullsFirst(String a, String b) {
		if (a == null) {
			return b == null ? 0 : -1;
		}
		return b == null ? 1 : a.compareTo(b);
	}
}
