/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.service.composite;

import java.util.Map;
import org.osgi.framework.BundleException;
import org.osgi.service.composite.CompositeBundle;

public interface CompositePolicyAdmin {
	void updateSharingPolicy(CompositeBundle composite, Map<String, String> policy) throws BundleException;
}
