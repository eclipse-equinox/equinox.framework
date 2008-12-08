/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.internal.composite;

import java.io.*;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import org.eclipse.osgi.framework.adaptor.BundleData;
import org.eclipse.osgi.framework.adaptor.ClassLoaderDelegate;
import org.eclipse.osgi.framework.internal.core.*;
import org.eclipse.osgi.framework.internal.core.Constants;
import org.eclipse.osgi.internal.loader.BundleLoaderProxy;
import org.eclipse.osgi.internal.module.LinkHelper;
import org.eclipse.osgi.internal.module.ResolverBundle;
import org.eclipse.osgi.launch.Equinox;
import org.eclipse.osgi.service.internal.composite.Composite;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.ExportPackageDescription;
import org.osgi.framework.*;
import org.osgi.framework.launch.Framework;
import org.osgi.service.framework.LinkBundle;

public class CompositeBundle extends BundleHost implements LinkBundle, LinkHelper, Composite {
	private static String CHILD_STORAGE = "child.storage"; //$NON-NLS-1$
	private static String PARENT_CONTENT = "parent.content"; //$NON-NLS-1$
	public static String CHILD_CONFIGURATION = "childConfig.properties"; //$NON-NLS-1$
	public static String PROP_PARENTFRAMEWORK = "org.eclipse.equinox.parentFramework"; //$NON-NLS-1$
	public static String PROP_CHILDLINK = "org.eclipse.equinox.childLink"; //$NON-NLS-1$

	private final boolean isParent;
	private final Framework companionFramework;
	private final long companionID;
	private final ServiceTrackerManager trackerManager = new ServiceTrackerManager();
	private final ThreadLocal refreshing = new ThreadLocal();

	public CompositeBundle(BundleData bundledata, org.eclipse.osgi.framework.internal.core.Framework framework) throws BundleException {
		super(bundledata, framework);
		this.isParent = (bundledata.getType() & BundleData.TYPE_LINKBUNDLE_PARENT) > 0;
		this.companionFramework = findCompanionFramework(isParent, framework, bundledata);
		this.companionID = isParent ? ((LinkBundle) FrameworkProperties.getProperties().get(PROP_CHILDLINK)).getBundleId() : 1;
	}

	private Framework findCompanionFramework(boolean parent, org.eclipse.osgi.framework.internal.core.Framework thisFramework, BundleData thisData) throws BundleException {
		if (parent)
			// just get the property which was set when creating the child framework
			return (Framework) FrameworkProperties.getProperties().get(PROP_PARENTFRAMEWORK);
		// child framework case:
		// allocate storage area for the child framework
		File childStorage = thisData.getDataFile(CHILD_STORAGE);
		boolean firstTime = false;
		if (!childStorage.exists())
			// the child storage area has not been allocated; this is the first time
			firstTime = true;
		// find the configuration properties
		URL childConfig = bundledata.getEntry(CHILD_CONFIGURATION);
		Properties props = new Properties();
		try {
			props.load(childConfig.openStream());
		} catch (IOException e) {
			throw new BundleException("Could not load child configuration", e);
		}
		props.put(Constants.FRAMEWORK_STORAGE, childStorage.getAbsolutePath());
		// save the parent framework so the parent companion bundle can find it
		props.put(PROP_PARENTFRAMEWORK, thisFramework.getSystemBundleContext().getBundle());
		// TODO leaks "this" out of the constructor
		props.put(PROP_CHILDLINK, this);
		Equinox equinox = new Equinox(props);
		if (!firstTime)
			// if not the first time then we are done
			return equinox;
		equinox.init();
		installParentCompanion(equinox.getBundleContext(), thisData);
		return equinox;
	}

	private void installParentCompanion(BundleContext companionContext, BundleData thisData) throws BundleException {
		File parentContent = getParentCompanionContent(thisData, null, null);
		Bundle companion;
		try {
			URL parentURL = new URL("reference:" + parentContent.toURL().toExternalForm()); //$NON-NLS-1$
			companion = companionContext.installBundle(thisData.getLocation(), parentURL.openStream());
		} catch (IOException e) {
			throw new BundleException("Error installing parent companion link bundle", e);
		}
		// disable the parent composite initially since we know we have not resolved the child yet.
		CompositeHelper.setDisabled(true, companion, companionContext);
		// set the permissions of the companion bundle
		CompositeHelper.setCompositePermissions(companion, companionContext);
	}

	private boolean updateParentCompanion(BundleData thisData, BundleDescription child, ExportPackageDescription[] matchingExports) throws BundleException {
		// update the parent content with the matching exports provided by the child
		getParentCompanionContent(thisData, child, matchingExports);
		Composite parentComposite = (Composite) getCompanionLinkBundle();
		parentComposite.updateContent();
		// enable/disable the parent composite based on if we have exports handed to us
		boolean disable = matchingExports == null ? true : false;
		CompositeHelper.setDisabled(disable, getCompanionLinkBundle(), getCompanionFramework().getBundleContext());
		// return true if we can resolve the parent composite
		return disable ? false : parentComposite.resolveContent();
	}

	private File getParentCompanionContent(BundleData thisData, BundleDescription child, ExportPackageDescription[] matchingExports) throws BundleException {
		File parentContent = thisData.getDataFile(PARENT_CONTENT);
		File manifestFile = new File(parentContent, "META-INF/MANIFEST.MF"); //$NON-NLS-1$
		manifestFile.getParentFile().mkdirs();
		String parentManifest = CompositeHelper.getParentLinkManifest(thisData.getManifest(), child, matchingExports);
		try {
			CompositeHelper.writeManifest(parentContent, parentManifest);
		} catch (IOException e) {
			throw new BundleException("Error installing parent companion link bundle", e);
		}
		return parentContent;
	}

	private LinkBundle findCompanionBundle() throws BundleException {
		if (!isParent && (companionFramework.getState() & (Bundle.INSTALLED | Bundle.RESOLVED)) != 0)
			companionFramework.init();
		return (LinkBundle) companionFramework.getBundleContext().getBundle(companionID);
	}

	public Framework getCompanionFramework() {
		return companionFramework;
	}

	public LinkBundle getCompanionLinkBundle() {
		try {
			return findCompanionBundle();
		} catch (BundleException e) {
			throw new RuntimeException("Error intializing child framework", e);
		}
	}

	public boolean isParentLink() {
		return isParent;
	}

	public void update(Map linkManifest) throws BundleException {
		if (isParentLink())
			// cannot update parent links
			throw new BundleException("Cannot update a parent link bundle", BundleException.INVALID_OPERATION);
		// validate the link manifest
		CompositeHelper.validateLinkManifest(linkManifest);
		// get the child link manifest headers
		String manifest = CompositeHelper.getChildLinkManifest(linkManifest);
		try {
			// update the data of the child manifest
			CompositeHelper.updateChildManifest(getBundleData(), manifest);
		} catch (IOException e) {
			throw new BundleException("Unable to update bundle content", e);
		}
		// first update the parent companion and disable it
		updateParentCompanion(getBundleData(), null, null);
		// update the content with the new manifest
		updateContent();
	}

	public void uninstall() throws BundleException {
		// ensure class loader is created if needed
		checkClassLoader();
		if (isParentLink()) {
			getCompanionLinkBundle().uninstall();
		} else {
			// stop first before stopping the child to let the service listener clean up
			stop(Bundle.STOP_TRANSIENT);
			stopChildFramework();
			super.uninstall();
		}
	}

	private void checkClassLoader() {
		BundleLoaderProxy proxy = getLoaderProxy();
		if (proxy != null && proxy.inUse() && proxy.getBundleLoader() != null)
			proxy.getBundleLoader().createClassLoader();
	}

	public void update() throws BundleException {
		throw new BundleException("Cannot update link bundles", BundleException.INVALID_OPERATION);
	}

	public void updateContent() throws BundleException {
		super.update();
	}

	public void update(InputStream in) throws BundleException {
		try {
			in.close();
		} catch (IOException e) {
			// ignore
		}
		throw new BundleException("Cannot update link bundles", BundleException.INVALID_OPERATION);
	}

	protected void startHook() throws BundleException {
		// do not start the framework unless we are persistently started or if this is the parent composite
		if (!isParent) {
			// always start the child framework
			companionFramework.start();
			trackerManager.startedChild();
		} else {
			((Composite) getCompanionLinkBundle()).started(this);
		}

	}

	protected void stopHook() throws BundleException {
		if (!isParent) {
			trackerManager.stoppedChild();
			// do not stop the framework unless we are persistently stopped 
			if ((bundledata.getStatus() & Constants.BUNDLE_STARTED) == 0)
				stopChildFramework();
		} else {
			((Composite) getCompanionLinkBundle()).stopped(this);
		}
	}

	public void started(LinkBundle parent) {
		if (!isParent && parent == getCompanionLinkBundle())
			trackerManager.startedParent();
	}

	public void stopped(LinkBundle parent) {
		if (!isParent && parent == getCompanionLinkBundle())
			trackerManager.stoppedParent();
	}

	private void stopChildFramework() throws BundleException {
		companionFramework.stop();
		try {
			FrameworkEvent stopped = companionFramework.waitForStop(30000);
			switch (stopped.getType()) {
				case FrameworkEvent.ERROR :
					throw new BundleException("Error stopping the child framework.", stopped.getThrowable());
				case FrameworkEvent.INFO :
					throw new BundleException("Timed out waiting for the child framework to stop.");
				case FrameworkEvent.STOPPED :
					// normal stop, just return
					return;
				default :
					throw new BundleException("Unexpected code returned when stopping the child framework:" + stopped.getType());
			}
		} catch (InterruptedException e) {
			throw new BundleException("Error stopping child framework", e);
		}
	}

	public boolean giveExports(ExportPackageDescription[] matchingExports) {
		if (matchingExports == null) {
			LinkBundle companion = getCompanionLinkBundle();
			if (isParent) {
				// set the parent to disabled to prevent resolution this go around
				CompositeHelper.setDisabled(true, getBundleDescription());
				// refresh the child bundle (in the parent framework) asynchronously and enable it
				// should only do this if the child composite is not in the process of refreshing this
				// parent composite
				if (refreshing.get() == null)
					((Composite) companion).refreshContent(false);
				return true;
			}
			// is child case:
			// disable the parent composite
			CompositeHelper.setDisabled(true, companion, getCompanionFramework().getBundleContext());
			// refresh the parent composite synchronously
			((Composite) companion).refreshContent(true);
			return true;
		}
		return validateCompanion(getBundleDescription(), matchingExports);
	}

	private boolean validateCompanion(BundleDescription linkBundle, ExportPackageDescription[] matchingExports) {
		if (isParent)
			return validExports(linkBundle, matchingExports);
		try {
			return updateParentCompanion(getBundleData(), linkBundle, matchingExports);
		} catch (BundleException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	private boolean validExports(BundleDescription linkBundle, ExportPackageDescription[] matchingExports) {
		// make sure each matching exports matches the export signature of the child composite
		Composite child = (Composite) getCompanionLinkBundle();
		BundleDescription childDesc = child.getCompositeDescription();
		ExportPackageDescription[] childExports = childDesc.getExportPackages();
		for (int i = 0; i < matchingExports.length; i++) {
			for (int j = 0; j < childExports.length; j++) {
				if (matchingExports[i].getName().equals(childExports[j].getName())) {
					if (!validateExport(matchingExports[i], childExports[j]))
						return false;
					continue;
				}
			}
		}
		return true;
	}

	private boolean validateExport(ExportPackageDescription matchingExport, ExportPackageDescription childExport) {
		Version matchingVersion = matchingExport.getVersion();
		Version childVersion = childExport.getVersion();
		if (!childVersion.equals(Version.emptyVersion) && !matchingVersion.equals(childVersion))
			return false;
		if (!ResolverBundle.equivalentMaps(childExport.getAttributes(), matchingExport.getAttributes(), false))
			return false;
		if (!ResolverBundle.equivalentMaps(childExport.getDirectives(), matchingExport.getDirectives(), false))
			return false;
		return true;
	}

	public void refreshContent(boolean synchronously) {
		if (synchronously)
			refreshing.set(Boolean.TRUE);
		try {
			framework.getPackageAdmin().refreshPackages(new Bundle[] {this}, synchronously);
		} finally {
			if (synchronously)
				refreshing.set(null);
		}
	}

	public boolean resolveContent() {
		return framework.getPackageAdmin().resolveBundles(new Bundle[] {this});
	}

	public BundleDescription getCompositeDescription() {
		return getBundleDescription();
	}

	public ClassLoaderDelegate getDelegate() {
		return getBundleLoader();
	}

	/*
	 * Listens to source and target bundles and source and target framework changes
	 */
	class ServiceTrackerManager {
		static final int CHILD_ACTIVE = 0x01;
		static final int PARENT_ACTIVE = 0x02;
		// @GuardedBy(this)
		private int bundlesActive = 0;
		// @GuardedBy(this)
		private CompositeServiceTracker shareToChildServices;
		// @GuardedBy(this)
		private CompositeServiceTracker shareToParentServices;

		void startedChild() throws BundleException {
			open(CHILD_ACTIVE);
			getCompanionLinkBundle().start(Bundle.START_TRANSIENT);
		}

		void startedParent() {
			open(PARENT_ACTIVE);
		}

		void stoppedChild() throws BundleException {
			try {
				getCompanionLinkBundle().stop(Bundle.STOP_TRANSIENT);
			} catch (BundleException e) {
				// nothing
			} catch (IllegalStateException e) {
				// child framework must have been stoped
			}
			close(CHILD_ACTIVE);
		}

		void stoppedParent() {
			close(PARENT_ACTIVE);
		}

		private synchronized void open(int bundleActive) {
			bundlesActive |= bundleActive;
			if ((bundlesActive & (CHILD_ACTIVE | PARENT_ACTIVE)) != (CHILD_ACTIVE | PARENT_ACTIVE))
				return;
			// create a service tracker to track and share services from the parent framework
			shareToChildServices = new CompositeServiceTracker(CompositeBundle.this);
			shareToChildServices.open();
			// create a service tracker to track and share services from the child framework
			shareToParentServices = new CompositeServiceTracker(getCompanionLinkBundle());
			shareToParentServices.open();

		}

		private synchronized void close(int bundleStopped) {
			bundlesActive ^= bundleStopped;
			// close the service tracker to stop tracking and sharing services
			if (shareToChildServices != null)
				shareToChildServices.close();
			if (shareToParentServices != null)
				shareToParentServices.close();
		}
	}
}
