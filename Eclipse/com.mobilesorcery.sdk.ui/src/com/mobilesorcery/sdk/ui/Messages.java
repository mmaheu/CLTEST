/*  Copyright (C) 2009 Mobile Sorcery AB

    This program is free software; you can redistribute it and/or modify it
    under the terms of the Eclipse Public License v1.0.

    This program is distributed in the hope that it will be useful, but WITHOUT
    ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
    FITNESS FOR A PARTICULAR PURPOSE. See the Eclipse Public License v1.0 for
    more details.

    You should have received a copy of the Eclipse Public License v1.0 along
    with this program. It is also available at http://www.eclipse.org/legal/epl-v10.html
*/
package com.mobilesorcery.sdk.ui;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
    private static final String BUNDLE_NAME = "com.mobilesorcery.sdk.ui.messages"; //$NON-NLS-1$
    public static String ImportProjectsRunnable_10;
    public static String ImportProjectsRunnable_ImportFailed;
    public static String ImportProjectsRunnable_ImportProgress;
    public static String ImportProjectsRunnable_SomeProjectsFailed;
    public static String ImportProjectsRunnable_UniqueNameCreationFailed;
    
    public static String MosyncUIPlugin_0;
	public static String MosyncUIPlugin_12;
	public static String MosyncUIPlugin_13;

	static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}
