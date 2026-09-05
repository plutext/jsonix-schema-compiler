package org.hisrc.jsonix.compilation.mapping.typeinfo.builtin;

import java.util.Base64;

import org.jvnet.jaxb.xmlschema.XmlSchemaConstants;

public class Base64BinaryTypeInfoCompiler<T, C extends T, O> extends BinaryTypeInfoCompiler<T, C, O> {

	public Base64BinaryTypeInfoCompiler() {
		super("Base64Binary", XmlSchemaConstants.BASE64BINARY);
	}

	@Override
	public byte[] parse(String item) {
		// java.util.Base64 rather than jakarta.xml.bind.DatatypeConverter: the Jakarta 4.0.x
		// implementation rejects valid one-byte input such as "QQ==" (CR-004).
		return Base64.getMimeDecoder().decode(item);
	}
}
