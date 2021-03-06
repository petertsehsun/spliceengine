/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.pipeline.client;

import com.splicemachine.encoding.ExpandedDecoder;
import com.splicemachine.encoding.ExpandingEncoder;
import com.splicemachine.kvpair.KVPair;
import com.splicemachine.si.api.data.TxnOperationFactory;
import com.splicemachine.si.api.txn.TxnView;
import com.splicemachine.utils.ByteSlice;
import org.spark_project.guava.collect.Iterators;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Utilities around encoding and decoding BulkWriteRequests and responses.
 *
 * @author Scott Fines
 *         Date: 1/19/15
 */
public class PipelineEncoding {

    public static byte[] encode(TxnOperationFactory operationFactory,BulkWrites bulkWrites){
        /*
         * The encoding for a BulkWrites is as follows:
         * Txn (1-N bytes)
         * # of BulkWrites (1-N bytes)
         * for 1...# of BulkWrites:
         *  encodedStringName
         * for 1...# of BulkWrites:
         *  skipWriteIndex
         * for 1...# of BulkWrites:
         *  KVPairs
         *
         * This encoding follows the rule of "Header-body", where the "header" of the data
         * in this case is the metadata about the request, while the "body" is a byte array
         * sequence of KVPairs. This means that we can decode the necessary metadata eagerly,
         * but deserialize the KVPairs on an as-needed basis.
         *
         */
        byte[] txnBytes = operationFactory.encode(bulkWrites.getTxn());

        int heapSize = bulkWrites.getBufferHeapSize();
        ExpandingEncoder buffer = new ExpandingEncoder(heapSize+txnBytes.length);
        buffer.rawEncode(txnBytes);

        //encode BulkWrite metadata
        Collection<BulkWrite> bws = bulkWrites.getBulkWrites();
        buffer.encode(bws.size());
        for(BulkWrite bw:bws){
            buffer.encode(bw.getEncodedStringName());
        }

        for(BulkWrite bw:bws){
            buffer.encode(bw.getSkipIndexWrite());
        }

        for(BulkWrite bw:bws){
            Collection<KVPair> mutations = bw.getMutations();
            buffer.encode(mutations.size());
            for(KVPair kvPair:mutations){
                //TODO -sf- use a run-length encoding for type information here?
                buffer.rawEncode(kvPair.getType().asByte());
                buffer.rawEncode(kvPair.rowKeySlice());
                buffer.rawEncode(kvPair.valueSlice());
            }
        }
        return buffer.getBuffer();
    }


    public static BulkWrites decode(TxnOperationFactory operationFactory,byte[] data){
        ExpandedDecoder decoder = new ExpandedDecoder(data);
        byte[] txnBytes = decoder.rawBytes();
        TxnView txn = operationFactory.decode(txnBytes,0,txnBytes.length);
        int bwSize = decoder.decodeInt();
        List<String> stringNames = new ArrayList<>(bwSize);
        for(int i=0;i<bwSize;i++) {
            stringNames.add(decoder.decodeString());
        }
        byte[] skipIndexWrites = new byte[bwSize];
        for (int i=0; i<bwSize; i++) {
            skipIndexWrites[i] = decoder.decodeByte();
        }

        return new BulkWrites(new BulkWriteCol(skipIndexWrites,data,decoder.currentOffset(),stringNames),txn);
    }


    /***********************************************************************************************************/
    /*private helper classes*/
    private static class BulkWriteCol extends AbstractCollection<BulkWrite>{
        private final int kvOffset;
        private final List<String> encodedStringNames;
        private final byte[] skipIndexWrites;
        private final byte[] buffer;
        /*
         * we keep a cache of previously created BulkWrites, so that we can have
         * deterministic iteration (i.e. returning the same objects instead of
         * creating new with each iteration);
         */
        private transient Collection<BulkWrite> cache;
        private transient ExpandedDecoder decoder;
        private transient int lastIndex = 0;

        public BulkWriteCol(byte[] skipIndexWrites, byte[] buffer,int kvOffset, List<String> encodedStringNames) {
            this.kvOffset = kvOffset;
            this.encodedStringNames = encodedStringNames;
            this.buffer = buffer;
            this.skipIndexWrites = skipIndexWrites;
        }

        @Override
        @Nonnull
        public Iterator<BulkWrite> iterator() {
            if(cache!=null){
                if(cache.size()==encodedStringNames.size())
                    return cache.iterator();
                else{
                    /*
                     * We haven't read the entire data off yet, so we need to concatenate the
                     * cache with the remainder of the stuff
                     */
                    return Iterators.concat(cache.iterator(), new BulkIter(lastIndex));
                }
            }
            cache = new ArrayList<>(encodedStringNames.size());
            decoder = new ExpandedDecoder(buffer,kvOffset);
            return new BulkIter(0);
        }

        @Override public int size() { return encodedStringNames.size(); }

        private class BulkIter implements Iterator<BulkWrite> {
            final Iterator<String> encodedStrings;
            int index;

            public BulkIter(int startIndex) {
                this.index = startIndex;
                if(index!=0)
                    this.encodedStrings = encodedStringNames.subList(index,encodedStringNames.size()).iterator();
                else
                    this.encodedStrings = encodedStringNames.iterator();
            }

            @Override public boolean hasNext() { return encodedStrings.hasNext(); }
            @Override public void remove() { throw new UnsupportedOperationException(); }

            @Override
            public BulkWrite next() {
                String esN = encodedStrings.next();
                byte skipIndexWrite = skipIndexWrites[index++];
                int size = decoder.decodeInt();
                Collection<KVPair> kvPairs = new ArrayList<>(size);
                KVPair template = new KVPair();
                ByteSlice rowKeySlice = template.rowKeySlice();
                ByteSlice valueSlice = template.valueSlice();
                for(int i=0;i<size;i++){
                    template.setType(KVPair.Type.decode(decoder.rawByte()));
                    decoder.sliceNext(rowKeySlice);
                    decoder.sliceNext(valueSlice);
                    kvPairs.add(template.shallowClone());
                }


                BulkWrite bulkWrite = new BulkWrite(kvPairs, esN, skipIndexWrite);
                cache.add(bulkWrite);
                lastIndex=index;
                return bulkWrite;
            }
        }
    }
}
