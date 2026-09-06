package org.hisrc.jsonix.compilation.typescript;

import org.hisrc.jsonix.definition.Module;

/**
 * Writes a generated text file (for instance TypeScript declarations) for a
 * module. Implemented by the XJC plugin (as a code model resource) and by the
 * standalone compiler (as a file in the target directory).
 */
public interface TextFileWriter<T, C extends T> {

	public void writeTextFile(Module<T, C> module, String fileName,
			String contents);
}
