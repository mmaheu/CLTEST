//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.maven.plugin;

/**
 * <p>
 * This goal is used to run Jetty with a pre-assembled war.
 * </p>
 * <p>
 * It accepts exactly the same options as the <a href="run-war-mojo.html">run-war</a> goal. 
 * However, it doesn't assume that the current artifact is a
 * webapp and doesn't try to assemble it into a war before its execution. 
 * So using it makes sense only when used in conjunction with the 
 * <a href="run-war-mojo.html#webApp">webApp</a> configuration parameter pointing to a pre-built WAR.
 * </p>
 * <p>
 * This goal is useful e.g. for launching a web app in Jetty as a target for unit-tested 
 * HTTP client components.
 * </p>
 * 
 * @goal deploy-war
 * @requiresDependencyResolution runtime
 * @execute phase="validate"
 * @description Deploy a pre-assembled war
 * 
 */
public class JettyDeployWar extends JettyRunWarMojo
{
}
