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

import org.eclipse.osgi.framework.adaptor.ScopePolicy;
import org.eclipse.osgi.framework.internal.core.AbstractBundle;
import org.eclipse.osgi.framework.internal.core.Framework;
import org.eclipse.osgi.service.resolver.BaseDescription;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;

public class CompositePolicy implements ScopePolicy {
	private final Framework framework;

	public CompositePolicy(Framework framework) {
		this.framework = framework;
	}

	public boolean isVisible(Bundle client, ServiceReference serviceProvider) {
		return isVisible0((AbstractBundle) client, serviceProvider, null);
	}

	public boolean isVisible(BundleDescription client, BaseDescription constraintProvider) {
		return isVisible0(framework.getBundle(client.getBundleId()), null, constraintProvider);
	}

	private boolean isVisible0(AbstractBundle client, ServiceReference serviceProvider, BaseDescription constraintProvider) {
		if (client == null)
			throw new IllegalArgumentException("Client cannot be null"); //$NON-NLS-1$
		if (serviceProvider == null && constraintProvider == null)
			throw new IllegalArgumentException("Provider cannot be null"); //$NON-NLS-1$
		long clientCompID = client.getBundleData().getCompositeID();
		long providerCompID = 0;
		if (serviceProvider != null) {
			providerCompID = ((AbstractBundle) serviceProvider.getBundle()).getBundleData().getCompositeID();
		} else {
			providerCompID = framework.getBundle(constraintProvider.getSupplier().getBundleId()).getBundleData().getCompositeID();
		}
		if (clientCompID == providerCompID)
			return true; // in the same composite
		CompositeInfo clientInfo = ((CompositeImpl) framework.getBundle(clientCompID)).getCompositeInfo();
		CompositeInfo providerInfo = ((CompositeImpl) framework.getBundle(providerCompID)).getCompositeInfo();
		if (providerInfo == null || clientInfo == providerInfo)
			return true;
		return clientInfo.isVisible(serviceProvider != null ? (Object) serviceProvider : (Object) constraintProvider, clientInfo, providerInfo);
	}

}
