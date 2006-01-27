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

package org.eclipse.osgi.baseadaptor.hooks;

import org.eclipse.osgi.baseadaptor.BaseData;
import org.eclipse.osgi.baseadaptor.HookRegistry;
import org.eclipse.osgi.baseadaptor.loader.BaseClassLoader;
import org.eclipse.osgi.framework.adaptor.*;

/**
 * A DataHook hooks into the <code>BaseData</code> class.
 * @see BaseData
 * @see HookRegistry#getDataHooks()
 * @see HookRegistry#addDataHook(DataHook)
 */
public interface DataHook {
	/**
	 * Gets called by a base data during {@link BundleData#setStatus(int)}.
	 * A base data will call this method for each configured data hook until one
	 * data hook returns true.  If all configured data hooks return false then the
	 * BaseData will be marked dirty and will cause the status to be persistently
	 * saved.
	 * @param data the base data with a status change
	 * @param status the new status of the base data
	 * @return false if the status is not to be persistently saved; otherwise true is returned
	 */
	boolean forgetStatusChange(BaseData data, int status);

	/**
	 * Gets called by a base data during {@link BundleData#setStartLevel(int)}.
	 * A base data will call this method for each configured data hook until one
	 * data hook returns true.  If all configured data hooks return false then the
	 * BaseData will be marked dirty and will cause the startlevel to be persistently
	 * saved.
	 * @param data the base data with a startlevel change
	 * @param startlevel the new startlevel of the base data
	 * @return false if the startlevel is not to be persistently saved; otherwise true is returned
	 */
	boolean forgetStartLevelChange(BaseData data, int startlevel);

	/**
	 * Gets called by a base data during {@link BundleData#matchDNChain(String)}.
	 * The BaseData will call this method for each configured data hook until one 
	 * data hook returns a true value.  If no data hook returns a true value 
	 * then the BaseAdaptor will return false.
	 * @param data the base data to match the pattern against
	 * @param pattern the pattern of distinguished name (DN) chains to match
	 * @return true if the pattern matches. A value of false is returned
	 * if bundle signing is not supported.
	 */
	boolean matchDNChain(BaseData data, String pattern);

	/**
	 * Gets called by a base data during 
	 * {@link BundleData#createClassLoader(ClassLoaderDelegate, BundleProtectionDomain, String[])}.
	 * The BaseData will call this method for each configured data hook until one data
	 * hook returns a non-null value.  If no data hook returns a non-null value then a 
	 * default implemenation of BundleClassLoader will be created.
	 * @param parent the parent classloader for the BundleClassLoader
	 * @param delegate the delegate for the bundle classloader
	 * @param domain the domian for the bundle classloader
	 * @param data the BundleData for the BundleClassLoader
	 * @param bundleclasspath the classpath for the bundle classloader
	 * @return a newly created bundle classloader
	 */
	BaseClassLoader createClassLoader(ClassLoader parent, ClassLoaderDelegate delegate, BundleProtectionDomain domain, BaseData data, String[] bundleclasspath);

	/**
	 * Gets called by a base data during
	 * {@link BundleData#createClassLoader(ClassLoaderDelegate, BundleProtectionDomain, String[])}.
	 * The BaseData will call this method for each configured data hook after a 
	 * BundleClassLoader has been created.
	 * @param baseClassLoader the newly created bundle classloader
	 * @param data the BundleData associated with the bundle classloader
	 */
	void initializedClassLoader(BaseClassLoader baseClassLoader, BaseData data);
}
