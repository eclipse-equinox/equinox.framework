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

import org.eclipse.osgi.internal.composite.CompositeImpl;
import org.osgi.framework.*;

public class StartLevelFactory implements ServiceFactory {
	private final Framework framework;
	private final StartLevelManager rootStartLevel;

	public StartLevelFactory(StartLevelManager rootStartLevel, Framework framework) {
		this.rootStartLevel = rootStartLevel;
		this.framework = framework;
	}

	public StartLevelManager getStartLevelManager(AbstractBundle bundle) {
		long compositeID = bundle.getCompositeId();
		if (compositeID == 0)
			return rootStartLevel;
		CompositeImpl composite = (CompositeImpl) framework.getBundle(compositeID);
		return composite.getStartLevelService();
	}

	public Object getService(Bundle bundle, ServiceRegistration registration) {
		return getStartLevelManager((AbstractBundle) bundle);
	}

	public void ungetService(Bundle bundle, ServiceRegistration registration, Object service) {
		// Nothing to do
	}

}
