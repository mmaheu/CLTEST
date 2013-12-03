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
public class FragmentExtensionTest
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

    private String[] split(String str, int partSize)
    {
        int strLength = str.length();
        int count = (int)Math.ceil((double)str.length() / partSize);
        String ret[] = new String[count];
        int idx;
        for (int i = 0; i < count; i++)
        {
            idx = (i * partSize);
            ret[i] = str.substring(idx,Math.min(idx + partSize,strLength));
        }
        return ret;
    }

    @Test
    public void testFragmentExtension() throws Exception
    {
        int fragSize = 4;

        BlockheadClient client = new BlockheadClient(server.getServerUri());
        client.clearExtensions();
        client.addExtensions("fragment;maxLength=" + fragSize);
        client.setProtocols("onConnect");

        try
        {
            // Make sure the read times out if there are problems with the implementation
            client.setTimeout(TimeUnit.SECONDS,1);
            client.connect();
            client.sendStandardRequest();
            HttpResponse resp = client.expectUpgradeResponse();

            Assert.assertThat("Response",resp.getExtensionsHeader(),containsString("fragment"));

            String msg = "Sent as a long message that should be split";
            client.write(new TextFrame().setPayload(msg));

            String parts[] = split(msg,fragSize);
            IncomingFramesCapture capture = client.readFrames(parts.length,TimeUnit.MILLISECONDS,1000);
            for (int i = 0; i < parts.length; i++)
            {
                WebSocketFrame frame = capture.getFrames().poll();
                Assert.assertThat("text[" + i + "].payload",frame.getPayloadAsUTF8(),is(parts[i]));
            }
        }
        finally
        {
            client.close();
        }
    }
}
