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

package org.eclipse.jetty.spdy;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jetty.spdy.StandardSession.FrameBytes;
import org.eclipse.jetty.spdy.api.SPDYException;
import org.eclipse.jetty.spdy.api.Stream;
import org.eclipse.jetty.spdy.api.StreamStatus;
import org.eclipse.jetty.util.ArrayQueue;
import org.eclipse.jetty.util.IteratingCallback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class Flusher
{
    private static final Logger LOG = Log.getLogger(Flusher.class);
    private static final int MAX_GATHER = Integer.getInteger("org.eclipse.jetty.spdy.Flusher.MAX_GATHER",8);

    private final FlusherCB flusherCB = new FlusherCB();
    private final Controller controller;
    private final Object lock = new Object();
    private final ArrayQueue<StandardSession.FrameBytes> queue = new ArrayQueue<>(lock);
    private Throwable failure;

    public Flusher(Controller controller)
    {
        this.controller = controller;
    }

    void removeFrameBytesFromQueue(Stream stream)
    {
        synchronized (lock)
        {
            for (StandardSession.FrameBytes frameBytes : queue)
                if (frameBytes.getStream() == stream)
                    queue.remove(frameBytes);
        }
    }

    void append(StandardSession.FrameBytes frameBytes)
    {
        Throwable failure;
        synchronized (lock)
        {
            failure = this.failure;
            if (failure == null)
            {
                // Control frames are added in order
                if (frameBytes instanceof StandardSession.ControlFrameBytes)
                    queue.add(frameBytes);
                else
                {
                    // Otherwise scan from the back of the queue to insert by priority
                    int index = queue.size();
                    while (index > 0)
                    {
                        StandardSession.FrameBytes element = queue.getUnsafe(index - 1);
                        if (element.compareTo(frameBytes) >= 0)
                            break;
                        --index;
                    }
                    queue.add(index, frameBytes);
                }
            }
        }
        
        // If no failures make sure we are iterating
        if (failure == null)
            flush();
        else
            frameBytes.failed(new SPDYException(failure));
    }

    void prepend(StandardSession.FrameBytes frameBytes)
    {
        Throwable failure;
        synchronized (lock)
        {
            failure = this.failure;
            if (failure == null)
            {
                // Scan from the front of the queue looking to skip higher priority messages
                int index = 0;
                int size=queue.size();
                while (index < size)
                {
                    StandardSession.FrameBytes element = queue.getUnsafe(index);
                    if (element.compareTo(frameBytes) <= 0)
                        break;
                    ++index;
                }
                queue.add(index, frameBytes);
            }
        }

        // If no failures make sure we are iterating
        if (failure == null)
            flush();
        else
            frameBytes.failed(new SPDYException(failure));
    }

    void flush()
    {
        flusherCB.iterate();
    }

    public int getQueueSize()
    {
        synchronized (lock)
        {
            return queue.size();
        }
    }

    private class FlusherCB extends IteratingCallback
    {
        // TODO should active and succeeded be local?
        private final List<StandardSession.FrameBytes> active = new ArrayList<>(MAX_GATHER);
        private final List<StandardSession.FrameBytes> succeeded = new ArrayList<>(MAX_GATHER);
        private final Set<IStream> stalled = new HashSet<>();
        
        @Override
        protected State process() throws Exception
        {
            synchronized (lock)
            {
                succeeded.clear();
                
                // Scan queue for data to write from first non stalled stream. 
                int qs=queue.size();
                for (int i = 0; i < qs && active.size()<MAX_GATHER;)
                {
                    StandardSession.FrameBytes frameBytes = queue.getUnsafe(i);
                    IStream stream = frameBytes.getStream();
                    
                    // Continue if this is stalled stream
                    if (stream!=null)
                    {
                        if (stalled.size()>0 && stalled.contains(stream))
                        {
                            i++;
                            continue;
                        }

                        if (stream.getWindowSize()<=0)
                        {
                            stalled.add(stream);
                            i++;
                            continue;
                        }
                    }
                    
                    // we will be writing this one, so take the frame off the queue
                    queue.remove(i);
                    qs--;
                    
                    // Has the stream been reset and if this not a control frame?
                    if (stream != null && stream.isReset() && !(frameBytes instanceof StandardSession.ControlFrameBytes))
                    {
                        frameBytes.failed(new StreamException(frameBytes.getStream().getId(), StreamStatus.INVALID_STREAM,
                                "Stream: " + frameBytes.getStream() + " is reset!"));
                        continue;
                    }   
                    
                    active.add(frameBytes);
                }
                stalled.clear();

                if (LOG.isDebugEnabled())
                    LOG.debug("Flushing {} of {} frame(s) in queue", active.size(), queue.size());
            }

            if (active.size() == 0)
                return State.IDLE;

            // Get the bytes to write
            ByteBuffer[] buffers = new ByteBuffer[active.size()];
            for (int i=0;i<buffers.length;i++)
                buffers[i]=active.get(i).getByteBuffer();

            if (controller != null)
                controller.write(flusherCB, buffers);
            return State.SCHEDULED;
        }

        @Override
        protected void completed()
        {
            // will never be called as process always returns SCHEDULED or IDLE
            throw new IllegalStateException();
        }

        @Override
        public void succeeded()
        {
            synchronized (lock)
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("Succeeded write of {}, q={}", active, queue.size());
                succeeded.addAll(active);
                active.clear();
            }
            for (FrameBytes frame: succeeded)
                frame.succeeded(); // TODO should we try catch?
            super.succeeded();
        }

        @Override
        public void failed(Throwable x)
        {
            List<StandardSession.FrameBytes> failed = new ArrayList<>();
            synchronized (lock)
            {
                failure = x;
                if (LOG.isDebugEnabled())
                {
                    String logMessage = String.format("Failed write of %s, failing all %d frame(s) in queue", this, queue.size());
                    LOG.debug(logMessage, x);
                }
                failed.addAll(active);
                active.clear();
                failed.addAll(queue);
                queue.clear();
            }
            for (StandardSession.FrameBytes fb : failed)
                fb.failed(x);  // TODO should we try catch?
            super.failed(x);
        }
    }

}
