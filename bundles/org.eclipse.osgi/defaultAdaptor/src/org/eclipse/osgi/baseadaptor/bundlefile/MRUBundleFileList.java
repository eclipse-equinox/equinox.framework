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

package org.eclipse.osgi.baseadaptor.bundlefile;

import java.io.IOException;
import org.eclipse.osgi.framework.eventmgr.*;

/**
 * A simple/quick/small implementation of an MRU (Most Recently Used) list to keep
 * track of open BundleFiles.  The MRU will use the file limit specified by the property
 * &quot;osgi.bundlefile.limit&quot; by default unless the MRU is constructed with a specific
 * file limit.
 * @since 3.2
 */
public class MRUBundleFileList implements EventDispatcher {
	private static final String PROP_FILE_LIMIT = "osgi.bundlefile.limit"; //$NON-NLS-1$
	private static final int MIN = 10;
	// list of open bundle files
	private BundleFile[] bundleFileList;
	// list of open bundle files use stamps
	private long[] useStampList;
	// the current use stamp
	private long curUseStamp = 0;
	// the limit of open files to allow before least used bundle file is closed
	private int fileLimit = 0; // value < MIN will disable MRU
	// the current number of open bundle files
	private int numOpen = 0;
	private EventManager bundleFileCloserManager;
	private EventListeners bundleFileCloser;

	public MRUBundleFileList() {
		try {
			String prop = BundleFile.secureAction.getProperty(PROP_FILE_LIMIT);
			if (prop != null)
				init(Integer.parseInt(prop));
		} catch (NumberFormatException e) {
			//MRU will be disabled
		}
	}

	public MRUBundleFileList(int fileLimit) {
		init(fileLimit);
	}

	private void init(int initFileLimit) {
		// only enable the MRU if the initFileLimit is > MIN
		if (initFileLimit >= MIN) {
			this.fileLimit = initFileLimit;
			this.bundleFileList = new BundleFile[initFileLimit];
			this.useStampList = new long[initFileLimit];
			this.bundleFileCloserManager = new EventManager("Bundle File Closer"); //$NON-NLS-1$
			this.bundleFileCloser = new EventListeners();
			this.bundleFileCloser.addListener(this, this);
		}
	}

	/**
	 * Adds a BundleFile which is about to be opened to the MRU list.  If 
	 * the number of open BundleFiles == the fileLimit then the least 
	 * recently used BundleFile is closed.
	 * @param bundleFile the bundle file about to be opened.
	 * @throws IOException if an error occurs while closing the least recently used BundleFile
	 */
	public void add(BundleFile bundleFile) throws IOException {
		if (fileLimit < MIN)
			return; // MRU is disabled
		BundleFile toRemove = null;
		synchronized (bundleFileList) {
			int index = 0; // default to the first slot
			if (numOpen < fileLimit) {
				// numOpen does not exceed the fileLimit
				// find the first null slot to use in the MRU
				for (int i = 0; i < fileLimit; i++)
					if (bundleFileList[i] == null) {
						index = i;
						break;
					}
			} else {
				// numOpen has reached the fileLimit
				// find the least recently used bundleFile and close it 
				// and use it slot for the new bundleFile to be opened.
				index = 0;
				for (int i = 1; i < fileLimit; i++)
					if (useStampList[i] < useStampList[index])
						index = i;
				toRemove = bundleFileList[index];
				remove(toRemove);
			}
			// found an index to place to bundleFile to be opened
			bundleFileList[index] = bundleFile;
			bundleFile.setMruIndex(index);
			incUseStamp(index);
			numOpen++;
		}
		// must not close the toRemove bundle file while holding the lock of another bundle file (bug 161976)
		// This queue the bundle file for close asynchronously.
		closeBundleFile(toRemove);
	}

	/**
	 * Removes a bundle file which is about to be closed
	 * @param bundleFile the bundle file about to be closed
	 * @return true if the bundleFile existed in the MRU; false otherwise
	 */
	public boolean remove(BundleFile bundleFile) {
		if (fileLimit < MIN)
			return false; // MRU is disabled
		synchronized (bundleFileList) {
			int index = bundleFile.getMruIndex();
			if ((index >= 0 && index < fileLimit) && bundleFileList[index] == bundleFile) {
				bundleFile.setMruIndex(-1);
				bundleFileList[index] = null;
				useStampList[index] = -1;
				numOpen--;
				return true;
			}
		}
		return false;
	}

	/**
	 * Increments the use stamp of a bundle file
	 * @param bundleFile the bundle file to increment the use stamp for
	 */
	public void use(BundleFile bundleFile) {
		if (fileLimit < MIN)
			return; // MRU is disabled
		synchronized (bundleFileList) {
			int index = bundleFile.getMruIndex();
			if ((index >= 0 && index < fileLimit) && bundleFileList[index] == bundleFile)
				incUseStamp(index);
		}
	}

	private void incUseStamp(int index) {
		if (curUseStamp == Long.MAX_VALUE) {
			// we hit the curUseStamp max better reset all the stamps
			for (int i = 0; i < fileLimit; i++)
				useStampList[i] = 0;
			curUseStamp = 0;
		}
		useStampList[index] = ++curUseStamp;
	}

	public final void dispatchEvent(Object eventListener, Object listenerObject, int eventAction, Object eventObject) {
		try {
			((BundleFile) eventObject).close();
	} catch (IOException e) {
			// TODO should log ??
		}
	}

	private void closeBundleFile(BundleFile toRemove) {
		if (toRemove == null)
			return;
		/* queue to hold set of listeners */
		ListenerQueue queue = new ListenerQueue(bundleFileCloserManager);
		/* add bundle file closer to the queue */
		queue.queueListeners(bundleFileCloser, this);
		/* dispatch event to set of listeners */
		queue.dispatchEventAsynchronous(0, toRemove);
	}
}
