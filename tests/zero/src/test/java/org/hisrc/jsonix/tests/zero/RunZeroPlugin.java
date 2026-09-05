package org.hisrc.jsonix.tests.zero;

import java.util.ArrayList;
import java.util.List;

import org.jvnet.jaxb.maven.AbstractXJCMojo;
import org.jvnet.jaxb.maven.test.RunXJCMojo;

public class RunZeroPlugin extends RunXJCMojo {
	
	@Override
	protected void configureMojo(AbstractXJCMojo mojo) {
		super.configureMojo(mojo);
		mojo.setExtension(true);
		mojo.setForceRegenerate(true);
		mojo.setDebug(false);
	}

	@Override
	public List<String> getArgs() {
		final List<String> args = new ArrayList<String>(super.getArgs());
		args.add("-Xjsonix");
		return args;
	}

}
