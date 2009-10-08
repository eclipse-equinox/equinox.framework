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

import java.util.Dictionary;
import java.util.Map;
import org.eclipse.osgi.framework.adaptor.BundleData;
import org.eclipse.osgi.framework.internal.core.*;
import org.eclipse.osgi.framework.util.Headers;
import org.eclipse.osgi.service.resolver.*;
import org.osgi.framework.*;
import org.osgi.framework.Constants;
import org.osgi.service.composite.CompositeBundle;
import org.osgi.service.composite.CompositeConstants;

public class CompositeImpl extends BundleHost implements CompositeBundle {
	private static final CompositeInfo rootInfo = new CompositeInfo(null, null, null, null, null, null);
	final BundleContext systemContext;
	final CompositeInfo compositeInfo;

	public CompositeImpl(BundleData bundledata, Framework framework) throws BundleException {
		super(bundledata, framework);
		systemContext = new CompositeContext((BundleHost) framework.getBundle(0));
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
		try {
			importServiceFilter = importService == null ? null : systemContext.createFilter(importService);
			exportServiceFilter = exportService == null ? null : systemContext.createFilter(exportService);
		} catch (InvalidSyntaxException e) {
			throw new BundleException("Invalid service sharing policy.", e);
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
		// TODO set parent info

		CompositeInfo parentInfo;
		long compositeID = bundledata.getCompositeID();
		if (compositeID == 0)
			parentInfo = rootInfo;
		else
			parentInfo = ((CompositeImpl) framework.getBundle(bundledata.getCompositeID())).getCompositeInfo();
		CompositeInfo result = new CompositeInfo(parentInfo, imports, exports, requires, importServiceFilter, exportServiceFilter);
		parentInfo.addChild(result);
		return result;
	}

	public BundleContext getSystemBundleContext() {
		return systemContext;
	}

	public void update(Map compositeManifest) throws BundleException {
		// TODO Auto-generated method stub

	}

	public void uninstall() throws BundleException {
		super.uninstall();
		// TODO remove the CompositeInfo as a child from the parent
	}

	public class CompositeContext extends BundleContextImpl {

		protected CompositeContext(BundleHost bundle) {
			super(bundle);
		}

		protected long getCompositeID() {
			return getBundleId();
		}
	}
}
