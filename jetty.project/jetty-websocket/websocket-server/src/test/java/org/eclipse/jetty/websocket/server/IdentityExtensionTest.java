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

package org.eclipse.jetty.websocket.server;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.common.WebSocketFrame;
import org.eclipse.jetty.websocket.common.frames.TextFrame;
import org.eclipse.jetty.websocket.server.blockhead.BlockheadClient;
import org.eclipse.jetty.websocket.server.blockhead.HttpResponse;
import org.eclipse.jetty.websocket.server.helper.EchoServlet;
import org.eclipse.jetty.websocket.server.helper.IncomingFramesCapture;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Bug 395444")
public class IdentityExtensionTest
{
    private static SimpleServletServer server;

    @BeforeClass
    public static void startServer() throws Exception
    {
        server = new SimpleServletServer(new EchoServlet());
        server.start();
    }

    @AfterClass
    public static void stopServer()
    {
        server.stop();
    }

    @Test
    public void testIdentityExtension() throws Exception
    {
        BlockheadClient client = new BlockheadClient(server.getServerUri());
        client.clearExtensions();
        client.addExtensions("identity;param=0");
        client.addExtensions("identity;param=1, identity ; param = '2' ; other = ' some = value '");
        client.setProtocols("onConnect");

        try
        {
            // Make sure the read times out if there are problems with the implementation
            client.setTimeout(TimeUnit.SECONDS,1);
            client.connect();
            client.sendStandardRequest();
            HttpResponse resp = client.expectUpgradeResponse();

            Assert.assertThat("Response",resp.getExtensionsHeader(),containsString("identity"));

            client.write(new TextFrame().setPayload("Hello"));

            IncomingFramesCapture capture = client.readFrames(1,TimeUnit.MILLISECONDS,1000);
            WebSocketFrame frame = capture.getFrames().poll();
            Assert.assertThat("TEXT.payload",frame.getPayloadAsUTF8(),is("Hello"));
        }
        finally
        {
            client.close();
        }
    }
}
