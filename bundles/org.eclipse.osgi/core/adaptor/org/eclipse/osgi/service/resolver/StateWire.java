/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.service.resolver;

/**
 * @since 3.7
 */
public class StateWire {
	private final BundleDescription requirementHost;
	private final VersionConstraint declaredRequirement;
	private final BundleDescription capabilityHost;
	private final BaseDescription declaredCapability;

	public StateWire(BundleDescription requirementHost, VersionConstraint declaredRequirement, BundleDescription capabilityHost, BaseDescription declaredCapability) {
		super();
		this.requirementHost = requirementHost;
		this.declaredRequirement = declaredRequirement;
		this.capabilityHost = capabilityHost;
		this.declaredCapability = declaredCapability;

	}

	public BundleDescription getRequirementHost() {
		return requirementHost;
	}

	public VersionConstraint getDeclaredRequirement() {
		return declaredRequirement;
	}

	public BundleDescription getCapabilityHost() {
		return capabilityHost;
	}

	public BaseDescription getDeclaredCapability() {
		return declaredCapability;
	}
}
