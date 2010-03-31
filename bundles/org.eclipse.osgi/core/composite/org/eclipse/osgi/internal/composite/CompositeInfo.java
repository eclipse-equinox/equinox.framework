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

import java.util.*;
import org.eclipse.osgi.service.resolver.*;
import org.osgi.framework.*;

class CompositeInfo {
	private final CompositeInfo parent;
	private final List<CompositeInfo> children = Collections.synchronizedList(new ArrayList<CompositeInfo>(0));
	private final long id;
	private String name;
	private Version version;
	private ClassSpacePolicyInfo[] importPackagePolicy;
	private ClassSpacePolicyInfo[] exportPackagePolicy;
	private ClassSpacePolicyInfo[] requireBundlePolicy;
	private ClassSpacePolicyInfo[] provideBundlePolicy;
	private ServicePolicyInfo[] importServicePolicy;
	private ServicePolicyInfo[] exportServicePolicy;

	CompositeInfo(long id, String name, Version version, CompositeInfo parent, ClassSpacePolicyInfo[] importPackagePolicy, ClassSpacePolicyInfo[] exportPackagePolicy, ClassSpacePolicyInfo[] requireBundlePolicy, ClassSpacePolicyInfo[] provideBundlePolicy, ServicePolicyInfo[] importServicePolicy, ServicePolicyInfo[] exportServicePolicy) {
		this.id = id;
		this.name = name == null ? "" : name; //$NON-NLS-1$
		this.version = version == null ? Version.emptyVersion : version;
		this.parent = parent;
		this.importPackagePolicy = importPackagePolicy;
		this.exportPackagePolicy = exportPackagePolicy;
		this.requireBundlePolicy = requireBundlePolicy;
		this.provideBundlePolicy = provideBundlePolicy;
		this.importServicePolicy = importServicePolicy;
		this.exportServicePolicy = exportServicePolicy;
	}

	boolean isVisible(Object provider, CompositeInfo origin, CompositeInfo providerComposite) {
		return isVisible0(provider, origin, null, providerComposite);
	}

	boolean isVisible0(Object provider, CompositeInfo origin, PolicyInfo<?, ?> peerPolicy, CompositeInfo providerComposite) {
		// first check if the the import policy allows the parent to provide
		// we only allow for parent delegation if the peer policy does not have affinity
		if (origin != parent && (peerPolicy == null || !peerPolicy.hasPeerConstraint())) {
			// this request did not come from the parent
			PolicyInfo<?, ?> matchedPolicy = matchImportPolicy(provider);
			if (matchedPolicy != null) {
				// Found a match policy that allows the provider to be imported; we have not checked the peer constraints yet
				if (providerComposite == parent && !matchedPolicy.hasPeerConstraint())
					// the parent actually provides this; a parent can only provide that if there is no peer constraint
					return true;
				// check if the provider is visible from the parent policy perspective;
				if (parent.isVisible0(provider, this, matchedPolicy, providerComposite))
					return true;
			}
		}
		// not able to import from parent; check the children now
		// get a snap shot of the children
		CompositeInfo[] currentChildren = children.toArray(new CompositeInfo[children.size()]);
		for (int i = 0; i < currentChildren.length; i++) {
			if (origin != currentChildren[i]) {
				// this request did not come from the child
				if (currentChildren[i].matchExportPolicy(provider, peerPolicy) != null) {
					// the child policy allows the provider to be exported from the child.
					if (providerComposite == currentChildren[i])
						// the child actually provides this
						return true;
					if (currentChildren[i].isVisible0(provider, this, null, providerComposite))
						return true;
				}
			}
		}
		// cannot import from parent or children
		return false;
	}

	synchronized boolean hasBundlePolicyEquivalent(BundleDescription singleton) {
		if (requireBundlePolicy != null) {
			for (ClassSpacePolicyInfo policy : requireBundlePolicy)
				if (policy.matchName(singleton))
					return true;
		}
		if (provideBundlePolicy != null) {
			for (ClassSpacePolicyInfo policy : provideBundlePolicy)
				if (policy.matchName(singleton))
					return true;
		}
		return false;
	}

	private synchronized PolicyInfo<?, ?> matchImportPolicy(Object provider) {
		if (provider instanceof ServiceReference<?>)
			return matchFilters((ServiceReference<?>) provider, importServicePolicy, this, null);
		if (provider instanceof ExportPackageDescription)
			return matchConstraints((BaseDescription) provider, importPackagePolicy, this, null);
		if (provider instanceof BundleDescription)
			return matchConstraints((BaseDescription) provider, requireBundlePolicy, this, null);
		return null;
	}

	private synchronized PolicyInfo<?, ?> matchExportPolicy(Object provider, PolicyInfo<?, ?> peerPolicy) {
		if (provider instanceof ServiceReference<?>)
			return matchFilters((ServiceReference<?>) provider, exportServicePolicy, this, (ServicePolicyInfo) peerPolicy);
		if (provider instanceof ExportPackageDescription)
			return matchConstraints((BaseDescription) provider, exportPackagePolicy, this, (ClassSpacePolicyInfo) peerPolicy);
		if (provider instanceof BundleDescription)
			return matchConstraints((BaseDescription) provider, provideBundlePolicy, this, (ClassSpacePolicyInfo) peerPolicy);
		return null;
	}

	private static ServicePolicyInfo matchFilters(ServiceReference<?> provider, ServicePolicyInfo[] servicePolicy, CompositeInfo providerComposite, ServicePolicyInfo peerPolicy) {
		if (servicePolicy == null)
			return null;
		for (ServicePolicyInfo policy : servicePolicy)
			if (policy.match(provider, providerComposite, peerPolicy))
				return policy;
		return null;
	}

	private static ClassSpacePolicyInfo matchConstraints(BaseDescription provider, ClassSpacePolicyInfo[] constraints, CompositeInfo providerComposite, ClassSpacePolicyInfo peerPolicy) {
		if (constraints == null)
			return null;
		for (ClassSpacePolicyInfo policy : constraints)
			if (policy.match(provider, providerComposite, peerPolicy))
				return policy;
		return null;
	}

	void addChild(CompositeInfo child) {
		children.add(child);
	}

	private void removeChild(CompositeInfo child) {
		children.remove(child);
	}

	void orphaned() {
		parent.removeChild(this);
	}

	boolean noChildren() {
		return children.isEmpty();
	}

	synchronized void update(CompositeInfo updatedInfo) {
		this.name = updatedInfo.name;
		this.version = updatedInfo.version;
		this.importPackagePolicy = updatedInfo.importPackagePolicy;
		this.exportPackagePolicy = updatedInfo.exportPackagePolicy;
		this.requireBundlePolicy = updatedInfo.requireBundlePolicy;
		this.provideBundlePolicy = updatedInfo.provideBundlePolicy;
		this.importServicePolicy = updatedInfo.importServicePolicy;
		this.exportServicePolicy = updatedInfo.exportServicePolicy;
	}

	long getId() {
		return id;
	}

	String getName() {
		return name;
	}

	Version getVersion() {
		return version;
	}

	static abstract class PolicyInfo<C, P> {
		protected final String peerCompositeName;
		protected final VersionRange peerCompositeRange;
		protected final C spec;

		public PolicyInfo(String peerCompositeName, VersionRange peerCompositeRange, C spec) {
			this.peerCompositeName = peerCompositeName;
			this.peerCompositeRange = peerCompositeRange;
			this.spec = spec;
		}

		public final boolean match(P provider, CompositeInfo providerComposite, PolicyInfo<C, P> peerPolicy) {
			if (peerPolicy != null && !peerPolicy.matchPeerConstraint(providerComposite))
				return false;
			return matchProvider(provider);

		}

		boolean hasPeerConstraint() {
			return peerCompositeName != null || peerCompositeRange != null;
		}

		private boolean matchPeerConstraint(CompositeInfo providerComposite) {
			if (peerCompositeName != null && !peerCompositeName.equals(providerComposite.getName()))
				return false;
			if (peerCompositeRange != null && !peerCompositeRange.isIncluded(providerComposite.getVersion()))
				return false;
			return true;
		}

		protected abstract boolean matchProvider(P provider);
	}

	static class ClassSpacePolicyInfo extends PolicyInfo<VersionConstraint, BaseDescription> {

		public ClassSpacePolicyInfo(String peerCompositeName, VersionRange peerCompositeRange, VersionConstraint spec) {
			super(peerCompositeName, peerCompositeRange, spec);
		}

		public boolean matchName(BundleDescription singleton) {
			return spec.getName().equals(singleton.getName());
		}

		protected boolean matchProvider(BaseDescription provider) {
			return spec.isSatisfiedBy(provider);
		}
	}

	static class ServicePolicyInfo extends PolicyInfo<Filter, ServiceReference<?>> {

		public ServicePolicyInfo(String peerCompositeName, VersionRange peerCompositeRange, Filter spec) {
			super(peerCompositeName, peerCompositeRange, spec);
		}

		protected boolean matchProvider(ServiceReference<?> provider) {
			return spec.match(provider);
		}

	}

	synchronized CompositeInfo getChildCompositeInfo(long compositeId) {
		if (compositeId < getId() || noChildren())
			return null; // We assume that nested child composites will always have a higher id
		// get a snap shot of the children
		CompositeInfo[] currentChildren = children.toArray(new CompositeInfo[children.size()]);
		for (CompositeInfo childInfo : currentChildren) {
			if (childInfo.getId() == compositeId)
				return childInfo;
			CompositeInfo result = childInfo.getChildCompositeInfo(compositeId);
			if (result != null)
				return result;
		}
		return null;
	}
}