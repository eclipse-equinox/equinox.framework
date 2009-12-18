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

import java.io.IOException;
import org.eclipse.osgi.internal.composite.CompositeImpl;
import org.eclipse.osgi.internal.permadmin.SecurityAdmin;
import org.osgi.framework.*;
import org.osgi.service.packageadmin.*;
import org.osgi.service.permissionadmin.PermissionAdmin;
import org.osgi.service.startlevel.StartLevel;

public class CoreServicesFactory implements ServiceFactory {
	private final Framework framework;
	private final StartLevelManager rootStartLevel;
	private final SecurityAdmin rootSecurityAdmin;
	private final PackageAdminImpl packageAdminImpl;

	public CoreServicesFactory(Framework framework) throws IOException {
		this.rootStartLevel = new StartLevelManager(framework, 0, framework.systemBundle);
		this.rootSecurityAdmin = new SecurityAdmin(framework, framework.getAdaptor().getPermissionStorage(), 0);
		this.packageAdminImpl = new PackageAdminImpl(framework);
		this.framework = framework;
	}

	public StartLevelManager getStartLevelManager(AbstractBundle bundle) {
		return (StartLevelManager) getCoreService(bundle, rootStartLevel);
	}

	private Object getCoreService(AbstractBundle bundle, Object rootService) {
		long compositeID = bundle.getCompositeId();
		if (compositeID == 0)
			return rootService;
		CompositeImpl composite = (CompositeImpl) framework.getBundle(compositeID);
		if (rootService == rootStartLevel)
			return composite.getStartLevelService();
		if (rootService == rootSecurityAdmin)
			return composite.getSecurityAdmin();
		return null;
	}

	public PackageAdminImpl getPackageAdminImpl() {
		return packageAdminImpl;
	}

	public PackageAdmin getPackageAdmin(AbstractBundle bundle) {
		return new CompositePackageAdmin(bundle);
	}

	public SecurityAdmin getSecurityAdmin(AbstractBundle bundle) {
		return (SecurityAdmin) getCoreService(bundle, rootSecurityAdmin);
	}

	public Object getService(Bundle bundle, ServiceRegistration registration) {
		String[] clazz = (String[]) registration.getReference().getProperty(Constants.OBJECTCLASS);
		if (clazz[0] == StartLevel.class.getName())
			return getStartLevelManager((AbstractBundle) bundle);
		if (clazz[0] == PackageAdmin.class.getName())
			return getPackageAdmin((AbstractBundle) bundle);
		if (clazz[0] == PermissionAdmin.class.getName())
			return getSecurityAdmin((AbstractBundle) bundle);
		return null;
	}

	public void ungetService(Bundle bundle, ServiceRegistration registration, Object service) {
		// Nothing to do
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
