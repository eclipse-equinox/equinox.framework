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

import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import org.eclipse.osgi.baseadaptor.BaseData;
import org.eclipse.osgi.baseadaptor.HookRegistry;
import org.eclipse.osgi.baseadaptor.bundlefile.BundleEntry;
import org.eclipse.osgi.baseadaptor.loader.ClasspathEntry;
import org.eclipse.osgi.baseadaptor.loader.ClasspathManager;

/**
 * A ClasspathManagerHook hooks into the <code>ClasspathManager</code> class.
 * @see ClasspathManager
 * @see HookRegistry#getClasspathManagerHooks()
 * @see HookRegistry#addClasspathManagerHook(ClasspathManagerHook)
 */
public interface ClasspathManagerHook {
	/**
	 * Gets called by a classpath manager during {@link ClasspathManager#findLocalClass(String)} before 
	 * searching the local classloader for a class.  A classpath manager will call this method for 
	 * each configured classpath manager hook.
	 * @param name the name of the requested class
	 * @param manager the classpath manager used to find and load the requested class
	 * @throws ClassNotFoundException to prevent the requested class from loading
	 */
	void preFindLocalClass(String name, ClasspathManager manager) throws ClassNotFoundException;

	/**
	 * Gets called by a classpath manager during {@link ClasspathManager#findLocalClass(String)} after
	 * searching the local classloader for a class. A classpath manager will call this method for 
	 * each configured classpath manager hook.
	 * @param name the name of the requested class
	 * @param clazz the loaded class or null if not found
	 * @param manager the classpath manager used to find and load the requested class
	 */
	void postFindLocalClass(String name, Class clazz, ClasspathManager manager);

	/**
	 * Gets called by a classpath manager during {@link ClasspathManager#findLocalResource(String)} before
	 * searching the local classloader for a resource. A classpath manager will call this method for 
	 * each configured classpath manager hook.
	 * @param name the name of the requested resource
	 * @param manager the classpath manager used to find the requested resource
	 */
	void preFindLocalResource(String name, ClasspathManager manager);

	/**
	 * Gets called by a classpath manager during {@link ClasspathManager#findLocalResource(String)} after
	 * searching the local classloader for a resource. A classpath manager will call this method for 
	 * each configured classpath manager hook.
	 * @param name the name of the requested resource
	 * @param resource the URL to the requested resource or null if not found
	 * @param manager the classpath manager used to find the requested resource
	 */
	void postFindLocalResource(String name, URL resource, ClasspathManager manager);

	/**
	 * Gets called by a classpath manager before defining a class.  This method allows a classpath manager hook 
	 * to process the bytes of a class that is about to be defined.
	 * @param name the name of the class being defined
	 * @param classbytes the bytes of the class being defined
	 * @param classpathEntry the ClasspathEntry where the class bytes have been read from.
	 * @param entry the BundleEntry source of the class bytes
	 * @param manager the classpath manager used to define the requested class
	 * @return a modified array of classbytes or null if the original bytes should be used.
	 */
	byte[] processClass(String name, byte[] classbytes, ClasspathEntry classpathEntry, BundleEntry entry, ClasspathManager manager);

	/**
	 * Gets called by a classpath manager after a successfully defining a class.  This method allows 
	 * a classpath manager hook to record data about a class definition. 
	 * @param name the name of the class that got defined
	 * @param clazz the class object that got defined
	 * @param classbytes the class bytes used to define the class
	 * @param classpathEntry the ClasspathEntry where the class bytes got read from
	 * @param entry the BundleEntyr source of the class bytes
	 * @param manager the classpath manager used to define the class
	 */
	void recordClassDefine(String name, Class clazz, byte[] classbytes, ClasspathEntry classpathEntry, BundleEntry entry, ClasspathManager manager);

	/**
	 * Gets called by a classpath manager when looking for ClasspathEntry objects.  This method allows 
	 * a classpath manager hook to add additional ClasspathEntry objects
	 * @param cpEntries the list of ClasspathEntry objects currently available for the requested classpath
	 * @param cp the name of the requested classpath
	 * @param hostmanager the classpath manager the requested ClasspathEntry is for
	 * @param sourcedata the source bundle data of the requested ClasspathEntry
	 * @param sourcedomain the source domain of the requested ClasspathEntry
	 * @return true if a ClasspathEntry has been added to cpEntries
	 */
	boolean addClassPathEntry(ArrayList cpEntries, String cp, ClasspathManager hostmanager, BaseData sourcedata, ProtectionDomain sourcedomain);

	/**
	 * Gets called by a base data during {@link BundleData#findLibrary(String)}.
	 * A base data will call this method for each configured classpath manager hook until one 
	 * classpath manager hook returns a non-null value.  If no classpath manager hook returns 
	 * a non-null value then the base data will return null.
	 * @param data the base data to find a native library for.
	 * @param libName the name of the native library.
	 * @return The absolute path name of the native library or null.
	 */
	String findLibrary(BaseData data, String libName);

	/**
	 * Gets called by the adaptor during {@link FrameworkAdaptor#getBundleClassLoaderParent()}.
	 * The adaptor will call this method for each configured classpath manager hook until one 
	 * classpath manager hook returns a non-null value.  If no classpath manager hook returns 
	 * a non-null value then the adaptor will perform the default behavior.
	 * @return the parent classloader to be used by all bundle classloaders or null.
	 */
	public ClassLoader getBundleClassLoaderParent();
}
