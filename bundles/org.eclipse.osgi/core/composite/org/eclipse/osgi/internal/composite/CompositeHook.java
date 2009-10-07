package org.eclipse.osgi.internal.composite;

import java.io.IOException;
import java.net.URLConnection;
import java.util.Properties;
import org.eclipse.osgi.baseadaptor.*;
import org.eclipse.osgi.baseadaptor.hooks.AdaptorHook;
import org.eclipse.osgi.framework.adaptor.ScopePolicy;
import org.eclipse.osgi.framework.log.FrameworkLog;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

public class CompositeHook implements HookConfigurator, AdaptorHook {
	// the base adaptor
	private BaseAdaptor adaptor;
	private ScopePolicy policy;

	public void addHooks(HookRegistry hookRegistry) {
		hookRegistry.addAdaptorHook(this);
	}

	public void addProperties(Properties properties) {
		// do nothing

	}

	public FrameworkLog createFrameworkLog() {
		// do nothing
		return null;
	}

	public void frameworkStart(BundleContext context) throws BundleException {
		adaptor.getState().getResolver().setScopePolicy(policy);
	}

	public void frameworkStop(BundleContext context) throws BundleException {
		// do nothing

	}

	public void frameworkStopping(BundleContext context) {
		// do nothing

	}

	public void handleRuntimeError(Throwable error) {
		// do nothing

	}

	public void initialize(BaseAdaptor adaptor) {
		this.adaptor = adaptor;
	}

	public URLConnection mapLocationToURLConnection(String location) throws IOException {
		// do nothing
		return null;
	}

}
