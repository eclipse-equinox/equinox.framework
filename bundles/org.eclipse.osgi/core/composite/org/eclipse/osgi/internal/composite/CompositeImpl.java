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

import org.eclipse.osgi.framework.adaptor.BundleData;
import org.eclipse.osgi.framework.internal.core.BundleHost;
import org.eclipse.osgi.framework.internal.core.Framework;
import org.osgi.framework.BundleException;

public class CompositeImpl extends BundleHost {

	public CompositeImpl(BundleData bundledata, Framework framework) throws BundleException {
		super(bundledata, framework);
	}

}
