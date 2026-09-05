package org.hisrc.jsonix.compilation.jsonschema.typeinfo.builtin;

import java.util.Base64;

import org.jvnet.jaxb.xmlschema.XmlSchemaConstants;

public class Base64BinaryTypeInfoProducer<T, C extends T, O> extends BinaryTypeInfoProducer<T, C, O> {

	public Base64BinaryTypeInfoProducer() {
		super(XmlSchemaConstants.BASE64BINARY);
	}

	@Override
	public byte[] parse(String item) {
		// java.util.Base64 rather than jakarta.xml.bind.DatatypeConverter: the Jakarta 4.0.x
		// implementation rejects valid one-byte input such as "QQ==" (CR-004).
		return Base64.getMimeDecoder().decode(item);
	}
}
