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

import java.io.*;
import java.net.URLConnection;
import java.util.*;
import org.eclipse.core.runtime.adaptor.EclipseLog;
import org.eclipse.core.runtime.adaptor.EclipseStarter;
import org.eclipse.core.runtime.adaptor.LocationManager;
import org.eclipse.osgi.baseadaptor.*;
import org.eclipse.osgi.baseadaptor.hooks.AdaptorHook;
import org.eclipse.osgi.framework.adaptor.FrameworkAdaptor;
import org.eclipse.osgi.framework.internal.core.Constants;
import org.eclipse.osgi.framework.internal.core.FrameworkProperties;
import org.eclipse.osgi.framework.log.FrameworkLog;
import org.eclipse.osgi.framework.log.FrameworkLogEntry;
import org.eclipse.osgi.internal.baseadaptor.AdaptorUtil;
import org.eclipse.osgi.service.datalocation.Location;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

public class EclipseErrorHandler implements AdaptorHook, HookConfigurator {
	// System property used to prevent VM exit when unexpected errors occur
	private static final String PROP_EXITONERROR = "eclipse.exitOnError"; //$NON-NLS-1$
	// The eclipse log file extension */
	private static final String LOG_EXT = ".log"; //$NON-NLS-1$
	private BaseAdaptor adaptor;

	public void frameworkStart(BundleContext context) throws BundleException {
		AdaptorUtil.register(FrameworkLog.class.getName(), adaptor.getFrameworkLog(), context);
		registerPerformanceLog(context);
	}

	public void frameworkStop(BundleContext context) throws BundleException {
		// TODO should unregister services registered in start
	}

	public void frameworkStopping(BundleContext context) {
		// do nothing
	}

	public void addProperties(Properties properties) {
		// do nothing
	}

	public URLConnection mapLocationToURLConnection(String location) throws IOException {
		// do nothing
		return null;
	}

	private boolean isFatalException(Throwable error) {
		if (error instanceof VirtualMachineError) {
			return true;
		}
		if (error instanceof ThreadDeath) {
			return true;
		}
		return false;
	}

	public void handleRuntimeError(Throwable error) {
		// this is the important method to handle errors
		boolean exitOnError = false;
		try {
			// check the prop each time this happens (should NEVER happen!)
			exitOnError = Boolean.valueOf(FrameworkProperties.getProperty(EclipseErrorHandler.PROP_EXITONERROR, "true")).booleanValue(); //$NON-NLS-1$
			String message = EclipseAdaptorMsg.ECLIPSE_ADAPTOR_RUNTIME_ERROR;
			if (exitOnError && isFatalException(error))
				message += ' ' + EclipseAdaptorMsg.ECLIPSE_ADAPTOR_EXITING;
			FrameworkLogEntry logEntry = new FrameworkLogEntry(FrameworkAdaptor.FRAMEWORK_SYMBOLICNAME, FrameworkLogEntry.ERROR, 0, message, 0, error, null);
			adaptor.getFrameworkLog().log(logEntry);
		} catch (Throwable t) {
			// we may be in a currupted state and must be able to handle any
			// errors (ie OutOfMemoryError)
			// that may occur when handling the first error; this is REALLY the
			// last resort.
			try {
				error.printStackTrace();
				t.printStackTrace();
			} catch (Throwable t1) {
				// if we fail that then we are beyond help.
			}
		} finally {
			// do the exit outside the try block just incase another runtime
			// error was thrown while logging
			if (exitOnError && isFatalException(error))
				System.exit(13);
		}
	}

	public boolean matchDNChain(String pattern, String[] dnChain) {
		// do nothing
		return false;
	}

	public void addHooks(HookRegistry hookRegistry) {
		hookRegistry.addAdaptorHook(this);
	}

	public FrameworkLog createFrameworkLog() {
		FrameworkLog frameworkLog;
		String logFileProp = FrameworkProperties.getProperty(EclipseStarter.PROP_LOGFILE);
		if (logFileProp != null) {
			frameworkLog = new EclipseLog(new File(logFileProp));
		} else {
			Location location = LocationManager.getConfigurationLocation();
			File configAreaDirectory = null;
			if (location != null)
				// TODO assumes the URL is a file: url
				configAreaDirectory = new File(location.getURL().getFile());

			if (configAreaDirectory != null) {
				String logFileName = Long.toString(System.currentTimeMillis()) + EclipseErrorHandler.LOG_EXT;
				File logFile = new File(configAreaDirectory, logFileName);
				FrameworkProperties.setProperty(EclipseStarter.PROP_LOGFILE, logFile.getAbsolutePath());
				frameworkLog = new EclipseLog(logFile);
			} else
				frameworkLog = new EclipseLog();
		}
		if ("true".equals(FrameworkProperties.getProperty(EclipseStarter.PROP_CONSOLE_LOG))) //$NON-NLS-1$
			frameworkLog.setConsoleLog(true);
		return frameworkLog;
	}

	public void initialize(BaseAdaptor adaptor) {
		this.adaptor = adaptor;
	}

	private void registerPerformanceLog(BundleContext context) {
		Object service = createPerformanceLog();
		String serviceName = FrameworkLog.class.getName();
		Hashtable serviceProperties = new Hashtable(7);
		Dictionary headers = context.getBundle().getHeaders();

		serviceProperties.put(Constants.SERVICE_VENDOR, headers.get(Constants.BUNDLE_VENDOR));
		serviceProperties.put(Constants.SERVICE_RANKING, new Integer(Integer.MIN_VALUE));
		serviceProperties.put(Constants.SERVICE_PID, context.getBundle().getBundleId() + '.' + service.getClass().getName());
		serviceProperties.put(FrameworkLog.SERVICE_PERFORMANCE, Boolean.TRUE.toString());

		context.registerService(serviceName, service, serviceProperties);
	}

	private FrameworkLog createPerformanceLog() {
		String logFileProp = FrameworkProperties.getProperty(EclipseStarter.PROP_LOGFILE);
		if (logFileProp != null) {
			int lastSlash = logFileProp.lastIndexOf(File.separatorChar);
			if (lastSlash > 0) {
				String logFile = logFileProp.substring(0, lastSlash + 1) + "performance.log"; //$NON-NLS-1$
				return new EclipseLog(new File(logFile));
			}
		}
		//if all else fails, write to std err
		return new EclipseLog(new PrintWriter(System.err));
	}
}
