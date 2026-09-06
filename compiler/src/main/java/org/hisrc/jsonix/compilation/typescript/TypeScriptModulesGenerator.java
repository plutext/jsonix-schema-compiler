package org.hisrc.jsonix.compilation.typescript;

import org.apache.commons.lang3.Validate;
import org.hisrc.jsonix.compilation.typescript.TypeScriptModuleCompiler.GeneratedFile;
import org.hisrc.jsonix.definition.Module;
import org.hisrc.jsonix.definition.Modules;
import org.hisrc.jsonix.definition.TypeScript;

/**
 * Generates TypeScript declaration files for every module that has a
 * {@code jsonix:typeScript} configuration.
 */
public class TypeScriptModulesGenerator<T, C extends T> {

	private final Modules<T, C> modules;

	public TypeScriptModulesGenerator(Modules<T, C> modules) {
		Validate.notNull(modules);
		this.modules = modules;
	}

	public Modules<T, C> getModules() {
		return modules;
	}

	public void generate(TextFileWriter<T, C> writer) {
		for (final Module<T, C> module : this.modules.getModules()) {
			if (module.isEmpty()) {
				continue;
			}
			for (TypeScript typeScript : module.getTypeScripts()) {
				final TypeScriptModuleCompiler<T, C> moduleCompiler = new TypeScriptModuleCompiler<T, C>(modules,
						module, typeScript);
				for (GeneratedFile file : moduleCompiler.compile()) {
					writer.writeTextFile(module, file.fileName, file.contents);
				}
			}
		}
	}
}
