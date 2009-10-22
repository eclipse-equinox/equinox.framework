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

import java.util.*;
import org.eclipse.osgi.service.resolver.*;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;

class CompositeInfo {
	private final CompositeInfo parent;
	private final List<CompositeInfo> children = Collections.synchronizedList(new ArrayList<CompositeInfo>(0));
	private ImportPackageSpecification[] importPackagePolicy;
	private ImportPackageSpecification[] exportPackagePolicy;
	private BundleSpecification[] requireBundlePolicy;
	private Filter importServicePolicy;
	private Filter exportServicePolicy;

	CompositeInfo(CompositeInfo parent, ImportPackageSpecification[] importPackagePolicy, ImportPackageSpecification[] exportPackagePolicy, BundleSpecification[] requireBundlePolicy, Filter importServicePolicy, Filter exportServicePolicy) {
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
				// check if the provider is visible from the parent policy perspective
				else if (parent.isVisible(provider, this, providerComposite))
					return true;
			}
		}
		// not able to import from parent; check the children now
		// get a snap shot of the children
		CompositeInfo[] currentChildren = children.toArray(new CompositeInfo[children.size()]);
		for (int i = 0; i < currentChildren.length; i++) {
			if (origin != currentChildren[i]) {
				// this request did not come from the child
				if (currentChildren[i].matchExportPolicy(provider)) {
					// the child policy allows the provider to be exported from the child.
					if (providerComposite == currentChildren[i])
						// the child actually provides this
						return true;
					else if (currentChildren[i].isVisible(provider, this, providerComposite))
						return true;
				}
			}
		}
		// cannot import from parent or children
		return false;
	}

	private synchronized boolean matchImportPolicy(Object provider) {
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

	private synchronized boolean matchExportPolicy(Object provider) {
		if (provider instanceof ServiceReference)
			return exportServicePolicy != null && exportServicePolicy.match((ServiceReference) provider);
		if (provider instanceof ExportPackageDescription) {
			if (exportPackagePolicy == null)
				return false;
			for (int i = 0; i < exportPackagePolicy.length; i++)
				if (exportPackagePolicy[i].isSatisfiedBy((ExportPackageDescription) provider))
					return true;
		}
		return false;
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
		this.importPackagePolicy = updatedInfo.importPackagePolicy;
		this.exportPackagePolicy = updatedInfo.exportPackagePolicy;
		this.requireBundlePolicy = updatedInfo.requireBundlePolicy;
		this.importServicePolicy = updatedInfo.importServicePolicy;
		this.exportServicePolicy = updatedInfo.exportServicePolicy;
	}
}