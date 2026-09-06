package org.hisrc.jsonix.compilation.typescript;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.jvnet.jaxb.xml.bind.model.MAnyAttributePropertyInfo;
import org.jvnet.jaxb.xml.bind.model.MAnyElementPropertyInfo;
import org.jvnet.jaxb.xml.bind.model.MAttributePropertyInfo;
import org.jvnet.jaxb.xml.bind.model.MClassInfo;
import org.jvnet.jaxb.xml.bind.model.MClassRef;
import org.jvnet.jaxb.xml.bind.model.MClassTypeInfo;
import org.jvnet.jaxb.xml.bind.model.MElement;
import org.jvnet.jaxb.xml.bind.model.MElementInfo;
import org.jvnet.jaxb.xml.bind.model.MElementPropertyInfo;
import org.jvnet.jaxb.xml.bind.model.MElementRefPropertyInfo;
import org.jvnet.jaxb.xml.bind.model.MElementRefsPropertyInfo;
import org.jvnet.jaxb.xml.bind.model.MElementTypeInfo;
import org.jvnet.jaxb.xml.bind.model.MElementTypeRef;
import org.jvnet.jaxb.xml.bind.model.MElementsPropertyInfo;
import org.jvnet.jaxb.xml.bind.model.MPropertyInfoVisitor;
import org.jvnet.jaxb.xml.bind.model.MTypeInfo;
import org.jvnet.jaxb.xml.bind.model.MValuePropertyInfo;
import org.jvnet.jaxb.xml.bind.model.util.DefaultTypeInfoVisitor;

/**
 * Collects the class types that the properties of a class can hold as child
 * objects (element, elements, elementRef and elementRefs properties, expanded
 * through substitution groups). Used to compute {@code PARENT} unions (CR-006).
 */
class HeldTypesCollector<T, C extends T> implements MPropertyInfoVisitor<T, C, Void> {

	private final TypeScriptModuleCompiler<T, C> moduleCompiler;
	private final Set<MClassTypeInfo<T, C, ?>> heldTypes = new LinkedHashSet<MClassTypeInfo<T, C, ?>>();

	private final DefaultTypeInfoVisitor<T, C, Void> classCollector = new DefaultTypeInfoVisitor<T, C, Void>() {
		@Override
		public Void visitClassInfo(MClassInfo<T, C> info) {
			heldTypes.add(info);
			return null;
		}

		@Override
		public Void visitClassRef(MClassRef<T, C> info) {
			heldTypes.add(info);
			return null;
		}
	};

	HeldTypesCollector(TypeScriptModuleCompiler<T, C> moduleCompiler) {
		this.moduleCompiler = Validate.notNull(moduleCompiler);
	}

	Set<MClassTypeInfo<T, C, ?>> getHeldTypes() {
		return heldTypes;
	}

	private void hold(MTypeInfo<T, C> typeInfo) {
		if (typeInfo != null) {
			typeInfo.acceptTypeInfoVisitor(classCollector);
		}
	}

	private <M extends MElementTypeInfo<T, C, O>, O> void holdElement(M elementTypeInfo) {
		hold(elementTypeInfo.getTypeInfo());
		for (MElementInfo<T, C> substituting : moduleCompiler.substitutingElements(elementTypeInfo.getElementName())) {
			hold(substituting.getTypeInfo());
		}
	}

	@Override
	public Void visitElementPropertyInfo(MElementPropertyInfo<T, C> info) {
		hold(info.getTypeInfo());
		return null;
	}

	@Override
	public Void visitElementsPropertyInfo(MElementsPropertyInfo<T, C> info) {
		for (MElementTypeRef<T, C> ref : info.getElementTypeInfos()) {
			hold(ref.getTypeInfo());
		}
		return null;
	}

	@Override
	public Void visitElementRefPropertyInfo(MElementRefPropertyInfo<T, C> info) {
		if (info.isTypedObjectAllowed()) {
			holdElement(info);
		}
		return null;
	}

	@Override
	public Void visitElementRefsPropertyInfo(MElementRefsPropertyInfo<T, C> info) {
		if (info.isTypedObjectAllowed()) {
			for (MElement<T, C> ref : info.getElementTypeInfos()) {
				holdElement(ref);
			}
		}
		return null;
	}

	@Override
	public Void visitAnyElementPropertyInfo(MAnyElementPropertyInfo<T, C> info) {
		return null;
	}

	@Override
	public Void visitAttributePropertyInfo(MAttributePropertyInfo<T, C> info) {
		return null;
	}

	@Override
	public Void visitAnyAttributePropertyInfo(MAnyAttributePropertyInfo<T, C> info) {
		return null;
	}

	@Override
	public Void visitValuePropertyInfo(MValuePropertyInfo<T, C> info) {
		return null;
	}
}
