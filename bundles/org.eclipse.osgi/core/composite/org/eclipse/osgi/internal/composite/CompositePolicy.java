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

import java.net.ContentHandler;
import org.eclipse.osgi.framework.adaptor.ScopePolicy;
import org.eclipse.osgi.framework.internal.core.AbstractBundle;
import org.eclipse.osgi.framework.internal.core.Framework;
import org.eclipse.osgi.internal.loader.BundleLoaderProxy;
import org.eclipse.osgi.internal.serviceregistry.ServiceReferenceImpl;
import org.eclipse.osgi.service.resolver.BaseDescription;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.osgi.framework.*;
import org.osgi.framework.hooks.service.*;
import org.osgi.service.url.URLStreamHandlerService;

public class CompositePolicy implements ScopePolicy {
	private final static BundleDescription[] EMPTY_DESCRIPTIONS = new BundleDescription[0];
	private final Framework framework;
	private final CompositeInfo rootCompositeInfo = new CompositeInfo(Constants.SYSTEM_BUNDLE_SYMBOLICNAME, null, null, null, null, null, null, null);
	private static String[] scopedSystemServices;

	public CompositePolicy(Framework framework) {
		this.framework = framework;
		scopedSystemServices = new String[] {URLStreamHandlerService.class.getName().intern(), ContentHandler.class.getName().intern(), EventHook.class.getName(), FindHook.class.getName(), ListenerHook.class.getName()};
	}

	public boolean isVisible(Bundle client, ServiceReference<?> serviceProvider, String[] clazzes) {
		return noScopes() || isVisible0((AbstractBundle) client, serviceProvider, clazzes, null);
	}

	public boolean isVisible(BundleDescription client, BaseDescription constraintProvider) {
		return noScopes() || isVisible0(framework.getBundle(client.getBundleId()), null, null, constraintProvider);
	}

	public boolean noScopes() {
		return rootCompositeInfo.noChildren();
	}

	private boolean isVisible0(AbstractBundle client, ServiceReference<?> serviceProvider, String[] clazzes, BaseDescription constraintProvider) {
		if (client == null)
			throw new IllegalArgumentException("Client cannot be null"); //$NON-NLS-1$
		if (serviceProvider == null && constraintProvider == null)
			throw new IllegalArgumentException("Provider cannot be null"); //$NON-NLS-1$
		if (client.getBundleId() == 0 && client.getCompositeId() == 0 && !scopedSystemService(clazzes))
			// root system bundle sees everything
			return true;
		AbstractBundle providerBundle = null;
		if (serviceProvider != null)
			// Need to access internals incase the reference has been unregistered (in this case getBundle returns null)
			providerBundle = (AbstractBundle) ((ServiceReferenceImpl<?>) serviceProvider).getRegistration().getRegisteringBundle();
		else
			providerBundle = framework.getBundle(constraintProvider.getSupplier().getBundleId());
		if (providerBundle == null)
			return false; // we assume the bundle is uninstalled and should not be visible
		if (providerBundle.getBundleId() == 0 && providerBundle.getCompositeId() == 0 && !scopedSystemService(clazzes))
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

	private CompositeInfo getCompositeInfo(long compositeId) {
		return (compositeId == 0) ? getRootCompositeInfo() : ((CompositeImpl) framework.getBundle(compositeId)).getCompositeInfo();
	}

	public boolean hasRequireEquivalent(BundleDescription singleton) {
		AbstractBundle bundle = framework.getBundle(singleton.getBundleId());
		if (bundle == null)
			return false; // must be uninstalled
		CompositeInfo compositeInfo = getCompositeInfo(bundle.getCompositeId());
		if (compositeInfo == null)
			return false;
		return compositeInfo.hasRequireEquivalent(singleton);
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
