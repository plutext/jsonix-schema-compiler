package org.hisrc.jsonix.definition;

import java.text.MessageFormat;

import org.apache.commons.lang3.Validate;
import org.hisrc.jsonix.naming.Naming;

public class Output {
	
	private final String outputPackageName = "";
	private final String directory = "";
	private final String fileName;
	private final Naming naming;
	private final OutputFormat format;

	public Output(final String fileName, final Naming naming) {
		this(fileName, naming, OutputFormat.UMD);
	}

	public Output(final String fileName, final Naming naming, final OutputFormat format) {
		Validate.notNull(fileName);
		Validate.notNull(naming);
		Validate.notNull(format);
		this.fileName = fileName;
		this.naming = naming;
		this.format = format;
	}

	public OutputFormat getFormat() {
		return this.format;
	}

	public String getOutputPackageName() {
		return outputPackageName;
	}

	public String getDirectory() {
		return this.directory;
	}

	public String getFileName() {
		return this.fileName;
	}

	public Naming getNaming() {
		return this.naming;
	}
	
	@Override
	public String toString() {
		return MessageFormat.format("Output [{0}]", this.fileName);
	}
	
}
