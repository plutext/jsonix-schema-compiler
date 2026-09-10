package org.hisrc.jsonix.definition;

import java.text.MessageFormat;

import org.apache.commons.lang3.Validate;

/**
 * A TypeScript declarations output of a module.
 */
public class TypeScript {

	private final String directory = "";
	private final String fileName;
	private final boolean factories;

	public TypeScript(final String fileName) {
		this(fileName, false);
	}

	public TypeScript(final String fileName, final boolean factories) {
		Validate.notNull(fileName);
		this.fileName = fileName;
		this.factories = factories;
	}

	/**
	 * Whether element factories ({@code <base>.factory.mjs}, {@code <base>.el.mjs}
	 * and their declarations, CR-010) are generated next to the declarations.
	 */
	public boolean isFactories() {
		return factories;
	}

	public String getDirectory() {
		return this.directory;
	}

	public String getFileName() {
		return this.fileName;
	}

	/**
	 * File name without the {@code .d.ts} / {@code .ts} suffix, as used in
	 * {@code import ... from './<baseName>'}.
	 */
	public String getBaseName() {
		if (fileName.endsWith(".d.ts")) {
			return fileName.substring(0, fileName.length() - ".d.ts".length());
		} else if (fileName.endsWith(".ts")) {
			return fileName.substring(0, fileName.length() - ".ts".length());
		} else {
			return fileName;
		}
	}

	@Override
	public String toString() {
		return MessageFormat.format("TypeScript [{0}]", this.fileName);
	}
}
