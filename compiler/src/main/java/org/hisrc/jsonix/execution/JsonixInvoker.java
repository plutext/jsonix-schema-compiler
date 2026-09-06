package org.hisrc.jsonix.execution;

import org.hisrc.jsonix.compilation.jsonschema.JsonSchemaModulesGenerator;
import org.hisrc.jsonix.compilation.jsonschema.JsonStructureWriter;
import org.hisrc.jsonix.compilation.mapping.ModulesCompiler;
import org.hisrc.jsonix.compilation.mapping.ProgramWriter;
import org.hisrc.jsonix.compilation.typescript.TextFileWriter;
import org.hisrc.jsonix.compilation.typescript.TypeScriptModulesGenerator;
import org.hisrc.jsonix.configuration.TypeScriptConfiguration;
import org.hisrc.jsonix.configuration.JsonSchemaConfiguration;
import org.hisrc.jsonix.configuration.ModulesConfiguration;
import org.hisrc.jsonix.configuration.ModulesConfigurationUnmarshaller;
import org.hisrc.jsonix.configuration.OutputConfiguration;
import org.hisrc.jsonix.context.DefaultJsonixContext;
import org.hisrc.jsonix.definition.Modules;
import org.hisrc.jsonix.settings.Settings;
import org.jvnet.jaxb.xjc.model.concrete.XJCCMInfoFactory;
import org.jvnet.jaxb.xml.bind.model.MModelInfo;
import org.slf4j.Logger;

import com.sun.tools.xjc.model.Model;
import com.sun.tools.xjc.model.nav.NClass;
import com.sun.tools.xjc.model.nav.NType;

public class JsonixInvoker {

	public void execute(Settings settings, Model model,
			ProgramWriter<NType, NClass> programWriter,
			JsonStructureWriter<NType, NClass> jsonStructureWriter) {
		execute(settings, model, programWriter, jsonStructureWriter, null);
	}

	public void execute(Settings settings, Model model,
			ProgramWriter<NType, NClass> programWriter,
			JsonStructureWriter<NType, NClass> jsonStructureWriter,
			TextFileWriter<NType, NClass> textFileWriter) {

		final DefaultJsonixContext context = new DefaultJsonixContext();

		context.setLogLevel(settings.getLogLevel().asInt());

		final Logger logger = context.getLoggerFactory().getLogger(
				JsonixInvoker.class.getName());

		if (model == null) {
			logger.error("The model is null, there was probably a problem parsing schemas.");
			return;
		}

		final ModulesConfigurationUnmarshaller customizationHandler = new ModulesConfigurationUnmarshaller(
				context);

		final OutputConfiguration defaultOutputConfiguration = new OutputConfiguration(
				settings.getDefaultNaming().getName(),
				OutputConfiguration.STANDARD_FILE_NAME_PATTERN);

		final JsonSchemaConfiguration defaultJsonSchemaConfiguration = settings
				.isGenerateJsonSchema() ? new JsonSchemaConfiguration(
				JsonSchemaConfiguration.STANDARD_FILE_NAME_PATTERN) : null;

		final TypeScriptConfiguration defaultTypeScriptConfiguration = settings
				.isGenerateTypeScript() ? new TypeScriptConfiguration(
				TypeScriptConfiguration.STANDARD_FILE_NAME_PATTERN) : null;

		final ModulesConfiguration modulesConfiguration = customizationHandler
				.unmarshal(model, defaultOutputConfiguration,
						defaultJsonSchemaConfiguration,
						defaultTypeScriptConfiguration);

		final MModelInfo<NType, NClass> modelinfo = new XJCCMInfoFactory(model)
				.createModel();

		final Modules<NType, NClass> modules = modulesConfiguration.build(
				context, modelinfo);

		final ModulesCompiler<NType, NClass> modulesCompiler = new ModulesCompiler<NType, NClass>(
				modules);

		modulesCompiler.compile(programWriter, textFileWriter);

		final JsonSchemaModulesGenerator<NType, NClass> jsonSchemaModulesGenerator = new JsonSchemaModulesGenerator<NType, NClass>(
				modules);
		jsonSchemaModulesGenerator.generate(jsonStructureWriter);

		if (textFileWriter != null) {
			new TypeScriptModulesGenerator<NType, NClass>(modules)
					.generate(textFileWriter);
		} else {
			logger.warn("No text file writer was provided, TypeScript declarations (if configured) will not be generated.");
		}
	}
}
