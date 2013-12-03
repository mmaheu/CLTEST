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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.Assert;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlets.gzip.CompressedResponseWrapper;
import org.eclipse.jetty.servlets.gzip.GzipTester;
import org.eclipse.jetty.toolchain.test.TestingDir;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test the GzipFilter support built into the {@link DefaultServlet}
 */
@RunWith(Parameterized.class)
public class GzipFilterDefaultTest
{
    @Parameters
    public static Collection<String[]> data()
    {
        String[][] data = new String[][]
        {
        { GzipFilter.GZIP },
        { GzipFilter.DEFLATE } };

        return Arrays.asList(data);
    }

    private String compressionType;

    public GzipFilterDefaultTest(String compressionType)
    {
        this.compressionType = compressionType;
    }

    public static class HttpStatusServlet extends HttpServlet
    {
        private int _status = 204;

        public HttpStatusServlet()
        {
            super();
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
        {
            resp.setStatus(_status);
            resp.setHeader("ETag","W/\"204\"");
        }

    }

    public static class HttpErrorServlet extends HttpServlet
    {
        private int _status = 400;

        public HttpErrorServlet()
        {
            super();
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
        {
            resp.getOutputStream().write("error message".getBytes());
            resp.setStatus(_status);
        }
    }
    

    public static class HttpContentTypeWithEncoding extends HttpServlet
    {
        public static final String COMPRESSED_CONTENT = "<html><head></head><body><h1>COMPRESSED</h1></body></html>";
        
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
        {
            resp.setContentType("text/plain;charset=UTF8");
            resp.setStatus(200);
            ServletOutputStream out = resp.getOutputStream();
            out.print(COMPRESSED_CONTENT);
        }

    }

    @Rule
    public TestingDir testingdir = new TestingDir();

    

    @Test
    public void testIsGzipByMethod() throws Exception
    {
        GzipTester tester = new GzipTester(testingdir, compressionType);

        // Test content that is smaller than the buffer.
        int filesize = CompressedResponseWrapper.DEFAULT_BUFFER_SIZE * 2;
        tester.prepareServerFile("file.txt",filesize);
        
        FilterHolder holder = tester.setContentServlet(GetServlet.class);
        holder.setInitParameter("mimeTypes","text/plain");
        holder.setInitParameter("methods","POST, WIBBLE");
                
        try
        {
            tester.start();
            tester.assertIsResponseGzipCompressed("POST","file.txt");
            tester.assertIsResponseGzipCompressed("WIBBLE","file.txt");
            tester.assertIsResponseNotGzipCompressed("GET","file.txt",filesize,200);
        }
        finally
        {
            tester.stop();
        }
    }
    
    public static class GetServlet extends DefaultServlet
    {
        public GetServlet()
        {    
            super();
        }
        
        @Override
        public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException,ServletException
        {
            String uri=req.getRequestURI();
            if (uri.endsWith(".deferred"))
            {
                // System.err.println("type for "+uri.substring(0,uri.length()-9)+" is "+getServletContext().getMimeType(uri.substring(0,uri.length()-9)));
                resp.setContentType(getServletContext().getMimeType(uri.substring(0,uri.length()-9)));
            }
            
            doGet(req,resp);
        }
    }
    
   
    
    @Test
    public void testIsGzipCompressedEmpty() throws Exception
    {
        GzipTester tester = new GzipTester(testingdir, compressionType);

        // Test content that is smaller than the buffer.
        tester.prepareServerFile("empty.txt",0);
        
        FilterHolder holder = tester.setContentServlet(org.eclipse.jetty.servlet.DefaultServlet.class);
        holder.setInitParameter("mimeTypes","text/plain");

        try
        {
            tester.start();
            HttpTester.Response http = tester.assertIsResponseNotGzipCompressed("GET","empty.txt",0,200);
        }
        finally
        {
            tester.stop();
        }
    }
    
    @Test
    public void testIsGzipCompressedTiny() throws Exception
    {
        GzipTester tester = new GzipTester(testingdir, compressionType);

        // Test content that is smaller than the buffer.
        int filesize = CompressedResponseWrapper.DEFAULT_BUFFER_SIZE / 4;
        tester.prepareServerFile("file.txt",filesize);

        FilterHolder holder = tester.setContentServlet(org.eclipse.jetty.servlet.DefaultServlet.class);
        holder.setInitParameter("mimeTypes","text/plain");

        try
        {
            tester.start();
            HttpTester.Response http = tester.assertIsResponseGzipCompressed("GET","file.txt");
            Assert.assertEquals("Accept-Encoding",http.get("Vary"));
        }
        finally
        {
            tester.stop();
        }
    }

    @Test
    public void testIsGzipCompressedTinyWithQ() throws Exception
    {
        GzipTester tester = new GzipTester(testingdir, compressionType+";q=0.5");

        // Test content that is smaller than the buffer.
        int filesize = CompressedResponseWrapper.DEFAULT_BUFFER_SIZE / 4;
        tester.prepareServerFile("file.txt",filesize);
        
        FilterHolder holder = tester.setContentServlet(org.eclipse.jetty.servlet.DefaultServlet.class);
        holder.setInitParameter("mimeTypes","text/plain");

        try
        {
            tester.start();
            HttpTester.Response http = tester.assertIsResponseGzipCompressed("GET","file.txt");
            Assert.assertEquals("Accept-Encoding",http.get("Vary"));
        }
        finally
        {
            tester.stop();
        }
    }
    
    @Test
    public void testIsGzipCompressedTinyWithBadQ() throws Exception
    {
        GzipTester tester = new GzipTester(testingdir, compressionType+";q=");

        // Test content that is smaller than the buffer.
        int filesize = CompressedResponseWrapper.DEFAULT_BUFFER_SIZE / 4;
        tester.prepareServerFile("file.txt",filesize);
        
        FilterHolder holder = tester.setContentServlet(org.eclipse.jetty.servlet.DefaultServlet.class);
        holder.setInitParameter("mimeTypes","text/plain");

        try
        {
            tester.start();
            HttpTester.Response http = tester.assertIsResponseGzipCompressed("GET","file.txt");
            Assert.assertEquals("Accept-Encoding",http.get("Vary"));
        }
        finally
        {
            tester.stop();
        }
    }
    
    @Test
    public void testIsGzipCompressedLarge() throws Exception
    {
        GzipTester tester = new GzipTester(testingdir, compressionType);

        // Test content that is smaller than the buffer.
        int filesize = CompressedResponseWrapper.DEFAULT_BUFFER_SIZE * 4;
        tester.prepareServerFile("file.txt",filesize);

        FilterHolder holder = tester.setContentServlet(org.eclipse.jetty.servlet.DefaultServlet.class);
        holder.setInitParameter("mimeTypes","text/plain");

        try
        {
            tester.start();
            HttpTester.Response http = tester.assertIsResponseGzipCompressed("GET","file.txt");
            Assert.assertEquals("Accept-Encoding",http.get("Vary"));
        }
        finally
        {
            tester.stop();
        }
    }
    

    @Test
    public void testGzipedIfModified() throws Exception
    {
        GzipTester tester = new GzipTester(testingdir, compressionType);

        // Test content that is smaller than the buffer.
        int filesize = CompressedResponseWrapper.DEFAULT_BUFFER_SIZE * 4;
        tester.prepareServerFile("file.txt",filesize);
        
        FilterHolder holder = tester.setContentServlet(org.eclipse.jetty.servlet.DefaultServlet.class);
        holder.setInitParameter("mimeTypes","text/plain");

        try
        {
            tester.start();
            HttpTester.Response http = tester.assertIsResponseGzipCompressed("GET","file.txt",System.currentTimeMillis()-4000);
            Assert.assertEquals("Accept-Encoding",http.get("Vary"));
        }
        finally
        {
            tester.stop();
        }
    }
    

    @Test
    public void testNotGzipedIfNotModified() throws Exception
    {
        GzipTester tester = new GzipTester(testingdir, compressionType);

        // Test content that is smaller than the buffer.
        int filesize = CompressedResponseWrapper.DEFAULT_BUFFER_SIZE * 4;
        tester.prepareServerFile("file.txt",filesize);
        
        FilterHolder holder = tester.setContentServlet(org.eclipse.jetty.servlet.DefaultServlet.class);
        holder.setInitParameter("mimeTypes","text/plain");
        holder.setInitParameter("etags","true");

        try
        {
            tester.start();
            tester.assertIsResponseNotModified("GET","file.txt",System.currentTimeMillis()+4000);
        }
        finally
        {
            tester.stop();
        }
    }
    

    @Test
    public void testIsNotGzipCompressedWithQ() throws Exception
    {
        GzipTester tester = new GzipTester(testingdir, compressionType+"; q = 0");
        
        int filesize = CompressedResponseWrapper.DEFAULT_BUFFER_SIZE / 4;
        tester.prepareServerFile("file.txt",filesize);
        
        FilterHolder holder = tester.setContentServlet(org.eclipse.jetty.servlet.DefaultServlet.class);
        holder.setInitParameter("mimeTypes","text/plain");

        try
        {
            tester.start();
            HttpTester.Response http = tester.assertIsResponseNotGzipCompressed("GET","file.txt", filesize, HttpStatus.OK_200);
            Assert.assertEquals("Accept-Encoding",http.get("Vary"));
        }
        finally
        {
            tester.stop();
        }
    }
    
    @Test
    public void testIsNotGzipCompressedByContentType() throws Exception
    {
        GzipTester tester = new GzipTester(testingdir, compressionType);

        int filesize = CompressedResponseWrapper.DEFAULT_BUFFER_SIZE * 4;
        tester.prepareServerFile("file.mp3",filesize);

        FilterHolder holder = tester.setContentServlet(org.eclipse.jetty.servlet.DefaultServlet.class);
        holder.setInitParameter("mimeTypes","text/plain");

        try
        {
            tester.start();
            HttpTester.Response http = tester.assertIsResponseNotGzipCompressed("GET","file.mp3", filesize, HttpStatus.OK_200);
            Assert.assertNull(http.get("Vary"));
        }
        finally
        {
            tester.stop();
        }
    }
    
    @Test
    public void testGzipCompressedByContentTypeWithEncoding() throws Exception
    { 
        GzipTester tester = new GzipTester(testingdir, compressionType);
        FilterHolder holder = tester.setContentServlet(HttpContentTypeWithEncoding.class);
        holder.setInitParameter("mimeTypes","text/plain");
        try
        {
            tester.start();
            HttpTester.Response http = tester.assertNonStaticContentIsResponseGzipCompressed("GET","xxx", HttpContentTypeWithEncoding.COMPRESSED_CONTENT);
            Assert.assertEquals("Accept-Encoding",http.get("Vary"));
            System.err.println(http.get("Content-Type"));
            System.err.println(http.get("Content-Encoding"));
        }
        finally
        {
            tester.stop();
        }
    }
    
    
    @Test
    public void testIsNotGzipCompressedByDeferredContentType() throws Exception
    {
        GzipTester tester = new GzipTester(testingdir, compressionType);

        int filesize = CompressedResponseWrapper.DEFAULT_BUFFER_SIZE * 4;
        tester.prepareServerFile("file.mp3.deferred",filesize);
        
        FilterHolder holder = tester.setContentServlet(GetServlet.class);
        holder.setInitParameter("mimeTypes","text/plain");

        try
        {
            tester.start();
            HttpTester.Response http = tester.assertIsResponseNotGzipCompressed("GET","file.mp3.deferred", filesize, HttpStatus.OK_200);
            Assert.assertNull(http.get("Vary"));
        }
        finally
        {
            tester.stop();
        }
    }
    
    @Test
    public void testIsNotGzipCompressedHttpStatus() throws Exception
    {
        GzipTester tester = new GzipTester(testingdir, compressionType);

        // Test error code 204
        FilterHolder holder = tester.setContentServlet(HttpStatusServlet.class);
        holder.setInitParameter("mimeTypes","text/plain");

        try
        {
            tester.start();
            tester.assertIsResponseNotGzipCompressed("GET",-1, 204);
        }
        finally
        {
            tester.stop();
        }

    }

    @Test
    public void testIsNotGzipCompressedHttpBadRequestStatus() throws Exception
    {
        GzipTester tester = new GzipTester(testingdir, compressionType);

        // Test error code 400
        FilterHolder holder = tester.setContentServlet(HttpErrorServlet.class);
        holder.setInitParameter("mimeTypes","text/plain");

        try
        {
            tester.start();
            tester.assertIsResponseNotGzipCompressedAndEqualToExpectedString("GET","error message", -1, 400);
        }
        finally
        {
            tester.stop();
        }

    }

    @Test
    public void testUserAgentExclusion() throws Exception
    {
        GzipTester tester = new GzipTester(testingdir,compressionType);

        FilterHolder holder = tester.setContentServlet(DefaultServlet.class);
        holder.setInitParameter("excludedAgents","bar, foo");
        tester.setUserAgent("foo");

        int filesize = CompressedResponseWrapper.DEFAULT_BUFFER_SIZE * 4;
        tester.prepareServerFile("file.txt",filesize);

        try
        {
            tester.start();
            tester.assertIsResponseNotGzipCompressed("GET","file.txt",filesize,HttpStatus.OK_200);
        }
        finally
        {
            tester.stop();
        }
    }

    @Test
    public void testUserAgentExclusionByExcludedAgentPatterns() throws Exception
    {
        GzipTester tester = new GzipTester(testingdir,compressionType);

        FilterHolder holder = tester.setContentServlet(DefaultServlet.class);
        holder.setInitParameter("excludedAgents","bar");
        holder.setInitParameter("excludeAgentPatterns","fo.*");
        tester.setUserAgent("foo");

        int filesize = CompressedResponseWrapper.DEFAULT_BUFFER_SIZE * 4;
        tester.prepareServerFile("file.txt",filesize);

        try
        {
            tester.start();
            tester.assertIsResponseNotGzipCompressed("GET","file.txt",filesize,HttpStatus.OK_200);
        }
        finally
        {
            tester.stop();
        }
    }

    @Test
    public void testExcludePaths() throws Exception
    {
        GzipTester tester = new GzipTester(testingdir,compressionType);

        FilterHolder holder = tester.setContentServlet(DefaultServlet.class);
        holder.setInitParameter("excludePaths","/bar/, /context/");

        int filesize = CompressedResponseWrapper.DEFAULT_BUFFER_SIZE * 4;
        tester.prepareServerFile("file.txt",filesize);

        try
        {
            tester.start();
            tester.assertIsResponseNotGzipCompressed("GET","file.txt",filesize,HttpStatus.OK_200);
        }
        finally
        {
            tester.stop();
        }
    }

    @Test
    public void testExcludePathPatterns() throws Exception
    {
        GzipTester tester = new GzipTester(testingdir,compressionType);

        FilterHolder holder = tester.setContentServlet(DefaultServlet.class);
        holder.setInitParameter("excludePathPatterns","/cont.*");

        int filesize = CompressedResponseWrapper.DEFAULT_BUFFER_SIZE * 4;
        tester.prepareServerFile("file.txt",filesize);

        try
        {
            tester.start();
            tester.assertIsResponseNotGzipCompressed("GET","file.txt",filesize,HttpStatus.OK_200);
        }
        finally
        {
            tester.stop();
        }
    }
}
