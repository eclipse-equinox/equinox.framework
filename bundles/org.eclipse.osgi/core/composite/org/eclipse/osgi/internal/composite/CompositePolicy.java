/*******************************************************************************
 * Copyright (c) 2009, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.internal.composite;

import java.net.ContentHandler;
import org.eclipse.osgi.framework.adaptor.BundleData;
import org.eclipse.osgi.framework.internal.core.*;
import org.eclipse.osgi.internal.loader.BundleLoaderProxy;
import org.eclipse.osgi.service.resolver.BaseDescription;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.osgi.framework.*;
import org.osgi.framework.Constants;
import org.osgi.framework.hooks.service.*;
import org.osgi.service.url.URLStreamHandlerService;

public class CompositePolicy implements ScopePolicy {
	private final static BundleDescription[] EMPTY_DESCRIPTIONS = new BundleDescription[0];
	private final Framework framework;
	private final CompositeInfo rootCompositeInfo = new CompositeInfo(0, Constants.SYSTEM_BUNDLE_SYMBOLICNAME, null, null, null, null, null, null, null, null);
	private final static String[] scopedSystemServices = new String[] {URLStreamHandlerService.class.getName().intern(), ContentHandler.class.getName().intern(), EventHook.class.getName().intern(), FindHook.class.getName().intern(), ListenerHook.class.getName().intern()};

	public CompositePolicy(Framework framework) {
		this.framework = framework;
	}

	public boolean isVisible(Bundle client, Bundle provider, ServiceReference<?> serviceProvider, String[] clazzes) {
		return noScopes() || isVisible0((AbstractBundle) client, (AbstractBundle) provider, serviceProvider, clazzes, null);
	}

	public boolean isVisible(BundleDescription client, BaseDescription constraintProvider) {
		return noScopes() || isVisible0(framework.getBundle(client.getBundleId()), null, null, null, constraintProvider);
	}

	public boolean isVisible(Bundle client, BaseDescription constraintProvider) {
		return noScopes() || isVisible0((AbstractBundle) client, null, null, null, constraintProvider);
	}

	public boolean isVisible(BundleData client, BundleData provider) {
		return noScopes() || isVisible0(client.getBundleID(), client.getCompositeID(), provider.getBundleID(), provider.getCompositeID(), null, null, createBundleDescription(provider));
	}

	private BundleDescription createBundleDescription(BundleData provider) {
		return CompositeImpl.stateFactory.createBundleDescription(provider.getBundleID(), provider.getSymbolicName(), provider.getVersion(), provider.getLocation(), null, null, null, null, false, true, true, null, null, null, null);
	}

	public boolean noScopes() {
		return rootCompositeInfo.noChildren();
	}

	private boolean isVisible0(AbstractBundle client, AbstractBundle providerBundle, ServiceReference<?> serviceProvider, String[] clazzes, BaseDescription constraintProvider) {
		if (client == null)
			throw new IllegalArgumentException("Client cannot be null"); //$NON-NLS-1$
		if (serviceProvider == null && constraintProvider == null)
			throw new IllegalArgumentException("Provider cannot be null"); //$NON-NLS-1$
		if (serviceProvider != null) {
			if (providerBundle == null)
				throw new IllegalArgumentException("The service provider bundle must be specified."); //$NON-NLS-1$
		} else {
			providerBundle = framework.getBundle(constraintProvider.getSupplier().getBundleId());
		}
		if (providerBundle == null)
			return false; // we assume the bundle is uninstalled and should not be visible
		return isVisible0(client.getBundleId(), client.getCompositeId(), providerBundle.getBundleId(), providerBundle.getCompositeId(), serviceProvider, clazzes, constraintProvider);
	}

	private boolean isVisible0(long clientID, long clientCompositeID, long providerID, long providerCompositeID, ServiceReference<?> serviceProvider, String[] clazzes, BaseDescription constraintProvider) {
		if (serviceProvider == null && constraintProvider == null)
			throw new IllegalArgumentException("Provider cannot be null"); //$NON-NLS-1$
		if (serviceProvider != null && clientID == 0 && clientCompositeID == 0 && !scopedSystemService(clazzes))
			// root system bundle sees every service
			return true;

		if (providerID == 0 && providerCompositeID == 0 && !scopedSystemService(clazzes))
			// Everyone sees the root system bundle' services and packages
			return true;
		if (clientCompositeID == providerCompositeID)
			return true; // in the same composite
		CompositeInfo clientInfo = getCompositeInfo(clientCompositeID);
		CompositeInfo providerInfo = getCompositeInfo(providerCompositeID);
		if (providerInfo == null || clientInfo == providerInfo)
			return true;
		return clientInfo.isVisible(serviceProvider != null ? (Object) serviceProvider : (Object) constraintProvider, clientInfo, providerInfo);
	}

	private boolean scopedSystemService(String[] clazzes) {
		if (clazzes == null)
			return false;
		for (int i = 0; i < clazzes.length; i++)
			for (int j = 0; j < scopedSystemServices.length; j++)
				// we assume the strings are interned
				if (clazzes[i] == scopedSystemServices[j])
					return true;
		return false;
	}

	public CompositeInfo getCompositeInfo(long compositeId) {
		return (compositeId == 0) ? getRootCompositeInfo() : rootCompositeInfo.getChildCompositeInfo(compositeId);
	}

	public boolean hasBundlePolicyEquivalent(BundleDescription singleton) {
		AbstractBundle bundle = framework.getBundle(singleton.getBundleId());
		if (bundle == null)
			return false; // must be uninstalled
		CompositeInfo compositeInfo = getCompositeInfo(bundle.getCompositeId());
		if (compositeInfo == null)
			return false;
		return compositeInfo.hasBundlePolicyEquivalent(singleton);
	}

	public boolean sameScope(Bundle b1, Bundle b2) {
		if (noScopes())
			return true;
		if (b1 == null || b2 == null)
			return false;
		long b1CompId = ((AbstractBundle) b1).getCompositeId();
		long b2CompId = ((AbstractBundle) b2).getCompositeId();
		if (b1CompId == b2CompId)
			return true;
		if ((b1CompId == 0 && b1.getBundleId() == 0) || (b2CompId == 0 && b2.getBundleId() == 0))
			return true; // the root system bundle belongs to every scope
		return false;
	}

	public boolean sameScope(BaseDescription d1, BaseDescription d2) {
		if (noScopes())
			return true;
		if (d1 == null || d2 == null)
			return false;
		Bundle b1 = framework.getBundle(d1.getSupplier().getBundleId());
		Bundle b2 = framework.getBundle(d2.getSupplier().getBundleId());
		return sameScope(b1, b2);
	}

	public BundleDescription[] getScopeContent(BundleDescription desc) {
		if (noScopes())
			return EMPTY_DESCRIPTIONS;
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
