/**********************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved.   This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.osgi.framework.adaptor.core;

import org.eclipse.osgi.util.NLS;

public class AdaptorMsg extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.osgi.framework.adaptor.core.ExternalMessages"; //$NON-NLS-1$
	
	public static String MANIFEST_NOT_FOUND_EXCEPTION;
	public static String ADAPTOR_STORAGE_EXCEPTION;
	public static String ADAPTOR_DATA_AREA_NOT_SET;
	public static String BUNDLE_NATIVECODE_EXCEPTION;
	public static String ADAPTOR_ERROR_GETTING_MANIFEST;
	
	public static String ADAPTOR_URL_CREATE_EXCEPTION;
	public static String ADAPTOR_SAME_REF_UPDATE;
	public static String ADAPTOR_DIRECTORY_CREATE_EXCEPTION;
	public static String BUNDLE_READ_EXCEPTION;

	public static String ADAPTER_FILEEXIST_EXCEPTION;
	public static String ADAPTOR_DIRECTORY_EXCEPTION;
	
	public static String RESOURCE_NOT_FOUND_EXCEPTION;
	
	public static String SYSTEMBUNDLE_MISSING_MANIFEST;
	
	public static String URL_NO_BUNDLE_ID;
	public static String URL_INVALID_BUNDLE_ID;
	public static String URL_NO_BUNDLE_FOUND;
	
	public static String BUNDLE_CLASSPATH_PROPERTIES_ERROR;
	
	static {
		// initialize resource bundles
		NLS.initializeMessages(BUNDLE_NAME, AdaptorMsg.class);
	}
}