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

package org.eclipse.jetty.servlets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.toolchain.test.TestingDir;
import org.eclipse.jetty.util.resource.Resource;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DataRateLimitedServletTest
{
    public static final int BUFFER=8192;
    public static final int PAUSE=10;
    
    @Rule
    public TestingDir testdir = new TestingDir();

    private Server server;
    private LocalConnector connector;
    private ServletContextHandler context;

    @Before
    public void init() throws Exception
    {
        server = new Server();

        connector = new LocalConnector(server);
        connector.getConnectionFactory(HttpConfiguration.ConnectionFactory.class).getHttpConfiguration().setSendServerVersion(false);

        context = new ServletContextHandler();
 
        context.setContextPath("/context");
        context.setWelcomeFiles(new String[]{"index.html", "index.jsp", "index.htm"});
        context.setBaseResource(Resource.newResource(testdir.getEmptyDir()));
        
        ServletHolder holder =context.addServlet(DataRateLimitedServlet.class,"/stream/*");
        holder.setInitParameter("buffersize",""+BUFFER);
        holder.setInitParameter("pause",""+PAUSE);
        server.setHandler(context);
        server.addConnector(connector);

        server.start();
    }

    @After
    public void destroy() throws Exception
    {
        server.stop();
        server.join();
    }

    @Test
    public void testStream() throws Exception
    {
        File content = testdir.getFile("content.txt");
        try(OutputStream out = new FileOutputStream(content);)
        {
            byte[] b= new byte[1024];
            
            for (int i=1024;i-->0;)
            {
                Arrays.fill(b,(byte)('0'+(i%10)));
                out.write(b);
                out.write('\n');
            }
        }
        
        long start=System.currentTimeMillis();
        String response = connector.getResponses("GET /context/stream/content.txt HTTP/1.0\r\n\r\n");
        long duration=System.currentTimeMillis()-start;
        
        assertThat(response.length(),greaterThan(1024*1024));
        assertThat(response,containsString("200 OK"));
        assertThat(duration,greaterThan(PAUSE*1024L*1024/BUFFER));
        
    }
}
