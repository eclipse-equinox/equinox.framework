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
package org.eclipse.osgi.framework.internal.core;

import org.osgi.framework.*;
import org.osgi.service.packageadmin.*;

public class PackageAdminFactory implements ServiceFactory<PackageAdmin> {
	final PackageAdminImpl packageAdminImpl;

	public PackageAdminFactory(Framework framework) {
		packageAdminImpl = new PackageAdminImpl(framework);
	}

	public PackageAdminImpl getPackageAdminImpl() {
		return packageAdminImpl;
	}

	public PackageAdmin getService(Bundle bundle, ServiceRegistration<PackageAdmin> registration) {
		return new CompositePackageAdmin((AbstractBundle) bundle);
	}

	public void ungetService(Bundle bundle, ServiceRegistration<PackageAdmin> registration, PackageAdmin service) {
		// do nothing
	}

	class CompositePackageAdmin implements PackageAdmin {
		private final AbstractBundle client;

		public CompositePackageAdmin(AbstractBundle client) {
			this.client = client;
		}

		public Bundle getBundle(Class clazz) {
			return packageAdminImpl.getBundle(clazz);
		}

		public int getBundleType(Bundle bundle) {
			return packageAdminImpl.getBundleType(bundle);
		}

		public Bundle[] getBundles(String symbolicName, String versionRange) {
			return packageAdminImpl.getBundles(client.getCompositeId(), symbolicName, versionRange);
		}

		public ExportedPackage getExportedPackage(String name) {
			return packageAdminImpl.getExportedPackage(client, name);
		}

		public ExportedPackage[] getExportedPackages(Bundle bundle) {
			return packageAdminImpl.getExportedPackages(client, bundle);
		}

		public ExportedPackage[] getExportedPackages(String name) {
			return packageAdminImpl.getExportedPackages(client, name);
		}

		public Bundle[] getFragments(Bundle bundle) {
			return packageAdminImpl.getFragments(bundle);
		}

		public Bundle[] getHosts(Bundle bundle) {
			return packageAdminImpl.getHosts(bundle);
		}

		public RequiredBundle[] getRequiredBundles(String symbolicName) {
			return packageAdminImpl.getRequiredBundles(client, symbolicName);
		}

		public void refreshPackages(Bundle[] bundles) {
			packageAdminImpl.refreshPackages(bundles);
		}

		public boolean resolveBundles(Bundle[] bundles) {
			return packageAdminImpl.resolveBundles(bundles);
		}
	}
}
