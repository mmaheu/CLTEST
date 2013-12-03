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

package org.eclipse.jetty.websocket.common.extensions;

import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.api.extensions.Extension;
import org.eclipse.jetty.websocket.api.extensions.ExtensionConfig;
import org.eclipse.jetty.websocket.api.extensions.ExtensionFactory;
import org.eclipse.jetty.websocket.common.extensions.identity.IdentityExtension;
import org.junit.Assert;
import org.junit.Test;

public class ExtensionStackTest
{
    private static final Logger LOG = Log.getLogger(ExtensionStackTest.class);

    @SuppressWarnings("unchecked")
    private <T> T assertIsExtension(String msg, Object obj, Class<T> clazz)
    {
        if (clazz.isAssignableFrom(obj.getClass()))
        {
            return (T)obj;
        }
        Assert.assertEquals("Expected " + msg + " class",clazz.getName(),obj.getClass().getName());
        return null;
    }

    private ExtensionStack createExtensionStack()
    {
        WebSocketPolicy policy = WebSocketPolicy.newClientPolicy();
        ByteBufferPool bufferPool = new ArrayByteBufferPool();
        ExtensionFactory factory = new WebSocketExtensionFactory(policy,bufferPool);
        return new ExtensionStack(factory);
    }

    @Test
    public void testStartIdentity() throws Exception
    {
        ExtensionStack stack = createExtensionStack();
        try
        {
            // 1 extension
            List<ExtensionConfig> configs = new ArrayList<>();
            configs.add(ExtensionConfig.parse("identity"));
            stack.negotiate(configs);

            // Setup Listeners
            DummyIncomingFrames session = new DummyIncomingFrames("Session");
            DummyOutgoingFrames connection = new DummyOutgoingFrames("Connection");
            stack.setNextOutgoing(connection);
            stack.setNextIncoming(session);

            // Start
            stack.start();

            // Dump
            LOG.debug("{}",stack.dump());

            // Should be no change to handlers
            Extension actualIncomingExtension = assertIsExtension("Incoming",stack.getNextIncoming(),IdentityExtension.class);
            Extension actualOutgoingExtension = assertIsExtension("Outgoing",stack.getNextOutgoing(),IdentityExtension.class);
            Assert.assertEquals(actualIncomingExtension,actualOutgoingExtension);
        }
        finally
        {
            stack.stop();
        }
    }

    @Test
    public void testStartIdentityTwice() throws Exception
    {
        ExtensionStack stack = createExtensionStack();
        try
        {
            // 1 extension
            List<ExtensionConfig> configs = new ArrayList<>();
            configs.add(ExtensionConfig.parse("identity; id=A"));
            configs.add(ExtensionConfig.parse("identity; id=B"));
            stack.negotiate(configs);

            // Setup Listeners
            DummyIncomingFrames session = new DummyIncomingFrames("Session");
            DummyOutgoingFrames connection = new DummyOutgoingFrames("Connection");
            stack.setNextOutgoing(connection);
            stack.setNextIncoming(session);

            // Start
            stack.start();

            // Dump
            LOG.debug("{}",stack.dump());

            // Should be no change to handlers
            IdentityExtension actualIncomingExtension = assertIsExtension("Incoming",stack.getNextIncoming(),IdentityExtension.class);
            IdentityExtension actualOutgoingExtension = assertIsExtension("Outgoing",stack.getNextOutgoing(),IdentityExtension.class);

            Assert.assertThat("Incoming[identity].id",actualIncomingExtension.getParam("id"),is("A"));
            Assert.assertThat("Outgoing[identity].id",actualOutgoingExtension.getParam("id"),is("B"));
        }
        finally
        {
            stack.stop();
        }
    }

    @Test
    public void testStartNothing() throws Exception
    {
        ExtensionStack stack = createExtensionStack();
        try
        {
            // intentionally empty
            List<ExtensionConfig> configs = new ArrayList<>();
            stack.negotiate(configs);

            // Setup Listeners
            DummyIncomingFrames session = new DummyIncomingFrames("Session");
            DummyOutgoingFrames connection = new DummyOutgoingFrames("Connection");
            stack.setNextOutgoing(connection);
            stack.setNextIncoming(session);

            // Start
            stack.start();

            // Dump
            LOG.debug("{}",stack.dump());

            // Should be no change to handlers
            Assert.assertEquals("Incoming Handler",stack.getNextIncoming(),session);
            Assert.assertEquals("Outgoing Handler",stack.getNextOutgoing(),connection);
        }
        finally
        {
            stack.stop();
        }
    }

    @Test
    public void testToString()
    {
        ExtensionStack stack = createExtensionStack();
        // Shouldn't cause a NPE.
        LOG.debug("Shouldn't cause a NPE: {}",stack.toString());
    }
}
