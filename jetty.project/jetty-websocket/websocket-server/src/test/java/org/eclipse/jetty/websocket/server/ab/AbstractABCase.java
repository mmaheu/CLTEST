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

package org.eclipse.jetty.websocket.server.ab;

import java.nio.ByteBuffer;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.StacklessLogging;
import org.eclipse.jetty.util.log.StdErrLog;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.common.Generator;
import org.eclipse.jetty.websocket.common.OpCode;
import org.eclipse.jetty.websocket.common.WebSocketFrame;
import org.eclipse.jetty.websocket.server.SimpleServletServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;

public abstract class AbstractABCase
{
    // Allow Fuzzer / Generator to create bad frames for testing frame validation
    protected static class BadFrame extends WebSocketFrame
    {
        public BadFrame(byte opcode)
        {
            super(OpCode.CONTINUATION);
            super.finRsvOp = (byte)((finRsvOp & 0xF0) | (opcode & 0x0F));
            // NOTE: Not setting Frame.Type intentionally
        }

        @Override
        public void assertValid()
        {
        }

        @Override
        public boolean isControlFrame()
        {
            return false;
        }

        @Override
        public boolean isDataFrame()
        {
            return false;
        }
    }
    
    protected static final byte FIN = (byte)0x80;
    protected static final byte NOFIN = 0x00;
    protected static final byte[] MASK =
    { 0x12, 0x34, 0x56, 0x78 };

    protected static Generator strictGenerator;
    protected static Generator laxGenerator;
    protected static SimpleServletServer server;

    @BeforeClass
    public static void initGenerators()
    {
        WebSocketPolicy policy = WebSocketPolicy.newClientPolicy();
        ByteBufferPool bufferPool = new MappedByteBufferPool();
        strictGenerator = new Generator(policy,bufferPool,true);
        laxGenerator = new Generator(policy,bufferPool,false);
    }

    @BeforeClass
    public static void startServer() throws Exception
    {
        server = new SimpleServletServer(new ABServlet());
        server.start();
    }

    @AfterClass
    public static void stopServer()
    {
        server.stop();
    }
    
    /**
     * Make a copy of a byte buffer.
     * <p>
     * This is important in some tests, as the underlying byte buffer contained in a Frame can be modified through
     * masking and make it difficult to compare the results in the fuzzer. 
     * 
     * @param payload the payload to copy
     * @return a new byte array of the payload contents
     */
    protected ByteBuffer copyOf(byte[] payload)
    {
        byte copy[] = new byte[payload.length];
        System.arraycopy(payload,0,copy,0,payload.length);
        return ByteBuffer.wrap(copy);
    }
    
    /**
     * Make a copy of a byte buffer.
     * <p>
     * This is important in some tests, as the underlying byte buffer contained in a Frame can be modified through
     * masking and make it difficult to compare the results in the fuzzer. 
     * 
     * @param payload the payload to copy
     * @return a new byte array of the payload contents
     */
    protected ByteBuffer clone(ByteBuffer payload)
    {
        ByteBuffer copy = ByteBuffer.allocate(payload.remaining());
        copy.put(payload.slice());
        copy.flip();
        return copy;
    }
    
    /**
     * Make a copy of a byte buffer.
     * <p>
     * This is important in some tests, as the underlying byte buffer contained in a Frame can be modified through
     * masking and make it difficult to compare the results in the fuzzer. 
     * 
     * @param payload the payload to copy
     * @return a new byte array of the payload contents
     */
    protected ByteBuffer copyOf(ByteBuffer payload)
    {
        ByteBuffer copy = ByteBuffer.allocate(payload.remaining());
        BufferUtil.clearToFill(copy);
        BufferUtil.put(payload,copy);
        BufferUtil.flipToFlush(copy,0);
        return copy;
    }

    public static String toUtf8String(byte[] buf)
    {
        String raw = StringUtil.toUTF8String(buf,0,buf.length);
        StringBuilder ret = new StringBuilder();
        int len = raw.length();
        for (int i = 0; i < len; i++)
        {
            int codepoint = raw.codePointAt(i);
            if (Character.isUnicodeIdentifierPart(codepoint))
            {
                ret.append(String.format("\\u%04X",codepoint));
            }
            else
            {
                ret.append(Character.toChars(codepoint));
            }
        }
        return ret.toString();
    }

    @Rule
    public TestName testname = new TestName();

    /**
     * @deprecated use {@link StacklessLogging} in a try-with-resources block instead
     */
    @Deprecated
    protected void enableStacks(Class<?> clazz, boolean enabled)
    {
        StdErrLog log = StdErrLog.getLogger(clazz);
        log.setHideStacks(!enabled);
    }

    public Generator getLaxGenerator()
    {
        return laxGenerator;
    }

    public SimpleServletServer getServer()
    {
        return server;
    }

    public static byte[] masked(final byte[] data)
    {
        return RawFrameBuilder.mask(data,MASK);
    }

    public static void putLength(ByteBuffer buf, int length, boolean masked)
    {
        RawFrameBuilder.putLength(buf,length,masked);
    }

    public static void putMask(ByteBuffer buf)
    {
        buf.put(MASK);
    }

    public static void putPayloadLength(ByteBuffer buf, int length)
    {
        putLength(buf,length,true);
    }
}
