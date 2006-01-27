/*******************************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.osgi.baseadaptor;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import org.eclipse.osgi.baseadaptor.hooks.*;
import org.eclipse.osgi.framework.adaptor.BundleWatcher;
import org.eclipse.osgi.framework.adaptor.FrameworkAdaptor;
import org.eclipse.osgi.framework.internal.core.FrameworkProperties;
import org.eclipse.osgi.framework.log.FrameworkLogEntry;
import org.eclipse.osgi.util.ManifestElement;

/**
 * The hook registry is used to store all the hooks which are
 * configured by the hook configurators.
 * @see HookConfigurator
 */
public final class HookRegistry {
	/**
	 * The hook configurators properties file (&quot;hookconfigurators.properties&quot;) <p>
	 * A framework extension may supply a hook configurators properties file to specify a 
	 * list of hook configurators.
	 * @see #HOOK_CONFIGURATORS
	 */
	public static final String HOOK_CONFIGURATORS_FILE = "hookconfigurators.properties"; //$NON-NLS-1$

	/**
	 * The hook configurators property key (&quot;hookconfigurators.properties&quot;) used in 
	 * a hook configurators properties file to specify a comma separated list of fully 
	 * qualified hook configurator classes.
	 */
	public static final String HOOK_CONFIGURATORS = "hook.configurators"; //$NON-NLS-1$

	/**
	 * A system property (&quot;osgi.hook.configurators.include&quot;) used to add additional
	 * hook configurators.  This is helpful for configuring optional hook configurators.
	 */
	public static final String PROP_HOOK_CONFIGURATORS_INCLUDE = "osgi.hook.configurators.include"; //$NON-NLS-1$

	/**
	 * A system property (&quot;osgi.hook.configurators.exclude&quot;) used to exclude 
	 * any hook configurators.  This is helpful for disabling hook
	 * configurators that is specified in hook configurator properties files.
	 */
	public static final String PROP_HOOK_CONFIGURATORS_EXCLUDE = "osgi.hook.configurators.exclude"; //$NON-NLS-1$

	private BaseAdaptor adaptor;
	private boolean readonly = false;
	private AdaptorHook[] adaptorHooks = new AdaptorHook[0];
	private BundleWatcher[] watchers = new BundleWatcher[0];
	private ClassLoadingHook[] classLoadingHooks = new ClassLoadingHook[0];
	private ClassLoadingStatsHook[] classLoadingStatsHooks = new ClassLoadingStatsHook[0];
	private StorageHook[] storageHooks = new StorageHook[0];
	private DataHook[] dataHooks = new DataHook[0];
	private BundleFileFactory[] bundleFileFactories = new BundleFileFactory[0];

	public HookRegistry(BaseAdaptor adaptor) {
		this.adaptor = adaptor;
	}

	/**
	 * Initializes the hook configurators.  The following steps are used to initialize the hook configurators. <p>
	 * 1. Get a list of hook configurators from all hook configurators properties files on the classpath, 
	 *    add this list to the overall list of hook configurators, remove duplicates. <p>
	 * 2. Get a list of hook configurators from the (&quot;osgi.hook.configurators.include&quot;) system property 
	 *    and add this list to the overall list of hook configurators, remove duplicates. <p>
	 * 3. Get a list of hook configurators from the (&quot;osgi.hook.configurators.exclude&quot;) system property
	 *    and remove this list from the overall list of hook configurators. <p>
	 * 4. Load each hook configurator class, create a new instance, then call the {@link HookConfigurator#addHooks(HookRegistry)} method <p>
	 * 5. Set this HookRegistry object to read only to prevent any other hooks from being added. <p>
	 */
	public void initialize() {
		ArrayList configurators = new ArrayList(5);
		mergeFileHookConfigurators(configurators);
		mergePropertyHookConfigurators(configurators);
		loadConfigurators(configurators);
		// set to read-only
		readonly = true;
	}

	private void mergeFileHookConfigurators(ArrayList configuratorList) {
		ClassLoader cl = getClass().getClassLoader();
		// get all hook configurators files in your classloader delegation
		Enumeration hookConfigurators;
		try {
			hookConfigurators = cl != null ? cl.getResources(HookRegistry.HOOK_CONFIGURATORS_FILE) : ClassLoader.getSystemResources(HookRegistry.HOOK_CONFIGURATORS_FILE);
		} catch (IOException e1) {
			// TODO should log this!!
			return;
		}
		while (hookConfigurators.hasMoreElements()) {
			URL url = (URL) hookConfigurators.nextElement();
			try {
				// check each file for a hook.configurators property
				Properties configuratorProps = new Properties();
				configuratorProps.load(url.openStream());
				String hooksValue = configuratorProps.getProperty(HOOK_CONFIGURATORS);
				if (hooksValue == null)
					continue;
				String[] configurators = ManifestElement.getArrayFromList(hooksValue, ","); //$NON-NLS-1$
				for (int i = 0; i < configurators.length; i++)
					if (!configuratorList.contains(configurators[i]))
						configuratorList.add(configurators[i]);
			} catch (IOException e) {
				// ignore and continue to next URL
			}
		}
	}

	private void mergePropertyHookConfigurators(ArrayList configuratorList) {
		// Make sure the configurators from the include property are in the list
		String[] includeConfigurators = ManifestElement.getArrayFromList(FrameworkProperties.getProperty(HookRegistry.PROP_HOOK_CONFIGURATORS_INCLUDE), ","); //$NON-NLS-1$
		for (int i = 0; i < includeConfigurators.length; i++)
			if (!configuratorList.contains(includeConfigurators[i]))
				configuratorList.add(includeConfigurators[i]);
		// Make sure the configurators from the exclude property are no in the list
		String[] excludeHooks = ManifestElement.getArrayFromList(FrameworkProperties.getProperty(HookRegistry.PROP_HOOK_CONFIGURATORS_EXCLUDE), ","); //$NON-NLS-1$
		for (int i = 0; i < excludeHooks.length; i++)
			configuratorList.remove(excludeHooks[i]);
	}

	private void loadConfigurators(ArrayList configurators) {
		for (Iterator iHooks = configurators.iterator(); iHooks.hasNext();) {
			String hookName = (String) iHooks.next();
			try {
				Class clazz = Class.forName(hookName);
				HookConfigurator configurator = (HookConfigurator) clazz.newInstance();
				configurator.addHooks(this);
			} catch (Throwable t) {
				// We expect the follow exeptions may happen; but we need to catch all here
				// ClassNotFoundException
				// IllegalAccessException
				// InstantiationException
				// ClassCastException
				adaptor.getFrameworkLog().log(new FrameworkLogEntry(FrameworkAdaptor.FRAMEWORK_SYMBOLICNAME, FrameworkLogEntry.ERROR, 0, t.getMessage(), 0, t, null));
			}
		}
	}

	/**
	 * Returns the list of configured adaptor hooks.
	 * @return the list of configured adaptor hooks.
	 */
	public AdaptorHook[] getAdaptorHooks() {
		return adaptorHooks;
	}

	/**
	 * Returns the list of configured bundle watchers.
	 * @return the list of configured bundle watchers.
	 */
	public BundleWatcher[] getWatchers() {
		return watchers;
	}

	/**
	 * Returns the list of configured class loading hooks.
	 * @return the list of configured class loading hooks.
	 */
	public ClassLoadingHook[] getClassLoadingHooks() {
		return classLoadingHooks;
	}

	/**
	 * Returns the list of configured class loading stats hooks.
	 * @return the list of configured class loading stats hooks.
	 */
	public ClassLoadingStatsHook[] getClassLoadingStatsHooks() {
		return classLoadingStatsHooks;
	}

	/**
	 * Returns the list of configured storage hooks.
	 * @return the list of configured storage hooks.
	 */
	public StorageHook[] getStorageHooks() {
		return storageHooks;
	}

	/**
	 * Returns the list of configured data hooks.
	 * @return the list of configured data hooks.
	 */
	public DataHook[] getDataHooks() {
		return dataHooks;
	}

	/**
	 * Returns the list of configured bundle file factories.
	 * @return the list of configured bundle file factories.
	 */
	public BundleFileFactory[] getBundleFileFactories() {
		return bundleFileFactories;
	}

	/**
	 * Adds a adaptor hook to this hook registry.
	 * @param adaptorHook an adaptor hook object.
	 */
	public void addAdaptorHook(AdaptorHook adaptorHook) {
		adaptorHooks = (AdaptorHook[]) add(adaptorHook, adaptorHooks, new AdaptorHook[adaptorHooks.length + 1]);
	}

	/**
	 * Adds a bundle watcher to this hook registry.
	 * @param watcher a bundle watcher object.
	 */
	public void addWatcher(BundleWatcher watcher) {
		watchers = (BundleWatcher[]) add(watcher, watchers, new BundleWatcher[watchers.length + 1]);
	}

	/**
	 * Adds a class loading hook to this hook registry.
	 * @param classLoadingHook a class loading hook object.
	 */
	public void addClassLoadingHook(ClassLoadingHook classLoadingHook) {
		classLoadingHooks = (ClassLoadingHook[]) add(classLoadingHook, classLoadingHooks, new ClassLoadingHook[classLoadingHooks.length + 1]);
	}

	/**
	 * Adds a class loading stats hook to this hook registry.
	 * @param classLoadingStatsHook a class loading hook object.
	 */
	public void addClassLoadingStatsHook(ClassLoadingStatsHook classLoadingStatsHook) {
		classLoadingStatsHooks = (ClassLoadingStatsHook[]) add(classLoadingStatsHook, classLoadingStatsHooks, new ClassLoadingStatsHook[classLoadingStatsHooks.length + 1]);
	}

	/**
	 * Adds a storage hook to this hook registry.
	 * @param storageHook a storage hook object.
	 */
	public void addStorageHook(StorageHook storageHook) {
		storageHooks = (StorageHook[]) add(storageHook, storageHooks, new StorageHook[storageHooks.length + 1]);
	}

	/**
	 * Adds a data hook to this hook registry.
	 * @param dataHook a data hook object.
	 */
	public void addDataHook(DataHook dataHook) {
		dataHooks = (DataHook[]) add(dataHook, dataHooks, new DataHook[dataHooks.length + 1]);
	}

	/**
	 * Adds a bundle file factory to this hook registry.
	 * @param factory an bundle file factory object.
	 */
	public void addBundleFileFactory(BundleFileFactory factory) {
		bundleFileFactories = (BundleFileFactory[]) add(factory, bundleFileFactories, new BundleFileFactory[bundleFileFactories.length + 1]);
	}

	private Object[] add(Object newValue, Object[] oldValues, Object[] newValues) {
		if (readonly)
			throw new IllegalStateException("Cannot add hooks dynamically.");
		if (oldValues.length > 0)
			System.arraycopy(oldValues, 0, newValues, 0, oldValues.length);
		newValues[oldValues.length] = newValue;
		return newValues;
	}

	/**
	 * Returns the base adaptor associated with this hook registry.
	 * @return the base adaptor associated with this hook registry.
	 */
	public BaseAdaptor getAdaptor() {
		return adaptor;
	}
}
