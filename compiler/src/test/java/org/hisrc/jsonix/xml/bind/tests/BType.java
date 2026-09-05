package org.hisrc.jsonix.xml.bind.tests;

import java.util.Date;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class BType {
	
	@XmlElement
	public Integer one;

	@XmlElement(defaultValue = "2")
	public Integer two;

	@XmlElement(nillable = true)
	public Integer three;

	@XmlElement(nillable = true, defaultValue = "4")
	public Integer four;
	
	@XmlElement
	public Date five;

	@XmlElement
	public Date six;
}
