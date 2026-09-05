package org.hisrc.jsonix.compilation.mapping.typeinfo.builtin;

import jakarta.xml.bind.DatatypeConverter;

import org.jvnet.jaxb.xmlschema.XmlSchemaConstants;

public class HexBinaryTypeInfoCompiler<T, C extends T, O> extends BinaryTypeInfoCompiler<T, C, O> {

	public HexBinaryTypeInfoCompiler() {
		super("HexBinary", XmlSchemaConstants.HEXBINARY);
	}

	@Override
	public byte[] parse(String item) {
		return DatatypeConverter.parseHexBinary(item);
	}
}
