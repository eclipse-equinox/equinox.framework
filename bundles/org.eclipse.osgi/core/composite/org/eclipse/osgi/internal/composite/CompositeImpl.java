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

package org.eclipse.osgi.internal.composite;

import java.util.Map;
import org.eclipse.osgi.framework.adaptor.BundleData;
import org.eclipse.osgi.framework.internal.core.*;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.composite.CompositeBundle;

public class CompositeImpl extends BundleHost implements CompositeBundle {

	final BundleContext systemContext;

	public CompositeImpl(BundleData bundledata, Framework framework) throws BundleException {
		super(bundledata, framework);
		systemContext = new CompositeContext((BundleHost) framework.getBundle(0));
	}

	public BundleContext getSystemBundleContext() {
		return systemContext;
	}

	public void update(Map compositeManifest) throws BundleException {
		// TODO Auto-generated method stub

	}

	public class CompositeContext extends BundleContextImpl {

		protected CompositeContext(BundleHost bundle) {
			super(bundle);
		}

		protected long getCompositeID() {
			return getBundleId();
		}
	}
}
