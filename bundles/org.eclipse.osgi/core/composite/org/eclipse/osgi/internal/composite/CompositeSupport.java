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

import java.io.*;
import java.security.AllPermission;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.*;
import org.eclipse.osgi.framework.adaptor.ScopePolicy;
import org.eclipse.osgi.framework.internal.core.BundleHost;
import org.eclipse.osgi.framework.internal.core.Framework;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.*;
import org.osgi.service.composite.*;

public class CompositeSupport implements ServiceFactory {
	private static final String[] INVALID_COMPOSITE_HEADERS = new String[] {Constants.DYNAMICIMPORT_PACKAGE, Constants.IMPORT_PACKAGE, Constants.EXPORT_PACKAGE, Constants.REQUIRE_BUNDLE, Constants.FRAGMENT_HOST, Constants.BUNDLE_NATIVECODE, Constants.BUNDLE_CLASSPATH, Constants.BUNDLE_ACTIVATOR, Constants.BUNDLE_LOCALIZATION, Constants.BUNDLE_ACTIVATIONPOLICY};
	public static String COMPOSITE_CONFIGURATION = "compositeConfig.properties"; //$NON-NLS-1$

	final Framework framework;
	final CompositePolicy compositPolicy;

	public CompositeSupport(Framework framework) {
		this.framework = framework;
		this.compositPolicy = new CompositePolicy(framework);
	}

	public Object getService(Bundle bundle, ServiceRegistration registration) {
		return new EquinoxCompositeAdmin((BundleHost) bundle);
	}

	public void ungetService(Bundle bundle, ServiceRegistration registration, Object service) {
		// Nothing to do
	}

	public ScopePolicy getCompositePolicy() {
		return compositPolicy;
	}

	class EquinoxCompositeAdmin implements CompositeAdmin {
		private final BundleHost bundle;

		public EquinoxCompositeAdmin(BundleHost bundle) {
			this.bundle = bundle;
		}

		public String getFrameworkUUID() {
			// TODO Auto-generated method stub
			return null;
		}

		public CompositeBundle getParentCompositeBundle() {
			if (bundle.getCompositeId() == 0)
				return null;
			Bundle composite = framework.getBundle(bundle.getCompositeId());
			if (!(composite instanceof CompositeBundle))
				throw new IllegalStateException("Unexpected bundle type for the composite: " + composite); //$NON-NLS-1$
			return (CompositeBundle) composite;
		}

		public CompositeBundle installCompositeBundle(String location, Map compositeManifest, Map configuration) throws BundleException {
			SecurityManager sm = System.getSecurityManager();
			if (sm != null)
				// must have AllPermission to do this
				sm.checkPermission(new AllPermission());
			// make a local copy of the manifest first
			compositeManifest = new HashMap(compositeManifest);
			// make sure the manifest is valid
			CompositeSupport.validateCompositeManifest(compositeManifest);
			try {
				// get an in memory input stream to jar content of the composite we want to install
				InputStream content = CompositeSupport.getCompositeInput(configuration, compositeManifest);
				Bundle result = framework.installBundle(location, bundle.getCompositeId(), content);
				if (!(result instanceof CompositeBundle))
					throw new BundleException("A bundle is already installed at the location: " + location); //$NON-NLS-1$
				return (CompositeBundle) result;
			} catch (IOException e) {
				throw new BundleException("Error creating composite bundle", e); //$NON-NLS-1$
			}
		}
	}

	static InputStream getCompositeInput(Map frameworkConfig, Map compositeManifest) throws IOException {
		// use an in memory stream to store the content
		ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
		// the composite bundles only consist of a manifest describing the packages they import and export
		// and a framework config properties file
		Manifest manifest = CompositeSupport.getCompositeManifest(compositeManifest);
		JarOutputStream jarOut = new JarOutputStream(bytesOut, manifest);
		try {
			// store the framework config
			Properties fwProps = new Properties();
			if (frameworkConfig != null)
				fwProps.putAll(frameworkConfig);
			JarEntry entry = new JarEntry(COMPOSITE_CONFIGURATION);
			jarOut.putNextEntry(entry);
			fwProps.store(jarOut, null);
			jarOut.closeEntry();
			jarOut.flush();
		} finally {
			try {
				jarOut.close();
			} catch (IOException e) {
				// nothing
			}
		}
		return new ByteArrayInputStream(bytesOut.toByteArray());
	}

	private static Manifest getCompositeManifest(Map compositeManifest) {
		Manifest manifest = new Manifest();
		Attributes attributes = manifest.getMainAttributes();
		attributes.putValue("Manifest-Version", "1.0"); //$NON-NLS-1$//$NON-NLS-2$
		// get the common headers Bundle-ManifestVersion, Bundle-SymbolicName and Bundle-Version
		// get the manifest version from the map
		String manifestVersion = (String) compositeManifest.remove(Constants.BUNDLE_MANIFESTVERSION);
		// here we assume the validation got the correct version for us
		attributes.putValue(Constants.BUNDLE_MANIFESTVERSION, manifestVersion);
		for (Iterator entries = compositeManifest.entrySet().iterator(); entries.hasNext();) {
			Map.Entry entry = (Entry) entries.next();
			if (entry.getKey() instanceof String && entry.getValue() instanceof String)
				attributes.putValue((String) entry.getKey(), (String) entry.getValue());
		}
		return manifest;
	}

	static void validateCompositeManifest(Map compositeManifest) throws BundleException {
		if (compositeManifest == null)
			throw new BundleException("The composite manifest cannot be null.", BundleException.MANIFEST_ERROR); //$NON-NLS-1$
		// check for symbolic name
		String bsn = (String) compositeManifest.get(Constants.BUNDLE_SYMBOLICNAME);
		if (bsn == null)
			throw new BundleException("The composite manifest must contain a Bundle-SymbolicName header.", BundleException.MANIFEST_ERROR); //$NON-NLS-1$
		ManifestElement[] bsnElements = ManifestElement.parseHeader(Constants.BUNDLE_SYMBOLICNAME, bsn);
		if (bsnElements.length != 1)
			throw new BundleException("Invalid composite manifest Bundle-SymbolicName header: " + bsn, BundleException.MANIFEST_ERROR); //$NON-NLS-1$
		String composite = bsnElements[0].getDirective(CompositeConstants.COMPOSITE_DIRECTIVE);
		if (!"true".equals(composite)) //$NON-NLS-1$
			throw new BundleException("Composite manifest Bundle-SymbolicName header must set composite directive to true: " + bsn, BundleException.MANIFEST_ERROR); //$NON-NLS-1$
		// check for invalid manifests headers
		for (int i = 0; i < INVALID_COMPOSITE_HEADERS.length; i++)
			if (compositeManifest.get(INVALID_COMPOSITE_HEADERS[i]) != null)
				throw new BundleException("The composite manifest must not contain the header " + INVALID_COMPOSITE_HEADERS[i], BundleException.MANIFEST_ERROR); //$NON-NLS-1$
		// validate manifest version
		String manifestVersion = (String) compositeManifest.get(Constants.BUNDLE_MANIFESTVERSION);
		if (manifestVersion == null) {
			compositeManifest.put(Constants.BUNDLE_MANIFESTVERSION, "2"); //$NON-NLS-1$
		} else {
			try {
				Integer parsed = Integer.valueOf(manifestVersion);
				if (parsed.intValue() > 2 || parsed.intValue() < 2)
					throw new BundleException("Invalid Bundle-ManifestVersion: " + manifestVersion); //$NON-NLS-1$
			} catch (NumberFormatException e) {
				throw new BundleException("Invalid Bundle-ManifestVersion: " + manifestVersion); //$NON-NLS-1$
			}
		}
	}
}
