package org.osgi.service.packageadmin;

import org.osgi.framework.Bundle;

public interface SymbolicBundle {
	public Bundle getProvidingBundle();
	public Bundle[] getRequiringBundles();
	public String getName();
	public String getVersion();
	public boolean isRemovalPending();
}
