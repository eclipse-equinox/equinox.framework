package org.eclipse.osgi.service.internal.composite;

import org.eclipse.osgi.framework.adaptor.ClassLoaderDelegate;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.osgi.framework.BundleException;

/**
 * An internal interface only used by the composite implementation
 * 
 * @noextend This interface is not intended to be extended by clients.
 * @noimplement This interface is not intended to be implemented by clients.
 */
public interface Composite {
	public void updateContent() throws BundleException;

	public void refreshContent(boolean synchronously);

	public boolean resolveContent();

	public BundleDescription getCompositeDescription();

	public ClassLoaderDelegate getDelegate();

	public void started(Composite compositeBundle);

	public void stopped(Composite compositeBundle);
}
