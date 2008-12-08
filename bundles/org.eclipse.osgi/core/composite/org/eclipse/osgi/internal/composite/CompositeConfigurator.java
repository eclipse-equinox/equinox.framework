/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.internal.composite;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.security.AllPermission;
import java.security.ProtectionDomain;
import java.util.*;
import org.eclipse.osgi.baseadaptor.*;
import org.eclipse.osgi.baseadaptor.bundlefile.BundleEntry;
import org.eclipse.osgi.baseadaptor.hooks.AdaptorHook;
import org.eclipse.osgi.baseadaptor.hooks.ClassLoadingHook;
import org.eclipse.osgi.baseadaptor.loader.*;
import org.eclipse.osgi.framework.adaptor.*;
import org.eclipse.osgi.framework.log.FrameworkLog;
import org.eclipse.osgi.internal.module.*;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.osgi.framework.*;
import org.osgi.framework.launch.Framework;
import org.osgi.service.framework.CompositeBundle;
import org.osgi.service.framework.CompositeBundleFactory;

public class CompositeConfigurator implements HookConfigurator, AdaptorHook, ClassLoadingHook, CompositeBundleFactory, CompositeResolveHelperRegistry {

	private BaseAdaptor adaptor;
	private ServiceRegistration factoryService;
	private BundleContext systemContext;
	private volatile int compositeID = 1;

	public void addHooks(HookRegistry hookRegistry) {
		hookRegistry.addAdaptorHook(this);
		hookRegistry.addClassLoadingHook(this);
	}

	public void addProperties(Properties properties) {
		// nothing
	}

	public FrameworkLog createFrameworkLog() {
		// nothing
		return null;
	}

	/**
	 * @throws BundleException  
	 */
	public void frameworkStart(BundleContext context) throws BundleException {
		this.systemContext = context;
		((ResolverImpl) adaptor.getState().getResolver()).setCompositeResolveHelperRegistry(this);
		factoryService = context.registerService(new String[] {CompositeBundleFactory.class.getName()}, this, null);
	}

	public void frameworkStop(BundleContext context) {
		if (factoryService != null)
			factoryService.unregister();
		factoryService = null;
		stopFrameworks();
	}

	public void frameworkStopping(BundleContext context) {
		// nothing
	}

	public void handleRuntimeError(Throwable error) {
		// nothing
	}

	public void initialize(BaseAdaptor initAdaptor) {
		this.adaptor = initAdaptor;
	}

	public URLConnection mapLocationToURLConnection(String location) {
		// nothing
		return null;
	}

	public boolean matchDNChain(String pattern, String[] dnChain) {
		// nothing
		return false;
	}

	public CompositeBundle newChildCompositeBundle(Map frameworkConfig, String location, Map compositeManifest) throws BundleException {
		SecurityManager sm = System.getSecurityManager();
		if (sm != null)
			sm.checkPermission(new AllPermission());
		CompositeHelper.validateCompositeManifest(compositeManifest);
		compositeManifest = new HashMap(compositeManifest);
		URL content = getBundleInput(frameworkConfig, compositeManifest);
		try {
			CompositeBundle result = (CompositeBundle) systemContext.installBundle(location, content.openStream());
			CompositeHelper.setCompositePermissions(result, systemContext);
			return result;
		} catch (IOException e) {
			throw new BundleException("Error creating composite bundle", e);
		}
	}

	private URL getBundleInput(Map frameworkConfig, Map compositeManifest) throws BundleException {
		// using directory bundles for now, could use in memory zip streams but this is way more simple
		String bsn = (String) compositeManifest.get(Constants.BUNDLE_SYMBOLICNAME);
		// get a unique directory for the bundle content
		File bundleFile = systemContext.getDataFile("composites/" + bsn + '_' + compositeID++); //$NON-NLS-1$
		while (bundleFile.exists())
			bundleFile = systemContext.getDataFile("composites/" + bsn + '_' + compositeID++); //$NON-NLS-1$
		bundleFile.mkdirs();
		// the composite bundles only consist of a manifest describing the packages they import and export
		String manifest = CompositeHelper.getChildCompositeManifest(compositeManifest);
		try {
			CompositeHelper.writeManifest(bundleFile, manifest);
			// store the framework config
			Properties fwProps = new Properties();
			if (frameworkConfig != null)
				fwProps.putAll(frameworkConfig);
			fwProps.store(new FileOutputStream(new File(bundleFile, EquinoxComposite.CHILD_CONFIGURATION)), null);
			// return the reference location
			return new URL("reference:" + bundleFile.toURL().toExternalForm()); //$NON-NLS-1$
		} catch (IOException e) {
			throw new BundleException("Error creating composite bundle", e);
		}
	}

	private void stopFrameworks() {
		Bundle[] allBundles = systemContext.getBundles();
		// stop each child framework
		for (int i = 0; i < allBundles.length; i++) {
			if (!(allBundles[i] instanceof CompositeBundle))
				continue;
			CompositeBundle composite = (CompositeBundle) allBundles[i];
			if (composite.getCompositeType() == CompositeBundle.TYPE_PARENT)
				continue;
			try {
				Framework child = composite.getCompanionFramework();
				child.stop();
				// need to wait for each child to stop
				child.waitForStop(30000);
				// TODO need to figure out a way to invalid the child
			} catch (Throwable t) {
				// TODO consider logging
				t.printStackTrace();
			}
		}
	}

	public CompositeResolveHelper getCompositeResolveHelper(BundleDescription bundle) {
		Bundle composite = systemContext.getBundle(bundle.getBundleId());
		return (CompositeResolveHelper) ((!(composite instanceof CompositeBundle)) ? null : composite);
	}

	public boolean addClassPathEntry(ArrayList cpEntries, String cp, ClasspathManager hostmanager, BaseData sourcedata, ProtectionDomain sourcedomain) {
		// nothing
		return false;
	}

	public BaseClassLoader createClassLoader(ClassLoader parent, ClassLoaderDelegate delegate, BundleProtectionDomain domain, BaseData data, String[] bundleclasspath) {
		if ((data.getType() & (BundleData.TYPE_COMPOSITEBUNDLE_CHILD | BundleData.TYPE_COMPOSITEBUNDLE_PARENT)) == 0)
			return null;
		return new CompositeClassLoader(parent, delegate, data);
	}

	public String findLibrary(BaseData data, String libName) {
		// nothing
		return null;
	}

	public ClassLoader getBundleClassLoaderParent() {
		// nothing
		return null;
	}

	public void initializedClassLoader(BaseClassLoader baseClassLoader, BaseData data) {
		// nothing
	}

	public byte[] processClass(String name, byte[] classbytes, ClasspathEntry classpathEntry, BundleEntry entry, ClasspathManager manager) {
		// nothing
		return null;
	}
}
