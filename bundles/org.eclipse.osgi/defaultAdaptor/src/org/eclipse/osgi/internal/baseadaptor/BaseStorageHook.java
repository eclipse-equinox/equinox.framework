/*******************************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.osgi.internal.baseadaptor;

import java.io.*;
import java.util.Dictionary;
import org.eclipse.core.runtime.adaptor.LocationManager;
import org.eclipse.core.runtime.internal.adaptor.EclipseStorageHook;
import org.eclipse.osgi.baseadaptor.*;
import org.eclipse.osgi.baseadaptor.hooks.StorageHook;
import org.eclipse.osgi.framework.adaptor.*;
import org.eclipse.osgi.framework.debug.Debug;
import org.eclipse.osgi.framework.internal.core.*;
import org.eclipse.osgi.framework.util.KeyedElement;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

public class BaseStorageHook implements StorageHook {
	public static final String KEY = BaseStorageHook.class.getName();
	public static final int HASHCODE = KEY.hashCode();
	public static final int DEL_BUNDLE_STORE = 0x01;
	public static final int DEL_GENERATION = 0x02;
	private static final int STORAGE_VERION = 1;

	/** bundle's file name */
	private String fileName;
	/** native code paths for this BundleData */
	private String[] nativePaths;
	/** bundle generation */
	private int generation = 1;
	/** Is bundle a reference */
	private boolean reference;

	private BaseData bundledata;
	private BaseStorage storage;
	private File bundleStore;
	private File dataStore;

	public BaseStorageHook(BaseStorage storage) {
		this.storage = storage;
	}

	public int getStorageVersion() {
		return STORAGE_VERION;
	}

	public StorageHook create(BaseData bundledata) throws BundleException {
		BaseStorageHook storageHook = new BaseStorageHook(storage);
		storageHook.bundledata = bundledata;
		return storageHook;
	}

	public void initialize(Dictionary manifest) throws BundleException {
		BaseStorageHook.loadManifest(bundledata, manifest);
	}

	static void loadManifest(BaseData bundledata, Dictionary manifest) throws BundleException {
		try {
			bundledata.setVersion(Version.parseVersion((String) manifest.get(Constants.BUNDLE_VERSION)));
		} catch (IllegalArgumentException e) {
			bundledata.setVersion(new InvalidVersion((String) manifest.get(Constants.BUNDLE_VERSION)));
		}
		ManifestElement[] bsnHeader = ManifestElement.parseHeader(Constants.BUNDLE_SYMBOLICNAME, (String) manifest.get(Constants.BUNDLE_SYMBOLICNAME));
		int bundleType = 0;
		if (bsnHeader != null) {
			bundledata.setSymbolicName(bsnHeader[0].getValue());
			String singleton = bsnHeader[0].getDirective(Constants.SINGLETON_DIRECTIVE);
			if (singleton == null)
				singleton = bsnHeader[0].getAttribute(Constants.SINGLETON_DIRECTIVE);
			if ("true".equals(singleton)) //$NON-NLS-1$
				bundleType |= BundleData.TYPE_SINGLETON;
		}
		bundledata.setClassPathString((String) manifest.get(Constants.BUNDLE_CLASSPATH));
		bundledata.setActivator((String) manifest.get(Constants.BUNDLE_ACTIVATOR));
		String host = (String) manifest.get(Constants.FRAGMENT_HOST);
		if (host != null) {
			bundleType |= BundleData.TYPE_FRAGMENT;
			ManifestElement[] hostElement = ManifestElement.parseHeader(Constants.FRAGMENT_HOST, host);
			if (Constants.getInternalSymbolicName().equals(hostElement[0].getValue()) || Constants.OSGI_SYSTEM_BUNDLE.equals(hostElement[0].getValue())) {
				String extensionType = hostElement[0].getDirective("extension"); //$NON-NLS-1$
				if (extensionType == null || extensionType.equals("framework")) //$NON-NLS-1$
					bundleType |= BundleData.TYPE_FRAMEWORK_EXTENSION;
				else
					bundleType |= BundleData.TYPE_BOOTCLASSPATH_EXTENSION;
			}
		}
		bundledata.setType(bundleType);
		bundledata.setExecutionEnvironment((String) manifest.get(Constants.BUNDLE_REQUIREDEXECUTIONENVIRONMENT));
		bundledata.setDynamicImports((String) manifest.get(Constants.DYNAMICIMPORT_PACKAGE));
	}

	public StorageHook load(BaseData bundledata, DataInputStream in) throws IOException {
		BaseStorageHook storageHook = new BaseStorageHook(storage);
		storageHook.bundledata = bundledata;
		storageHook.bundledata.setLocation(AdaptorUtil.readString(in, false));
		storageHook.bundledata.setSymbolicName(AdaptorUtil.readString(in, false));
		storageHook.bundledata.setVersion(AdaptorUtil.loadVersion(in));
		storageHook.bundledata.setActivator(AdaptorUtil.readString(in, false));
		storageHook.bundledata.setClassPathString(AdaptorUtil.readString(in, false));
		storageHook.bundledata.setExecutionEnvironment(AdaptorUtil.readString(in, false));
		storageHook.bundledata.setDynamicImports(AdaptorUtil.readString(in, false));
		storageHook.bundledata.setStartLevel(in.readInt());
		storageHook.bundledata.setStatus(in.readInt());
		storageHook.bundledata.setType(in.readInt());
		storageHook.bundledata.setLastModified(in.readLong());
		storageHook.bundledata.setDirty(false); // make sure to reset the dirty bit;

		storageHook.generation = in.readInt();
		storageHook.reference = in.readBoolean();
		storageHook.fileName = getAbsolute(storageHook.reference, AdaptorUtil.readString(in, false));
		int nativePathCount = in.readInt();
		storageHook.nativePaths = nativePathCount > 0 ? new String[nativePathCount] : null;
		for (int i = 0; i < nativePathCount; i++)
			storageHook.nativePaths[i] = in.readUTF();
		return storageHook;
	}

	private String getAbsolute(boolean isReference, String path) {
		if (!isReference)
			return path;
		// fileName for bundles installed with reference URLs is stored relative to the install location
		File storedPath = new File(path);
		if (!storedPath.isAbsolute())
			// make sure it has the absolute location instead
			return new FilePath(storage.getInstallPath() + path).toString();
		return path;
	}

	public void save(DataOutputStream out) throws IOException {
		if (bundledata == null)
			throw new IllegalStateException();
		AdaptorUtil.writeStringOrNull(out, bundledata.getLocation());
		AdaptorUtil.writeStringOrNull(out, bundledata.getSymbolicName());
		AdaptorUtil.writeStringOrNull(out, bundledata.getVersion().toString());
		AdaptorUtil.writeStringOrNull(out, bundledata.getActivator());
		AdaptorUtil.writeStringOrNull(out, bundledata.getClassPathString());
		AdaptorUtil.writeStringOrNull(out, bundledata.getExecutionEnvironment());
		AdaptorUtil.writeStringOrNull(out, bundledata.getDynamicImports());
		out.writeInt(bundledata.getStartLevel());
		/* TODO this is an unfortunate reference to EclipseStorageHook; 
		 * not sure how to get rid of it without adding very specific hook api 
		 */
		EclipseStorageHook extraHook = (EclipseStorageHook) bundledata.getStorageHook(EclipseStorageHook.KEY);
		out.writeInt(extraHook == null || !extraHook.isAutoStartable() ? bundledata.getStatus() : (~Constants.BUNDLE_STARTED) & bundledata.getStatus());
		out.writeInt(bundledata.getType());
		out.writeLong(bundledata.getLastModified());

		out.writeInt(getGeneration());
		out.writeBoolean(isReference());
		String storedFileName = isReference() ? new FilePath(storage.getInstallPath()).makeRelative(new FilePath(getFileName())) : getFileName();
		AdaptorUtil.writeStringOrNull(out, storedFileName);
		if (nativePaths == null)
			out.writeInt(0);
		else {
			out.writeInt(nativePaths.length);
			for (int i = 0; i < nativePaths.length; i++)
				out.writeUTF(nativePaths[i]);
		}

	}

	public int getKeyHashCode() {
		return HASHCODE;
	}

	public boolean compare(KeyedElement other) {
		return other.getKey() == KEY;
	}

	public Object getKey() {
		return KEY;
	}

	public String getFileName() {
		return fileName;
	}

	public int getGeneration() {
		return generation;
	}

	public String[] getNativePaths() {
		return nativePaths;
	}

	public void setNativePaths(String[] nativePaths) {
		this.nativePaths = nativePaths;
	}

	public boolean isReference() {
		return reference;
	}

	public File getBundleStore() {
		if (bundleStore == null)
			bundleStore = new File(storage.getBundleStoreRoot(), String.valueOf(bundledata.getBundleID()));
		return bundleStore;
	}

	public File getDataFile(String path) {
		// lazily initialize dirData to prevent early access to configuration location
		if (dataStore == null)
			dataStore = new File(getBundleStore(), BaseStorage.DATA_DIR_NAME);
		if (path != null && !dataStore.exists() && (storage.isReadOnly() || !dataStore.mkdirs()))
			if (Debug.DEBUG && Debug.DEBUG_GENERAL)
				Debug.println("Unable to create bundle data directory: " + dataStore.getPath()); //$NON-NLS-1$
		return path == null ? dataStore : new File(dataStore, path);
	}

	void delete(boolean postpone, int type) throws IOException {
		File delete = null;
		switch (type) {
			case DEL_GENERATION :
				delete = getGenerationDir();
				break;
			case DEL_BUNDLE_STORE :
				delete = getBundleStore();
				break;
		}
		if (delete != null && delete.exists() && (postpone || !AdaptorUtil.rm(delete))) {
			/* create .delete */
			FileOutputStream out = new FileOutputStream(new File(delete, ".delete")); //$NON-NLS-1$
			out.close();
		}
	}

	File getGenerationDir() {
		return new File(getBundleStore(), String.valueOf(getGeneration()));
	}

	File getParentGenerationDir() {
		Location parentConfiguration = null;
		Location currentConfiguration = LocationManager.getConfigurationLocation();
		if (currentConfiguration != null && (parentConfiguration = currentConfiguration.getParentLocation()) != null)
			return new File(parentConfiguration.getURL().getFile(), FrameworkAdaptor.FRAMEWORK_SYMBOLICNAME + '/' + LocationManager.BUNDLES_DIR + '/' + bundledata.getBundleID() + '/' + getGeneration());
		return null;
	}

	File createGenerationDir() {
		File generationDir = getGenerationDir();
		if (!generationDir.exists() && (storage.isReadOnly() || !generationDir.mkdirs()))
			if (Debug.DEBUG && Debug.DEBUG_GENERAL)
				Debug.println("Unable to create bundle generation directory: " + generationDir.getPath()); //$NON-NLS-1$
		return generationDir;
	}

	public void setReference(boolean reference) {
		this.reference = reference;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public void copy(StorageHook storageHook) {
		if (!(storageHook instanceof BaseStorageHook))
			throw new IllegalArgumentException();
		BaseStorageHook hook = (BaseStorageHook) storageHook;
		bundleStore = hook.bundleStore;
		dataStore = hook.dataStore;
		generation = hook.generation + 1;
		// fileName and reference will be set by update
	}

	public void validate() throws IllegalArgumentException {
		// do nothing
	}

	public Dictionary getManifest(boolean firstLoad) throws BundleException {
		// do nothing
		return null;
	}
}
