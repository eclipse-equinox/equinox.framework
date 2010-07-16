/*******************************************************************************
 * Copyright (c) 2005, 2010 IBM Corporation and others.
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
import java.util.*;
import java.util.Map.Entry;
import org.eclipse.osgi.framework.adaptor.PermissionStorage;

public class BasePermissionStorage implements PermissionStorage {
	private static final byte PERMDATA_VERSION = 2;
	private final HashMap<Long, HashMap<String, String[]>> locations = new HashMap<Long, HashMap<String, String[]>>();
	private final HashMap<Long, String[]> condPermInfos = new HashMap<Long, String[]>();
	private final BaseStorage storage;
	private boolean dirty = false;

	BasePermissionStorage(BaseStorage storage) {
		this.storage = storage;
	}

	void readPermissionStorage(DataInputStream in) throws IOException {
		if (in == null)
			return;
		if (PERMDATA_VERSION != in.readByte())
			return;

		synchronized (locations) {
			// read the number of scope Ids first
			int numScopes = in.readInt();
			for (int i = 0; i < numScopes; i++) {
				long scopeId = in.readLong();
				HashMap<String, String[]> scopedLocations = new HashMap<String, String[]>();
				locations.put(scopeId, scopedLocations);
				int numLocs = in.readInt();
				for (int j = 0; j < numLocs; j++) {
					String loc = AdaptorUtil.readString(in, false);
					int numPerms = in.readInt();
					String[] perms = new String[numPerms];
					for (int k = 0; k < numPerms; k++)
						perms[k] = in.readUTF();
					scopedLocations.put(loc, perms);
				}
			}
		}
		synchronized (condPermInfos) {
			// read the number of scope Ids first
			int numScopes = in.readInt();
			for (int i = 0; i < numScopes; i++) {
				long scopeId = in.readLong();
				int numCondPerms = in.readInt();
				if (numCondPerms > 0) {
					String[] condPerms = new String[numCondPerms];
					for (int j = 0; j < numCondPerms; j++)
						condPerms[j] = in.readUTF();
					condPermInfos.put(scopeId, condPerms);
				}
			}
		}
	}

	void writePermissionStorage(DataOutputStream out) throws IOException {
		out.writeByte(PERMDATA_VERSION);
		synchronized (locations) {
			out.writeInt(locations.size());
			for (Iterator<Entry<Long, HashMap<String, String[]>>> scopedLocations = locations.entrySet().iterator(); scopedLocations.hasNext();) {
				Entry<Long, HashMap<String, String[]>> scopedLocationsEntry = scopedLocations.next();
				out.writeLong(scopedLocationsEntry.getKey().longValue());
				out.writeInt(scopedLocationsEntry.getValue().size());
				for (Iterator<Entry<String, String[]>> locationsEntries = scopedLocationsEntry.getValue().entrySet().iterator(); locationsEntries.hasNext();) {
					Entry<String, String[]> locationsEntry = locationsEntries.next();
					AdaptorUtil.writeStringOrNull(out, locationsEntry.getKey());
					String[] perms = locationsEntry.getValue();
					out.writeInt(perms.length);
					for (int i = 0; i < perms.length; i++)
						out.writeUTF(perms[i]);
				}
			}
		}
		synchronized (condPermInfos) {
			out.writeInt(condPermInfos.size());
			for (Iterator<Entry<Long, String[]>> scopedInfos = condPermInfos.entrySet().iterator(); scopedInfos.hasNext();) {
				Entry<Long, String[]> infosEntry = scopedInfos.next();
				out.writeLong(infosEntry.getKey().longValue());
				String[] infos = infosEntry.getValue();
				out.writeInt(infos.length);
				for (int i = 0; i < infos.length; i++)
					out.writeUTF(infos[i]);
			}
		}
	}

	/**
	 * @throws IOException
	 */
	public String[] getLocations(long scopeId) throws IOException {
		synchronized (locations) {
			HashMap<String, String[]> scopedLocations = locations.get(scopeId);
			if (scopedLocations == null)
				return null;
			ArrayList<String> result = new ArrayList<String>(scopedLocations.size());
			for (Iterator<String> iLocs = scopedLocations.keySet().iterator(); iLocs.hasNext();) {
				String location = iLocs.next();
				if (location != null)
					result.add(location);
			}
			if (result.size() == 0)
				return null;
			return result.toArray(new String[result.size()]);
		}
	}

	/**
	 * @throws IOException
	 */
	public String[] getPermissionData(String location, long scopeId) throws IOException {
		synchronized (locations) {
			HashMap<String, String[]> scopedLocations = locations.get(scopeId);
			if (scopedLocations == null)
				return null;
			return scopedLocations.get(location);
		}
	}

	/**
	 * @throws IOException
	 */
	public void setPermissionData(String location, String[] data, long scopeId) throws IOException {
		synchronized (locations) {
			HashMap<String, String[]> scopedLocations = locations.get(scopeId);
			if (data == null) {
				if (scopedLocations != null) {
					scopedLocations.remove(location);
					if (scopedLocations.size() == 0)
						locations.remove(scopeId);
				}
			} else {
				if (scopedLocations == null) {
					scopedLocations = new HashMap<String, String[]>();
					locations.put(scopeId, scopedLocations);
				}
				scopedLocations.put(location, data);
			}
		}
		setDirty(true);
		storage.requestSave();
	}

	/**
	 * @throws IOException
	 */
	public void saveConditionalPermissionInfos(String[] infos, long scopeId) throws IOException {
		synchronized (condPermInfos) {
			if (infos == null || infos.length == 0)
				condPermInfos.remove(scopeId);
			else
				condPermInfos.put(scopeId, infos);
		}
		setDirty(true);
		storage.requestSave();
	}

	/**
	 * @throws IOException
	 */
	public String[] getConditionalPermissionInfos(long scopeId) throws IOException {
		synchronized (condPermInfos) {
			return condPermInfos.get(scopeId);
		}
	}

	public boolean isDirty() {
		return dirty;
	}

	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}
}
