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

package org.eclipse.jetty.start;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.toolchain.test.MavenTestingUtils;
import org.junit.Test;

/**
 * Various Home + Base use cases
 */
public class TestUseCases
{
    private void assertUseCase(String homeName, String baseName, String assertName) throws Exception
    {
        File homeDir = MavenTestingUtils.getTestResourceDir("usecases/" + homeName);
        File baseDir = MavenTestingUtils.getTestResourceDir("usecases/" + baseName);

        Main main = new Main();
        List<String> cmdLine = new ArrayList<>();
        cmdLine.add("jetty.home=" + homeDir.getAbsolutePath());
        cmdLine.add("jetty.base=" + baseDir.getAbsolutePath());
        StartArgs args = main.processCommandLine(cmdLine);
        BaseHome baseHome = main.getBaseHome();
        ConfigurationAssert.assertConfiguration(baseHome,args,"usecases/" + assertName);
    }

    @Test
    public void testBarebones() throws Exception
    {
        assertUseCase("home","base.barebones","assert-barebones.txt");
    }
    
    @Test
    public void testJMX() throws Exception
    {
        assertUseCase("home","base.jmx","assert-jmx.txt");
    }
    
    @Test
    public void testWithSpdy() throws Exception
    {
        assertUseCase("home","base.enable.spdy","assert-enable-spdy.txt");
    }
    
    @Test
    public void testWithDatabase() throws Exception
    {
        assertUseCase("home","base.with.db","assert-with-db.txt");
    }
}
