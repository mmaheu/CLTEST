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

package org.eclipse.jetty.websocket.common;

import static org.hamcrest.Matchers.is;

import java.nio.ByteBuffer;

import org.eclipse.jetty.util.BufferUtil;
import org.junit.Assert;

public class ByteBufferAssert
{
    public static void assertEquals(String message, byte[] expected, byte[] actual)
    {
        Assert.assertThat(message + " byte[].length",actual.length,is(expected.length));
        int len = expected.length;
        for (int i = 0; i < len; i++)
        {
            Assert.assertThat(message + " byte[" + i + "]",actual[i],is(expected[i]));
        }
    }

    public static void assertEquals(String message, ByteBuffer expectedBuffer, ByteBuffer actualBuffer)
    {
        byte expectedBytes[] = BufferUtil.toArray(expectedBuffer);
        byte actualBytes[] = BufferUtil.toArray(actualBuffer);
        assertEquals(message,expectedBytes,actualBytes);
    }

    public static void assertEquals(String message, String expectedString, ByteBuffer actualBuffer)
    {
        String actualString = BufferUtil.toString(actualBuffer);
        Assert.assertThat(message,actualString,is(expectedString));
    }

    public static void assertSize(String message, int expectedSize, ByteBuffer buffer)
    {
        if ((expectedSize == 0) && (buffer == null))
        {
            return;
        }
        Assert.assertThat(message + " buffer.remaining",buffer.remaining(),is(expectedSize));
    }
}
