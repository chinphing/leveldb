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

import org.iq80.leveldb.impl.SeekingIterator;
import org.iq80.leveldb.util.Slice;
import org.iq80.leveldb.util.SliceInput;
import org.iq80.leveldb.util.SliceOutput;
import org.iq80.leveldb.util.Slices;
import org.iq80.leveldb.util.VariableLengthQuantity;

import java.util.Comparator;
import java.util.NoSuchElementException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkPositionIndex;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static org.iq80.leveldb.util.SizeOf.SIZE_OF_INT;

public class BlockIterator
        implements SeekingIterator<Slice, Slice>
{
    private final SliceInput data;
    private final Comparator<Slice> comparator;

    private BlockEntry nextEntry;

    public BlockIterator(Slice data, Comparator<Slice> comparator)
    {
        requireNonNull(data, "data is null");
        requireNonNull(comparator, "comparator is null");

        this.data = data.input();
        this.comparator = comparator;

        seekToFirst();
    }

    @Override
    public boolean hasNext()
    {
        return nextEntry != null;
    }

    @Override
    public BlockEntry peek()
    {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return nextEntry;
    }

    @Override
    public BlockEntry next()
    {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        BlockEntry entry = nextEntry;

        if (!data.isReadable()) {
            nextEntry = null;
        }
        else {
            // read entry at current data position
            nextEntry = readEntry(data, nextEntry);
        }

        return entry;
    }

    @Override
    public void remove()
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Repositions the iterator so the beginning of this block.
     */
    @Override
    public void seekToFirst()
    {
        seekToPosition(0);
    }

    /**
     * Repositions the iterator so the key of the next BlockElement returned greater than or equal to the specified targetKey.
     */
    @Override
    public void seek(Slice targetKey)
    {
        // linear search (within restart block) for first key greater than or equal to targetKey
        for (seekToFirst(); hasNext(); next()) {
            if (comparator.compare(peek().getKey(), targetKey) >= 0) {
                break;
            }
        }

    }

    /**
     * Seeks to and reads the entry at the specified restart position.
     * <p/>
     * After this method, nextEntry will contain the next entry to return, and the previousEntry will be null.
     */
    private void seekToPosition(int offset)
    {
        data.setPosition(offset);

        // clear the entries to assure key is not prefixed
        nextEntry = null;

        if(data.available() > 0) {
            // read the entry
            nextEntry = readEntry(data, null);
        }
    }

    /**
     * Reads the entry at the current data readIndex.
     * After this method, data readIndex is positioned at the beginning of the next entry
     * or at the end of data if there was not a next entry.
     *
     * @return true if an entry was read
     */
    private static BlockEntry readEntry(SliceInput data, BlockEntry previousEntry)
    {
        requireNonNull(data, "data is null");

        // read entry header
        int keyLength = VariableLengthQuantity.readVariableLengthInt(data);
        int valueLength = VariableLengthQuantity.readVariableLengthInt(data);

        // read key
        Slice key = data.readSlice(keyLength);

        // read value
        Slice value = data.readSlice(valueLength);

        return new BlockEntry(key, value);
    }
}
