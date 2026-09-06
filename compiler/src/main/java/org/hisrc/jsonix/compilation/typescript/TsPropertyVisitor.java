package org.hisrc.jsonix.compilation.typescript;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.commons.lang3.Validate;
import org.hisrc.jsonix.xml.xsom.ParticleMultiplicityCounter;
import org.hisrc.xml.xsom.XSFunctionApplier;
import org.jvnet.jaxb.xml.bind.model.MAnyAttributePropertyInfo;
import org.jvnet.jaxb.xml.bind.model.MAnyElementPropertyInfo;
import org.jvnet.jaxb.xml.bind.model.MAttributePropertyInfo;
import org.jvnet.jaxb.xml.bind.model.MElement;
import org.jvnet.jaxb.xml.bind.model.MElementInfo;
import org.jvnet.jaxb.xml.bind.model.MElementPropertyInfo;
import org.jvnet.jaxb.xml.bind.model.MElementRefPropertyInfo;
import org.jvnet.jaxb.xml.bind.model.MElementRefsPropertyInfo;
import org.jvnet.jaxb.xml.bind.model.MElementTypeInfo;
import org.jvnet.jaxb.xml.bind.model.MElementTypeRef;
import org.jvnet.jaxb.xml.bind.model.MElementsPropertyInfo;
import org.jvnet.jaxb.xml.bind.model.MMixable;
import org.jvnet.jaxb.xml.bind.model.MPropertyInfo;
import org.jvnet.jaxb.xml.bind.model.MPropertyInfoVisitor;
import org.jvnet.jaxb.xml.bind.model.MValuePropertyInfo;
import org.jvnet.jaxb.xml.bind.model.MWildcard;

import com.sun.tools.xjc.model.Multiplicity;

/**
 * Renders one interface member ({@code name?: type;}) per property, following
 * the runtime representation Jsonix uses for each property kind.
 */
public class TsPropertyVisitor<T, C extends T> implements MPropertyInfoVisitor<T, C, String> {

	private final TypeScriptModuleCompiler<T, C> moduleCompiler;
	private final CreateTsTypeVisitor<T, C> types;
	private final XSFunctionApplier<Multiplicity> multiplicityCounter = new XSFunctionApplier<Multiplicity>(
			ParticleMultiplicityCounter.INSTANCE);

	public TsPropertyVisitor(TypeScriptModuleCompiler<T, C> moduleCompiler) {
		this.moduleCompiler = Validate.notNull(moduleCompiler);
		this.types = new CreateTsTypeVisitor<T, C>(moduleCompiler);
	}

	/** Same rule as the mapping compiler: required iff minOccurs is not 0. */
	private boolean isRequired(MPropertyInfo<T, C> propertyInfo) {
		final Multiplicity multiplicity = multiplicityCounter.apply(propertyInfo.getOrigin());
		return multiplicity != null && multiplicity.min != null && !BigInteger.ZERO.equals(multiplicity.min);
	}

	private String member(MPropertyInfo<T, C> propertyInfo, String type) {
		final String valueType = propertyInfo.isCollection() ? Ts.array(type) : type;
		return Ts.member(propertyInfo.getPrivateName()) + (isRequired(propertyInfo) ? "" : "?") + ": " + valueType
				+ ";";
	}

	private <M extends MElementTypeInfo<T, C, O>, O> List<String> typedNamedValues(M elementTypeInfo,
			List<String> result) {
		final QName elementName = elementTypeInfo.getElementName();
		result.add(Ts.generic(Ts.TYPED_NAMED_VALUE, types.typeOf(elementTypeInfo, elementTypeInfo.getTypeInfo())));
		for (MElementInfo<T, C> substituting : moduleCompiler.substitutingElements(elementName)) {
			result.add(Ts.generic(Ts.TYPED_NAMED_VALUE, types.typeOf(substituting, substituting.getTypeInfo())));
		}
		return result;
	}

	private void addWildcardAndMixed(MWildcard wildcard, MMixable mixable, List<String> result) {
		if (wildcard != null && wildcard.isDomAllowed()) {
			result.add(Ts.XML_DOM_NODE);
		}
		if (mixable != null && mixable.isMixed()) {
			result.add(Ts.STRING);
		}
	}

	@Override
	public String visitElementPropertyInfo(MElementPropertyInfo<T, C> info) {
		return member(info, types.typeOf(info, info.getTypeInfo()));
	}

	@Override
	public String visitElementsPropertyInfo(MElementsPropertyInfo<T, C> info) {
		final List<String> alternatives = new ArrayList<String>();
		for (MElementTypeRef<T, C> ref : info.getElementTypeInfos()) {
			alternatives.add(types.typeOf(ref, ref.getTypeInfo()));
		}
		return member(info, Ts.union(alternatives));
	}

	@Override
	public String visitAnyElementPropertyInfo(MAnyElementPropertyInfo<T, C> info) {
		return member(info, Ts.XML_ANY_ELEMENT);
	}

	@Override
	public String visitAttributePropertyInfo(MAttributePropertyInfo<T, C> info) {
		return member(info, types.typeOf(info, info.getTypeInfo()));
	}

	@Override
	public String visitAnyAttributePropertyInfo(MAnyAttributePropertyInfo<T, C> info) {
		return member(info, Ts.XML_ANY_ATTRIBUTES);
	}

	@Override
	public String visitValuePropertyInfo(MValuePropertyInfo<T, C> info) {
		return member(info, types.typeOf(info, info.getTypeInfo()));
	}

	@Override
	public String visitElementRefPropertyInfo(MElementRefPropertyInfo<T, C> info) {
		final List<String> alternatives = new ArrayList<String>();
		if (info.isTypedObjectAllowed()) {
			typedNamedValues(info, alternatives);
		}
		addWildcardAndMixed(info, info, alternatives);
		return member(info, Ts.union(alternatives));
	}

	@Override
	public String visitElementRefsPropertyInfo(MElementRefsPropertyInfo<T, C> info) {
		final List<String> alternatives = new ArrayList<String>();
		if (info.isTypedObjectAllowed()) {
			for (MElement<T, C> ref : info.getElementTypeInfos()) {
				typedNamedValues(ref, alternatives);
			}
		}
		addWildcardAndMixed(info, info, alternatives);
		return member(info, Ts.union(alternatives));
	}
}
