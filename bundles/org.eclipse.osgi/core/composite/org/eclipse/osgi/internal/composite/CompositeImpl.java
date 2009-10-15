/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.osgi.internal.composite;

import java.io.InputStream;
import java.util.Dictionary;
import java.util.Map;
import org.eclipse.osgi.framework.adaptor.BundleData;
import org.eclipse.osgi.framework.internal.core.*;
import org.eclipse.osgi.framework.internal.core.Framework;
import org.eclipse.osgi.framework.util.Headers;
import org.eclipse.osgi.internal.loader.BundleLoaderProxy;
import org.eclipse.osgi.service.resolver.*;
import org.osgi.framework.*;
import org.osgi.framework.Constants;
import org.osgi.service.composite.CompositeBundle;
import org.osgi.service.composite.CompositeConstants;

public class CompositeImpl extends BundleHost implements CompositeBundle {
	static final CompositeInfo rootInfo = new CompositeInfo(null, null, null, null, null, null);
	final BundleHost compositeSystemBundle;
	final CompositeInfo compositeInfo;
	final StartLevelManager startLevelManager;

	public CompositeImpl(BundleData bundledata, Framework framework) throws BundleException {
		super(bundledata, framework);
		compositeSystemBundle = new CompositeSystemBundle((BundleHost) framework.getBundle(0), framework);
		compositeInfo = createCompositeInfo();
		startLevelManager = new StartLevelManager(framework, bundledata.getBundleID(), compositeSystemBundle);
	}

	CompositeInfo getCompositeInfo() {
		return compositeInfo;
	}

	private CompositeInfo createCompositeInfo() throws BundleException {
		Dictionary manifest = bundledata.getManifest();
		String importPackage = (String) manifest.get(CompositeConstants.COMPOSITE_PACKAGE_IMPORT_POLICY);
		String exportPackage = (String) manifest.get(CompositeConstants.COMPOSITE_PACKAGE_EXPORT_POLICY);
		String requireBundle = (String) manifest.get(CompositeConstants.COMPOSITE_BUNDLE_REQUIRE_POLICY);
		String importService = (String) manifest.get(CompositeConstants.COMPOSITE_SERVICE_IMPORT_POLICY);
		String exportService = (String) manifest.get(CompositeConstants.COMPOSITE_SERVICE_EXPORT_POLICY);

		ImportPackageSpecification[] imports = null;
		ImportPackageSpecification[] exports = null;
		BundleSpecification[] requires = null;
		Filter importServiceFilter = null;
		Filter exportServiceFilter = null;
		BundleContext systemContext = compositeSystemBundle.getBundleContext();
		try {
			importServiceFilter = importService == null ? null : systemContext.createFilter(importService);
			exportServiceFilter = exportService == null ? null : systemContext.createFilter(exportService);
		} catch (InvalidSyntaxException e) {
			throw new BundleException("Invalid service sharing policy.", BundleException.MANIFEST_ERROR, e); //$NON-NLS-1$
		}

		StateObjectFactory factory = StateObjectFactory.defaultFactory;
		Headers builderManifest = new Headers(4);
		builderManifest.put(Constants.BUNDLE_MANIFESTVERSION, "2"); //$NON-NLS-1$
		builderManifest.put(Constants.BUNDLE_SYMBOLICNAME, manifest.get(Constants.BUNDLE_SYMBOLICNAME));
		if (importPackage != null)
			builderManifest.put(Constants.IMPORT_PACKAGE, importPackage);
		if (requireBundle != null)
			builderManifest.put(Constants.REQUIRE_BUNDLE, requireBundle);
		BundleDescription desc = factory.createBundleDescription(null, builderManifest, "", 0); //$NON-NLS-1$
		if (importPackage != null)
			imports = desc.getImportPackages();
		if (exportPackage != null)
			requires = desc.getRequiredBundles();

		if (exportPackage == null)
			builderManifest.put(Constants.IMPORT_PACKAGE, exportPackage);
		desc = factory.createBundleDescription(null, builderManifest, "", 0); //$NON-NLS-1$
		if (exportPackage != null)
			exports = desc.getImportPackages();

		// set the parent info
		CompositeInfo parentInfo;
		long compositeID = bundledata.getCompositeID();
		if (compositeID == 0) // this is the root framework
			parentInfo = rootInfo;
		else
			parentInfo = ((CompositeImpl) framework.getBundle(bundledata.getCompositeID())).getCompositeInfo();
		CompositeInfo result = new CompositeInfo(parentInfo, imports, exports, requires, importServiceFilter, exportServiceFilter);
		// add the the composite info as a child of the parent.
		parentInfo.addChild(result);
		return result;
	}

	public BundleContext getSystemBundleContext() {
		if (getState() == Bundle.UNINSTALLED)
			return null;
		return compositeSystemBundle.getBundleContext();
	}

	public void update() throws BundleException {
		throw new BundleException("Must update a composite with the update(Map) method.", BundleException.UNSUPPORTED_OPERATION);
	}

	public void update(InputStream in) throws BundleException {
		update();
	}

	public void update(Map compositeManifest) throws BundleException {
		// TODO Auto-generated method stub

	}

	protected void startHook() {
		startLevelManager.initialize();
		startLevelManager.doSetStartLevel(1);
	}

	protected void stopHook() {
		startLevelManager.shutdown();
		startLevelManager.cleanup();
	}

	public void uninstallWorkerPrivileged() throws BundleException {
		Bundle[] bundles = compositeSystemBundle.getBundleContext().getBundles();
		// uninstall all the constituents first
		for (int i = 0; i < bundles.length; i++)
			if (bundles[i].getBundleId() != 0) // not the system bundle
				try {
					bundles[i].uninstall();
				} catch (BundleException e) {
					framework.publishFrameworkEvent(FrameworkEvent.ERROR, bundles[i], e);
				}
		// now uninstall the composite
		super.uninstallWorkerPrivileged();

	}

	protected void close() {
		super.close();
		// remove the composite info from the parent
		compositeInfo.getParent().removeChild(compositeInfo);
	}

	public class CompositeSystemBundle extends InternalSystemBundle {
		private final BundleHost rootSystemBundle;

		public CompositeSystemBundle(BundleHost systemBundle, Framework framework) throws BundleException {
			super(systemBundle.getBundleData(), framework);
			this.rootSystemBundle = systemBundle;
			this.state = Bundle.STARTING; // Initial state must be STARTING for composite system bundle
		}

		protected BundleContextImpl createContext() {
			return new CompositeContext(this);
		}

		public ServiceReference[] getRegisteredServices() {
			// TODO this is not scoped; do we care?
			return rootSystemBundle.getRegisteredServices();
		}

		public ServiceReference[] getServicesInUse() {
			return rootSystemBundle.getServicesInUse();
		}

		public void start(int options) throws BundleException {
			throw new BundleException("Cannot start a composite system bundle.", BundleException.UNSUPPORTED_OPERATION); //$NON-NLS-1$
		}

		public void start() throws BundleException {
			start(0);
		}

		public void stop(int options) throws BundleException {
			throw new BundleException("Cannot stop a composite system bundle.", BundleException.UNSUPPORTED_OPERATION); //$NON-NLS-1$
		}

		public void stop() throws BundleException {
			stop(0);
		}

		public void uninstall() throws BundleException {
			throw new BundleException("Cannot uninstall a composite system bundle.", BundleException.UNSUPPORTED_OPERATION); //$NON-NLS-1$
		}

		public void update(InputStream input) throws BundleException {
			throw new BundleException("Cannot update a composite system bundle.", BundleException.UNSUPPORTED_OPERATION); //$NON-NLS-1$
		}

		public void update() throws BundleException {
			update(null);
		}

		public long getCompositeId() {
			return CompositeImpl.this.getBundleId();
		}

		public BundleLoaderProxy getLoaderProxy() {
			return rootSystemBundle.getLoaderProxy();
		}
	}

	public class CompositeContext extends BundleContextImpl {

		protected CompositeContext(BundleHost bundle) {
			super(bundle);
		}

		protected long getCompositeId() {
			return CompositeImpl.this.getBundleId();
		}

		protected void start() {
			// nothing;
		}

		protected void stop() {
			// nothing
		}
	}

	public StartLevelManager getStartLevelService() {
		return startLevelManager;
	}

	public BundleHost getSystemBundle() {
		return compositeSystemBundle;
	}
}
