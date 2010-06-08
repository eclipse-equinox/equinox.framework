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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.AccessControlContext;
import java.security.AllPermission;
import java.util.*;
import org.eclipse.osgi.framework.adaptor.BundleData;
import org.eclipse.osgi.framework.debug.Debug;
import org.eclipse.osgi.framework.internal.core.*;
import org.eclipse.osgi.framework.internal.protocol.ContentHandlerFactory;
import org.eclipse.osgi.framework.internal.protocol.StreamHandlerFactory;
import org.eclipse.osgi.internal.composite.CompositeInfo.ClassSpacePolicyInfo;
import org.eclipse.osgi.internal.composite.CompositeInfo.ServicePolicyInfo;
import org.eclipse.osgi.internal.loader.BundleLoaderProxy;
import org.eclipse.osgi.internal.permadmin.SecurityAdmin;
import org.eclipse.osgi.internal.resolver.StateBuilder;
import org.eclipse.osgi.internal.resolver.StateObjectFactoryImpl;
import org.eclipse.osgi.service.resolver.*;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.*;
import org.osgi.framework.Constants;
import org.osgi.service.composite.CompositeBundle;
import org.osgi.service.composite.CompositeConstants;
import org.osgi.service.condpermadmin.ConditionalPermissionUpdate;

public class CompositeImpl extends BundleHost implements CompositeBundle {
	static final StateObjectFactory stateFactory = new StateObjectFactoryImpl();
	private final ThreadLocal<Boolean> updating = new ThreadLocal<Boolean>() {
		public Boolean initialValue() {
			return Boolean.FALSE;
		}
	};

	private final CompositeSystemBundle compositeSystemBundle;
	private final CompositeInfo compositeInfo;
	private final StartLevelManager startLevelManager;
	private final SecurityAdmin securityAdmin;
	private final StreamHandlerFactory streamHandlerFactory;
	private final ContentHandlerFactory contentHandlerFactory;
	private final List<BundleDescription> constituents = new ArrayList<BundleDescription>(0);
	private final int beginningStartLevel;
	private final Properties configuration;

	public CompositeImpl(BundleData bundledata, Framework framework, boolean setCompositeParent) throws BundleException {
		super(bundledata, framework);
		compositeSystemBundle = new CompositeSystemBundle((BundleHost) framework.getBundle(0), framework);
		compositeInfo = createCompositeInfo(setCompositeParent);
		Object existing = framework.getBundle(bundledata.getBundleID());
		if (existing != null && existing instanceof CompositeImpl) {
			checkBSNAndVersions((CompositeImpl) existing, compositeInfo);
		}

		startLevelManager = new StartLevelManager(framework, bundledata.getBundleID(), compositeSystemBundle);
		startLevelManager.initialize();
		configuration = loadCompositeConfiguration();
		beginningStartLevel = loadBeginningStartLevel(configuration);
		if (setCompositeParent) {
			try {
				securityAdmin = new SecurityAdmin(framework, framework.getAdaptor().getPermissionStorage(), bundledata.getBundleID());
			} catch (IOException e) {
				throw new BundleException("Error creating SecurityAdmin", e); //$NON-NLS-1$
			}
			streamHandlerFactory = new StreamHandlerFactory(compositeSystemBundle.getBundleContext(), framework.getAdaptor());
			contentHandlerFactory = new ContentHandlerFactory(compositeSystemBundle.getBundleContext(), framework.getAdaptor());
			framework.getStreamHandlerFactory().registerComposite(streamHandlerFactory);
			framework.getContentHandlerFactory().registerComposite(contentHandlerFactory);
		} else {
			securityAdmin = null;
			streamHandlerFactory = null;
			contentHandlerFactory = null;
		}
	}

	private void checkBSNAndVersions(CompositeImpl existing, CompositeInfo compositeInfo) throws BundleException {
		// save off original policy
		CompositeInfo original = new CompositeInfo(existing.getBundleId(), null, null, null, null, null, null, null, null, null);
		original.update(existing.compositeInfo);
		// update current policy
		existing.compositeInfo.update(compositeInfo);
		try {
			validateBSNAndVersions(existing);
		} finally {
			existing.compositeInfo.update(original);
		}
	}

	private void validateBSNAndVersions(CompositeImpl composite) throws BundleException {
		AbstractBundle[] bundles = framework.getBundles(composite.getBundleId());
		// check that we still have valid BSN/Version consistency
		for (AbstractBundle bundle : bundles) {
			if (bundle.getBundleId() == 0) // not the system bundle
				continue;
			framework.validateNameAndVersion(bundle.getBundleData());
			if (bundle instanceof CompositeImpl)
				validateBSNAndVersions((CompositeImpl) bundle);
		}
	}

	private int loadBeginningStartLevel(Properties config) {
		String level = config.getProperty(Constants.FRAMEWORK_BEGINNING_STARTLEVEL);
		if (level == null)
			return 1;
		return Integer.parseInt(level);
	}

	private Properties loadCompositeConfiguration() throws BundleException {
		Properties result = new Properties();
		URL configURL = bundledata.getEntry(CompositeSupport.COMPOSITE_CONFIGURATION);
		if (configURL == null)
			return result;
		InputStream in = null;
		try {
			in = configURL.openStream();
			result.load(configURL.openStream());
		} catch (IOException e) {
			throw new BundleException("Error loading composite configuration: " + configURL, e); //$NON-NLS-1$
		} finally {
			if (in != null)
				try {
					in.close();
				} catch (IOException e) {
					// nothing
				}
		}
		return result;
	}

	private CompositeInfo createCompositeInfo(boolean setParent) throws BundleException {
		Dictionary manifest = bundledata.getManifest();
		String importPackage = (String) manifest.get(CompositeConstants.COMPOSITE_PACKAGE_IMPORT_POLICY);
		String exportPackage = (String) manifest.get(CompositeConstants.COMPOSITE_PACKAGE_EXPORT_POLICY);
		String requireBundle = (String) manifest.get(CompositeConstants.COMPOSITE_BUNDLE_REQUIRE_POLICY);
		String provideBundle = (String) manifest.get(CompositeConstants.COMPOSITE_BUNDLE_PROVIDE_POLICY);
		String importService = (String) manifest.get(CompositeConstants.COMPOSITE_SERVICE_IMPORT_POLICY);
		String exportService = (String) manifest.get(CompositeConstants.COMPOSITE_SERVICE_EXPORT_POLICY);

		BundleContext systemContext = compositeSystemBundle.getBundleContext();

		BundleDescription desc = stateFactory.createBundleDescription(manifest, bundledata.getLocation(), bundledata.getBundleID());
		ClassSpacePolicyInfo[] imports = createClassSpacePolicy(importPackage, stateFactory, CompositeConstants.COMPOSITE_PACKAGE_IMPORT_POLICY, desc);
		ClassSpacePolicyInfo[] exports = createClassSpacePolicy(exportPackage, stateFactory, CompositeConstants.COMPOSITE_PACKAGE_EXPORT_POLICY, desc);
		ClassSpacePolicyInfo[] requires = createClassSpacePolicy(requireBundle, stateFactory, CompositeConstants.COMPOSITE_BUNDLE_REQUIRE_POLICY, desc);
		ClassSpacePolicyInfo[] provides = createClassSpacePolicy(provideBundle, stateFactory, CompositeConstants.COMPOSITE_BUNDLE_PROVIDE_POLICY, desc);
		ServicePolicyInfo[] importServiceFilter = createServicePolicyInfo(importService, stateFactory, CompositeConstants.COMPOSITE_SERVICE_IMPORT_POLICY, systemContext);
		ServicePolicyInfo[] exportServiceFilter = createServicePolicyInfo(exportService, stateFactory, CompositeConstants.COMPOSITE_SERVICE_EXPORT_POLICY, systemContext);
		// set the parent info
		CompositeInfo parentInfo = null;
		if (setParent) {
			parentInfo = framework.getCompositeSupport().compositPolicy.getCompositeInfo(bundledata.getCompositeID());
		}
		CompositeInfo result = new CompositeInfo(bundledata.getBundleID(), bundledata.getSymbolicName(), bundledata.getVersion(), parentInfo, imports, exports, requires, provides, importServiceFilter, exportServiceFilter);
		if (setParent) {
			// add the the composite info as a child of the parent.
			parentInfo.addChild(result);
		}
		return result;
	}

	public BundleContext getSystemBundleContext() {
		if (getState() == Bundle.UNINSTALLED)
			return null;
		return compositeSystemBundle.getBundleContext();
	}

	public void update() throws BundleException {
		throw new BundleException("Must update a composite with the update(Map) method.", BundleException.UNSUPPORTED_OPERATION); //$NON-NLS-1$
	}

	public void update(InputStream in) throws BundleException {
		if (in == RELOAD) {
			super.update(RELOAD);
			return;
		}
		try {
			in.close();
		} catch (IOException e) {
			// do nothing
		}
		update();
	}

	public void update(Map<String, String> compositeManifest) throws BundleException {
		if (Debug.DEBUG && Debug.DEBUG_GENERAL) {
			Debug.println("update location " + bundledata.getLocation()); //$NON-NLS-1$
			Debug.println("   from: " + compositeManifest); //$NON-NLS-1$
		}
		SecurityManager sm = System.getSecurityManager();
		if (sm != null)
			// must have AllPermission to do this
			sm.checkPermission(new AllPermission());
		// make a local copy of the manifest first
		compositeManifest = new HashMap<String, String>(compositeManifest);
		// make sure the manifest is valid
		CompositeSupport.validateCompositeManifest(compositeManifest);
		try {
			URL configURL = bundledata.getEntry(CompositeSupport.COMPOSITE_CONFIGURATION);
			Properties config = new Properties();
			config.load(configURL.openStream());
			// get an in memory input stream to jar content of the composite we want to install
			InputStream content = CompositeSupport.getCompositeInput(config, compositeManifest);
			updating.set(true);
			// update with the new content
			super.update(content);
		} catch (IOException e) {
			throw new BundleException("Error creating composite bundle", e); //$NON-NLS-1$
		} finally {
			updating.set(false);
		}
	}

	@Override
	protected void updateWorkerPrivileged(URLConnection source, AccessControlContext callerContext) throws BundleException {
		CompositeInfo originalInfo = createCompositeInfo(false);
		super.updateWorkerPrivileged(source, callerContext);
		// update the composite info with the new data.
		CompositeInfo updatedInfo = createCompositeInfo(false);
		compositeInfo.update(updatedInfo);
		AbstractBundle[] bundles = framework.getBundles(getBundleId());
		// reload all the constituents 
		for (AbstractBundle bundle : bundles) {
			if (bundle.getBundleId() == 0) // not the system bundle
				continue;
			try {
				bundle.reload();
			} catch (BundleException e) {
				framework.publishFrameworkEvent(FrameworkEvent.ERROR, bundle, e);
			}
		}
	}

	protected void startHook() {
		startLevelManager.doSetStartLevel(beginningStartLevel);
	}

	protected void stopHook() {
		if (updating.get())
			startLevelManager.update();
		else
			startLevelManager.shutdown();
	}

	public void uninstallWorkerPrivileged() throws BundleException {
		// uninstall the composite first to invalidate the context
		super.uninstallWorkerPrivileged();
		Bundle[] bundles = framework.getBundles(getBundleId());
		// uninstall all the constituents 
		for (int i = 0; i < bundles.length; i++) {
			if (bundles[i].getBundleId() != 0) // not the system bundle
				try {
					bundles[i].uninstall();
				} catch (BundleException e) {
					framework.publishFrameworkEvent(FrameworkEvent.ERROR, bundles[i], e);
				}
		}
		// clean up persistent storage associated with this composite
		framework.getAdaptor().setInitialBundleStartLevel(getBundleId(), -1);
		securityAdmin.setDefaultPermissions(null);
		String[] locations = securityAdmin.getLocations();
		if (locations != null)
			for (int i = 0; i < locations.length; i++)
				securityAdmin.setPermissions(locations[i], null);
		ConditionalPermissionUpdate update = securityAdmin.newConditionalPermissionUpdate();
		update.getConditionalPermissionInfos().clear();
		update.commit();
	}

	protected void close() {
		super.close();
		startLevelManager.cleanup();
		// remove the composite info from the parent
		compositeInfo.orphaned();
		// unregister the composite factory before closing the system bundle
		getFramework().getStreamHandlerFactory().unregisterComposite(streamHandlerFactory);
		getFramework().getContentHandlerFactory().unregisterComposite(contentHandlerFactory);
		compositeSystemBundle.close();
	}

	public class CompositeSystemBundle extends InternalSystemBundle {
		private final BundleHost rootSystemBundle;

		public CompositeSystemBundle(BundleHost systemBundle, Framework framework) throws BundleException {
			super(systemBundle.getBundleData(), framework);
			this.rootSystemBundle = systemBundle;
			this.state = Bundle.STARTING; // Initial state must be STARTING for composite system bundle
		}

		protected BundleContextImpl createContext() {
			CompositeContext compositeContext = new CompositeContext(this);
			return compositeContext;
		}

		public ServiceReference[] getRegisteredServices() {
			// TODO this is not scoped; do we care?
			return rootSystemBundle.getRegisteredServices();
		}

		public ServiceReference[] getServicesInUse() {
			return rootSystemBundle.getServicesInUse();
		}

		public void start(int options) throws BundleException {
			throw new BundleException("Cannot start a composite system bundle.", BundleException.UNSUPPORTED_OPERATION); //$NON-NLS-1$
		}

		public void start() throws BundleException {
			start(0);
		}

		public void stop(int options) throws BundleException {
			throw new BundleException("Cannot stop a composite system bundle.", BundleException.UNSUPPORTED_OPERATION); //$NON-NLS-1$
		}

		public void stop() throws BundleException {
			stop(0);
		}

		public void uninstall() throws BundleException {
			throw new BundleException("Cannot uninstall a composite system bundle.", BundleException.UNSUPPORTED_OPERATION); //$NON-NLS-1$
		}

		public void update(InputStream input) throws BundleException {
			throw new BundleException("Cannot update a composite system bundle.", BundleException.UNSUPPORTED_OPERATION); //$NON-NLS-1$
		}

		public void update() throws BundleException {
			update(null);
		}

		public long getCompositeId() {
			return CompositeImpl.this.getBundleId();
		}

		public BundleLoaderProxy getLoaderProxy() {
			return rootSystemBundle.getLoaderProxy();
		}

		protected void close() {
			super.close();
			this.state = Bundle.UNINSTALLED;
		}

	}

	public class CompositeContext extends BundleContextImpl {

		protected CompositeContext(BundleHost bundle) {
			super(bundle);
		}

		protected long getCompositeId() {
			return CompositeImpl.this.getBundleId();
		}

		protected void start() {
			// nothing;
		}

		protected void stop() {
			// nothing
		}
	}

	public StartLevelManager getStartLevelService() {
		return startLevelManager;
	}

	public SecurityAdmin getSecurityAdmin() {
		return securityAdmin;
	}

	public BundleHost getSystemBundle() {
		return compositeSystemBundle;
	}

	public String getProperty(String key) {
		SecurityManager sm = System.getSecurityManager();
		if (sm != null)
			sm.checkPropertyAccess(key);
		return configuration.getProperty(key, FrameworkProperties.getProperty(key));
	}

	public String setProperty(String key, String value) {
		SecurityManager sm = System.getSecurityManager();
		if (sm != null)
			sm.checkPermission(new PropertyPermission(key, "write")); //$NON-NLS-1$
		return (String) configuration.setProperty(key, value);
	}

	protected void refresh() {
		super.refresh();
		loadConstituents();
	}

	void loadConstituents() {
		synchronized (constituents) {
			constituents.clear();
			AbstractBundle[] bundles = framework.getBundles(getBundleId());
			for (int i = 0; i < bundles.length; i++) {
				if (bundles[i].getBundleId() == 0)
					continue;
				BundleDescription constituent = bundles[i].getBundleDescription();
				if (constituent != null)
					constituents.add(constituent);
			}
		}
	}

	void addConstituent(BundleDescription description) {
		synchronized (constituents) {
			constituents.add(description);
		}
	}

	BundleDescription[] getConstituentDescriptions() {
		synchronized (constituents) {
			return constituents.toArray(new BundleDescription[constituents.size()]);
		}
	}

	private static ClassSpacePolicyInfo[] createClassSpacePolicy(String policySpec, StateObjectFactory factory, String header, BundleDescription desc) throws BundleException {
		ManifestElement[] policy = ManifestElement.parseHeader(header, policySpec);
		if (policy == null || policy.length == 0)
			return null;
		ArrayList<ClassSpacePolicyInfo> result = new ArrayList<ClassSpacePolicyInfo>(policy.length);
		for (int i = 0; i < policy.length; i++) {
			String compositeAffinityName = policy[i].getDirective(CompositeConstants.COMPOSITE_SYMBOLICNAME_DIRECTIVE);
			VersionRange compositeAffinityVersion = StateBuilder.getVersionRange(policy[i].getDirective(CompositeConstants.COMPOSITE_VERSION_DIRECTIVE));
			if (header == CompositeConstants.COMPOSITE_PACKAGE_IMPORT_POLICY || header == CompositeConstants.COMPOSITE_PACKAGE_EXPORT_POLICY) {
				VersionRange versionRange = StateBuilder.getVersionRange(policy[i].getAttribute(Constants.VERSION_ATTRIBUTE));

				String bundleSymbolicName = policy[i].getAttribute(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE);
				VersionRange bundleVersionRange = StateBuilder.getVersionRange(policy[i].getAttribute(Constants.BUNDLE_VERSION_ATTRIBUTE));

				Map attributes = StateBuilder.getAttributes(policy[i], StateBuilder.DEFINED_MATCHING_ATTRS);

				String[] packageNames = policy[i].getValueComponents();
				for (int j = 0; j < packageNames.length; j++) {
					ImportPackageSpecification importSpec = factory.createImportPackageSpecification(packageNames[j], versionRange, bundleSymbolicName, bundleVersionRange, null, attributes, desc);
					result.add(new ClassSpacePolicyInfo(compositeAffinityName, compositeAffinityVersion, importSpec));
				}
			} else {
				String bsn = policy[i].getValue();
				VersionRange bundleVersion = StateBuilder.getVersionRange(policy[i].getAttribute(Constants.BUNDLE_VERSION_ATTRIBUTE));
				result.add(new ClassSpacePolicyInfo(compositeAffinityName, compositeAffinityVersion, factory.createBundleSpecification(bsn, bundleVersion, false, false)));
			}
		}
		return result.toArray(new ClassSpacePolicyInfo[result.size()]);
	}

	private static ServicePolicyInfo[] createServicePolicyInfo(String policySpec, StateObjectFactory factory, String header, BundleContext context) throws BundleException {
		ManifestElement[] policy = ManifestElement.parseHeader(header, policySpec);
		if (policy == null || policy.length == 0)
			return null;
		ArrayList<ServicePolicyInfo> result = new ArrayList<ServicePolicyInfo>(policy.length);
		for (int i = 0; i < policy.length; i++) {
			String compositeAffinityName = policy[i].getDirective(CompositeConstants.COMPOSITE_SYMBOLICNAME_DIRECTIVE);
			VersionRange compositeAffinityVersion = StateBuilder.getVersionRange(policy[i].getDirective(CompositeConstants.COMPOSITE_VERSION_DIRECTIVE));
			String[] filters = policy[i].getValueComponents();
			for (int j = 0; j < filters.length; j++) {
				Filter filter = null;
				try {
					filter = context.createFilter(filters[j]);
				} catch (InvalidSyntaxException e) {
					throw new BundleException("Invalid service sharing policy: " + filters[j], BundleException.MANIFEST_ERROR, e); //$NON-NLS-1$
				}
				result.add(new ServicePolicyInfo(compositeAffinityName, compositeAffinityVersion, filter));
			}
		}
		return result.toArray(new ServicePolicyInfo[result.size()]);
	}
}
