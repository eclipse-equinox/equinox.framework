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
	private String name;
	private Version version;
	private ClassSpacePolicyInfo[] importPackagePolicy;
	private ClassSpacePolicyInfo[] exportPackagePolicy;
	private ClassSpacePolicyInfo[] requireBundlePolicy;
	private ServicePolicyInfo[] importServicePolicy;
	private ServicePolicyInfo[] exportServicePolicy;

	CompositeInfo(String name, Version version, CompositeInfo parent, ClassSpacePolicyInfo[] importPackagePolicy, ClassSpacePolicyInfo[] exportPackagePolicy, ClassSpacePolicyInfo[] requireBundlePolicy, ServicePolicyInfo[] importServicePolicy, ServicePolicyInfo[] exportServicePolicy) {
		this.name = name == null ? "" : name; //$NON-NLS-1$
		this.version = version == null ? Version.emptyVersion : version;
		this.parent = parent;
		this.importPackagePolicy = importPackagePolicy;
		this.exportPackagePolicy = exportPackagePolicy;
		this.requireBundlePolicy = requireBundlePolicy;
		this.importServicePolicy = importServicePolicy;
		this.exportServicePolicy = exportServicePolicy;
	}

	boolean isVisible(Object provider, CompositeInfo origin, CompositeInfo providerComposite) {
		return isVisible0(provider, origin, null, providerComposite);
	}

	boolean isVisible0(Object provider, CompositeInfo origin, PolicyInfo<?, ?> peerPolicy, CompositeInfo providerComposite) {
		// first check if the the import policy allows the parent to provide
		if (origin != parent) {
			// this request did not come from the parent
			PolicyInfo<?, ?> matchedPolicy = matchImportPolicy(provider);
			if (matchedPolicy != null) {
				// our policy allows the provider to be imported from the parent
				if (providerComposite == parent && matchedPolicy.matchParentAffinity(parent))
					// the parent actually provides this; and affinity matches with parent
					return true;
				// check if the provider is visible from the parent policy perspective;
				// We only allow for parent delegation if the peer policy does not have affinity
				if ((peerPolicy == null || !peerPolicy.hasAffinity()) && parent.isVisible0(provider, this, matchedPolicy, providerComposite))
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

	synchronized boolean hasRequireEquivalent(BundleDescription singleton) {
		if (requireBundlePolicy == null)
			return false;
		for (ClassSpacePolicyInfo policy : requireBundlePolicy)
			if (policy.matchName(singleton))
				return true;
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
		this.importServicePolicy = updatedInfo.importServicePolicy;
		this.exportServicePolicy = updatedInfo.exportServicePolicy;
	}

	String getName() {
		return name;
	}

	Version getVersion() {
		return version;
	}

	static abstract class PolicyInfo<C, P> {
		private static final String PARENT_AFFINITY = "<<parent>>"; //$NON-NLS-1$
		protected final String compositeName;
		protected final VersionRange compositeRange;
		protected final C spec;

		public PolicyInfo(String compositeName, VersionRange compositeRange, C spec) {
			this.compositeName = compositeName;
			this.compositeRange = compositeRange;
			this.spec = spec;
		}

		public final boolean match(P provider, CompositeInfo providerComposite, PolicyInfo<C, P> peerPolicy) {
			if (peerPolicy != null && !peerPolicy.matchPeerAffinity(providerComposite))
				return false;
			return matchProvider(provider);

		}

		boolean hasAffinity() {
			return compositeName != null || compositeRange != null;
		}

		boolean matchParentAffinity(CompositeInfo parentComposite) {
			if (PARENT_AFFINITY.equals(compositeName))
				return true; // ignore version in this case
			return matchAffinity(parentComposite);
		}

		private boolean matchPeerAffinity(CompositeInfo peerComposite) {
			if (PARENT_AFFINITY.equals(compositeName))
				return false; // peer cannot satisfy parent affinity
			return matchAffinity(peerComposite);
		}

		private boolean matchAffinity(CompositeInfo providerComposite) {
			if (compositeName != null && !compositeName.equals(providerComposite.getName()))
				return false;
			if (compositeRange != null && !compositeRange.isIncluded(providerComposite.getVersion()))
				return false;
			return true;
		}

		protected abstract boolean matchProvider(P provider);
	}

	static class ClassSpacePolicyInfo extends PolicyInfo<VersionConstraint, BaseDescription> {

		public ClassSpacePolicyInfo(String compositeName, VersionRange compositeRange, VersionConstraint spec) {
			super(compositeName, compositeRange, spec);
		}

		public boolean matchName(BundleDescription singleton) {
			return spec.getName().equals(singleton.getName());
		}

		protected boolean matchProvider(BaseDescription provider) {
			return spec.isSatisfiedBy(provider);
		}
	}

	static class ServicePolicyInfo extends PolicyInfo<Filter, ServiceReference<?>> {

		public ServicePolicyInfo(String compositeName, VersionRange compositeRange, Filter spec) {
			super(compositeName, compositeRange, spec);
		}

		protected boolean matchProvider(ServiceReference<?> provider) {
			return spec.match(provider);
		}

	}
}