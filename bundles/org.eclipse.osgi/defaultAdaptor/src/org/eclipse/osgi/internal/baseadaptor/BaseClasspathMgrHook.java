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

package org.eclipse.osgi.internal.baseadaptor;

import java.io.File;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import org.eclipse.osgi.baseadaptor.BaseData;
import org.eclipse.osgi.baseadaptor.bundlefile.BundleEntry;
import org.eclipse.osgi.baseadaptor.hooks.ClasspathManagerHook;
import org.eclipse.osgi.baseadaptor.loader.*;
import org.eclipse.osgi.framework.debug.Debug;

public class BaseClasspathMgrHook implements ClasspathManagerHook {

	public String findLibrary(BaseData data, String libName) {
		String mappedName = System.mapLibraryName(libName);
		String path = null;
		if (Debug.DEBUG && Debug.DEBUG_LOADER)
			Debug.println("  mapped library name: " + mappedName); //$NON-NLS-1$
		path = findNativePath(data, mappedName);
		if (path == null) {
			if (Debug.DEBUG && Debug.DEBUG_LOADER)
				Debug.println("  library does not exist: " + mappedName); //$NON-NLS-1$
			path = findNativePath(data, libName);
		}
		if (Debug.DEBUG && Debug.DEBUG_LOADER)
			Debug.println("  returning library: " + path); //$NON-NLS-1$
		return path;
	}

	private String findNativePath(BaseData bundledata, String libname) {
		int slash = libname.lastIndexOf('/');
		if (slash >= 0)
			libname = libname.substring(slash + 1);
		String[] nativepaths = getNativePaths(bundledata);
		if (nativepaths == null)
			return null;
		for (int i = 0; i < nativepaths.length; i++) {
			slash = nativepaths[i].lastIndexOf('/');
			String path = slash < 0 ? nativepaths[i] : nativepaths[i].substring(slash + 1);
			if (path.equals(libname)) {
				File nativeFile = bundledata.getBundleFile().getFile(nativepaths[i], true);
				if (nativeFile != null)
					return nativeFile.getAbsolutePath();
			}
		}
		return null;
	}

	private String[] getNativePaths(BaseData bundledata) {
		BaseStorageHook storageHook = (BaseStorageHook) bundledata.getStorageHook(BaseStorageHook.KEY);
		return storageHook != null ? storageHook.getNativePaths() : null;
	}

	public boolean addClassPathEntry(ArrayList cpEntries, String cp, ClasspathManager hostmanager, BaseData sourcedata, ProtectionDomain sourcedomain) {
		// do nothing
		return false;
	}

	public ClassLoader getBundleClassLoaderParent() {
		// do nothing
		return null;
	}

	public void postFindLocalClass(String name, Class clazz, ClasspathManager manager) {
		// do nothing

	}

	public void postFindLocalResource(String name, URL resource, ClasspathManager manager) {
		// do nothing

	}

	public void preFindLocalClass(String name, ClasspathManager manager) throws ClassNotFoundException {
		// do nothing

	}

	public void preFindLocalResource(String name, ClasspathManager manager) {
		// do nothing

	}

	public byte[] processClass(String name, byte[] classbytes, ClasspathEntry classpathEntry, BundleEntry entry, ClasspathManager manager) {
		// do nothing
		return null;
	}

	public void recordClassDefine(String name, Class clazz, byte[] classbytes, ClasspathEntry classpathEntry, BundleEntry entry, ClasspathManager manager) {
		// do nothing
	}
}
