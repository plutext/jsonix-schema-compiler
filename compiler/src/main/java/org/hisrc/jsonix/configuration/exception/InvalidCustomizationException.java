package org.hisrc.jsonix.configuration.exception;

/**
 * A customization refers to something that does not exist or is malformed
 * (for instance a {@code jsonix:propertyOrder} naming an unknown property).
 */
public class InvalidCustomizationException extends ConfigurationException {

	private static final long serialVersionUID = 1L;

	public InvalidCustomizationException(String message) {
		super(message);
	}
}
