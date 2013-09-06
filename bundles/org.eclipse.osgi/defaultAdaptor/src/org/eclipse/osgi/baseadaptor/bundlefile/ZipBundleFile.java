/*******************************************************************************
 * Copyright (c) 2005, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Rob Harrop - SpringSource Inc. (bug 253942)
 *******************************************************************************/

package org.eclipse.osgi.baseadaptor.bundlefile;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.eclipse.osgi.baseadaptor.BaseData;
import org.eclipse.osgi.framework.debug.Debug;
import org.eclipse.osgi.internal.baseadaptor.*;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.FrameworkEvent;

/**
 * A BundleFile that uses a ZipFile as it base file.
 * @since 3.2
 */
public class ZipBundleFile extends BundleFile {

	private final MRUBundleFileList mruList;
	/**
	 * The bundle data
	 */
	protected BaseData bundledata;
	/**
	 * The zip file
	 */
	protected volatile ZipFile zipFile;
	/**
	 * The closed flag
	 */
	protected volatile boolean closed = true;

	private int referenceCount = 0;

	/**
	 * Constructs a ZipBundle File
	 * @param basefile the base file
	 * @param bundledata the bundle data
	 * @throws IOException
	 */
	public ZipBundleFile(File basefile, BaseData bundledata) throws IOException {
		this(basefile, bundledata, null);
	}

	public ZipBundleFile(File basefile, BaseData bundledata, MRUBundleFileList mruList) throws IOException {
		super(basefile);
		if (!BundleFile.secureAction.exists(basefile))
			throw new IOException(NLS.bind(AdaptorMsg.ADAPTER_FILEEXIST_EXCEPTION, basefile));
		this.bundledata = bundledata;
		this.closed = true;
		this.mruList = mruList;
	}

	/**
	 * Checks if the zip file is open
	 * @return true if the zip file is open
	 */
	protected boolean checkedOpen() {
		try {
			return getZipFile() != null;
		} catch (IOException e) {
			if (bundledata != null)
				bundledata.getAdaptor().getEventPublisher().publishFrameworkEvent(FrameworkEvent.ERROR, bundledata.getBundle(), e);
			return false;
		}
	}

	/**
	 * Opens the ZipFile for this bundle file
	 * @return an open ZipFile for this bundle file
	 * @throws IOException
	 */
	protected ZipFile basicOpen() throws IOException {
		return BundleFile.secureAction.getZipFile(this.basefile);
	}

	/**
	 * Returns an open ZipFile for this bundle file.  If an open
	 * ZipFile does not exist then a new one is created and
	 * returned.
	 * @return an open ZipFile for this bundle
	 * @throws IOException
	 */
	protected synchronized ZipFile getZipFile() throws IOException {
		if (closed) {
			mruListAdd();
			zipFile = basicOpen();
			closed = false;
		} else
			mruListUse();
		return zipFile;
	}

	/**
	* Returns a ZipEntry for the bundle file. Must be called while synchronizing on this object.
	* This method does not ensure that the ZipFile is opened. Callers may need to call getZipfile() prior to calling this 
	* method.
	* @param path the path to an entry
	* @return a ZipEntry or null if the entry does not exist
	*/
	protected ZipEntry getZipEntry(String path) {
		if (path.length() > 0 && path.charAt(0) == '/')
			path = path.substring(1);
		ZipEntry entry = zipFile.getEntry(path);
		if (entry != null && entry.getSize() == 0 && !entry.isDirectory()) {
			// work around the directory bug see bug 83542
			ZipEntry dirEntry = zipFile.getEntry(path + '/');
			if (dirEntry != null)
				entry = dirEntry;
		}
		return entry;
	}

	/**
	 * Extracts a directory and all sub content to disk
	 * @param dirName the directory name to extract
	 * @return the File used to extract the content to.  A value
	 * of <code>null</code> is returned if the directory to extract does 
	 * not exist or if content extraction is not supported.
	 */
	protected synchronized File extractDirectory(String dirName) {
		if (!checkedOpen())
			return null;
		Enumeration<? extends ZipEntry> entries = zipFile.entries();
		while (entries.hasMoreElements()) {
			String entryPath = entries.nextElement().getName();
			if (entryPath.startsWith(dirName) && !entryPath.endsWith("/")) //$NON-NLS-1$
				getFile(entryPath, false);
		}
		return getExtractFile(dirName);
	}

	protected File getExtractFile(String entryName) {
		if (bundledata == null)
			return null;
		String path = ".cp"; /* put all these entries in this subdir *///$NON-NLS-1$
		String name = entryName.replace('/', File.separatorChar);
		if ((name.length() > 1) && (name.charAt(0) == File.separatorChar)) /* if name has a leading slash */
			path = path.concat(name);
		else
			path = path + File.separator + name;
		return bundledata.getExtractFile(path);
	}

	public synchronized File getFile(String entry, boolean nativeCode) {
		if (!checkedOpen())
			return null;
		ZipEntry zipEntry = getZipEntry(entry);
		if (zipEntry == null)
			return null;

		try {
			File nested = getExtractFile(zipEntry.getName());
			if (nested != null) {
				if (nested.exists()) {
					/* the entry is already cached */
					if (Debug.DEBUG_GENERAL)
						Debug.println("File already present: " + nested.getPath()); //$NON-NLS-1$
					if (nested.isDirectory())
						// must ensure the complete directory is extracted (bug 182585)
						extractDirectory(zipEntry.getName());
				} else {
					if (zipEntry.getName().endsWith("/")) { //$NON-NLS-1$
						if (!nested.mkdirs()) {
							if (Debug.DEBUG_GENERAL)
								Debug.println("Unable to create directory: " + nested.getPath()); //$NON-NLS-1$
							throw new IOException(NLS.bind(AdaptorMsg.ADAPTOR_DIRECTORY_CREATE_EXCEPTION, nested.getAbsolutePath()));
						}
						extractDirectory(zipEntry.getName());
					} else {
						InputStream in = zipFile.getInputStream(zipEntry);
						if (in == null)
							return null;
						/* the entry has not been cached */
						if (Debug.DEBUG_GENERAL)
							Debug.println("Creating file: " + nested.getPath()); //$NON-NLS-1$
						/* create the necessary directories */
						File dir = new File(nested.getParent());
						if (!dir.exists() && !dir.mkdirs()) {
							if (Debug.DEBUG_GENERAL)
								Debug.println("Unable to create directory: " + dir.getPath()); //$NON-NLS-1$
							throw new IOException(NLS.bind(AdaptorMsg.ADAPTOR_DIRECTORY_CREATE_EXCEPTION, dir.getAbsolutePath()));
						}
						/* copy the entry to the cache */
						AdaptorUtil.readFile(in, nested);
						if (nativeCode)
							setPermissions(nested);
					}
				}

				return nested;
			}
		} catch (IOException e) {
			if (Debug.DEBUG_GENERAL)
				Debug.printStackTrace(e);
		}
		return null;
	}

	public synchronized boolean containsDir(String dir) {
		if (!checkedOpen())
			return false;
		if (dir == null)
			return false;

		if (dir.length() == 0)
			return true;

		if (dir.charAt(0) == '/') {
			if (dir.length() == 1)
				return true;
			dir = dir.substring(1);
		}

		if (dir.length() > 0 && dir.charAt(dir.length() - 1) != '/')
			dir = dir + '/';

		Enumeration<? extends ZipEntry> entries = zipFile.entries();
		ZipEntry zipEntry;
		String entryPath;
		while (entries.hasMoreElements()) {
			zipEntry = entries.nextElement();
			entryPath = zipEntry.getName();
			if (entryPath.startsWith(dir)) {
				return true;
			}
		}
		return false;
	}

	public synchronized BundleEntry getEntry(String path) {
		if (!checkedOpen())
			return null;
		ZipEntry zipEntry = getZipEntry(path);
		if (zipEntry == null) {
			if (path.length() == 0 || path.charAt(path.length() - 1) == '/') {
				// this is a directory request lets see if any entries exist in this directory
				if (containsDir(path))
					return new DirZipBundleEntry(this, path);
			}
			return null;
		}

		return new ZipBundleEntry(zipEntry, this);

	}

	public synchronized Enumeration<String> getEntryPaths(String path) {
		// Get entry paths. Recurse or not based on caller's thread local
		// request.
		Enumeration<String> result = getEntryPaths(path, ListEntryPathsThreadLocal.isRecursive());
		// Always set the thread local back to its default value. If the caller
		// requested recursion, this will indicate that recursion was done.
		// Otherwise, no harm is done.
		ListEntryPathsThreadLocal.setRecursive(false);
		return result;
	}

	// Optimized method allowing this zip bundle file to recursively return 
	// entry paths when requested.
	public synchronized Enumeration<String> getEntryPaths(String path, boolean doRecurse) {
		if (path == null)
			throw new NullPointerException();
		// Is the zip file already open or, if not, can it be opened?
		if (!checkedOpen())
			return null;

		// Strip any leading '/' off of path.
		if (path.length() > 0 && path.charAt(0) == '/')
			path = path.substring(1);
		// Append a '/', if not already there, to path if not an empty string.
		if (path.length() > 0 && path.charAt(path.length() - 1) != '/')
			path = new StringBuffer(path).append("/").toString(); //$NON-NLS-1$

		LinkedHashSet<String> result = new LinkedHashSet<String>();
		// Get all zip file entries and add the ones of interest.
		Enumeration<? extends ZipEntry> entries = zipFile.entries();
		while (entries.hasMoreElements()) {
			ZipEntry zipEntry = entries.nextElement();
			String entryPath = zipEntry.getName();
			// Is the entry of possible interest? Note that 
			// string.startsWith("") == true.
			if (entryPath.startsWith(path)) {
				// If we get here, we know that the entry is either (1) equal to
				// path, (2) a file under path, or (3) a subdirectory of path.
				if (path.length() < entryPath.length()) {
					// If we get here, we know that entry is not equal to path.
					getEntryPaths(path, entryPath.substring(path.length()), doRecurse, result);
				}
			}
		}
		return result.size() == 0 ? null : Collections.enumeration(result);
	}

	/**
	 * Process the given entry by appending it to path and adding the full path
	 * to entries. If recursive, sub-paths of entry will also be processed.
	 * 
	 * For example, given the following parameters:
	 * 
	 * path = com/
	 * entry = foo/bar/X.class
	 * doRecurse = false
	 * 
	 * com/foo/ will be added to entries and the method will return.
	 * 
	 * If, instead, doRecurse equals true, the following will be added to
	 * entries before returning:
	 * 
	 * com/foo/
	 * com/foo/bar/
	 * com/foo/bar/X.class
	 * 
	 * @param path - The requested or already processed path. On the first call
	 *               to this method, this will be the path requested by the
	 *               caller of {@link #getEntryPaths(String, boolean)}. On
	 *               subsequent, recursive calls, this will be the portion of
	 *               the path already processed.
	 * @param entry - The entry underneath path to process.
	 * @param doRecurse - If true, process all path segments under entry
	 *                    recursively. If false, process only the first path 
	 *                    segment in entry.
	 * @param entries - The set of processed entries.
	 */
	private void getEntryPaths(String path, String entry, boolean doRecurse, LinkedHashSet<String> entries) {
		if (entry.length() == 0) // Terminating condition.
			// The previous entry was a directory with no files.
			return;
		int slash = entry.indexOf('/');
		if (slash == -1) // Terminating condition.
			// The entry is a file so nothing follows. Add its full path and
			// return.
			entries.add(path + entry);
		else {
			// Append the entry to the path to track the full path for recursion.
			path = path + entry.substring(0, slash + 1);
			// Add the full entry path.
			entries.add(path);
			if (doRecurse)
				// Recurse with the updated path plus the next path segment of
				// entry.
				getEntryPaths(path, entry.substring(slash + 1), true, entries);
		}
	}

	public synchronized void close() throws IOException {
		if (!closed) {
			if (referenceCount > 0 && isMruListClosing()) {
				// there are some opened streams to this BundleFile still;
				// wait for them all to close because this is being closed by the MRUBundleFileList
				try {
					wait(1000); // timeout after 1 second
				} catch (InterruptedException e) {
					// do nothing for now ...
				}
				if (referenceCount != 0 || closed)
					// either another thread closed the bundle file or we timed waiting for all the reference inputstreams to close
					// If the referenceCount did not reach zero then this bundle file will remain open until the
					// bundle file is closed explicitly (i.e. bundle is updated/uninstalled or framework is shutdown)
					return;

			}
			closed = true;
			zipFile.close();
			mruListRemove();
		}
	}

	private boolean isMruListClosing() {
		return this.mruList != null && this.mruList.isClosing(this);
	}

	boolean isMruEnabled() {
		return this.mruList != null && this.mruList.isEnabled();
	}

	private void mruListRemove() {
		if (this.mruList != null) {
			this.mruList.remove(this);
		}
	}

	private void mruListUse() {
		if (this.mruList != null) {
			mruList.use(this);
		}
	}

	private void mruListAdd() {
		if (this.mruList != null) {
			mruList.add(this);
		}
	}

	public void open() {
		//do nothing
	}

	synchronized void incrementReference() {
		referenceCount += 1;
	}

	synchronized void decrementReference() {
		referenceCount = Math.max(0, referenceCount - 1);
		// only notify if the referenceCount is zero.
		if (referenceCount == 0)
			notify();
	}
}
