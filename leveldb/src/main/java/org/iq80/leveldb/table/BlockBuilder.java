/*
 * Copyright (C) 2011 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.iq80.leveldb.table;

import com.google.common.primitives.Ints;
import org.iq80.leveldb.util.DynamicSliceOutput;
import org.iq80.leveldb.util.IntVector;
import org.iq80.leveldb.util.Slice;
import org.iq80.leveldb.util.VariableLengthQuantity;

import java.util.Comparator;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkPositionIndex;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static org.iq80.leveldb.util.SizeOf.SIZE_OF_INT;

public class BlockBuilder
{
    private final Comparator<Slice> comparator;

    private int entryCount;

    private boolean finished;
    private final DynamicSliceOutput block;

    public BlockBuilder(int estimatedSize, int blockRestartInterval, Comparator<Slice> comparator)
    {
        checkArgument(estimatedSize >= 0, "estimatedSize is negative");
        checkArgument(blockRestartInterval >= 0, "blockRestartInterval is negative");
        requireNonNull(comparator, "comparator is null");

        this.block = new DynamicSliceOutput(estimatedSize);
        this.comparator = comparator;
    }

    public void reset()
    {
        block.reset();
        entryCount = 0;
        finished = false;
    }

    public int getEntryCount()
    {
        return entryCount;
    }

    public boolean isEmpty()
    {
        return entryCount == 0;
    }

    public int currentSizeEstimate()
    {
        return block.size();
    }

    public void add(BlockEntry blockEntry)
    {
        requireNonNull(blockEntry, "blockEntry is null");
        add(blockEntry.getKey(), blockEntry.getValue());
    }

    public void add(Slice key, Slice value)
    {
        requireNonNull(key, "key is null");
        requireNonNull(value, "value is null");
        checkState(!finished, "block is finished");

        // write "<key_size><value_size>"
        VariableLengthQuantity.writeVariableLengthInt(key.length(), block);
        VariableLengthQuantity.writeVariableLengthInt(value.length(), block);

        // write  key bytes
        block.writeBytes(key, 0, key.length());
        // write value bytes
        block.writeBytes(value, 0, value.length());

        // update state
        entryCount++;
    }

    public static int calculateSharedBytes(Slice leftKey, Slice rightKey)
    {
        int sharedKeyBytes = 0;

        if (leftKey != null && rightKey != null) {
            int minSharedKeyBytes = Ints.min(leftKey.length(), rightKey.length());
            while (sharedKeyBytes < minSharedKeyBytes && leftKey.getByte(sharedKeyBytes) == rightKey.getByte(sharedKeyBytes)) {
                sharedKeyBytes++;
            }
        }

        return sharedKeyBytes;
    }

    public Slice finish()
    {
        return block.slice();
    }
}
