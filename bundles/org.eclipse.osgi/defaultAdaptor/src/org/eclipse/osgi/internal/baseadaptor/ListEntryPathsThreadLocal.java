/*******************************************************************************
 * Copyright (c) 2005, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.internal.baseadaptor;

public class ListEntryPathsThreadLocal {
	private static final ThreadLocal<Boolean> recurse = new ThreadLocal<Boolean>() {
		@Override
		protected Boolean initialValue() {
			return Boolean.FALSE;
		}
	};

	public static boolean isRecursive() {
		return recurse.get();
	}

	public static void setRecursive(boolean value) {
		recurse.set(value);
	}
}
