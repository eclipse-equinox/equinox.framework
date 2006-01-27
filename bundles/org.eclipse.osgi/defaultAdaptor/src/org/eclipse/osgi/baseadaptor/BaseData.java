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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import org.eclipse.osgi.baseadaptor.bundlefile.BundleEntry;
import org.eclipse.osgi.baseadaptor.bundlefile.BundleFile;
import org.eclipse.osgi.baseadaptor.hooks.*;
import org.eclipse.osgi.baseadaptor.loader.BaseClassLoader;
import org.eclipse.osgi.framework.adaptor.*;
import org.eclipse.osgi.framework.debug.Debug;
import org.eclipse.osgi.framework.internal.core.Constants;
import org.eclipse.osgi.framework.internal.protocol.bundleentry.Handler;
import org.eclipse.osgi.framework.util.KeyedHashSet;
import org.eclipse.osgi.internal.baseadaptor.DefaultClassLoader;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.*;

/**
 * The BundleData implementation used by the BaseAdaptor.
 * @see BaseAdaptor
 * @see BundleData
 * @see DataHook
 */
public class BaseData implements BundleData {
	private long id;
	private BaseAdaptor adaptor;
	private Bundle bundle;
	private int startLevel = -1;
	private int status = 0;
	private KeyedHashSet storageHooks = new KeyedHashSet(5, false);
	private String location;
	private long lastModified;
	protected BundleFile bundleFile;
	private boolean dirty = false;
	protected Dictionary manifest;

	///////////////////// Begin values from Manifest     /////////////////////
	private String symbolicName;
	private Version version;
	private String activator;
	private String classpath;
	private String executionEnvironment;
	private String dynamicImports;
	private int type;

	///////////////////// End values from Manifest       /////////////////////

	/**
	 * Constructs a new BaseData with the specified id for the specified adaptor
	 * @param id the id of the BaseData
	 * @param adaptor the adaptor of the BaseData
	 */
	public BaseData(long id, BaseAdaptor adaptor) {
		this.id = id;
		this.adaptor = adaptor;
	}

	/**
	 * This method calls all the configured data hooks {@link DataHook#createClassLoader(ClassLoader, ClassLoaderDelegate, BundleProtectionDomain, BaseData, String[])} 
	 * methods until on returns a non-null value.  If none of the data hooks returns a non-null value 
	 * then the default classloader implementation is used. <p>
	 * After the classloader is created all configured data hooks 
	 * {@link DataHook#initializedClassLoader(BaseClassLoader, BaseData)} methods are called.
	 * @see BundleData#createClassLoader(ClassLoaderDelegate, BundleProtectionDomain, String[])
	 */
	public BundleClassLoader createClassLoader(ClassLoaderDelegate delegate, BundleProtectionDomain domain, String[] bundleclasspath) {
		DataHook[] hooks = adaptor.getHookRegistry().getDataHooks();
		ClassLoader parent = adaptor.getBundleClassLoaderParent();
		BaseClassLoader cl = null;
		for (int i = 0; i < hooks.length && cl == null; i++)
			cl = hooks[i].createClassLoader(parent, delegate, domain, this, bundleclasspath);
		if (cl == null)
			cl = new DefaultClassLoader(parent, delegate, domain, this, bundleclasspath);
		for (int i = 0; i < hooks.length; i++)
			hooks[i].initializedClassLoader(cl, this);
		return cl;
	}

	public final URL getEntry(String path) {
		BundleEntry entry = getBundleFile().getEntry(path);
		if (entry == null)
			return null;
		if (path.length() == 0 || path.charAt(0) != '/')
			path = path = '/' + path;
		try {
			//use the constant string for the protocol to prevent duplication
			return new URL(Constants.OSGI_ENTRY_URL_PROTOCOL, Long.toString(id), 0, path, new Handler(entry));
		} catch (MalformedURLException e) {
			return null;
		}
	}

	public final Enumeration getEntryPaths(String path) {
		return getBundleFile().getEntryPaths(path);
	}

	/**
	 * This method calls each configured classpath manager hook {@link BaseData#findLibrary(String)} method 
	 * until the first one returns a non-null value.
	 * @see BundleData#findLibrary(String)
	 */
	public String findLibrary(String libname) {
		ClasspathManagerHook[] hooks = adaptor.getHookRegistry().getClasspathManagerHooks();
		String result = null;
		for (int i = 0; i < hooks.length; i++) {
			result = hooks[i].findLibrary(this, libname);
			if (result != null)
				return result;
		}
		return result;
	}

	public void installNativeCode(String[] nativepaths) throws BundleException {
		adaptor.getStorage().installNativeCode(this, nativepaths);
	}

	public File getDataFile(String path) {
		return adaptor.getStorage().getDataFile(this, path);
	}

	public Dictionary getManifest() throws BundleException {
		if (manifest == null)
			manifest = adaptor.getStorage().loadManifest(this);
		return manifest;
	}

	public long getBundleID() {
		return id;
	}

	public final String getLocation() {
		return location;
	}

	/**
	 * Sets the location of this bundledata
	 * @param location the location of this bundledata
	 */
	public final void setLocation(String location) {
		this.location = location;
	}

	public final long getLastModified() {
		return lastModified;
	}

	/**
	 * Sets the last modified time stamp of this bundledata
	 * @param lastModified the last modified time stamp of this bundledata
	 */
	public final void setLastModified(long lastModified) {
		this.lastModified = lastModified;
	}

	public void close() throws IOException {
		if (bundleFile != null)
			getBundleFile().close(); // only close the bundleFile if it already exists.
	}

	public void open() throws IOException {
		getBundleFile().open();
	}

	public final void setBundle(Bundle bundle) {
		this.bundle = bundle;
	}

	/**
	 * Returns the bundle object of this BaseData
	 * @return the bundle object of this BaseData
	 */
	public final Bundle getBundle() {
		return bundle;
	}

	public int getStartLevel() {
		return startLevel;
	}

	public int getStatus() {
		return status;
	}

	/**
	 * This method calls each configured data hook {@link DataHook#forgetStartLevelChange(BaseData, int)} method.
	 * If one returns true then this bundledata is not marked dirty.
	 * @see BundleData#setStartLevel(int)
	 */
	public void setStartLevel(int value) {
		startLevel = setPersistentData(value, true, startLevel);
	}

	/**
	 * This method calls each configured data hook {@link DataHook#forgetStatusChange(BaseData, int)} method.
	 * If one returns true then this bundledata is not marked dirty.
	 * @see BundleData#setStartLevel(int)
	 */
	public void setStatus(int value) {
		status = setPersistentData(value, false, status);
	}

	private int setPersistentData(int value, boolean isStartLevel, int orig) {
		DataHook[] hooks = adaptor.getHookRegistry().getDataHooks();
		for (int i = 0; i < hooks.length; i++)
			if (isStartLevel) {
				if (hooks[i].forgetStartLevelChange(this, value))
					return value;
			} else {
				if (hooks[i].forgetStatusChange(this, value))
					return value;
			}
		if (value != orig)
			dirty = true;
		return value;
	}

	public void save() throws IOException {
		adaptor.getStorage().save(this);
	}

	/**
	 * Returns true if this bundledata is dirty
	 * @return true if this bundledata is dirty
	 */
	public boolean isDirty() {
		return dirty;
	}

	/**
	 * Sets the dirty flag for this BaseData
	 * @param dirty the dirty flag
	 */
	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

	public final String getSymbolicName() {
		return symbolicName;
	}

	/**
	 * Sets the symbolic name of this BaseData
	 * @param symbolicName the symbolic name
	 */
	public final void setSymbolicName(String symbolicName) {
		this.symbolicName = symbolicName;
	}

	public final Version getVersion() {
		return version;
	}

	/**
	 * Sets the version of this BaseData
	 * @param version the version
	 */
	public final void setVersion(Version version) {
		this.version = version;
	}

	public final int getType() {
		return type;
	}

	/**
	 * Sets the type of this BaseData
	 * @param type the type
	 */
	public final void setType(int type) {
		this.type = type;
	}

	public final String[] getClassPath() throws BundleException {
		ManifestElement[] classpathElements = ManifestElement.parseHeader(Constants.BUNDLE_CLASSPATH, classpath);
		return getClassPath(classpathElements);
	}

	// TODO make classpath a String[] instead of saving a comma separated string.
	public String getClassPathString() {
		return classpath;
	}

	//TODO make classpath a String[] instead of saving a comma separated string.
	public void setClassPathString(String classpath) {
		this.classpath = classpath;
	}

	public final String getActivator() {
		return activator;
	}

	/**
	 * Sets the activator of this BaseData
	 * @param activator the activator
	 */
	public final void setActivator(String activator) {
		this.activator = activator;
	}

	public final String getExecutionEnvironment() {
		return executionEnvironment;
	}

	/**
	 * Sets the execution environment of this BaseData
	 * @param executionEnvironment the execution environment
	 */
	public void setExecutionEnvironment(String executionEnvironment) {
		this.executionEnvironment = executionEnvironment;
	}

	public final String getDynamicImports() {
		return dynamicImports;
	}

	/**
	 * Sets the dynamic imports of this BaseData
	 * @param dynamicImports the dynamic imports
	 */
	public void setDynamicImports(String dynamicImports) {
		this.dynamicImports = dynamicImports;
	}

	public final boolean matchDNChain(String pattern) {
		DataHook[] hooks = adaptor.getHookRegistry().getDataHooks();
		for (int i = 0; i < hooks.length; i++)
			if (hooks[i].matchDNChain(this, pattern))
				return true;
		return false;
	}

	/**
	 * Returns the adaptor for this BaseData
	 * @return the adaptor
	 */
	public final BaseAdaptor getAdaptor() {
		return adaptor;
	}

	/**
	 * Returns the BundleFile for this BaseData.  The first time this method is called the
	 * configured storage {@link BaseAdaptor#createBundleFile(Object, BaseData)} method is called.
	 * @return the BundleFile
	 * @throws IllegalArgumentException
	 */
	public synchronized BundleFile getBundleFile() throws IllegalArgumentException {
		if (bundleFile == null)
			try {
				bundleFile = adaptor.createBundleFile(null, this);
			} catch (IOException e) {
				throw new IllegalArgumentException(e.getMessage());
			}
		return bundleFile;
	}

	private static String[] getClassPath(ManifestElement[] classpath) {
		if (classpath == null) {
			if (Debug.DEBUG && Debug.DEBUG_LOADER)
				Debug.println("  no classpath"); //$NON-NLS-1$
			/* create default BundleClassPath */
			return new String[] {"."}; //$NON-NLS-1$
		}

		ArrayList result = new ArrayList(classpath.length);
		for (int i = 0; i < classpath.length; i++) {
			if (Debug.DEBUG && Debug.DEBUG_LOADER)
				Debug.println("  found classpath entry " + classpath[i].getValueComponents()); //$NON-NLS-1$
			String[] paths = classpath[i].getValueComponents();
			for (int j = 0; j < paths.length; j++) {
				result.add(paths[j]);
			}
		}

		return (String[]) result.toArray(new String[result.size()]);
	}

	/**
	 * Returns the storage hook which is keyed by the specified key
	 * @param key the key of the storage hook to get
	 * @return the storage hook which is keyed by the specified key
	 */
	public StorageHook getStorageHook(String key) {
		synchronized (storageHooks) {
			return (StorageHook) storageHooks.getByKey(key);
		}
	}

	/**
	 * Adds a storage hook
	 * @param userObject the storage hook to add
	 */
	public void addStorageHook(StorageHook storageHook) {
		synchronized (storageHooks) {
			storageHooks.add(storageHook);
		}
	}

	/**
	 * Returns all the storage hooks associated with this BaseData
	 * @return all the storage hooks associated with this BaseData
	 */
	public StorageHook[] getStorageHooks() {
		synchronized (storageHooks) {
			return (StorageHook[]) storageHooks.elements(new StorageHook[storageHooks.size()]);
		}
	}

	/**
	 * Gets called by BundleFile during {@link BundleFile#getFile(String)}.  This method 
	 * will allocate a File object where content of the specified path may be 
	 * stored for the current generation of the base data.  The returned File object may 
	 * not exist if the content has not previously be stored.
	 * @param data the base data object to store content for
	 * @param path the path to the content to extract from the base data
	 * @return a file object where content of the specified path may be stored.
	 */
	public File getExtractFile(String path) {
		return adaptor.getStorage().getExtractFile(this, path);
	}

}
