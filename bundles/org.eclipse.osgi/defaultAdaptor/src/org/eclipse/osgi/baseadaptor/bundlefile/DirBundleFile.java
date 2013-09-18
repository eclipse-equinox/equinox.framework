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

package org.eclipse.osgi.baseadaptor.bundlefile;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.eclipse.osgi.internal.baseadaptor.AdaptorMsg;
import org.eclipse.osgi.internal.baseadaptor.ListEntryPathsThreadLocal;
import org.eclipse.osgi.util.NLS;

/**
 * A BundleFile that uses a directory as its base file.
 * @since 3.2
 */
public class DirBundleFile extends BundleFile {

	/**
	 * Constructs a DirBundleFile
	 * @param basefile the base file
	 * @throws IOException
	 */
	public DirBundleFile(File basefile) throws IOException {
		super(basefile);
		if (!BundleFile.secureAction.exists(basefile) || !BundleFile.secureAction.isDirectory(basefile)) {
			throw new IOException(NLS.bind(AdaptorMsg.ADAPTOR_DIRECTORY_EXCEPTION, basefile));
		}
	}

	public File getFile(String path, boolean nativeCode) {
		boolean checkInBundle = path != null && path.indexOf("..") >= 0; //$NON-NLS-1$
		File file = new File(basefile, path);
		if (!BundleFile.secureAction.exists(file)) {
			return null;
		}
		// must do an extra check to make sure file is within the bundle (bug 320546)
		if (checkInBundle) {
			try {
				if (!BundleFile.secureAction.getCanonicalPath(file).startsWith(BundleFile.secureAction.getCanonicalPath(basefile)))
					return null;
			} catch (IOException e) {
				return null;
			}
		}
		return file;
	}

	public BundleEntry getEntry(String path) {
		File filePath = getFile(path, false);
		if (filePath == null)
			return null;
		return new FileBundleEntry(filePath, path);
	}

	public boolean containsDir(String dir) {
		File dirPath = getFile(dir, false);
		return dirPath != null && BundleFile.secureAction.isDirectory(dirPath);
	}

	public Enumeration<String> getEntryPaths(String path) {
		// Get entry paths. Recurse or not based on caller's thread local
		// request.
		Enumeration<String> result = getEntryPaths(path, ListEntryPathsThreadLocal.isRecursive());
		// Always set the thread local back to its default value. If the caller
		// requested recursion, this will indicate that recursion was done.
		// Otherwise, no harm is done.
		ListEntryPathsThreadLocal.setRecursive(false);
		return result;
	}

	// Optimized method allowing this directory bundle file to recursively 
	// return entry paths when requested.
	public Enumeration<String> getEntryPaths(String path, boolean recurse) {
		if (path.length() > 0 && path.charAt(0) == '/')
			path = path.substring(1);
		File pathFile = getFile(path, false);
		if (pathFile == null || !BundleFile.secureAction.isDirectory(pathFile))
			return null;
		String[] fileList = BundleFile.secureAction.list(pathFile);
		if (fileList == null || fileList.length == 0)
			return null;
		String dirPath = path.length() == 0 || path.charAt(path.length() - 1) == '/' ? path : path + '/';

		LinkedHashSet<String> entries = new LinkedHashSet<String>();
		for (String s : fileList) {
			java.io.File childFile = new java.io.File(pathFile, s);
			StringBuffer sb = new StringBuffer(dirPath).append(s);
			if (BundleFile.secureAction.isDirectory(childFile)) {
				sb.append("/"); //$NON-NLS-1$
				if (recurse) {
					Enumeration<String> e = getEntryPaths(sb.toString(), true);
					if (e != null)
						entries.addAll(Collections.list(e));
				}
			}
			entries.add(sb.toString());
		}
		return Collections.enumeration(entries);
	}

	public void close() {
		// nothing to do.
	}

	public void open() {
		// nothing to do.
	}
}
