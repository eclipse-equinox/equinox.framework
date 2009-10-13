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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import org.eclipse.osgi.framework.adaptor.BundleData;
import org.eclipse.osgi.framework.internal.core.*;
import org.eclipse.osgi.framework.internal.core.Framework;
import org.eclipse.osgi.framework.util.Headers;
import org.eclipse.osgi.service.resolver.*;
import org.osgi.framework.*;
import org.osgi.framework.Constants;
import org.osgi.service.composite.CompositeBundle;
import org.osgi.service.composite.CompositeConstants;

public class CompositeImpl extends BundleHost implements CompositeBundle {
	static final CompositeInfo rootInfo = new CompositeInfo(null, null, null, null, null, null);
	final Bundle compositeSystemBundle;
	final CompositeInfo compositeInfo;

	public CompositeImpl(BundleData bundledata, Framework framework) throws BundleException {
		super(bundledata, framework);
		compositeSystemBundle = new CompositeSystemBundle((BundleHost) framework.getBundle(0));
		compositeInfo = createCompositeInfo();
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
		// TODO increment start-level for the composite framework
	}

	protected void stopHook() {
		// TODO decrement start-level for the composite framework
	}

	public void uninstallWorkerPrivileged() throws BundleException {
		// first do the work to uninstall the composite
		super.uninstallWorkerPrivileged();
		// uninstall all the constituents
		Bundle[] bundles = compositeSystemBundle.getBundleContext().getBundles();
		for (int i = 0; i < bundles.length; i++)
			if (bundles[i].getBundleId() != 0) // not the system bundle
				try {
					bundles[i].uninstall();
				} catch (BundleException e) {
					framework.publishFrameworkEvent(FrameworkEvent.ERROR, bundles[i], e);
				}
	}

	protected void close() {
		super.close();
		// remove the composite info from the parent
		compositeInfo.getParent().removeChild(compositeInfo);
	}

	public class CompositeSystemBundle implements Bundle {
		private final BundleHost systemBundle;
		private final BundleContext systemContext;

		public CompositeSystemBundle(BundleHost systemBundle) {
			this.systemBundle = systemBundle;
			this.systemContext = new CompositeContext(systemBundle);
		}

		public Enumeration findEntries(String path, String filePattern, boolean recurse) {
			return systemBundle.findEntries(path, filePattern, recurse);
		}

		public BundleContext getBundleContext() {
			framework.checkAdminPermission(compositeSystemBundle, AdminPermission.CONTEXT);
			return systemContext;
		}

		public long getBundleId() {
			return systemBundle.getBundleId();
		}

		public URL getEntry(String path) {
			return systemBundle.getEntry(path);
		}

		public Enumeration getEntryPaths(String path) {
			return systemBundle.getEntryPaths(path);
		}

		public Dictionary getHeaders() {
			return systemBundle.getHeaders();
		}

		public Dictionary getHeaders(String locale) {
			return systemBundle.getHeaders(locale);
		}

		public long getLastModified() {
			return systemBundle.getLastModified();
		}

		public String getLocation() {
			return systemBundle.getLocation();
		}

		public ServiceReference[] getRegisteredServices() {
			// TODO this is not scoped; do we care?
			return systemBundle.getRegisteredServices();
		}

		public URL getResource(String name) {
			return systemBundle.getResource(name);
		}

		public Enumeration getResources(String name) throws IOException {
			return systemBundle.getResources(name);
		}

		public ServiceReference[] getServicesInUse() {
			return systemBundle.getServicesInUse();
		}

		public Map getSignerCertificates(int signersType) {
			return systemBundle.getSignerCertificates(signersType);
		}

		public int getState() {
			// TODO Need to do the right thing according to Composite state
			return systemBundle.getState();
		}

		public String getSymbolicName() {
			return systemBundle.getSymbolicName();
		}

		public Version getVersion() {
			return systemBundle.getVersion();
		}

		public boolean hasPermission(Object permission) {
			return systemBundle.hasPermission(permission);
		}

		public Class loadClass(String name) throws ClassNotFoundException {
			return systemBundle.loadClass(name);
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

	}

	public class CompositeContext extends BundleContextImpl {

		protected CompositeContext(BundleHost bundle) {
			super(bundle);
		}

		protected long getCompositeId() {
			return getBundleId();
		}
	}
}
