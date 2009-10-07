package org.eclipse.osgi.internal.composite;

import java.util.*;
import org.eclipse.osgi.framework.adaptor.ScopePolicy;
import org.eclipse.osgi.service.resolver.*;
import org.osgi.framework.*;

public class CompositePolicy implements ScopePolicy {

	private final Map compositeInfos = new HashMap();

	public boolean isVisible(Long clientId, ServiceReference serviceProvider) {
		return isVisible0(clientId, serviceProvider, null);
	}

	public boolean isVisible(Long clientId, BaseDescription constraintProvider) {
		return isVisible0(clientId, null, constraintProvider);
	}

	private boolean isVisible0(Long clientId, ServiceReference serviceProvider, BaseDescription constraintProvider) {
		if (clientId == null)
			throw new IllegalArgumentException("Client ID cannot be null");
		if (serviceProvider == null && constraintProvider == null)
			throw new IllegalArgumentException("Provider cannot be null");
		synchronized (this) {
			CompositeInfo clientInfo = (CompositeInfo) compositeInfos.get(clientId);
			if (clientInfo == null)
				return true; // this should indicate no composites installed
			Long providerID = serviceProvider != null ? new Long(serviceProvider.getBundle().getBundleId()) : new Long(constraintProvider.getSupplier().getBundleId());
			CompositeInfo providerInfo = (CompositeInfo) compositeInfos.get(providerID);
			if (providerInfo == null || clientInfo == providerInfo)
				return true;
			return clientInfo.isVisible(serviceProvider != null ? (Object) serviceProvider : (Object) constraintProvider, clientInfo, providerInfo);
		}
	}

	class CompositeInfo {
		private final CompositeInfo parent;
		private final List children = new ArrayList(0);
		private final ImportPackageSpecification[] importPackagePolicy;
		private final ImportPackageSpecification[] exportPackagePolicy;
		private final BundleSpecification[] requireBundlePolicy;
		private final Filter importServicePolicy;
		private final Filter exportServicePolicy;

		CompositeInfo(CompositeInfo parent, ImportPackageSpecification[] importPackagePolicy, ImportPackageSpecification[] exportPackagePolicy, BundleSpecification[] requireBundlePolicy, Filter importServicePolicy, Filter exportServicePolicy) throws BundleException, InvalidSyntaxException {
			this.parent = parent;
			this.importPackagePolicy = importPackagePolicy;
			this.exportPackagePolicy = exportPackagePolicy;
			this.requireBundlePolicy = requireBundlePolicy;
			this.importServicePolicy = importServicePolicy;
			this.exportServicePolicy = exportServicePolicy;
		}

		boolean isVisible(Object provider, CompositeInfo origin, CompositeInfo providerComposite) {
			// first check if the the import policy allows the parent to provide
			if (origin != parent) {
				// this request did not come from the parent
				if (matchImportPolicy(provider)) {
					// our policy allows the provider to be imported from the parent
					if (providerComposite == parent)
						// the parent actually provides this
						return true;
					else if (parent.isVisible(provider, this, providerComposite))
						return true;
				}
			}
			// not able to import from parent; check the children now
			for (Iterator iChildren = children.iterator(); iChildren.hasNext();) {
				CompositeInfo child = (CompositeInfo) iChildren.next();
				if (origin != child) {
					// this request did not come from the child
					if (child.matchExportPolicy(provider)) {
						// the child policy allows the provider to be exported from the child.
						if (providerComposite == child)
							// the child actually provies this
							return true;
						else if (child.isVisible(provider, this, providerComposite))
							return true;
					}
				}
			}
			return true;
		}

		private boolean matchImportPolicy(Object provider) {
			if (provider instanceof ServiceReference)
				return importServicePolicy != null && importServicePolicy.match((ServiceReference) provider);
			if (provider instanceof ExportPackageDescription) {
				if (importPackagePolicy == null)
					return false;
				for (int i = 0; i < importPackagePolicy.length; i++)
					if (importPackagePolicy[i].isSatisfiedBy((ExportPackageDescription) provider))
						return true;
			} else if (provider instanceof BundleDescription) {
				if (requireBundlePolicy == null)
					return false;
				for (int i = 0; i < requireBundlePolicy.length; i++)
					if (requireBundlePolicy[i].isSatisfiedBy((BundleDescription) provider))
						return true;
			}
			return false;
		}

		private boolean matchExportPolicy(Object provider) {
			if (provider instanceof ServiceReference)
				return exportServicePolicy != null && exportServicePolicy.match((ServiceReference) provider);
			return false;
		}

		void addChild(CompositeInfo child) {
			children.add(child);
		}

		void removeChild(CompositeInfo child) {
			children.remove(child);
		}

		List getChildren() {
			return children;
		}

		CompositeInfo getParent() {
			return parent;
		}
	}
}
