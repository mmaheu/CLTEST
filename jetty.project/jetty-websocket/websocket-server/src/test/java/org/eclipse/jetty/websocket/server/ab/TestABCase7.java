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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jetty.toolchain.test.TestTracker;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.StacklessLogging;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.common.CloseInfo;
import org.eclipse.jetty.websocket.common.OpCode;
import org.eclipse.jetty.websocket.common.Parser;
import org.eclipse.jetty.websocket.common.WebSocketFrame;
import org.eclipse.jetty.websocket.common.frames.CloseFrame;
import org.eclipse.jetty.websocket.common.frames.ContinuationFrame;
import org.eclipse.jetty.websocket.common.frames.PingFrame;
import org.eclipse.jetty.websocket.common.frames.TextFrame;
import org.eclipse.jetty.websocket.server.helper.Hex;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test of Close Handling
 */
public class TestABCase7 extends AbstractABCase
{
    @Rule
    public TestTracker tt = new TestTracker();

    /**
     * Basic message then close frame, normal behavior
     */
    @Test
    public void testCase7_1_1() throws Exception
    {
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new TextFrame().setPayload("Hello World"));
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new TextFrame().setPayload("Hello World"));
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * Close frame, then another close frame (send frame ignored)
     */
    @Test
    public void testCase7_1_2() throws Exception
    {
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());
        send.add(new CloseInfo().asFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
            fuzzer.expectNoMoreFrames();
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * Close frame, then ping frame (no pong received)
     */
    @Test
    public void testCase7_1_3() throws Exception
    {
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());
        send.add(new PingFrame().setPayload("out of band ping"));

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
            fuzzer.expectNoMoreFrames();
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * Close frame, then ping frame (no pong received)
     */
    @Test
    public void testCase7_1_4() throws Exception
    {
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());
        send.add(new TextFrame().setPayload("out of band text"));

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
            fuzzer.expectNoMoreFrames();
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * Text fin=false, close, then continuation fin=true
     */
    @Test
    public void testCase7_1_5() throws Exception
    {
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new TextFrame().setPayload("an").setFin(false));
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());
        send.add(new ContinuationFrame().setPayload("ticipation").setFin(true));

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
            fuzzer.expectNoMoreFrames();
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * 256k msg, then close, then ping
     */
    @Test
    public void testCase7_1_6() throws Exception
    {
        byte msg[] = new byte[256 * 1024];
        Arrays.fill(msg,(byte)'*');
        ByteBuffer buf = ByteBuffer.wrap(msg);

        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new TextFrame().setPayload(buf));
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());
        send.add(new PingFrame().setPayload("out of band"));

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new TextFrame().setPayload(clone(buf)));
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
            fuzzer.expectNoMoreFrames();
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * close with no payload (payload length 0)
     */
    @Test
    public void testCase7_3_1() throws Exception
    {
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new CloseFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new CloseFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
            fuzzer.expectNoMoreFrames();
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * close with invalid payload (payload length 1)
     */
    @Test
    public void testCase7_3_2() throws Exception
    {
        byte payload[] = new byte[]
        { 0x00 };
        ByteBuffer buf = ByteBuffer.wrap(payload);

        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new CloseFrame().setPayload(buf));

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new CloseInfo(StatusCode.PROTOCOL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try(StacklessLogging scope = new StacklessLogging(Parser.class))
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
            fuzzer.expectNoMoreFrames();
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * close with valid payload (payload length 2)
     */
    @Test
    public void testCase7_3_3() throws Exception
    {
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
            fuzzer.expectNoMoreFrames();
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * close with valid payload (with reason)
     */
    @Test
    public void testCase7_3_4() throws Exception
    {
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new CloseInfo(StatusCode.NORMAL,"Hic").asFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new CloseInfo(StatusCode.NORMAL,"Hic").asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
            fuzzer.expectNoMoreFrames();
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * close with valid payload (with 123 byte reason)
     */
    @Test
    public void testCase7_3_5() throws Exception
    {
        byte utf[] = new byte[123];
        Arrays.fill(utf,(byte)'!');
        String reason = StringUtil.toUTF8String(utf,0,utf.length);

        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new CloseInfo(StatusCode.NORMAL,reason).asFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new CloseInfo(StatusCode.NORMAL,reason).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
            fuzzer.expectNoMoreFrames();
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * close with invalid UTF8 in payload
     */
    @Test
    public void testCase7_5_1() throws Exception
    {
        ByteBuffer payload = ByteBuffer.allocate(256);
        BufferUtil.clearToFill(payload);
        payload.put((byte)0x03); // normal close
        payload.put((byte)0xE8);
        byte invalidUtf[] = Hex.asByteArray("CEBAE1BDB9CF83CEBCCEB5EDA080656469746564");
        payload.put(invalidUtf);
        BufferUtil.flipToFlush(payload,0);

        List<WebSocketFrame> send = new ArrayList<>();
        WebSocketFrame close = new BadFrame(OpCode.CLOSE);
        close.setPayload(payload); // intentionally bad payload
        send.add(close);

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new CloseInfo(StatusCode.BAD_PAYLOAD).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try(StacklessLogging scope = new StacklessLogging(Parser.class))
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
            fuzzer.expectNoMoreFrames();
        }
        finally
        {
            fuzzer.close();
        }
    }
}
