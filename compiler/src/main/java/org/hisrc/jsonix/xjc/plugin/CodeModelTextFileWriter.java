package org.hisrc.jsonix.xjc.plugin;

import java.text.MessageFormat;

import org.apache.commons.lang3.Validate;
import org.hisrc.jsonix.compilation.typescript.TextFileWriter;
import org.hisrc.jsonix.definition.Module;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.fmt.JTextFile;
import com.sun.tools.xjc.model.nav.NClass;
import com.sun.tools.xjc.model.nav.NType;

/**
 * Writes generated text files as resources of the XJC code model, next to the
 * generated Java sources.
 */
public class CodeModelTextFileWriter implements TextFileWriter<NType, NClass> {

	private final JCodeModel codeModel;
	private final ErrorHandler errorHandler;

	public CodeModelTextFileWriter(JCodeModel codeModel, ErrorHandler errorHandler) {
		this.codeModel = Validate.notNull(codeModel);
		this.errorHandler = Validate.notNull(errorHandler);
	}

	@Override
	public void writeTextFile(Module<NType, NClass> module, String fileName, String contents) {
		Validate.notNull(fileName);
		Validate.notNull(contents);
		try {
			final JPackage _package = codeModel._package("");
			final JTextFile textFile = new JTextFile(fileName);
			textFile.setContents(contents);
			_package.addResourceFile(textFile);
		} catch (RuntimeException rex) {
			try {
				errorHandler.error(new SAXParseException(MessageFormat.format(
						"Could not create the file [{0}] for the module [{1}].", fileName, module.getName()), null,
						rex));
			} catch (SAXException ignored) {

			}
		}
	}
}
