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

package org.eclipse.core.runtime.internal.adaptor;

import org.eclipse.osgi.baseadaptor.*;
import org.eclipse.osgi.baseadaptor.hooks.DataHook;
import org.eclipse.osgi.baseadaptor.loader.BaseClassLoader;
import org.eclipse.osgi.framework.adaptor.BundleProtectionDomain;
import org.eclipse.osgi.framework.adaptor.ClassLoaderDelegate;

public class EclipseDataHook implements DataHook, HookConfigurator {

	public boolean forgetStatusChange(BaseData data, int status) {
		EclipseStorageHook storageHook = (EclipseStorageHook) data.getStorageHook(EclipseStorageHook.KEY);
		if (storageHook != null && storageHook.isAutoStartable())
			return true;
		return false;
	}

	public boolean forgetStartLevelChange(BaseData data, int startlevel) {
		return false;
	}

	public boolean matchDNChain(BaseData data, String pattern) {
		return false;
	}

	public void addHooks(HookRegistry hookRegistry) {
		hookRegistry.addDataHook(this);
	}

	public BaseClassLoader createClassLoader(ClassLoader parent, ClassLoaderDelegate delegate, BundleProtectionDomain domain, BaseData data, String[] bundleclasspath) {
		// do nothing
		return null;
	}

	public void initializedClassLoader(BaseClassLoader baseClassLoader, BaseData data) {
		// do nothing
	}
}
