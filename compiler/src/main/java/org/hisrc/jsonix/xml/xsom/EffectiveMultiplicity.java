package org.hisrc.jsonix.xml.xsom;

import java.math.BigInteger;

import org.hisrc.xml.xsom.SchemaComponentAware;
import org.jvnet.jaxb.xml.bind.model.MPropertyInfo;
import org.jvnet.jaxb.xml.bind.model.origin.MOriginated;

import com.sun.tools.xjc.model.Multiplicity;
import com.sun.xml.xsom.XSComplexType;
import com.sun.xml.xsom.XSComponent;
import com.sun.xml.xsom.XSContentType;
import com.sun.xml.xsom.XSModelGroup;
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSTerm;

/**
 * The multiplicity of a property's particle <em>within its class's content
 * model</em>: the particle's own occurrence multiplied by that of every
 * enclosing group, where being one of several branches of a choice makes the
 * minimum zero. {@link ParticleMultiplicityCounter} looks at the particle
 * alone, which marks a choice branch or an element inside an optional
 * sequence as required.
 */
public final class EffectiveMultiplicity {

	private EffectiveMultiplicity() {
	}

	/**
	 * The effective multiplicity of the property, or {@code null} if it cannot
	 * be determined from the schema (no XSOM origin, attribute, or the particle
	 * is not found in the class's content model).
	 */
	public static <T, C extends T> Multiplicity of(MPropertyInfo<T, C> propertyInfo) {
		final XSComponent component = schemaComponent(propertyInfo);
		if (!(component instanceof XSParticle)) {
			return null;
		}
		final XSComponent typeComponent = schemaComponent(propertyInfo.getClassInfo());
		if (!(typeComponent instanceof XSComplexType)) {
			return null;
		}
		final XSContentType contentType = ((XSComplexType) typeComponent).getContentType();
		final XSParticle root = contentType == null ? null : contentType.asParticle();
		if (root == null) {
			return null;
		}
		return find(root, (XSParticle) component, Multiplicity.ONE);
	}

	private static XSComponent schemaComponent(MOriginated<?> originated) {
		final Object origin = originated == null ? null : originated.getOrigin();
		return origin instanceof SchemaComponentAware ? ((SchemaComponentAware) origin).getSchemaComponent() : null;
	}

	private static Multiplicity find(XSParticle particle, XSParticle target, Multiplicity enclosing) {
		final Multiplicity here = Multiplicity.multiply(enclosing, own(particle));
		if (particle == target) {
			// The property's particle may be a model group (a repeating sequence or a choice
			// becomes one collection property): its content's multiplicity counts too, as in
			// MultiplicityCounterNG; for an element declaration this is ONE.
			final Multiplicity content = particle.getTerm().apply(MultiplicityCounterNG.INSTANCE);
			return content == null ? here : Multiplicity.multiply(here, content);
		}
		final XSTerm term = particle.getTerm();
		final XSModelGroup group = term.isModelGroup() ? term.asModelGroup()
				: term.isModelGroupDecl() ? term.asModelGroupDecl().getModelGroup() : null;
		if (group == null) {
			return null;
		}
		final XSParticle[] children = group.getChildren();
		// One of several choice branches may be absent altogether.
		final Multiplicity forChildren = group.getCompositor() == XSModelGroup.CHOICE && children.length > 1
				? Multiplicity.create(BigInteger.ZERO, here.max) : here;
		for (XSParticle child : children) {
			final Multiplicity found = find(child, target, forChildren);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static Multiplicity own(XSParticle particle) {
		final BigInteger max = BigInteger.valueOf(XSParticle.UNBOUNDED).equals(particle.getMaxOccurs()) ? null
				: particle.getMaxOccurs();
		return Multiplicity.create(particle.getMinOccurs(), max);
	}
}
