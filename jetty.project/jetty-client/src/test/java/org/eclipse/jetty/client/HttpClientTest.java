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

package org.eclipse.jetty.client;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Destination;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.http.HttpConnectionOverHTTP;
import org.eclipse.jetty.client.http.HttpDestinationOverHTTP;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.toolchain.test.TestingDir;
import org.eclipse.jetty.toolchain.test.annotation.Slow;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import static java.nio.file.StandardOpenOption.CREATE;

public class HttpClientTest extends AbstractHttpClientServerTest
{
    @Rule
    public TestingDir testdir = new TestingDir();

    public HttpClientTest(SslContextFactory sslContextFactory)
    {
        super(sslContextFactory);
    }

    @Test
    public void testStoppingClosesConnections() throws Exception
    {
        start(new EmptyServerHandler());

        String host = "localhost";
        int port = connector.getLocalPort();
        String path = "/";
        Response response = client.GET(scheme + "://" + host + ":" + port + path);
        Assert.assertEquals(200, response.getStatus());

        HttpDestinationOverHTTP destination = (HttpDestinationOverHTTP)client.getDestination(scheme, host, port);
        ConnectionPool connectionPool = destination.getConnectionPool();

        long start = System.nanoTime();
        HttpConnectionOverHTTP connection = null;
        while (connection == null && TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 5)
        {
            connection = (HttpConnectionOverHTTP)connectionPool.getIdleConnections().peek();
            TimeUnit.MILLISECONDS.sleep(10);
        }
        Assert.assertNotNull(connection);

        String uri = destination.getScheme() + "://" + destination.getHost() + ":" + destination.getPort();
        client.getCookieStore().add(URI.create(uri), new HttpCookie("foo", "bar"));

        client.stop();

        Assert.assertEquals(0, client.getDestinations().size());
        Assert.assertEquals(0, connectionPool.getIdleConnections().size());
        Assert.assertEquals(0, connectionPool.getActiveConnections().size());
        Assert.assertFalse(connection.getEndPoint().isOpen());
    }

    @Test
    public void test_DestinationCount() throws Exception
    {
        start(new EmptyServerHandler());

        String host = "localhost";
        int port = connector.getLocalPort();
        client.GET(scheme + "://" + host + ":" + port);

        List<Destination> destinations = client.getDestinations();
        Assert.assertNotNull(destinations);
        Assert.assertEquals(1, destinations.size());
        Destination destination = destinations.get(0);
        Assert.assertNotNull(destination);
        Assert.assertEquals(scheme, destination.getScheme());
        Assert.assertEquals(host, destination.getHost());
        Assert.assertEquals(port, destination.getPort());
    }

    @Test
    public void test_GET_ResponseWithoutContent() throws Exception
    {
        start(new EmptyServerHandler());

        Response response = client.GET(scheme + "://localhost:" + connector.getLocalPort());

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatus());
    }

    @Test
    public void test_GET_ResponseWithContent() throws Exception
    {
        final byte[] data = new byte[]{0, 1, 2, 3, 4, 5, 6, 7};
        start(new AbstractHandler()
        {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
            {
                response.getOutputStream().write(data);
                baseRequest.setHandled(true);
            }
        });

        ContentResponse response = client.GET(scheme + "://localhost:" + connector.getLocalPort());

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatus());
        byte[] content = response.getContent();
        Assert.assertArrayEquals(data, content);
    }

    @Test
    public void test_GET_WithParameters_ResponseWithContent() throws Exception
    {
        final String paramName1 = "a";
        final String paramName2 = "b";
        start(new AbstractHandler()
        {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
            {
                response.setCharacterEncoding("UTF-8");
                ServletOutputStream output = response.getOutputStream();
                String paramValue1 = request.getParameter(paramName1);
                output.write(paramValue1.getBytes(StandardCharsets.UTF_8));
                String paramValue2 = request.getParameter(paramName2);
                Assert.assertEquals("", paramValue2);
                output.write("empty".getBytes(StandardCharsets.UTF_8));
                baseRequest.setHandled(true);
            }
        });

        String value1 = "\u20AC";
        String paramValue1 = URLEncoder.encode(value1, "UTF-8");
        String query = paramName1 + "=" + paramValue1 + "&" + paramName2;
        ContentResponse response = client.GET(scheme + "://localhost:" + connector.getLocalPort() + "/?" + query);

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatus());
        String content = new String(response.getContent(), StandardCharsets.UTF_8);
        Assert.assertEquals(value1 + "empty", content);
    }

    @Test
    public void test_GET_WithParametersMultiValued_ResponseWithContent() throws Exception
    {
        final String paramName1 = "a";
        final String paramName2 = "b";
        start(new AbstractHandler()
        {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
            {
                response.setCharacterEncoding("UTF-8");
                ServletOutputStream output = response.getOutputStream();
                String[] paramValues1 = request.getParameterValues(paramName1);
                for (String paramValue : paramValues1)
                    output.write(paramValue.getBytes(StandardCharsets.UTF_8));
                String paramValue2 = request.getParameter(paramName2);
                output.write(paramValue2.getBytes(StandardCharsets.UTF_8));
                baseRequest.setHandled(true);
            }
        });

        String value11 = "\u20AC";
        String value12 = "\u20AA";
        String value2 = "&";
        String paramValue11 = URLEncoder.encode(value11, "UTF-8");
        String paramValue12 = URLEncoder.encode(value12, "UTF-8");
        String paramValue2 = URLEncoder.encode(value2, "UTF-8");
        String query = paramName1 + "=" + paramValue11 + "&" + paramName1 + "=" + paramValue12 + "&" + paramName2 + "=" + paramValue2;
        ContentResponse response = client.GET(scheme + "://localhost:" + connector.getLocalPort() + "/?" + query);

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatus());
        String content = new String(response.getContent(), StandardCharsets.UTF_8);
        Assert.assertEquals(value11 + value12 + value2, content);
    }

    @Test
    public void test_POST_WithParameters() throws Exception
    {
        final String paramName = "a";
        final String paramValue = "\u20AC";
        start(new AbstractHandler()
        {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
            {
                baseRequest.setHandled(true);
                String value = request.getParameter(paramName);
                if (paramValue.equals(value))
                {
                    response.setCharacterEncoding("UTF-8");
                    response.setContentType("text/plain");
                    response.getOutputStream().print(value);
                }
            }
        });

        ContentResponse response = client.POST(scheme + "://localhost:" + connector.getLocalPort())
                .param(paramName, paramValue)
                .timeout(5, TimeUnit.SECONDS)
                .send();

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals(paramValue, new String(response.getContent(), StandardCharsets.UTF_8));
    }

    @Test
    public void test_PUT_WithParameters() throws Exception
    {
        final String paramName = "a";
        final String paramValue = "\u20AC";
        start(new AbstractHandler()
        {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
            {
                baseRequest.setHandled(true);
                String value = request.getParameter(paramName);
                if (paramValue.equals(value))
                {
                    response.setCharacterEncoding("UTF-8");
                    response.setContentType("text/plain");
                    response.getOutputStream().print(value);
                }
            }
        });

        URI uri = URI.create(scheme + "://localhost:" + connector.getLocalPort() + "/path?" + paramName + "=" + paramValue);
        ContentResponse response = client.newRequest(uri)
                .method(HttpMethod.PUT)
                .timeout(5, TimeUnit.SECONDS)
                .send();

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals(paramValue, new String(response.getContent(), StandardCharsets.UTF_8));
    }

    @Test
    public void test_POST_WithParameters_WithContent() throws Exception
    {
        final byte[] content = {0, 1, 2, 3};
        final String paramName = "a";
        final String paramValue = "\u20AC";
        start(new AbstractHandler()
        {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
            {
                baseRequest.setHandled(true);
                String value = request.getParameter(paramName);
                if (paramValue.equals(value))
                {
                    response.setCharacterEncoding("UTF-8");
                    response.setContentType("text/plain");
                    response.getOutputStream().write(content);
                }
            }
        });

        ContentResponse response = client.POST(scheme + "://localhost:" + connector.getLocalPort() + "/?b=1")
                .param(paramName, paramValue)
                .content(new BytesContentProvider(content))
                .timeout(5, TimeUnit.SECONDS)
                .send();

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertArrayEquals(content, response.getContent());
    }

    @Test
    public void test_POST_WithContent_NotifiesRequestContentListener() throws Exception
    {
        final byte[] content = {0, 1, 2, 3};
        start(new EmptyServerHandler());

        ContentResponse response = client.POST(scheme + "://localhost:" + connector.getLocalPort())
                .onRequestContent(new Request.ContentListener()
                {
                    @Override
                    public void onContent(Request request, ByteBuffer buffer)
                    {
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        if (!Arrays.equals(content, bytes))
                            request.abort(new Exception());
                    }
                })
                .content(new BytesContentProvider(content))
                .timeout(5, TimeUnit.SECONDS)
                .send();

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatus());
    }

    @Test
    public void test_POST_WithContent_TracksProgress() throws Exception
    {
        start(new EmptyServerHandler());

        final AtomicInteger progress = new AtomicInteger();
        ContentResponse response = client.POST(scheme + "://localhost:" + connector.getLocalPort())
                .onRequestContent(new Request.ContentListener()
                {
                    @Override
                    public void onContent(Request request, ByteBuffer buffer)
                    {
                        byte[] bytes = new byte[buffer.remaining()];
                        Assert.assertEquals(1, bytes.length);
                        buffer.get(bytes);
                        Assert.assertEquals(bytes[0], progress.getAndIncrement());
                    }
                })
                .content(new BytesContentProvider(new byte[]{0}, new byte[]{1}, new byte[]{2}, new byte[]{3}, new byte[]{4}))
                .timeout(5, TimeUnit.SECONDS)
                .send();

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals(5, progress.get());
    }

    @Test
    public void test_QueuedRequest_IsSent_WhenPreviousRequestSucceeded() throws Exception
    {
        start(new EmptyServerHandler());

        client.setMaxConnectionsPerDestination(1);

        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch successLatch = new CountDownLatch(2);
        client.newRequest("localhost", connector.getLocalPort())
                .scheme(scheme)
                .onRequestBegin(new Request.BeginListener()
                {
                    @Override
                    public void onBegin(Request request)
                    {
                        try
                        {
                            latch.await();
                        }
                        catch (InterruptedException x)
                        {
                            x.printStackTrace();
                        }
                    }
                })
                .send(new Response.Listener.Adapter()
                {
                    @Override
                    public void onSuccess(Response response)
                    {
                        Assert.assertEquals(200, response.getStatus());
                        successLatch.countDown();
                    }
                });

        client.newRequest("localhost", connector.getLocalPort())
                .scheme(scheme)
                .onRequestQueued(new Request.QueuedListener()
                {
                    @Override
                    public void onQueued(Request request)
                    {
                        latch.countDown();
                    }
                })
                .send(new Response.Listener.Adapter()
                {
                    @Override
                    public void onSuccess(Response response)
                    {
                        Assert.assertEquals(200, response.getStatus());
                        successLatch.countDown();
                    }
                });

        Assert.assertTrue(successLatch.await(5, TimeUnit.SECONDS));
    }

    @Slow
    @Test
    public void test_QueuedRequest_IsSent_WhenPreviousRequestClosedConnection() throws Exception
    {
        start(new EmptyServerHandler());

        client.setMaxConnectionsPerDestination(1);
        final long idleTimeout = 1000;
        client.setIdleTimeout(idleTimeout);

        final CountDownLatch latch = new CountDownLatch(3);
        client.newRequest("localhost", connector.getLocalPort())
                .scheme(scheme)
                .path("/one")
                .listener(new Request.Listener.Adapter()
                {
                    @Override
                    public void onBegin(Request request)
                    {
                        try
                        {
                            TimeUnit.MILLISECONDS.sleep(2 * idleTimeout);
                        }
                        catch (InterruptedException x)
                        {
                            x.printStackTrace();
                        }
                    }

                    @Override
                    public void onFailure(Request request, Throwable failure)
                    {
                        latch.countDown();
                    }
                })
                .onResponseFailure(new Response.FailureListener()
                {
                    @Override
                    public void onFailure(Response response, Throwable failure)
                    {
                        latch.countDown();
                    }
                })
                .send(null);

        client.newRequest("localhost", connector.getLocalPort())
                .scheme(scheme)
                .path("/two")
                .onResponseSuccess(new Response.SuccessListener()
                {
                    @Override
                    public void onSuccess(Response response)
                    {
                        Assert.assertEquals(200, response.getStatus());
                        latch.countDown();
                    }
                })
                .send(null);

        Assert.assertTrue(latch.await(5 * idleTimeout, TimeUnit.MILLISECONDS));
    }

    @Slow
    @Test
    public void test_ExchangeIsComplete_OnlyWhenBothRequestAndResponseAreComplete() throws Exception
    {
        start(new EmptyServerHandler());

        // Prepare a big file to upload
        Path targetTestsDir = testdir.getEmptyDir().toPath();
        Files.createDirectories(targetTestsDir);
        Path file = Paths.get(targetTestsDir.toString(), "http_client_conversation.big");
        try (OutputStream output = Files.newOutputStream(file, CREATE))
        {
            byte[] kb = new byte[1024];
            for (int i = 0; i < 10 * 1024; ++i)
                output.write(kb);
        }

        final CountDownLatch latch = new CountDownLatch(3);
        final AtomicLong exchangeTime = new AtomicLong();
        final AtomicLong requestTime = new AtomicLong();
        final AtomicLong responseTime = new AtomicLong();
        client.newRequest("localhost", connector.getLocalPort())
                .scheme(scheme)
                .file(file)
                .onRequestSuccess(new Request.SuccessListener()
                {
                    @Override
                    public void onSuccess(Request request)
                    {
                        requestTime.set(System.nanoTime());
                        latch.countDown();
                    }
                })
                .send(new Response.Listener.Adapter()
                {
                    @Override
                    public void onSuccess(Response response)
                    {
                        responseTime.set(System.nanoTime());
                        latch.countDown();
                    }

                    @Override
                    public void onComplete(Result result)
                    {
                        exchangeTime.set(System.nanoTime());
                        latch.countDown();
                    }
                });

        Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));

        Assert.assertTrue(requestTime.get() <= exchangeTime.get());
        Assert.assertTrue(responseTime.get() <= exchangeTime.get());

        // Give some time to the server to consume the request content
        // This is just to avoid exception traces in the test output
        Thread.sleep(1000);

        Files.delete(file);
    }

    @Test
    public void test_ExchangeIsComplete_WhenRequestFailsMidway_WithResponse() throws Exception
    {
        start(new AbstractHandler()
        {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
            {
                // Echo back
                IO.copy(request.getInputStream(), response.getOutputStream());
            }
        });

        final CountDownLatch latch = new CountDownLatch(1);
        client.newRequest("localhost", connector.getLocalPort())
                .scheme(scheme)
                // The second ByteBuffer set to null will throw an exception
                .content(new ContentProvider()
                {
                    @Override
                    public long getLength()
                    {
                        return -1;
                    }

                    @Override
                    public Iterator<ByteBuffer> iterator()
                    {
                        return new Iterator<ByteBuffer>()
                        {
                            @Override
                            public boolean hasNext()
                            {
                                return true;
                            }

                            @Override
                            public ByteBuffer next()
                            {
                                throw new NoSuchElementException("explicitly_thrown_by_test");
                            }

                            @Override
                            public void remove()
                            {
                                throw new UnsupportedOperationException();
                            }
                        };
                    }
                })
                .send(new Response.Listener.Adapter()
                {
                    @Override
                    public void onComplete(Result result)
                    {
                        latch.countDown();
                    }
                });

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void test_ExchangeIsComplete_WhenRequestFails_WithNoResponse() throws Exception
    {
        start(new EmptyServerHandler());

        final CountDownLatch latch = new CountDownLatch(1);
        final String host = "localhost";
        final int port = connector.getLocalPort();
        client.newRequest(host, port)
                .scheme(scheme)
                .onRequestBegin(new Request.BeginListener()
                {
                    @Override
                    public void onBegin(Request request)
                    {
                        HttpDestinationOverHTTP destination = (HttpDestinationOverHTTP)client.getDestination(scheme, host, port);
                        destination.getConnectionPool().getActiveConnections().peek().close();
                    }
                })
                .send(new Response.Listener.Adapter()
                {
                    @Override
                    public void onComplete(Result result)
                    {
                        latch.countDown();
                    }
                });

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void test_GZIP_ContentEncoding() throws Exception
    {
        final byte[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        start(new AbstractHandler()
        {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
            {
                baseRequest.setHandled(true);
                response.setHeader("Content-Encoding", "gzip");
                GZIPOutputStream gzipOutput = new GZIPOutputStream(response.getOutputStream());
                gzipOutput.write(data);
                gzipOutput.finish();
            }
        });

        ContentResponse response = client.newRequest("localhost", connector.getLocalPort())
                .scheme(scheme)
                .timeout(5, TimeUnit.SECONDS)
                .send();

        Assert.assertEquals(200, response.getStatus());
        Assert.assertArrayEquals(data, response.getContent());
    }

    @Slow
    @Test
    public void test_Request_IdleTimeout() throws Exception
    {
        final long idleTimeout = 1000;
        start(new AbstractHandler()
        {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
            {
                try
                {
                    baseRequest.setHandled(true);
                    TimeUnit.MILLISECONDS.sleep(2 * idleTimeout);
                }
                catch (InterruptedException x)
                {
                    throw new ServletException(x);
                }
            }
        });

        final String host = "localhost";
        final int port = connector.getLocalPort();
        try
        {
            client.newRequest(host, port)
                    .scheme(scheme)
                    .idleTimeout(idleTimeout, TimeUnit.MILLISECONDS)
                    .timeout(3 * idleTimeout, TimeUnit.MILLISECONDS)
                    .send();
            Assert.fail();
        }
        catch (ExecutionException expected)
        {
            Assert.assertTrue(expected.getCause() instanceof TimeoutException);
        }

        // Make another request without specifying the idle timeout, should not fail
        ContentResponse response = client.newRequest(host, port)
                .scheme(scheme)
                .timeout(3 * idleTimeout, TimeUnit.MILLISECONDS)
                .send();

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatus());
    }

    @Test
    public void testSendToIPv6Address() throws Exception
    {
        start(new EmptyServerHandler());

        ContentResponse response = client.newRequest("[::1]", connector.getLocalPort())
                .scheme(scheme)
                .timeout(5, TimeUnit.SECONDS)
                .send();

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatus());
    }

    @Test
    public void testHeaderProcessing() throws Exception
    {
        final String headerName = "X-Header-Test";
        start(new AbstractHandler()
        {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
            {
                baseRequest.setHandled(true);
                response.setHeader(headerName, "X");
            }
        });

        ContentResponse response = client.newRequest("localhost", connector.getLocalPort())
                .scheme(scheme)
                .onResponseHeader(new Response.HeaderListener()
                {
                    @Override
                    public boolean onHeader(Response response, HttpField field)
                    {
                        return !field.getName().equals(headerName);
                    }
                })
                .timeout(5, TimeUnit.SECONDS)
                .send();

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertFalse(response.getHeaders().containsKey(headerName));
    }

    @Test
    public void test_HEAD_With_ResponseContentLength() throws Exception
    {
        final int length = 1024;
        start(new AbstractHandler()
        {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
            {
                baseRequest.setHandled(true);
                response.getOutputStream().write(new byte[length]);
            }
        });

        // HEAD requests receive a Content-Length header, but do not
        // receive the content so they must handle this case properly
        ContentResponse response = client.newRequest("localhost", connector.getLocalPort())
                .scheme(scheme)
                .method(HttpMethod.HEAD)
                .timeout(5, TimeUnit.SECONDS)
                .send();

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals(0, response.getContent().length);

        // Perform a normal GET request to be sure the content is now read
        response = client.newRequest("localhost", connector.getLocalPort())
                .scheme(scheme)
                .timeout(5, TimeUnit.SECONDS)
                .send();

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals(length, response.getContent().length);
    }

    @Test
    public void testConnectThrowsUnresolvedAddressException() throws Exception
    {
        start(new EmptyServerHandler());

        final CountDownLatch latch = new CountDownLatch(1);
        client.newRequest("idontexist", 80)
                .send(new Response.CompleteListener()
                {
                    @Override
                    public void onComplete(Result result)
                    {
                        Assert.assertTrue(result.isFailed());
                        Assert.assertTrue(result.getFailure() instanceof UnresolvedAddressException);
                        latch.countDown();
                    }
                });
        Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testCustomUserAgent() throws Exception
    {
        final String userAgent = "Test/1.0";
        start(new AbstractHandler()
        {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
            {
                baseRequest.setHandled(true);
                ArrayList<String> userAgents = Collections.list(request.getHeaders("User-Agent"));
                Assert.assertEquals(1, userAgents.size());
                Assert.assertEquals(userAgent, userAgents.get(0));
            }
        });

        ContentResponse response = client.newRequest("localhost", connector.getLocalPort())
                .scheme(scheme)
                .agent(userAgent)
                .timeout(5, TimeUnit.SECONDS)
                .send();

        Assert.assertEquals(200, response.getStatus());

        response = client.newRequest("localhost", connector.getLocalPort())
                .scheme(scheme)
                .header(HttpHeader.USER_AGENT, userAgent)
                .timeout(5, TimeUnit.SECONDS)
                .send();

        Assert.assertEquals(200, response.getStatus());
    }

    @Test
    public void testRequestListenerForMultipleEventsIsInvokedOncePerEvent() throws Exception
    {
        start(new EmptyServerHandler());

        final AtomicInteger counter = new AtomicInteger();
        Request.Listener listener = new Request.Listener()
        {
            @Override
            public void onQueued(Request request)
            {
                counter.incrementAndGet();
            }

            @Override
            public void onBegin(Request request)
            {
                counter.incrementAndGet();
            }

            @Override
            public void onHeaders(Request request)
            {
                counter.incrementAndGet();
            }

            @Override
            public void onCommit(Request request)
            {
                counter.incrementAndGet();
            }

            @Override
            public void onContent(Request request, ByteBuffer content)
            {
                // Should not be invoked
                counter.incrementAndGet();
            }

            @Override
            public void onFailure(Request request, Throwable failure)
            {
                // Should not be invoked
                counter.incrementAndGet();
            }

            @Override
            public void onSuccess(Request request)
            {
                counter.incrementAndGet();
            }
        };
        ContentResponse response = client.newRequest("localhost", connector.getLocalPort())
                .scheme(scheme)
                .onRequestQueued(listener)
                .onRequestBegin(listener)
                .onRequestHeaders(listener)
                .onRequestCommit(listener)
                .onRequestContent(listener)
                .onRequestSuccess(listener)
                .onRequestFailure(listener)
                .listener(listener)
                .send();

        Assert.assertEquals(200, response.getStatus());
        int expectedEventsTriggeredByOnRequestXXXListeners = 5;
        int expectedEventsTriggeredByListener = 5;
        int expected = expectedEventsTriggeredByOnRequestXXXListeners + expectedEventsTriggeredByListener;
        Assert.assertEquals(expected, counter.get());
    }

    @Test
    public void testResponseListenerForMultipleEventsIsInvokedOncePerEvent() throws Exception
    {
        start(new EmptyServerHandler());

        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);
        Response.Listener listener = new Response.Listener()
        {
            @Override
            public void onBegin(Response response)
            {
                counter.incrementAndGet();
            }

            @Override
            public boolean onHeader(Response response, HttpField field)
            {
                // Number of header may vary, so don't count
                return true;
            }

            @Override
            public void onHeaders(Response response)
            {
                counter.incrementAndGet();
            }

            @Override
            public void onContent(Response response, ByteBuffer content)
            {
                // Should not be invoked
                counter.incrementAndGet();
            }

            @Override
            public void onSuccess(Response response)
            {
                counter.incrementAndGet();
            }

            @Override
            public void onFailure(Response response, Throwable failure)
            {
                // Should not be invoked
                counter.incrementAndGet();
            }

            @Override
            public void onComplete(Result result)
            {
                Assert.assertEquals(200, result.getResponse().getStatus());
                counter.incrementAndGet();
                latch.countDown();
            }
        };
        client.newRequest("localhost", connector.getLocalPort())
                .scheme(scheme)
                .onResponseBegin(listener)
                .onResponseHeader(listener)
                .onResponseHeaders(listener)
                .onResponseContent(listener)
                .onResponseSuccess(listener)
                .onResponseFailure(listener)
                .send(listener);

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
        int expectedEventsTriggeredByOnResponseXXXListeners = 3;
        int expectedEventsTriggeredByCompletionListener = 4;
        int expected = expectedEventsTriggeredByOnResponseXXXListeners + expectedEventsTriggeredByCompletionListener;
        Assert.assertEquals(expected, counter.get());
    }

    @Test
    public void setOnCompleteCallbackWithBlockingSend() throws Exception
    {
        final byte[] content = new byte[512];
        new Random().nextBytes(content);
        start(new AbstractHandler()
        {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
            {
                baseRequest.setHandled(true);
                response.getOutputStream().write(content);
            }
        });

        final AtomicInteger complete = new AtomicInteger();
        BufferingResponseListener listener = new BufferingResponseListener()
        {
            @Override
            public void onComplete(Result result)
            {
                complete.incrementAndGet();
            }
        };

        ContentResponse response = client.newRequest("localhost", connector.getLocalPort())
                .scheme(scheme)
                .onResponseContent(listener)
                .onComplete(listener)
                .send();

        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals(1, complete.get());
        Assert.assertArrayEquals(content, listener.getContent());
        Assert.assertArrayEquals(content, response.getContent());
    }
}
