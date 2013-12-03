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
package org.apache.cassandra.db.compaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.DataTracker;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.io.sstable.SSTableIdentityIterator;
import org.apache.cassandra.io.sstable.SSTableReader;
import org.apache.cassandra.utils.AlwaysPresentFilter;

/**
 * Manage compaction options.
 */
public class CompactionController
{
    private static final Logger logger = LoggerFactory.getLogger(CompactionController.class);

    public final ColumnFamilyStore cfs;
    private final DataTracker.SSTableIntervalTree overlappingTree;
    private final Set<SSTableReader> overlappingSSTables;
    private final Set<SSTableReader> compacting;

    public final int gcBefore;
    public final int mergeShardBefore;

    protected CompactionController(ColumnFamilyStore cfs, int maxValue)
    {
        this(cfs, null, maxValue);
    }

    public CompactionController(ColumnFamilyStore cfs, Set<SSTableReader> compacting,  int gcBefore)
    {
        assert cfs != null;
        this.cfs = cfs;
        this.gcBefore = gcBefore;
        this.compacting = compacting;
        // If we merge an old CounterId id, we must make sure that no further increment for that id are in an active memtable.
        // For that, we must make sure that this id was renewed before the creation of the oldest unflushed memtable. We
        // add 5 minutes to be sure we're on the safe side in terms of thread safety (though we should be fine in our
        // current 'stop all write during memtable switch' situation).
        this.mergeShardBefore = (int) ((cfs.oldestUnflushedMemtable() + 5 * 3600) / 1000);
        Set<SSTableReader> overlapping = compacting == null ? null : cfs.getAndReferenceOverlappingSSTables(compacting);
        this.overlappingSSTables = overlapping == null ? Collections.<SSTableReader>emptySet() : overlapping;
        this.overlappingTree = overlapping == null ? null : DataTracker.buildIntervalTree(overlapping);
    }

    public Set<SSTableReader> getFullyExpiredSSTables()
    {
        return getFullyExpiredSSTables(cfs, compacting, overlappingSSTables, gcBefore);
    }

    /**
     * Finds expired sstables
     *
     * works something like this;
     * 1. find "global" minTimestamp of overlapping sstables (excluding the possibly droppable ones)
     * 2. build a list of candidates to be dropped
     * 3. sort the candidate list, biggest maxTimestamp first in list
     * 4. check if the candidates to be dropped actually can be dropped (maxTimestamp < global minTimestamp) and it is included in the compaction
     *    - if not droppable, update global minTimestamp and remove from candidates
     * 5. return candidates.
     *
     * @param cfStore
     * @param compacting we take the drop-candidates from this set, it is usually the sstables included in the compaction
     * @param overlapping the sstables that overlap the ones in compacting.
     * @param gcBefore
     * @return
     */
    public static Set<SSTableReader> getFullyExpiredSSTables(ColumnFamilyStore cfStore, Set<SSTableReader> compacting, Set<SSTableReader> overlapping, int gcBefore)
    {
        logger.debug("Checking droppable sstables in {}", cfStore);

        if (compacting == null)
            return Collections.<SSTableReader>emptySet();

        List<SSTableReader> candidates = new ArrayList<SSTableReader>();

        long minTimestamp = Long.MAX_VALUE;

        for (SSTableReader sstable : overlapping)
            minTimestamp = Math.min(minTimestamp, sstable.getMinTimestamp());

        for (SSTableReader candidate : compacting)
        {
            if (candidate.getSSTableMetadata().maxLocalDeletionTime < gcBefore)
                candidates.add(candidate);
            else
                minTimestamp = Math.min(minTimestamp, candidate.getMinTimestamp());
        }

        // we still need to keep candidates that might shadow something in a
        // non-candidate sstable. And if we remove a sstable from the candidates, we
        // must take it's timestamp into account (hence the sorting below).
        Collections.sort(candidates, SSTableReader.maxTimestampComparator);

        Iterator<SSTableReader> iterator = candidates.iterator();
        while (iterator.hasNext())
        {
            SSTableReader candidate = iterator.next();
            if (candidate.getMaxTimestamp() >= minTimestamp)
            {
                minTimestamp = Math.min(candidate.getMinTimestamp(), minTimestamp);
                iterator.remove();
            }
            else
            {
               logger.debug("Dropping expired SSTable {} (maxLocalDeletionTime={}, gcBefore={})",
                        candidate, candidate.getSSTableMetadata().maxLocalDeletionTime, gcBefore);
            }
        }
        return new HashSet<SSTableReader>(candidates);
    }

    public String getKeyspace()
    {
        return cfs.keyspace.getName();
    }

    public String getColumnFamily()
    {
        return cfs.name;
    }

    /**
     * @return the largest timestamp before which it's okay to drop tombstones for the given partition;
     * i.e., after the maxPurgeableTimestamp there may exist newer data that still needs to be supressed
     * in other sstables.
     */
    public long maxPurgeableTimestamp(DecoratedKey key)
    {
        List<SSTableReader> filteredSSTables = overlappingTree.search(key);
        long min = Long.MAX_VALUE;
        for (SSTableReader sstable : filteredSSTables)
        {
            // if we don't have bloom filter(bf_fp_chance=1.0 or filter file is missing),
            // we check index file instead.
            if (sstable.getBloomFilter() instanceof AlwaysPresentFilter && sstable.getPosition(key, SSTableReader.Operator.EQ, false) != null)
                min = Math.min(min, sstable.getMinTimestamp());
            else if (sstable.getBloomFilter().isPresent(key.key))
                min = Math.min(min, sstable.getMinTimestamp());
        }
        return min;
    }

    public void invalidateCachedRow(DecoratedKey key)
    {
        cfs.invalidateCachedRow(key);
    }

    public void close()
    {
        SSTableReader.releaseReferences(overlappingSSTables);
    }
}
