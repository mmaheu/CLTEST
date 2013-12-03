/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.net;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Checksum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.xxhash.XXHashFactory;
import org.apache.cassandra.tracing.TraceState;
import org.apache.cassandra.tracing.Tracing;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.utils.UUIDGen;
import org.xerial.snappy.SnappyOutputStream;

import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.DatabaseDescriptor;

import com.google.common.util.concurrent.Uninterruptibles;

public class OutboundTcpConnection extends Thread
{
    private static final Logger logger = LoggerFactory.getLogger(OutboundTcpConnection.class);

    private static final MessageOut CLOSE_SENTINEL = new MessageOut(MessagingService.Verb.INTERNAL_RESPONSE);
    private volatile boolean isStopped = false;

    private static final int OPEN_RETRY_DELAY = 100; // ms between retries
    private static final int WAIT_FOR_VERSION_MAX_TIME = 5000;
    private static final int NO_VERSION = Integer.MIN_VALUE;

    static final int LZ4_HASH_SEED = 0x9747b28c;

    private final BlockingQueue<QueuedMessage> backlog = new LinkedBlockingQueue<>();

    private final OutboundTcpConnectionPool poolReference;

    private DataOutputStream out;
    private Socket socket;
    private volatile long completed;
    private final AtomicLong dropped = new AtomicLong();
    private volatile int currentMsgBufferCount = 0;
    private int targetVersion;

    public OutboundTcpConnection(OutboundTcpConnectionPool pool)
    {
        super("WRITE-" + pool.endPoint());
        this.poolReference = pool;
    }

    private static boolean isLocalDC(InetAddress targetHost)
    {
        String remoteDC = DatabaseDescriptor.getEndpointSnitch().getDatacenter(targetHost);
        String localDC = DatabaseDescriptor.getEndpointSnitch().getDatacenter(FBUtilities.getBroadcastAddress());
        return remoteDC.equals(localDC);
    }

    public void enqueue(MessageOut<?> message, int id)
    {
        if (backlog.size() > 1024)
            expireMessages();
        try
        {
            backlog.put(new QueuedMessage(message, id));
        }
        catch (InterruptedException e)
        {
            throw new AssertionError(e);
        }
    }

    void closeSocket(boolean destroyThread)
    {
        backlog.clear();
        isStopped = destroyThread; // Exit loop to stop the thread
        enqueue(CLOSE_SENTINEL, -1);
    }

    void softCloseSocket()
    {
        enqueue(CLOSE_SENTINEL, -1);
    }

    public int getTargetVersion()
    {
        return targetVersion;
    }

    public void run()
    {
        // keeping list (batch) size small for now; that way we don't have an unbounded array (that we never resize)
        final List<QueuedMessage> drainedMessages = new ArrayList<>(128);
        outer:
        while (true)
        {
            if (backlog.drainTo(drainedMessages, drainedMessages.size()) == 0)
            {
                try
                {
                    drainedMessages.add(backlog.take());
                }
                catch (InterruptedException e)
                {
                    throw new AssertionError(e);
                }

            }
            currentMsgBufferCount = drainedMessages.size();

            int count = drainedMessages.size();
            for (QueuedMessage qm : drainedMessages)
            {
                try
                {
                    MessageOut<?> m = qm.message;
                    if (m == CLOSE_SENTINEL)
                    {
                        disconnect();
                        if (isStopped)
                            break outer;
                        continue;
                    }
                    if (qm.timestamp < System.currentTimeMillis() - m.getTimeout())
                        dropped.incrementAndGet();
                    else if (socket != null || connect())
                        writeConnected(qm, count == 1 && backlog.size() == 0);
                    else
                        // clear out the queue, else gossip messages back up.
                        backlog.clear();
                }
                catch (Exception e)
                {
                    // really shouldn't get here, as exception handling in writeConnected() is reasonably robust
                    // but we want to catch anything bad we don't drop the messages in the current batch
                    logger.error("error processing a message intended for {}", poolReference.endPoint(), e);
                }
                currentMsgBufferCount = --count;
            }
            drainedMessages.clear();
        }
    }

    public int getPendingMessages()
    {
        return backlog.size() + currentMsgBufferCount;
    }

    public long getCompletedMesssages()
    {
        return completed;
    }

    public long getDroppedMessages()
    {
        return dropped.get();
    }

    private boolean shouldCompressConnection()
    {
        // assumes version >= 1.2
        return DatabaseDescriptor.internodeCompression() == Config.InternodeCompression.all
               || (DatabaseDescriptor.internodeCompression() == Config.InternodeCompression.dc && !isLocalDC(poolReference.endPoint()));
    }

    private void writeConnected(QueuedMessage qm, boolean flush)
    {
        try
        {
            byte[] sessionBytes = qm.message.parameters.get(Tracing.TRACE_HEADER);
            if (sessionBytes != null)
            {
                UUID sessionId = UUIDGen.getUUID(ByteBuffer.wrap(sessionBytes));
                TraceState state = Tracing.instance.get(sessionId);
                String message = String.format("Sending message to %s", poolReference.endPoint());
                // session may have already finished; see CASSANDRA-5668
                if (state == null)
                {
                    TraceState.trace(ByteBuffer.wrap(sessionBytes), message, -1);
                }
                else
                {
                    state.trace(message);
                    if (qm.message.verb == MessagingService.Verb.REQUEST_RESPONSE)
                        Tracing.instance.stopNonLocal(state);
                }
            }

            writeInternal(qm.message, qm.id, qm.timestamp);

            completed++;
            if (flush)
                out.flush();
        }
        catch (Exception e)
        {
            disconnect();
            if (e instanceof IOException)
            {
                if (logger.isDebugEnabled())
                    logger.debug("error writing to {}", poolReference.endPoint(), e);

                // if the message was important, such as a repair acknowledgement, put it back on the queue
                // to retry after re-connecting.  See CASSANDRA-5393
                if (qm.shouldRetry())
                {
                    try
                    {
                        backlog.put(new RetriedQueuedMessage(qm));
                    }
                    catch (InterruptedException e1)
                    {
                        throw new AssertionError(e1);
                    }
                }
            }
            else
            {
                // Non IO exceptions are likely a programming error so let's not silence them
                logger.error("error writing to {}", poolReference.endPoint(), e);
            }
        }
    }

    private void writeInternal(MessageOut message, int id, long timestamp) throws IOException
    {
        out.writeInt(MessagingService.PROTOCOL_MAGIC);

        if (targetVersion < MessagingService.VERSION_20)
            out.writeUTF(String.valueOf(id));
        else
            out.writeInt(id);

        // int cast cuts off the high-order half of the timestamp, which we can assume remains
        // the same between now and when the recipient reconstructs it.
        out.writeInt((int) timestamp);
        message.serialize(out, targetVersion);
    }

    private static void writeHeader(DataOutputStream out, int version, boolean compressionEnabled) throws IOException
    {
        // 2 bits: unused.  used to be "serializer type," which was always Binary
        // 1 bit: compression
        // 1 bit: streaming mode
        // 3 bits: unused
        // 8 bits: version
        // 15 bits: unused
        int header = 0;
        if (compressionEnabled)
            header |= 4;
        header |= (version << 8);
        out.writeInt(header);
    }

    private void disconnect()
    {
        if (socket != null)
        {
            try
            {
                socket.close();
            }
            catch (IOException e)
            {
                if (logger.isTraceEnabled())
                    logger.trace("exception closing connection to " + poolReference.endPoint(), e);
            }
            out = null;
            socket = null;
        }
    }

    private boolean connect()
    {
        if (logger.isDebugEnabled())
            logger.debug("attempting to connect to {}", poolReference.endPoint());

        long start = System.nanoTime();
        long timeout = TimeUnit.MILLISECONDS.toNanos(DatabaseDescriptor.getRpcTimeout());
        while (System.nanoTime() - start < timeout)
        {
            targetVersion = MessagingService.instance().getVersion(poolReference.endPoint());
            try
            {
                socket = poolReference.newSocket();
                socket.setKeepAlive(true);
                if (isLocalDC(poolReference.endPoint()))
                {
                    socket.setTcpNoDelay(true);
                }
                else
                {
                    socket.setTcpNoDelay(DatabaseDescriptor.getInterDCTcpNoDelay());
                }
                if (DatabaseDescriptor.getInternodeSendBufferSize() != null)
                {
                    try
                    {
                        socket.setSendBufferSize(DatabaseDescriptor.getInternodeSendBufferSize());
                    }
                    catch (SocketException se)
                    {
                        logger.warn("Failed to set send buffer size on internode socket.", se);
                    }
                }
                out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 4096));

                out.writeInt(MessagingService.PROTOCOL_MAGIC);
                writeHeader(out, targetVersion, shouldCompressConnection());
                out.flush();

                DataInputStream in = new DataInputStream(socket.getInputStream());
                int maxTargetVersion = handshakeVersion(in);
                if (maxTargetVersion == NO_VERSION)
                {
                    // no version is returned, so disconnect an try again: we will either get
                    // a different target version (targetVersion < MessagingService.VERSION_12)
                    // or if the same version the handshake will finally succeed
                    logger.debug("Target max version is {}; no version information yet, will retry", maxTargetVersion);
                    disconnect();
                    continue;
                }
                if (targetVersion > maxTargetVersion)
                {
                    logger.debug("Target max version is {}; will reconnect with that version", maxTargetVersion);
                    MessagingService.instance().setVersion(poolReference.endPoint(), maxTargetVersion);
                    disconnect();
                    return false;
                }

                if (targetVersion < maxTargetVersion && targetVersion < MessagingService.current_version)
                {
                    logger.trace("Detected higher max version {} (using {}); will reconnect when queued messages are done",
                                 maxTargetVersion, targetVersion);
                    MessagingService.instance().setVersion(poolReference.endPoint(), Math.min(MessagingService.current_version, maxTargetVersion));
                    softCloseSocket();
                }

                out.writeInt(MessagingService.current_version);
                CompactEndpointSerializationHelper.serialize(FBUtilities.getBroadcastAddress(), out);
                if (shouldCompressConnection())
                {
                    out.flush();
                    logger.trace("Upgrading OutputStream to be compressed");
                    if (targetVersion < MessagingService.VERSION_21)
                    {
                        out = new DataOutputStream(new SnappyOutputStream(new BufferedOutputStream(socket.getOutputStream())));
                    }
                    else
                    {
                        LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();
                        Checksum checksum = XXHashFactory.fastestInstance().newStreamingHash32(LZ4_HASH_SEED).asChecksum();
                        out = new DataOutputStream(new LZ4BlockOutputStream(new BufferedOutputStream(socket.getOutputStream()),
                                                                            1 << 14,  // 16k block size
                                                                            compressor,
                                                                            checksum,
                                                                            true)); // no async flushing
                    }
                }

                return true;
            }
            catch (IOException e)
            {
                socket = null;
                if (logger.isTraceEnabled())
                    logger.trace("unable to connect to " + poolReference.endPoint(), e);
                Uninterruptibles.sleepUninterruptibly(OPEN_RETRY_DELAY, TimeUnit.MILLISECONDS);
            }
        }
        return false;
    }
    
    private int handshakeVersion(final DataInputStream inputStream)
    {
        final AtomicInteger version = new AtomicInteger(NO_VERSION);
        final CountDownLatch versionLatch = new CountDownLatch(1);
        new Thread("HANDSHAKE-" + poolReference.endPoint())
        {
            @Override
            public void run()
            {
                try
                {
                    logger.info("Handshaking version with {}", poolReference.endPoint());
                    version.set(inputStream.readInt());
                }
                catch (IOException ex) 
                {
                    final String msg = "Cannot handshake version with " + poolReference.endPoint();
                    if (logger.isTraceEnabled())
                        logger.trace(msg, ex);
                    else
                        logger.info(msg);
                }
                finally
                {
                    //unblock the waiting thread on either success or fail
                    versionLatch.countDown();
                }
            }
        }.start();

        try
        {
            versionLatch.await(WAIT_FOR_VERSION_MAX_TIME, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException ex)
        {
            throw new AssertionError(ex);
        }
        return version.get();
    }

    private void expireMessages()
    {
        Iterator<QueuedMessage> iter = backlog.iterator();
        while (iter.hasNext())
        {
            QueuedMessage qm = iter.next();
            if (qm.timestamp >= System.currentTimeMillis() - qm.message.getTimeout())
                return;
            iter.remove();
            dropped.incrementAndGet();
        }
    }

    /** messages that have not been retried yet */
    private static class QueuedMessage
    {
        final MessageOut<?> message;
        final int id;
        final long timestamp;

        QueuedMessage(MessageOut<?> message, int id)
        {
            this.message = message;
            this.id = id;
            this.timestamp = System.currentTimeMillis();
        }

        boolean shouldRetry()
        {
            return !MessagingService.DROPPABLE_VERBS.contains(message.verb);
        }
    }

    private static class RetriedQueuedMessage extends QueuedMessage
    {
        RetriedQueuedMessage(QueuedMessage msg)
        {
            super(msg.message, msg.id);
        }

        boolean shouldRetry()
        {
            return false;
        }
    }
}
