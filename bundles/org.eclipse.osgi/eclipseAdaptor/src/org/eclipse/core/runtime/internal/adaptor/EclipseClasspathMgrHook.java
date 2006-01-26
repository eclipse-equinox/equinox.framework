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

package org.eclipse.core.runtime.internal.adaptor;

import java.io.File;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.eclipse.core.runtime.internal.stats.*;
import org.eclipse.osgi.baseadaptor.*;
import org.eclipse.osgi.baseadaptor.bundlefile.BundleEntry;
import org.eclipse.osgi.baseadaptor.bundlefile.BundleFile;
import org.eclipse.osgi.baseadaptor.hooks.AdaptorHook;
import org.eclipse.osgi.baseadaptor.hooks.ClasspathManagerHook;
import org.eclipse.osgi.baseadaptor.loader.ClasspathEntry;
import org.eclipse.osgi.baseadaptor.loader.ClasspathManager;
import org.eclipse.osgi.framework.adaptor.FrameworkAdaptor;
import org.eclipse.osgi.framework.internal.core.*;
import org.eclipse.osgi.framework.log.FrameworkLogEntry;
import org.eclipse.osgi.internal.baseadaptor.DevClassPathHelper;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.*;

public class EclipseClasspathMgrHook implements ClasspathManagerHook, HookConfigurator {
	private static String[] NL_JAR_VARIANTS = buildNLJarVariants(EclipseEnvironmentInfo.getDefault().getNL());
	private static boolean DEFINE_PACKAGES;
	private static final String VARIABLE_DELIM_STRING = "$"; //$NON-NLS-1$
	private static final char VARIABLE_DELIM_CHAR = '$';
	private static final String EXTERNAL_LIB_PREFIX = "external:"; //$NON-NLS-1$
	private static String[] LIB_VARIENTS = buildLibraryVariants();

	static {
		try {
			Class.forName("java.lang.Package"); //$NON-NLS-1$
			DEFINE_PACKAGES = true;
		} catch (ClassNotFoundException e) {
			DEFINE_PACKAGES = false;
		}
	}

	private static String[] buildLibraryVariants() {
		ArrayList result = new ArrayList();
		EclipseEnvironmentInfo info = EclipseEnvironmentInfo.getDefault();
		result.add("ws/" + info.getWS() + "/"); //$NON-NLS-1$ //$NON-NLS-2$
		result.add("os/" + info.getOS() + "/" + info.getOSArch() + "/"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		result.add("os/" + info.getOS() + "/"); //$NON-NLS-1$ //$NON-NLS-2$
		String nl = info.getNL();
		nl = nl.replace('_', '/');
		while (nl.length() > 0) {
			result.add("nl/" + nl + "/"); //$NON-NLS-1$ //$NON-NLS-2$
			int i = nl.lastIndexOf('/');
			nl = (i < 0) ? "" : nl.substring(0, i); //$NON-NLS-1$
		}
		result.add(""); //$NON-NLS-1$
		return (String[]) result.toArray(new String[result.size()]);
	}

	public void preFindLocalClass(String name, ClasspathManager manager) throws ClassNotFoundException {
		if (StatsManager.MONITOR_CLASSES) //Suport for performance analysis
			ClassloaderStats.startLoadingClass(getClassloaderId(manager), name);
		AbstractBundle bundle = (AbstractBundle) manager.getBaseData().getBundle();
		// If the bundle is active, uninstalled or stopping then the bundle has already
		// been initialized (though it may have been destroyed) so just return the class.
		if ((bundle.getState() & (Bundle.ACTIVE | Bundle.UNINSTALLED | Bundle.STOPPING)) != 0)
			return;

		// The bundle is not active and does not require activation, just return the class
		if (!shouldActivateFor(name, manager.getBaseData()))
			return;

		// The bundle is starting.  Note that if the state changed between the tests 
		// above and this test (e.g., it was not ACTIVE but now is), that's ok, we will 
		// just try to start it again (else case).
		// TODO need an explanation here of why we duplicated the mechanism 
		// from the framework rather than just calling start() and letting it sort it out.
		if (bundle.getState() == Bundle.STARTING) {
			// If the thread trying to load the class is the one trying to activate the bundle, then return the class 
			if (bundle.testStateChanging(Thread.currentThread()) || bundle.testStateChanging(null))
				return;

			// If it's another thread, we wait and try again. In any case the class is returned. 
			// The difference is that an exception can be logged.
			// TODO do we really need this test?  We just did it on the previous line?
			if (!bundle.testStateChanging(Thread.currentThread())) {
				Thread threadChangingState = bundle.getStateChanging();
				if (StatsManager.TRACE_BUNDLES && threadChangingState != null)
					System.out.println("Concurrent startup of bundle " + bundle.getSymbolicName() + " by " + Thread.currentThread() + " and " + threadChangingState.getName() + ". Waiting up to 5000ms for " + threadChangingState + " to finish the initialization."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
				long start = System.currentTimeMillis();
				long delay = 5000;
				long timeLeft = delay;
				while (true) {
					try {
						Thread.sleep(100); // do not release the classloader lock (bug 86713)
						if (bundle.testStateChanging(null) || timeLeft <= 0)
							break;
					} catch (InterruptedException e) {
						//Ignore and keep waiting
					}
					timeLeft = start + delay - System.currentTimeMillis();
				}
				if (timeLeft <= 0 || bundle.getState() != Bundle.ACTIVE) {
					String bundleName = bundle.getSymbolicName() == null ? Long.toString(bundle.getBundleId()) : bundle.getSymbolicName();
					String message = NLS.bind(EclipseAdaptorMsg.ECLIPSE_CLASSLOADER_CONCURRENT_STARTUP, new Object[] {Thread.currentThread().getName(), name, threadChangingState.getName(), bundleName, Long.toString(delay)});
					manager.getBaseData().getAdaptor().getFrameworkLog().log(new FrameworkLogEntry(FrameworkAdaptor.FRAMEWORK_SYMBOLICNAME, FrameworkLogEntry.ERROR, 0, message, 0, new Exception(EclipseAdaptorMsg.ECLIPSE_CLASSLOADER_GENERATED_EXCEPTION), null));
				}
				return;
			}
		}

		//The bundle must be started.
		try {
			bundle.start();
		} catch (BundleException e) {
			String message = NLS.bind(EclipseAdaptorMsg.ECLIPSE_CLASSLOADER_ACTIVATION, bundle.getSymbolicName(), Long.toString(bundle.getBundleId()));
			manager.getBaseData().getAdaptor().getFrameworkLog().log(new FrameworkLogEntry(FrameworkAdaptor.FRAMEWORK_SYMBOLICNAME, FrameworkLogEntry.ERROR, 0, message, 0, e, null));
			throw new ClassNotFoundException(name, e);
		}
		return;
	}

	private String getClassloaderId(ClasspathManager loader) {
		return loader.getBaseData().getSymbolicName();
	}

	private boolean shouldActivateFor(String className, BaseData bundledata) throws ClassNotFoundException {
		if (!isAutoStartable(className, bundledata))
			return false;
		//Don't reactivate on shut down
		if (bundledata.getAdaptor().isStopping()) {
			BundleStopper stopper = getBundleStopper(bundledata);
			if (stopper != null && stopper.isStopped(bundledata.getBundle())) {
				String message = NLS.bind(EclipseAdaptorMsg.ECLIPSE_CLASSLOADER_ALREADY_STOPPED, className, bundledata.getSymbolicName());
				throw new ClassNotFoundException(message);
			}
		}
		return true;
	}

	private boolean isAutoStartable(String className, BaseData bundledata) {
		EclipseStorageHook storageHook = (EclipseStorageHook) bundledata.getStorageHook(EclipseStorageHook.KEY);
		boolean autoStart = storageHook.isAutoStart();
		String[] autoStartExceptions = storageHook.getAutoStartExceptions();
		// no exceptions, it is easy to figure it out
		if (autoStartExceptions == null)
			return autoStart;
		// otherwise, we need to check if the package is in the exceptions list
		int dotPosition = className.lastIndexOf('.');
		// the class has no package name... no exceptions apply
		if (dotPosition == -1)
			return autoStart;
		String packageName = className.substring(0, dotPosition);
		// should activate if autoStart and package is not an exception, or if !autoStart and package is exception
		return autoStart ^ contains(autoStartExceptions, packageName);
	}

	private boolean contains(String[] array, String element) {
		for (int i = 0; i < array.length; i++)
			if (array[i].equals(element))
				return true;
		return false;
	}

	private BundleStopper getBundleStopper(BaseData bundledata) {
		AdaptorHook[] adaptorhooks = bundledata.getAdaptor().getHookRegistry().getAdaptorHooks();
		for (int i = 0; i < adaptorhooks.length; i++)
			if (adaptorhooks[i] instanceof EclipseAdaptorHook)
				return ((EclipseAdaptorHook) adaptorhooks[i]).getBundleStopper();
		return null;
	}

	public void postFindLocalClass(String name, Class clazz, ClasspathManager manager) {
		if (StatsManager.MONITOR_CLASSES)
			ClassloaderStats.endLoadingClass(getClassloaderId(manager), name, clazz != null);
	}

	public void preFindLocalResource(String name, ClasspathManager manager) {
		// Do nothing
		return;
	}

	public void postFindLocalResource(String name, URL resource, ClasspathManager manager) {
		if (StatsManager.MONITOR_RESOURCES)
			if (resource != null && name.endsWith(".properties")) //$NON-NLS-1$
				ClassloaderStats.loadedBundle(getClassloaderId(manager), new ResourceBundleStats(getClassloaderId(manager), name, resource));
		return;
	}

	public byte[] processClass(String name, byte[] classbytes, ClasspathEntry classpathEntry, BundleEntry entry, ClasspathManager manager) {
		if (!DEFINE_PACKAGES)
			return null;
		// Define the package if it is not the default package.
		int lastIndex = name.lastIndexOf('.');
		if (lastIndex < 0)
			return null;
		String packageName = name.substring(0, lastIndex);
		Package pkg = (Package) manager.getBaseClassLoader().publicGetPackage(packageName);
		if (pkg != null)
			return null;

		// get info about the package from the classpath entry's manifest.
		String specTitle = null, specVersion = null, specVendor = null, implTitle = null, implVersion = null, implVendor = null;
		ClasspathManifest cpm = (ClasspathManifest) classpathEntry.getUserObject(ClasspathManifest.KEY);
		if (cpm == null) {
			cpm = new ClasspathManifest(classpathEntry, manager);
			classpathEntry.addUserObject(cpm);
		}
		Manifest mf = cpm.getManifest();
		if (mf != null) {
			Attributes mainAttributes = mf.getMainAttributes();
			String dirName = packageName.replace('.', '/') + '/';
			Attributes packageAttributes = mf.getAttributes(dirName);
			boolean noEntry = false;
			if (packageAttributes == null) {
				noEntry = true;
				packageAttributes = mainAttributes;
			}
			specTitle = packageAttributes.getValue(Attributes.Name.SPECIFICATION_TITLE);
			if (specTitle == null && !noEntry)
				specTitle = mainAttributes.getValue(Attributes.Name.SPECIFICATION_TITLE);
			specVersion = packageAttributes.getValue(Attributes.Name.SPECIFICATION_VERSION);
			if (specVersion == null && !noEntry)
				specVersion = mainAttributes.getValue(Attributes.Name.SPECIFICATION_VERSION);
			specVendor = packageAttributes.getValue(Attributes.Name.SPECIFICATION_VENDOR);
			if (specVendor == null && !noEntry)
				specVendor = mainAttributes.getValue(Attributes.Name.SPECIFICATION_VENDOR);
			implTitle = packageAttributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
			if (implTitle == null && !noEntry)
				implTitle = mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
			implVersion = packageAttributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
			if (implVersion == null && !noEntry)
				implVersion = mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
			implVendor = packageAttributes.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
			if (implVendor == null && !noEntry)
				implVendor = mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
		}
		// The package is not defined yet define it before we define the class.
		// TODO still need to seal packages.
		manager.getBaseClassLoader().publicDefinePackage(packageName, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, null);
		// not doing any byte processing
		return null;
	}

	public boolean addClassPathEntry(ArrayList cpEntries, String cp, ClasspathManager hostmanager, BaseData sourcedata, ProtectionDomain sourcedomain) {
		String var = hasPrefix(cp);
		if (var != null)
			// find internal library using eclipse predefined vars
			return addInternalClassPath(var, cpEntries, cp, hostmanager, sourcedata, sourcedomain);
		if (cp.startsWith(EXTERNAL_LIB_PREFIX)) {
			cp = cp.substring(EXTERNAL_LIB_PREFIX.length());
			// find external library using system property substitution
			ClasspathEntry cpEntry = hostmanager.getExternalClassPath(substituteVars(cp), sourcedata, sourcedomain);
			if (cpEntry != null) {
				cpEntries.add(cpEntry);
				return true;
			}
		}
		return false;
	}

	private boolean addInternalClassPath(String var, ArrayList cpEntries, String cp, ClasspathManager hostloader, BaseData sourcedata, ProtectionDomain sourcedomain) {
		if (var.equals("ws")) //$NON-NLS-1$
			return ClasspathManager.addClassPathEntry(cpEntries, "ws/" + EclipseEnvironmentInfo.getDefault().getWS() + cp.substring(4), hostloader, sourcedata, sourcedomain); //$NON-NLS-1$
		if (var.equals("os")) //$NON-NLS-1$
			return ClasspathManager.addClassPathEntry(cpEntries, "os/" + EclipseEnvironmentInfo.getDefault().getOS() + cp.substring(4), hostloader, sourcedata, sourcedomain); //$NON-NLS-1$ 
		if (var.equals("nl")) { //$NON-NLS-1$
			cp = cp.substring(4);
			for (int i = 0; i < NL_JAR_VARIANTS.length; i++)
				if (ClasspathManager.addClassPathEntry(cpEntries, "nl/" + NL_JAR_VARIANTS[i] + cp, hostloader, sourcedata, sourcedomain)) //$NON-NLS-1$ 
					return true;
			// is we are not in development mode, post some framework errors.
			if (!DevClassPathHelper.inDevelopmentMode()) {
				//BundleException be = new BundleException(Msg.formatter.getString("BUNDLE_CLASSPATH_ENTRY_NOT_FOUND_EXCEPTION", entry, hostdata.getLocation())); //$NON-NLS-1$
				BundleException be = new BundleException(NLS.bind(Msg.BUNDLE_CLASSPATH_ENTRY_NOT_FOUND_EXCEPTION, cp));
				sourcedata.getAdaptor().getEventPublisher().publishFrameworkEvent(FrameworkEvent.ERROR, sourcedata.getBundle(), be);
			}
		}
		return false;
	}

	//return a String representing the string found between the $s
	private static String hasPrefix(String libPath) {
		if (libPath.startsWith("$ws$")) //$NON-NLS-1$
			return "ws"; //$NON-NLS-1$
		if (libPath.startsWith("$os$")) //$NON-NLS-1$
			return "os"; //$NON-NLS-1$
		if (libPath.startsWith("$nl$")) //$NON-NLS-1$
			return "nl"; //$NON-NLS-1$
		return null;
	}

	private static String substituteVars(String cp) {
		StringBuffer buf = new StringBuffer(cp.length());
		StringTokenizer st = new StringTokenizer(cp, VARIABLE_DELIM_STRING, true);
		boolean varStarted = false; // indicates we are processing a var subtitute
		String var = null; // the current var key
		while (st.hasMoreElements()) {
			String tok = st.nextToken();
			if (VARIABLE_DELIM_STRING.equals(tok)) {
				if (!varStarted) {
					varStarted = true; // we found the start of a var
					var = ""; //$NON-NLS-1$
				} else {
					// we have found the end of a var
					String prop = null;
					// get the value of the var from system properties
					if (var != null && var.length() > 0)
						prop = FrameworkProperties.getProperty(var);
					if (prop != null)
						// found a value; use it
						buf.append(prop);
					else
						// could not find a value append the var name w/o delims 
						buf.append(var == null ? "" : var); //$NON-NLS-1$
					varStarted = false;
					var = null;
				}
			} else {
				if (!varStarted)
					buf.append(tok); // the token is not part of a var
				else
					var = tok; // the token is the var key; save the key to process when we find the end token
			}
		}
		if (var != null)
			// found a case of $var at the end of the cp with no trailing $; just append it as is.
			buf.append(VARIABLE_DELIM_CHAR).append(var);
		return buf.toString();
	}

	private static String[] buildNLJarVariants(String nl) {
		ArrayList result = new ArrayList();
		nl = nl.replace('_', '/');
		while (nl.length() > 0) {
			result.add("nl/" + nl + "/"); //$NON-NLS-1$ //$NON-NLS-2$
			int i = nl.lastIndexOf('/');
			nl = (i < 0) ? "" : nl.substring(0, i); //$NON-NLS-1$
		}
		result.add(""); //$NON-NLS-1$
		return (String[]) result.toArray(new String[result.size()]);
	}

	public void addHooks(HookRegistry hookRegistry) {
		hookRegistry.addClasspathManagerHook(this);
	}

	public void recordClassDefine(String name, Class clazz, byte[] classbytes, ClasspathEntry classpathEntry, BundleEntry entry, ClasspathManager manager) {
		// do nothing
	}

	public String findLibrary(BaseData data, String libName) {
		if (libName.length() == 0)
			return null;
		if (libName.charAt(0) == '/' || libName.charAt(0) == '\\')
			libName = libName.substring(1);
		libName = System.mapLibraryName(libName);
		return searchVariants(data, libName);
	}

	private String searchVariants(BaseData bundledata, String path) {
		for (int i = 0; i < LIB_VARIENTS.length; i++) {
			BundleFile baseBundleFile = bundledata.getBundleFile();
			BundleEntry libEntry = baseBundleFile.getEntry(LIB_VARIENTS[i] + path);
			if (libEntry != null) {
				File libFile = baseBundleFile.getFile(LIB_VARIENTS[i] + path, true);
				if (libFile == null)
					return null;
				// see bug 88697 - HP requires libraries to have executable permissions
				if (org.eclipse.osgi.service.environment.Constants.OS_HPUX.equals(EclipseEnvironmentInfo.getDefault().getOS())) {
					try {
						// use the string array method in case there is a space in the path
						Runtime.getRuntime().exec(new String[] {"chmod", "755", libFile.getAbsolutePath()}).waitFor(); //$NON-NLS-1$ //$NON-NLS-2$
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				return libFile.getAbsolutePath();
			}
		}
		return null;
	}

	public ClassLoader getBundleClassLoaderParent() {
		return null; // do nothing
	}

}
