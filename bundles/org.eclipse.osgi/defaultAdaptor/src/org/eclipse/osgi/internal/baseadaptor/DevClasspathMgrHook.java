/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.osgi.internal.baseadaptor;

import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import org.eclipse.osgi.baseadaptor.BaseData;
import org.eclipse.osgi.baseadaptor.bundlefile.BundleEntry;
import org.eclipse.osgi.baseadaptor.hooks.ClasspathManagerHook;
import org.eclipse.osgi.baseadaptor.loader.ClasspathEntry;
import org.eclipse.osgi.baseadaptor.loader.ClasspathManager;

public class DevClasspathMgrHook implements ClasspathManagerHook {

	public void preFindLocalClass(String name, ClasspathManager manager) throws ClassNotFoundException {
		// Do nothing
	}

	public void postFindLocalClass(String name, Class clazz, ClasspathManager manager) {
		// Do nothing
	}

	public void preFindLocalResource(String name, ClasspathManager manager) {
		// Do nothing
	}

	public void postFindLocalResource(String name, URL resource, ClasspathManager manager) {
		// Do nothing
	}

	public byte[] processClass(String name, byte[] classbytes, ClasspathEntry classpathEntry, BundleEntry entry, ClasspathManager manager) {
		// Do nothing
		return null;
	}

	public void recordClassDefine(String name, Class clazz, byte[] classbytes, ClasspathEntry classpathEntry, BundleEntry entry, ClasspathManager manager) {
		// Do nothing
	}

	public boolean addClassPathEntry(ArrayList cpEntries, String cp, ClasspathManager hostmanager, BaseData sourcedata, ProtectionDomain sourcedomain) {
		String[] devClassPath = !DevClassPathHelper.inDevelopmentMode() ? null : DevClassPathHelper.getDevClassPath(sourcedata.getSymbolicName());
		if (devClassPath == null)
			return false; // not in dev mode return
		boolean result = false;
		for (int i = 0; i < devClassPath.length; i++)
			result |= ClasspathManager.addClassPathEntry(cpEntries, devClassPath[i], hostmanager, sourcedata, sourcedomain);

		return result;
	}

	public String findLibrary(BaseData data, String libName) {
		// Do nothing
		return null;
	}

	public ClassLoader getBundleClassLoaderParent() {
		// Do nothing
		return null;
	}

}
