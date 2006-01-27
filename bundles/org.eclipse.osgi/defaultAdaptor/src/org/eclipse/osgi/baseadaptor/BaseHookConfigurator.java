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

package org.eclipse.osgi.baseadaptor;

import org.eclipse.osgi.internal.baseadaptor.*;

/**
 * Add the hooks necessary to support the OSGi Framework specification.  
 */
public class BaseHookConfigurator implements HookConfigurator {

	public void addHooks(HookRegistry registry) {
		// always add the BaseStorageHook and BaseClasspathMgrHook; it is required for the storage implementation
		registry.addStorageHook(new BaseStorageHook(registry.getAdaptor().getStorage()));
		registry.addClasspathManagerHook(new BaseClasspathMgrHook());
	}

}
