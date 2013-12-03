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
import java.util.List;

import org.eclipse.jetty.toolchain.test.AdvancedRunner;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.common.CloseInfo;
import org.eclipse.jetty.websocket.common.WebSocketFrame;
import org.eclipse.jetty.websocket.common.frames.PingFrame;
import org.eclipse.jetty.websocket.common.frames.TextFrame;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test various bad / forbidden opcodes (per spec)
 */
@RunWith(AdvancedRunner.class)
public class TestABCase4 extends AbstractABCase
{
    /**
     * Send opcode 3 (reserved)
     */
    @Test
    public void testCase4_1_1() throws Exception
    {
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new BadFrame((byte)3)); // intentionally bad

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new CloseInfo(StatusCode.PROTOCOL).asFrame());

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
     * Send opcode 4 (reserved), with payload
     */
    @Test
    public void testCase4_1_2() throws Exception
    {
        byte payload[] = StringUtil.getUtf8Bytes("reserved payload");
        ByteBuffer buf = ByteBuffer.wrap(payload);

        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new BadFrame((byte)4).setPayload(buf)); // intentionally bad

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new CloseInfo(StatusCode.PROTOCOL).asFrame());

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
     * Send small text, then frame with opcode 5 (reserved), then ping
     */
    @Test
    public void testCase4_1_3() throws Exception
    {
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new TextFrame().setPayload("hello"));
        send.add(new BadFrame((byte)5)); // intentionally bad
        send.add(new PingFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new TextFrame().setPayload("hello")); // echo
        expect.add(new CloseInfo(StatusCode.PROTOCOL).asFrame());

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
     * Send small text, then frame with opcode 6 (reserved) w/payload, then ping
     */
    @Test
    public void testCase4_1_4() throws Exception
    {
        ByteBuffer buf = ByteBuffer.wrap(StringUtil.getUtf8Bytes("bad"));
        
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new TextFrame().setPayload("hello"));
        send.add(new BadFrame((byte)6).setPayload(buf)); // intentionally bad
        send.add(new PingFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new TextFrame().setPayload("hello")); // echo
        expect.add(new CloseInfo(StatusCode.PROTOCOL).asFrame());

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
     * Send small text, then frame with opcode 7 (reserved) w/payload, then ping
     */
    @Test
    public void testCase4_1_5() throws Exception
    {
        ByteBuffer buf = ByteBuffer.wrap(StringUtil.getUtf8Bytes("bad"));
        
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new TextFrame().setPayload("hello"));
        send.add(new BadFrame((byte)7).setPayload(buf)); // intentionally bad
        send.add(new PingFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new TextFrame().setPayload("hello")); // echo
        expect.add(new CloseInfo(StatusCode.PROTOCOL).asFrame());

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
     * Send opcode 11 (reserved)
     */
    @Test
    public void testCase4_2_1() throws Exception
    {
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new BadFrame((byte)11)); // intentionally bad

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new CloseInfo(StatusCode.PROTOCOL).asFrame());

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
     * Send opcode 12 (reserved)
     */
    @Test
    public void testCase4_2_2() throws Exception
    {
        ByteBuffer buf = ByteBuffer.wrap(StringUtil.getUtf8Bytes("bad"));
        
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new BadFrame((byte)12).setPayload(buf)); // intentionally bad

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new CloseInfo(StatusCode.PROTOCOL).asFrame());

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
     * Send small text, then frame with opcode 13 (reserved), then ping
     */
    @Test
    public void testCase4_2_3() throws Exception
    {
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new TextFrame().setPayload("hello"));
        send.add(new BadFrame((byte)13)); // intentionally bad
        send.add(new PingFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new TextFrame().setPayload("hello")); // echo
        expect.add(new CloseInfo(StatusCode.PROTOCOL).asFrame());

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
     * Send small text, then frame with opcode 14 (reserved), then ping
     */
    @Test
    public void testCase4_2_4() throws Exception
    {
        ByteBuffer buf = ByteBuffer.wrap(StringUtil.getUtf8Bytes("bad"));
        
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new TextFrame().setPayload("hello"));
        send.add(new BadFrame((byte)14).setPayload(buf)); // intentionally bad
        send.add(new PingFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new TextFrame().setPayload("hello")); // echo
        expect.add(new CloseInfo(StatusCode.PROTOCOL).asFrame());

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
     * Send small text, then frame with opcode 15 (reserved), then ping
     */
    @Test
    public void testCase4_2_5() throws Exception
    {
        ByteBuffer buf = ByteBuffer.wrap(StringUtil.getUtf8Bytes("bad"));
        
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new TextFrame().setPayload("hello"));
        send.add(new BadFrame((byte)15).setPayload(buf)); // intentionally bad
        send.add(new PingFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new TextFrame().setPayload("hello")); // echo
        expect.add(new CloseInfo(StatusCode.PROTOCOL).asFrame());

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
}
