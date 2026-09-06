package org.hisrc.jsonix;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;

import org.apache.commons.lang3.Validate;
import org.hisrc.jsonix.compilation.typescript.TextFileWriter;
import org.hisrc.jsonix.definition.Module;
import org.xml.sax.SAXParseException;

import com.sun.tools.xjc.ErrorReceiver;
import com.sun.tools.xjc.model.nav.NClass;
import com.sun.tools.xjc.model.nav.NType;

/**
 * Writes generated text files (TypeScript declarations) into the target
 * directory of the standalone compiler.
 */
public class TargetDirectoryTextFileWriter implements TextFileWriter<NType, NClass> {

	private final File targetDirectory;
	private final ErrorReceiver errorReceiver;

	TargetDirectoryTextFileWriter(File targetDirectory, ErrorReceiver errorReceiver) {
		this.targetDirectory = Validate.notNull(targetDirectory);
		this.errorReceiver = Validate.notNull(errorReceiver);
	}

	@Override
	public void writeTextFile(Module<NType, NClass> module, String fileName, String contents) {
		Validate.notNull(fileName);
		Validate.notNull(contents);
		try {
			targetDirectory.mkdirs();
			final File file = new File(targetDirectory, fileName);
			final Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
			try {
				writer.write(contents);
			} finally {
				writer.close();
			}
		} catch (IOException ioex) {
			errorReceiver.error(new SAXParseException(MessageFormat.format(
					"Could not create the file [{0}] for the module [{1}].", fileName, module.getName()), null, ioex));
		}
	}
}
