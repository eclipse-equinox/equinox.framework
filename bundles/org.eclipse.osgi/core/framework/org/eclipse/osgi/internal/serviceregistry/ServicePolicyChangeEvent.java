/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.internal.serviceregistry;

import java.util.*;
import org.osgi.framework.ServiceReference;

/**
 * Used to synthesize modified service events when a composite sharing policy is changed.
 * Objects of this type are not thread safe are are assumed to be accessed from a single thread.
 */
public class ServicePolicyChangeEvent extends ModifiedServiceEvent {
	private static final long serialVersionUID = 1873771693795221452L;
	private final transient Collection<FilteredServiceListener> prePolicyChangeListeners = new ArrayList<FilteredServiceListener>();
	private final transient Collection<FilteredServiceListener> postPolicyChangeListeners = new ArrayList<FilteredServiceListener>();
	// we assume single thread access and do not guard this flag
	private final transient boolean[] prePolicyChange;

	public ServicePolicyChangeEvent(ServiceReference<?> reference, ServiceProperties previousProperties, boolean[] prePolicyChangeFlag) {
		super(reference, previousProperties);
		this.prePolicyChange = prePolicyChangeFlag;
	}

	public void addListener(FilteredServiceListener listener) {
		if (prePolicyChange[0]) {
			prePolicyChangeListeners.add(listener);
		} else
			postPolicyChangeListeners.add(listener);
	}

	private List<FilteredServiceListener> getRemoved() {
		List<FilteredServiceListener> result = new ArrayList<FilteredServiceListener>(prePolicyChangeListeners);
		result.removeAll(postPolicyChangeListeners);
		return result;
	}

	private List<FilteredServiceListener> getAdded() {
		ArrayList<FilteredServiceListener> result = new ArrayList<FilteredServiceListener>(postPolicyChangeListeners);
		result.removeAll(prePolicyChangeListeners);
		return result;
	}

	public void fireSyntheticEvents(ServiceRegistry registry) {
		for (FilteredServiceListener added : getAdded())
			added.fireSyntheticEvent(getModifiedEvent());
		for (FilteredServiceListener removed : getRemoved())
			removed.fireSyntheticEvent(getModifiedEndMatchEvent());

	}
}
