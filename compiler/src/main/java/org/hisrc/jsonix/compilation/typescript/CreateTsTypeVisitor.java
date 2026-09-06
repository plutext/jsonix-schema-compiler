package org.hisrc.jsonix.compilation.typescript;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.hisrc.jsonix.xml.xsom.CollectSimpleTypeNamesVisitor;
import org.hisrc.xml.xsom.SchemaComponentAware;
import org.jvnet.jaxb.xml.bind.model.origin.MOriginated;

import com.sun.xml.xsom.XSComponent;
import org.jvnet.jaxb.xml.bind.model.MBuiltinLeafInfo;
import org.jvnet.jaxb.xml.bind.model.MClassInfo;
import org.jvnet.jaxb.xml.bind.model.MClassRef;
import org.jvnet.jaxb.xml.bind.model.MEnumLeafInfo;
import org.jvnet.jaxb.xml.bind.model.MID;
import org.jvnet.jaxb.xml.bind.model.MIDREF;
import org.jvnet.jaxb.xml.bind.model.MIDREFS;
import org.jvnet.jaxb.xml.bind.model.MList;
import org.jvnet.jaxb.xml.bind.model.MTypeInfo;
import org.jvnet.jaxb.xml.bind.model.MTypeInfoVisitor;
import org.jvnet.jaxb.xml.bind.model.MWildcardTypeInfo;

/**
 * Maps a model type info to the TypeScript type of the value Jsonix produces
 * for it at runtime. Built-in XML Schema types are mapped by the JavaScript
 * representation Jsonix uses (see CR-005 for the table).
 */
public class CreateTsTypeVisitor<T, C extends T> implements MTypeInfoVisitor<T, C, String> {

	private static final String XSD = XMLConstants.W3C_XML_SCHEMA_NS_URI;

	private static final Set<String> NUMBER_TYPES = new HashSet<String>(Arrays.asList("float", "double", "decimal",
			"integer", "nonPositiveInteger", "negativeInteger", "long", "int", "short", "byte", "nonNegativeInteger",
			"unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte", "positiveInteger"));

	private static final Set<String> CALENDAR_TYPES = new HashSet<String>(Arrays.asList("dateTime", "time", "date",
			"gYearMonth", "gYear", "gMonthDay", "gDay", "gMonth"));

	/** Types whose enumeration values the mapping compiler runs through NormalizedStringAdapter. */
	private static final Set<String> NORMALIZED_STRING_TYPES = new HashSet<String>(Arrays.asList("normalizedString",
			"token", "language", "Name", "NCName", "ENTITY", "ENTITIES", "NMTOKEN", "NMTOKENS", "anyURI"));

	private static final Set<String> STRING_LIST_TYPES = new HashSet<String>(Arrays.asList("IDREFS", "NMTOKENS",
			"ENTITIES"));

	private final TypeScriptModuleCompiler<T, C> moduleCompiler;
	/**
	 * The property, element or enum the type is used in; its schema component
	 * (if any) tells the concrete XML Schema simple type, which the model's
	 * builtin leaf info alone does not (e.g. every date-like type is one
	 * XMLGregorianCalendar builtin).
	 */
	private final MOriginated<?> originated;

	public CreateTsTypeVisitor(TypeScriptModuleCompiler<T, C> moduleCompiler) {
		this(moduleCompiler, null);
	}

	public CreateTsTypeVisitor(TypeScriptModuleCompiler<T, C> moduleCompiler, MOriginated<?> originated) {
		this.moduleCompiler = Validate.notNull(moduleCompiler);
		this.originated = originated;
	}

	public String typeOf(MTypeInfo<T, C> typeInfo) {
		final String type = typeInfo.acceptTypeInfoVisitor(this);
		return type == null ? Ts.ANY : type;
	}

	/** The type of {@code typeInfo} as used by {@code originated}. */
	public String typeOf(MOriginated<?> originated, MTypeInfo<T, C> typeInfo) {
		return new CreateTsTypeVisitor<T, C>(moduleCompiler, originated).typeOf(typeInfo);
	}

	/** Whether the (builtin) type's lexical values get whitespace-normalised, as in the mapping. */
	public boolean isNormalizedString(MOriginated<?> originated, MTypeInfo<T, C> typeInfo) {
		if (!(typeInfo instanceof MBuiltinLeafInfo)) {
			return false;
		}
		final QName name = new CreateTsTypeVisitor<T, C>(moduleCompiler, originated)
				.resolveBuiltinName((MBuiltinLeafInfo<T, C>) typeInfo);
		return name != null && XSD.equals(name.getNamespaceURI())
				&& NORMALIZED_STRING_TYPES.contains(name.getLocalPart());
	}

	@Override
	public String visitBuiltinLeafInfo(MBuiltinLeafInfo<T, C> info) {
		return builtin(resolveBuiltinName(info));
	}

	private QName resolveBuiltinName(MBuiltinLeafInfo<T, C> info) {
		final List<QName> candidates = new LinkedList<QName>();
		final Object origin = originated == null ? null : originated.getOrigin();
		if (origin instanceof SchemaComponentAware) {
			final XSComponent component = ((SchemaComponentAware) origin).getSchemaComponent();
			if (component != null) {
				final CollectSimpleTypeNamesVisitor visitor = new CollectSimpleTypeNamesVisitor();
				component.visit(visitor);
				candidates.addAll(visitor.getTypeNames());
			}
		}
		candidates.add(info.getTypeName());
		for (QName candidate : candidates) {
			if (candidate != null && XSD.equals(candidate.getNamespaceURI())) {
				return candidate;
			}
		}
		return info.getTypeName();
	}

	public static String builtin(QName typeName) {
		if (typeName == null || !XSD.equals(typeName.getNamespaceURI())) {
			return Ts.ANY;
		}
		final String localPart = typeName.getLocalPart();
		if ("anyType".equals(localPart)) {
			return Ts.ANY;
		} else if ("boolean".equals(localPart)) {
			return Ts.BOOLEAN;
		} else if (NUMBER_TYPES.contains(localPart)) {
			return Ts.NUMBER;
		} else if (CALENDAR_TYPES.contains(localPart)) {
			return Ts.XML_CALENDAR;
		} else if ("QName".equals(localPart)) {
			return Ts.XML_QNAME;
		} else if ("duration".equals(localPart)) {
			return Ts.XML_DURATION;
		} else if (STRING_LIST_TYPES.contains(localPart)) {
			return Ts.array(Ts.STRING);
		} else {
			// string, normalizedString, token, language, Name, NCName, NMTOKEN,
			// ID, IDREF, ENTITY, anyURI, base64Binary, hexBinary, anySimpleType,
			// NOTATION and anything else lexical
			return Ts.STRING;
		}
	}

	@Override
	public String visitEnumLeafInfo(MEnumLeafInfo<T, C> info) {
		return moduleCompiler.ref(info);
	}

	@Override
	public String visitClassInfo(MClassInfo<T, C> info) {
		return moduleCompiler.ref(info);
	}

	@Override
	public String visitClassRef(MClassRef<T, C> info) {
		return moduleCompiler.ref(info);
	}

	@Override
	public String visitList(MList<T, C> info) {
		return Ts.array(typeOf(info.getItemTypeInfo()));
	}

	@Override
	public String visitWildcardTypeInfo(MWildcardTypeInfo<T, C> info) {
		return Ts.ANY;
	}

	@Override
	public String visitID(MID<T, C> info) {
		return Ts.STRING;
	}

	@Override
	public String visitIDREF(MIDREF<T, C> info) {
		return Ts.STRING;
	}

	@Override
	public String visitIDREFS(MIDREFS<T, C> info) {
		return Ts.array(Ts.STRING);
	}
}
