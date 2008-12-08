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
import java.util.*;
import java.util.Map.Entry;
import org.eclipse.osgi.baseadaptor.BaseData;
import org.eclipse.osgi.framework.adaptor.BundleData;
import org.eclipse.osgi.internal.baseadaptor.BaseStorageHook;
import org.eclipse.osgi.service.resolver.*;
import org.osgi.framework.*;
import org.osgi.service.permissionadmin.PermissionAdmin;
import org.osgi.service.permissionadmin.PermissionInfo;

public class CompositeHelper {
	private static final PermissionInfo[] COMPOSITE_PERMISSIONS = new PermissionInfo[] {new PermissionInfo(PackagePermission.class.getName(), "*", PackagePermission.EXPORT), new PermissionInfo(ServicePermission.class.getName(), "*", ServicePermission.REGISTER + ',' + ServicePermission.GET)}; //$NON-NLS-1$ //$NON-NLS-2$
	private static final String COMPOSITE_POLICY = "org.eclipse.osgi.composite"; //$NON-NLS-1$
	private static String HEADER_SEPARATOR = ": "; //$NON-NLS-1$
	private static String ELEMENT_SEPARATOR = "; "; //$NON-NLS-1$
	private static final Object EQUALS_QUOTE = "=\""; //$NON-NLS-1$

	static String getChildLinkManifest(Map childManifest) {
		// get the common headers Bundle-ManifestVersion, Bundle-SymbolicName and Bundle-Version
		StringBuffer manifest = new StringBuffer();
		// Ignore the manifest version from the map
		childManifest.remove(Constants.BUNDLE_MANIFESTVERSION);
		// always use bundle manifest version 2
		manifest.append(Constants.BUNDLE_MANIFESTVERSION).append(": 2\n"); //$NON-NLS-1$
		// Ignore the Equinox composite bundle header
		childManifest.remove(BaseStorageHook.COMPOSITE_HEADER);
		manifest.append(BaseStorageHook.COMPOSITE_HEADER).append(HEADER_SEPARATOR).append(BaseStorageHook.COMPOSITE_BUNDLE_CHILD).append('\n');
		for (Iterator entries = childManifest.entrySet().iterator(); entries.hasNext();) {
			Map.Entry entry = (Entry) entries.next();
			manifest.append(entry.getKey()).append(HEADER_SEPARATOR).append(entry.getValue()).append('\n');
		}
		return manifest.toString();
	}

	static String getParentLinkManifest(Dictionary childManifest, BundleDescription child, ExportPackageDescription[] matchingExports) throws BundleException {
		// get the common headers Bundle-ManifestVersion, Bundle-SymbolicName and Bundle-Version
		StringBuffer manifest = new StringBuffer();
		// Ignore the manifest version from the map
		// always use bundle manifest version 2
		manifest.append(Constants.BUNDLE_MANIFESTVERSION).append(": 2\n"); //$NON-NLS-1$
		// Ignore the Equinox composite bundle header
		manifest.append(BaseStorageHook.COMPOSITE_HEADER).append(HEADER_SEPARATOR).append(BaseStorageHook.COMPOSITE_BUNDLE_PARENT).append('\n');

		if (child != null && matchingExports != null) {
			// convert the exports from the child composite into imports
			addImports(manifest, child, matchingExports);

			// convert the matchingExports from the child composite into exports
			addExports(manifest, matchingExports);
		}

		// add the rest
		for (Enumeration keys = childManifest.keys(); keys.hasMoreElements();) {
			Object header = keys.nextElement();
			if (Constants.BUNDLE_MANIFESTVERSION.equals(header) || BaseStorageHook.COMPOSITE_HEADER.equals(header) || Constants.IMPORT_PACKAGE.equals(header) || Constants.EXPORT_PACKAGE.equals(header))
				continue;
			manifest.append(header).append(HEADER_SEPARATOR).append(childManifest.get(header)).append('\n');
		}
		return manifest.toString();
	}

	private static void addImports(StringBuffer manifest, BundleDescription child, ExportPackageDescription[] matchingExports) {
		ExportPackageDescription[] exports = child.getExportPackages();
		List systemExports = getSystemExports(matchingExports);
		if (exports.length == 0 && systemExports.size() == 0)
			return;
		manifest.append(Constants.IMPORT_PACKAGE).append(HEADER_SEPARATOR);
		Collection importedNames = new ArrayList(exports.length);
		int i = 0;
		for (; i < exports.length; i++) {
			if (i != 0)
				manifest.append(',').append('\n').append(' ');
			importedNames.add(exports[i].getName());
			getImportFrom(exports[i], manifest);
		}
		for (Iterator iSystemExports = systemExports.iterator(); iSystemExports.hasNext();) {
			ExportPackageDescription systemExport = (ExportPackageDescription) iSystemExports.next();
			if (!importedNames.contains(systemExport.getName())) {
				if (i != 0)
					manifest.append(',').append('\n').append(' ');
				i++;
				manifest.append(systemExport.getName()).append(ELEMENT_SEPARATOR).append(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE).append('=').append(Constants.SYSTEM_BUNDLE_SYMBOLICNAME);
			}
		}
		manifest.append('\n');
	}

	private static List getSystemExports(ExportPackageDescription[] matchingExports) {
		ArrayList list = null;
		for (int i = 0; i < matchingExports.length; i++) {
			if (matchingExports[i].getExporter().getBundleId() != 0)
				continue;
			if (list == null)
				list = new ArrayList();
			list.add(matchingExports[i]);
		}
		return list == null ? Collections.EMPTY_LIST : list;
	}

	private static void getImportFrom(ExportPackageDescription export, StringBuffer manifest) {
		manifest.append(export.getName()).append(ELEMENT_SEPARATOR);
		Version version = export.getVersion();
		manifest.append(Constants.VERSION_ATTRIBUTE).append(EQUALS_QUOTE).append('[').append(version).append(',').append(new Version(version.getMajor(), version.getMinor(), version.getMicro() + 1)).append(')').append('\"');
		addMap(manifest, export.getAttributes(), "="); //$NON-NLS-1$
	}

	private static void addExports(StringBuffer manifest, ExportPackageDescription[] matchingExports) {
		if (matchingExports.length == 0)
			return;
		manifest.append(Constants.EXPORT_PACKAGE).append(HEADER_SEPARATOR);
		for (int i = 0; i < matchingExports.length; i++) {
			if (i != 0)
				manifest.append(',').append('\n').append(' ');
			getExportFrom(matchingExports[i], manifest);
		}
		manifest.append('\n');
	}

	private static void getExportFrom(ExportPackageDescription export, StringBuffer manifest) {
		manifest.append(export.getName()).append(ELEMENT_SEPARATOR);
		manifest.append(Constants.VERSION_ATTRIBUTE).append(EQUALS_QUOTE).append(export.getVersion()).append('\"');
		addMap(manifest, export.getDirectives(), ":="); //$NON-NLS-1$
		addMap(manifest, export.getAttributes(), "="); //$NON-NLS-1$
	}

	private static void addMap(StringBuffer manifest, Map values, String assignment) {
		if (values == null)
			return; // nothing to add
		for (Iterator iEntries = values.entrySet().iterator(); iEntries.hasNext();) {
			manifest.append(ELEMENT_SEPARATOR);
			Map.Entry entry = (Entry) iEntries.next();
			manifest.append(entry.getKey()).append(assignment).append('\"');
			Object value = entry.getValue();
			if (value instanceof String[]) {
				String[] strings = (String[]) value;
				for (int i = 0; i < strings.length; i++) {
					if (i != 0)
						manifest.append(',');
					manifest.append(strings[i]);
				}
			} else {
				manifest.append(value);
			}
			manifest.append('\"');
		}
	}

	static void setCompositePermissions(Bundle bundle, BundleContext systemContext) {
		ServiceReference ref = systemContext.getServiceReference(PermissionAdmin.class.getName());
		PermissionAdmin permAdmin = (PermissionAdmin) (ref == null ? null : systemContext.getService(ref));
		if (permAdmin == null)
			throw new RuntimeException("No Permission Admin service is available");
		try {
			permAdmin.setPermissions(bundle.getLocation(), COMPOSITE_PERMISSIONS);
		} finally {
			systemContext.ungetService(ref);
		}
	}

	static void setDisabled(boolean disable, Bundle companion, BundleContext companionContext) {
		ServiceReference ref = companionContext.getServiceReference(PlatformAdmin.class.getName());
		PlatformAdmin pa = (PlatformAdmin) (ref == null ? null : companionContext.getService(ref));
		if (pa == null)
			throw new RuntimeException("No Platform Admin service is available.");
		try {
			State state = pa.getState(false);
			BundleDescription desc = state.getBundle(companion.getBundleId());
			setDisabled(disable, desc);
		} finally {
			companionContext.ungetService(ref);
		}
	}

	public static void setDisabled(boolean disable, BundleDescription bundle) {
		State state = bundle.getContainingState();
		if (disable) {
			state.addDisabledInfo(new DisabledInfo(COMPOSITE_POLICY, "Composite companion bundle is not resolved.", bundle));
		} else {
			DisabledInfo toRemove = state.getDisabledInfo(bundle, COMPOSITE_POLICY);
			if (toRemove != null)
				state.removeDisabledInfo(toRemove);
		}
	}

	static void writeManifest(File baseBundleFile, String manifest) throws IOException {
		File manifestFile = new File(baseBundleFile, "META-INF/MANIFEST.MF"); //$NON-NLS-1$
		manifestFile.getParentFile().mkdirs();
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(new FileOutputStream(manifestFile));
			pw.write(manifest);
		} finally {
			if (pw != null)
				pw.close();
		}
	}

	static void updateChildManifest(BundleData childData, String manifest) throws IOException {
		File baseFile = ((BaseData) childData).getBundleFile().getBaseFile();
		if (!baseFile.isDirectory())
			throw new RuntimeException("Base bundle file must be a directory");
		writeManifest(baseFile, manifest);
	}

	static void validateLinkManifest(Map linkManifest) throws BundleException {
		if (linkManifest == null)
			throw new BundleException("The link manifest cannot be null.", BundleException.MANIFEST_ERROR);
		if (linkManifest.get(Constants.BUNDLE_SYMBOLICNAME) == null)
			throw new BundleException("The link manifest must contain a Bundle-SymbolicName header.", BundleException.MANIFEST_ERROR);
	}
}
