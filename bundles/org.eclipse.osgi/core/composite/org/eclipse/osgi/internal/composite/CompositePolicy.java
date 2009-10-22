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
import org.eclipse.osgi.internal.loader.BundleLoaderProxy;
import org.eclipse.osgi.service.resolver.BaseDescription;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;

public class CompositePolicy implements ScopePolicy {
	private final static BundleDescription[] EMPTY_DESCRIPTIONS = new BundleDescription[0];
	private final Framework framework;
	private final CompositeInfo rootCompositeInfo = new CompositeInfo(null, null, null, null, null, null);

	public CompositePolicy(Framework framework) {
		this.framework = framework;
	}

	public boolean isVisible(Bundle client, ServiceReference serviceProvider) {
		return noComposites() || isVisible0((AbstractBundle) client, serviceProvider, null);
	}

	public boolean isVisible(BundleDescription client, BaseDescription constraintProvider) {
		return noComposites() || isVisible0(framework.getBundle(client.getBundleId()), null, constraintProvider);
	}

	private boolean noComposites() {
		return rootCompositeInfo.noChildren();
	}

	private boolean isVisible0(AbstractBundle client, ServiceReference serviceProvider, BaseDescription constraintProvider) {
		if (client == null)
			throw new IllegalArgumentException("Client cannot be null"); //$NON-NLS-1$
		if (serviceProvider == null && constraintProvider == null)
			throw new IllegalArgumentException("Provider cannot be null"); //$NON-NLS-1$
		if (client.getBundleId() == 0 && client.getCompositeId() == 0)
			// root system bundle sees everything
			return true;
		AbstractBundle providerBundle = serviceProvider != null ? (AbstractBundle) serviceProvider.getBundle() : framework.getBundle(constraintProvider.getSupplier().getBundleId());
		if (providerBundle == null)
			return false; // we assume the bundle is uninstalled and should not be visible
		if (providerBundle.getBundleId() == 0 && providerBundle.getCompositeId() == 0)
			// Everyone sees the root system bundle
			return true;
		long clientCompID = client.getCompositeId();
		long providerCompID = providerBundle.getCompositeId();
		if (clientCompID == providerCompID)
			return true; // in the same composite
		CompositeInfo clientInfo = getCompositeInfo(clientCompID);
		CompositeInfo providerInfo = getCompositeInfo(providerCompID);
		if (providerInfo == null || clientInfo == providerInfo)
			return true;
		return clientInfo.isVisible(serviceProvider != null ? (Object) serviceProvider : (Object) constraintProvider, clientInfo, providerInfo);
	}

	private CompositeInfo getCompositeInfo(long compositeId) {
		return (compositeId == 0) ? getRootCompositeInfo() : ((CompositeImpl) framework.getBundle(compositeId)).getCompositeInfo();
	}

	public boolean sameScope(Bundle b1, Bundle b2) {
		if (b1 == null || b2 == null)
			return false;
		long b1CompId = ((AbstractBundle) b1).getCompositeId();
		long b2CompId = ((AbstractBundle) b2).getCompositeId();
		if (b1CompId == b2CompId)
			return true;
		if ((b1CompId == 0 && b1.getBundleId() == 0) || (b2CompId == 0 && b2CompId == 0))
			return true; // the root system bundle belongs to every scope
		return false;
	}

	public boolean sameScope(BaseDescription d1, BaseDescription d2) {
		if (d1 == null || d2 == null)
			return false;
		Bundle b1 = framework.getBundle(d1.getSupplier().getBundleId());
		Bundle b2 = framework.getBundle(d2.getSupplier().getBundleId());
		return sameScope(b1, b2);
	}

	public BundleDescription[] getScopeContent(BundleDescription desc) {
		Object user = desc.getUserObject();
		if (!(user instanceof BundleLoaderProxy))
			return EMPTY_DESCRIPTIONS;
		AbstractBundle bundle = ((BundleLoaderProxy) user).getBundleHost();
		if (!(bundle instanceof CompositeImpl))
			return EMPTY_DESCRIPTIONS;
		// found a composite
		return ((CompositeImpl) bundle).getConstituentDescriptions();
	}

	CompositeInfo getRootCompositeInfo() {
		return rootCompositeInfo;
	}
}
