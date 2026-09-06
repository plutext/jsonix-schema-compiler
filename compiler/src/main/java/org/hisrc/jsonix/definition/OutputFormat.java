package org.hisrc.jsonix.definition;

/**
 * The module format of a generated mapping file.
 */
public enum OutputFormat {

	/** Universal module: AMD {@code define}, CommonJS {@code module.exports} or a global. The default. */
	UMD("umd"),
	/** ES module: {@code export const <Mapping> = {...};} (CR-005, phase 2). */
	ESM("esm");

	private final String formatName;

	private OutputFormat(String formatName) {
		this.formatName = formatName;
	}

	public String getFormatName() {
		return formatName;
	}

	public static OutputFormat fromName(String name) {
		if (name == null) {
			return UMD;
		}
		for (OutputFormat format : values()) {
			if (format.formatName.equalsIgnoreCase(name.trim())) {
				return format;
			}
		}
		throw new IllegalArgumentException("Unsupported output format [" + name + "], expected one of [umd, esm].");
	}
}
