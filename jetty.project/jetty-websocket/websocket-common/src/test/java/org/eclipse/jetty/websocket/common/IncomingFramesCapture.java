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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

import java.util.LinkedList;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.WebSocketException;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.api.extensions.IncomingFrames;
import org.junit.Assert;

public class IncomingFramesCapture implements IncomingFrames
{
    private static final Logger LOG = Log.getLogger(IncomingFramesCapture.class);

    private LinkedList<WebSocketFrame> frames = new LinkedList<>();
    private LinkedList<Throwable> errors = new LinkedList<>();

    public void assertErrorCount(int expectedCount)
    {
        Assert.assertThat("Captured error count",errors.size(),is(expectedCount));
    }

    public void assertFrameCount(int expectedCount)
    {
        if (frames.size() != expectedCount)
        {
            // dump details
            System.err.printf("Expected %d frame(s)%n",expectedCount);
            System.err.printf("But actually captured %d frame(s)%n",frames.size());
            for (int i = 0; i < frames.size(); i++)
            {
                Frame frame = frames.get(i);
                System.err.printf(" [%d] Frame[%s] - %s%n", i, 
                        OpCode.name(frame.getOpCode()),
                        BufferUtil.toDetailString(frame.getPayload()));
            }
        }
        Assert.assertThat("Captured frame count",frames.size(),is(expectedCount));
    }

    public void assertHasErrors(Class<? extends WebSocketException> errorType, int expectedCount)
    {
        Assert.assertThat(errorType.getSimpleName(),getErrorCount(errorType),is(expectedCount));
    }

    public void assertHasFrame(byte op)
    {
        Assert.assertThat(OpCode.name(op),getFrameCount(op),greaterThanOrEqualTo(1));
    }

    public void assertHasFrame(byte op, int expectedCount)
    {
        String msg = String.format("%s frame count",OpCode.name(op));
        Assert.assertThat(msg,getFrameCount(op),is(expectedCount));
    }

    public void assertHasNoFrames()
    {
        Assert.assertThat("Frame count",frames.size(),is(0));
    }

    public void assertNoErrors()
    {
        Assert.assertThat("Error count",errors.size(),is(0));
    }

    public void dump()
    {
        System.err.printf("Captured %d incoming frames%n",frames.size());
        for (int i = 0; i < frames.size(); i++)
        {
            Frame frame = frames.get(i);
            System.err.printf("[%3d] %s%n",i,frame);
            System.err.printf("          %s%n",BufferUtil.toDetailString(frame.getPayload()));
        }
    }

    public int getErrorCount(Class<? extends WebSocketException> errorType)
    {
        int count = 0;
        for (Throwable error : errors)
        {
            if (errorType.isInstance(error))
            {
                count++;
            }
        }
        return count;
    }

    public LinkedList<Throwable> getErrors()
    {
        return errors;
    }

    public int getFrameCount(byte op)
    {
        int count = 0;
        for (WebSocketFrame frame : frames)
        {
            if (frame.getOpCode() == op)
            {
                count++;
            }
        }
        return count;
    }

    public LinkedList<WebSocketFrame> getFrames()
    {
        return frames;
    }

    @Override
    public void incomingError(Throwable e)
    {
        LOG.debug(e);
        errors.add(e);
    }

    @Override
    public void incomingFrame(Frame frame)
    {
        frames.add(WebSocketFrame.copy(frame));
    }

    public int size()
    {
        return frames.size();
    }
}
