/*******************************************************************************
 * Copyright (c) 2003, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.osgi.internal.permadmin;

import java.io.IOException;
import java.security.*;
import org.eclipse.osgi.framework.adaptor.PermissionStorage;

/**
 * PermissionStorage privileged action class.
 */

public class SecurePermissionStorage implements PermissionStorage, PrivilegedExceptionAction {
	private PermissionStorage storage;
	private String location;
	private String[] data;
	private String[] infos;
	private long scopeId;
	private int action;
	private static final int GET = 1;
	private static final int SET = 2;
	private static final int LOCATION = 3;
	private static final int GET_INFOS = 4;
	private static final int SAVE_INFOS = 5;

	public SecurePermissionStorage(PermissionStorage storage) {
		this.storage = storage;
	}

	public Object run() throws IOException {
		switch (action) {
			case GET :
				return storage.getPermissionData(location, scopeId);
			case SET :
				storage.setPermissionData(location, data, scopeId);
				return null;
			case LOCATION :
				return storage.getLocations(scopeId);
			case SAVE_INFOS :
				storage.saveConditionalPermissionInfos(infos, scopeId);
				return null;
			case GET_INFOS :
				return storage.getConditionalPermissionInfos(scopeId);
		}

		throw new UnsupportedOperationException();
	}

	public String[] getPermissionData(String location, long scopeId) throws IOException {
		this.location = location;
		this.action = GET;
		this.scopeId = scopeId;

		try {
			return (String[]) AccessController.doPrivileged(this);
		} catch (PrivilegedActionException e) {
			throw (IOException) e.getException();
		}
	}

	public String[] getLocations(long scopeId) throws IOException {
		this.action = LOCATION;
		this.scopeId = scopeId;

		try {
			return (String[]) AccessController.doPrivileged(this);
		} catch (PrivilegedActionException e) {
			throw (IOException) e.getException();
		}
	}

	public void setPermissionData(String location, String[] data, long scopeId) throws IOException {
		this.location = location;
		this.data = data;
		this.action = SET;
		this.scopeId = scopeId;

		try {
			AccessController.doPrivileged(this);
		} catch (PrivilegedActionException e) {
			throw (IOException) e.getException();
		}
	}

	public void saveConditionalPermissionInfos(String[] infos, long scopeId) throws IOException {
		this.action = SAVE_INFOS;
		this.infos = infos;
		this.scopeId = scopeId;
		try {
			AccessController.doPrivileged(this);
		} catch (PrivilegedActionException e) {
			throw (IOException) e.getException();
		}

	}

	public String[] getConditionalPermissionInfos(long scopeId) throws IOException {
		this.action = GET_INFOS;
		this.scopeId = scopeId;
		try {
			return (String[]) AccessController.doPrivileged(this);
		} catch (PrivilegedActionException e) {
			throw (IOException) e.getException();
		}
	}
}
