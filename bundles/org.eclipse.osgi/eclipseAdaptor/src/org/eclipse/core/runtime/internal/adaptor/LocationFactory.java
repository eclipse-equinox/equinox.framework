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
package org.eclipse.core.runtime.internal.adaptor;

import java.io.IOException;
import java.net.URL;
import java.util.WeakHashMap;
import org.eclipse.osgi.framework.adaptor.FrameworkAdaptor;
import org.eclipse.osgi.framework.internal.core.AbstractBundle;
import org.eclipse.osgi.framework.internal.core.FrameworkProperties;
import org.eclipse.osgi.internal.composite.CompositeImpl;
import org.eclipse.osgi.service.datalocation.Location;
import org.osgi.framework.*;

public class LocationFactory implements ServiceFactory<Location> {

	private final BasicLocation rootLocation;
	private WeakHashMap<CompositeImpl, Location> locations;

	public LocationFactory(BasicLocation rootLocation) {
		this.rootLocation = rootLocation;
	}

	public Location getService(Bundle bundle, ServiceRegistration<Location> registration) {
		CompositeImpl composite = (CompositeImpl) ((AbstractBundle) bundle).getComposite();
		if (composite == null)
			return rootLocation;
		try {
			return getCompositeLocation(composite);
		} catch (IOException e) {
			throw new ServiceException(e.getMessage(), e);
		}
	}

	private synchronized Location getCompositeLocation(CompositeImpl composite) throws IOException {
		if (locations == null)
			locations = new WeakHashMap<CompositeImpl, Location>(1);
		Location location = locations.get(composite);
		if (location == null) {
			String key = rootLocation.getProperty();
			URL defaultURL = null;
			if (key != null)
				defaultURL = LocationHelper.buildURL(composite.getProperty(key + ".default"), true); //$NON-NLS-1$
			location = rootLocation.createLocation(null, defaultURL, rootLocation.isReadOnly());
			URL url = getLocation(composite, key);
			if (url != null)
				location.set(url, false);
			locations.put(composite, location);
		}
		return location;
	}

	private URL getLocation(CompositeImpl composite, String key) throws IOException {
		if (key == null)
			return null;
		String location = composite.getProperty(key);
		String rootLocationProperty = FrameworkProperties.getProperty(key);
		if (rootLocationProperty != null && rootLocationProperty.equals(location))
			return rootLocation.getDataArea(FrameworkAdaptor.FRAMEWORK_SYMBOLICNAME + "/composites/" + composite.getBundleId()); //$NON-NLS-1$
		return LocationHelper.buildURL(location, true);
	}

	public void ungetService(Bundle bundle, ServiceRegistration<Location> registration, Location service) {
		// nothing
	}

}
