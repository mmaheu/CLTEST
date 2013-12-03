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

package org.eclipse.jetty.websocket.common.extensions.compress;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.api.extensions.ExtensionConfig;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.common.ByteBufferAssert;
import org.eclipse.jetty.websocket.common.Generator;
import org.eclipse.jetty.websocket.common.Hex;
import org.eclipse.jetty.websocket.common.IncomingFramesCapture;
import org.eclipse.jetty.websocket.common.OpCode;
import org.eclipse.jetty.websocket.common.OutgoingNetworkBytesCapture;
import org.eclipse.jetty.websocket.common.Parser;
import org.eclipse.jetty.websocket.common.UnitParser;
import org.eclipse.jetty.websocket.common.WebSocketFrame;
import org.eclipse.jetty.websocket.common.frames.TextFrame;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class DeflateFrameExtensionTest
{
    @Rule
    public TestName testname = new TestName();

    private void assertIncoming(byte[] raw, String... expectedTextDatas)
    {
        WebSocketPolicy policy = WebSocketPolicy.newClientPolicy();

        DeflateFrameExtension ext = new DeflateFrameExtension();
        ext.setBufferPool(new MappedByteBufferPool());
        ext.setPolicy(policy);

        ExtensionConfig config = ExtensionConfig.parse("deflate-frame");
        ext.setConfig(config);

        // Setup capture of incoming frames
        IncomingFramesCapture capture = new IncomingFramesCapture();

        // Wire up stack
        ext.setNextIncomingFrames(capture);

        Parser parser = new UnitParser(policy);
        parser.configureFromExtensions(Collections.singletonList(ext));
        parser.setIncomingFramesHandler(ext);

        parser.parse(ByteBuffer.wrap(raw));

        int len = expectedTextDatas.length;
        capture.assertFrameCount(len);
        capture.assertHasFrame(OpCode.TEXT,len);

        for (int i = 0; i < len; i++)
        {
            WebSocketFrame actual = capture.getFrames().get(i);
            String prefix = "Frame[" + i + "]";
            Assert.assertThat(prefix + ".opcode",actual.getOpCode(),is(OpCode.TEXT));
            Assert.assertThat(prefix + ".fin",actual.isFin(),is(true));
            Assert.assertThat(prefix + ".rsv1",actual.isRsv1(),is(false)); // RSV1 should be unset at this point
            Assert.assertThat(prefix + ".rsv2",actual.isRsv2(),is(false));
            Assert.assertThat(prefix + ".rsv3",actual.isRsv3(),is(false));

            ByteBuffer expected = BufferUtil.toBuffer(expectedTextDatas[i],StandardCharsets.UTF_8);
            Assert.assertThat(prefix + ".payloadLength",actual.getPayloadLength(),is(expected.remaining()));
            ByteBufferAssert.assertEquals(prefix + ".payload",expected,actual.getPayload().slice());
        }
    }

    private void assertOutgoing(String text, String expectedHex) throws IOException
    {
        WebSocketPolicy policy = WebSocketPolicy.newClientPolicy();

        DeflateFrameExtension ext = new DeflateFrameExtension();
        ext.setBufferPool(new MappedByteBufferPool());
        ext.setPolicy(policy);

        ExtensionConfig config = ExtensionConfig.parse("deflate-frame");
        ext.setConfig(config);

        ByteBufferPool bufferPool = new MappedByteBufferPool();
        boolean validating = true;
        Generator generator = new Generator(policy,bufferPool,validating);
        generator.configureFromExtensions(Collections.singletonList(ext));

        OutgoingNetworkBytesCapture capture = new OutgoingNetworkBytesCapture(generator);
        ext.setNextOutgoingFrames(capture);

        Frame frame = new TextFrame().setPayload(text);
        ext.outgoingFrame(frame,null);

        capture.assertBytes(0,expectedHex);
    }

    @Test
    public void testBlockheadClient_HelloThere()
    {
        // Captured from Blockhead Client - "Hello" then "There" via unit test
        String hello = "c18700000000f248cdc9c90700";
        String there = "c187000000000ac9482d4a0500";
        byte rawbuf[] = Hex.asByteArray(hello + there);
        assertIncoming(rawbuf,"Hello","There");
    }

    @Test
    public void testChrome20_Hello()
    {
        // Captured from Chrome 20.x - "Hello" (sent from browser)
        byte rawbuf[] = Hex.asByteArray("c187832b5c11716391d84a2c5c");
        assertIncoming(rawbuf,"Hello");
    }

    @Test
    public void testChrome20_HelloThere()
    {
        // Captured from Chrome 20.x - "Hello" then "There" (sent from browser)
        String hello = "c1877b1971db8951bc12b21e71";
        String there = "c18759edc8f4532480d913e8c8";
        byte rawbuf[] = Hex.asByteArray(hello + there);
        assertIncoming(rawbuf,"Hello","There");
    }

    @Test
    public void testChrome20_Info()
    {
        // Captured from Chrome 20.x - "info:" (sent from browser)
        byte rawbuf[] = Hex.asByteArray("c187ca4def7f0081a4b47d4fef");
        assertIncoming(rawbuf,"info:");
    }

    @Test
    public void testChrome20_TimeTime()
    {
        // Captured from Chrome 20.x - "time:" then "time:" once more (sent from browser)
        String time1 = "c18782467424a88fb869374474";
        String time2 = "c1853cfda17f16fcb07f3c";
        byte rawbuf[] = Hex.asByteArray(time1 + time2);
        assertIncoming(rawbuf,"time:","time:");
    }

    @Test
    public void testPyWebSocket_TimeTimeTime()
    {
        // Captured from Pywebsocket (r781) - "time:" sent 3 times.
        String time1 = "c1876b100104" + "41d9cd49de1201";
        String time2 = "c1852ae3ff01" + "00e2ee012a";
        String time3 = "c18435558caa" + "37468caa";
        byte rawbuf[] = Hex.asByteArray(time1 + time2 + time3);
        assertIncoming(rawbuf,"time:","time:","time:");
    }

    @Test
    public void testCompress_TimeTimeTime()
    {
        // What pywebsocket produces for "time:", "time:", "time:"
        String expected[] = new String[]
        { "2AC9CC4DB50200", "2A01110000", "02130000" };

        // Lets see what we produce
        CapturedHexPayloads capture = new CapturedHexPayloads();
        DeflateFrameExtension ext = new DeflateFrameExtension();
        init(ext);
        ext.setNextOutgoingFrames(capture);

        ext.outgoingFrame(new TextFrame().setPayload("time:"),null);
        ext.outgoingFrame(new TextFrame().setPayload("time:"),null);
        ext.outgoingFrame(new TextFrame().setPayload("time:"),null);

        List<String> actual = capture.getCaptured();
        
        for (String entry : actual)
        {
            System.err.printf("actual: \"%s\"%n",entry);
        }

        Assert.assertThat("Compressed Payloads",actual,contains(expected));
    }

    private void init(DeflateFrameExtension ext)
    {
        ext.setConfig(new ExtensionConfig(ext.getName()));
        ext.setBufferPool(new MappedByteBufferPool());
    }

    @Test
    public void testDeflateBasics() throws Exception
    {
        // Setup deflater basics
        boolean nowrap = true;
        Deflater compressor = new Deflater(Deflater.BEST_COMPRESSION,nowrap);
        compressor.setStrategy(Deflater.DEFAULT_STRATEGY);

        // Text to compress
        String text = "info:";
        byte uncompressed[] = StringUtil.getUtf8Bytes(text);

        // Prime the compressor
        compressor.reset();
        compressor.setInput(uncompressed,0,uncompressed.length);
        compressor.finish();

        // Perform compression
        ByteBuffer outbuf = ByteBuffer.allocate(64);
        BufferUtil.clearToFill(outbuf);

        while (!compressor.finished())
        {
            byte out[] = new byte[64];
            int len = compressor.deflate(out,0,out.length,Deflater.SYNC_FLUSH);
            if (len > 0)
            {
                outbuf.put(out,0,len);
            }
        }
        compressor.end();

        BufferUtil.flipToFlush(outbuf,0);
        byte b0 = outbuf.get(0);
        if ((b0 & 1) != 0)
        {
            outbuf.put(0,(b0 ^= 1));
        }
        byte compressed[] = BufferUtil.toArray(outbuf);

        String actual = TypeUtil.toHexString(compressed);
        String expected = "CaCc4bCbB70200"; // what pywebsocket produces

        Assert.assertThat("Compressed data",actual,is(expected));
    }

    @Test
    public void testGeneratedTwoFrames() throws IOException
    {
        WebSocketPolicy policy = WebSocketPolicy.newClientPolicy();

        DeflateFrameExtension ext = new DeflateFrameExtension();
        ext.setBufferPool(new MappedByteBufferPool());
        ext.setPolicy(policy);
        ext.setConfig(new ExtensionConfig(ext.getName()));

        ByteBufferPool bufferPool = new MappedByteBufferPool();
        boolean validating = true;
        Generator generator = new Generator(policy,bufferPool,validating);
        generator.configureFromExtensions(Collections.singletonList(ext));

        OutgoingNetworkBytesCapture capture = new OutgoingNetworkBytesCapture(generator);
        ext.setNextOutgoingFrames(capture);

        ext.outgoingFrame(new TextFrame().setPayload("Hello"),null);
        ext.outgoingFrame(new TextFrame().setPayload("There"),null);

        capture.assertBytes(0,"c107f248cdc9c90700");
    }

    @Test
    public void testInflateBasics() throws Exception
    {
        // should result in "info:" text if properly inflated
        byte rawbuf[] = TypeUtil.fromHexString("CaCc4bCbB70200"); // what pywebsocket produces
        // byte rawbuf[] = TypeUtil.fromHexString("CbCc4bCbB70200"); // what java produces

        Inflater inflater = new Inflater(true);
        inflater.reset();
        inflater.setInput(rawbuf,0,rawbuf.length);

        byte outbuf[] = new byte[64];
        int len = inflater.inflate(outbuf);
        inflater.end();
        Assert.assertThat("Inflated length",len,greaterThan(4));

        String actual = StringUtil.toUTF8String(outbuf,0,len);
        Assert.assertThat("Inflated text",actual,is("info:"));
    }

    @Test
    public void testPyWebSocketServer_Hello()
    {
        // Captured from PyWebSocket - "Hello" (echo from server)
        byte rawbuf[] = TypeUtil.fromHexString("c107f248cdc9c90700");
        assertIncoming(rawbuf,"Hello");
    }

    @Test
    public void testPyWebSocketServer_Long()
    {
        // Captured from PyWebSocket - Long Text (echo from server)
        byte rawbuf[] = TypeUtil.fromHexString("c1421cca410a80300c44d1abccce9df7" + "f018298634d05631138ab7b7b8fdef1f" + "dc0282e2061d575a45f6f2686bab25e1"
                + "3fb7296fa02b5885eb3b0379c394f461" + "98cafd03");
        assertIncoming(rawbuf,"It's a big enough umbrella but it's always me that ends up getting wet.");
    }

    @Test
    public void testPyWebSocketServer_Medium()
    {
        // Captured from PyWebSocket - "stackoverflow" (echo from server)
        byte rawbuf[] = TypeUtil.fromHexString("c10f2a2e494ccece2f4b2d4acbc92f0700");
        assertIncoming(rawbuf,"stackoverflow");
    }

    /**
     * Make sure that the server generated compressed form for "Hello" is consistent with what PyWebSocket creates.
     */
    @Test
    public void testServerGeneratedHello() throws IOException
    {
        assertOutgoing("Hello","c107f248cdc9c90700");
    }

    /**
     * Make sure that the server generated compressed form for "There" is consistent with what PyWebSocket creates.
     */
    @Test
    public void testServerGeneratedThere() throws IOException
    {
        assertOutgoing("There","c1070ac9482d4a0500");
    }
}
