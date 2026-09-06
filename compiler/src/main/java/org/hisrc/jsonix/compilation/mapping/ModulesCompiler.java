package org.hisrc.jsonix.compilation.mapping;

import org.apache.commons.lang3.Validate;
import org.hisrc.jscm.codemodel.JSCodeModel;
import org.hisrc.jscm.codemodel.JSProgram;
import org.hisrc.jscm.codemodel.impl.CodeModelImpl;
import org.hisrc.jsonix.definition.Module;
import org.hisrc.jsonix.definition.Modules;
import org.hisrc.jsonix.compilation.typescript.TextFileWriter;
import org.hisrc.jsonix.definition.Output;
import org.hisrc.jsonix.definition.OutputFormat;

public class ModulesCompiler<T, C extends T> {

	private final Modules<T, C> modules;

	public ModulesCompiler(Modules<T, C> modules) {
		Validate.notNull(modules);
		this.modules = modules;
	}

	public void compile(ProgramWriter<T, C> programWriter) {
		compile(programWriter, null);
	}

	public void compile(ProgramWriter<T, C> programWriter, TextFileWriter<T, C> textFileWriter) {
		final JSCodeModel codeModel = new CodeModelImpl();

		for (Module<T, C> module : this.modules.getModules()) {
			if (!module.isEmpty()) {
				for (Output output : module.getOutputs()) {
					final ModuleCompiler<T, C> moduleCompiler = new ModuleCompiler<T, C>(
							codeModel, modules, module, output);

					if (output.getFormat() == OutputFormat.ESM) {
						if (textFileWriter == null) {
							throw new IllegalStateException(
									"The ES module output [" + output.getFileName()
											+ "] requires a text file writer.");
						}
						textFileWriter.writeTextFile(module, output.getFileName(),
								moduleCompiler.compileEsm());
					} else {
						final JSProgram program = moduleCompiler.compile();
						programWriter.writeProgram(module, program, output);
					}
				}
			}
		}
	}

}
