package org.hisrc.jsonix.xml.bind.tests;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class AType {
	
	@XmlElement
	public String one;

	@XmlElement(defaultValue = "two")
	public String two;

	@XmlElement(nillable = true)
	public String three;

	@XmlElement(nillable = true, defaultValue = "four")
	public String four;
}
