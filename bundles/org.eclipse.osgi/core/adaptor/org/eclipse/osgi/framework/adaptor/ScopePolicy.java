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
package org.eclipse.osgi.framework.adaptor;

import org.eclipse.osgi.service.resolver.BaseDescription;
import org.osgi.framework.ServiceReference;

/**
 * A scope policy determines the visibility of a resource provider to a 
 * potential client.
 * @noimplement This interface is not intended to be implemented by clients. 
 */
public interface ScopePolicy {
	/**
	 * Determines if the specified client ID should have visibility to the 
	 * specified service reference.
	 * @param clientId the id of the potential client bundle
	 * @param serviceProvider the service reference to determine visibility of
	 * @return true if the client has visibility according to this scope policy; false otherwise
	 */
	boolean isVisible(Long clientId, ServiceReference serviceProvider);

	/**
	 * Determines if the specified client ID should have visibility to the 
	 * specified constraint provider (exported package, bundle symbolic name etc).
	 * @param clientId the id of the potential client bundle
	 * @param constraintProvider the service reference to determine visibility of
	 * @return true if the client has visibility according to this scope policy; false otherwise
	 */
	boolean isVisible(Long clientId, BaseDescription constraintProvider);
}
