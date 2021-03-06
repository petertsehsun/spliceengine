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

package com.splicemachine.si.impl;

import com.splicemachine.si.api.txn.Txn;
import com.splicemachine.si.api.txn.TxnView;
import org.spark_project.guava.cache.Cache;
import org.spark_project.guava.cache.CacheBuilder;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class DDLFilter implements Comparable<DDLFilter> {
    private final TxnView myTransaction;
    private Cache<Long,Boolean> visibilityMap;

		public DDLFilter(TxnView myTransaction) {
				this.myTransaction = myTransaction;
				visibilityMap = CacheBuilder.newBuilder().expireAfterWrite(60, TimeUnit.SECONDS).maximumSize(10000).build();
		}

		public boolean isVisibleBy(final TxnView txn) throws IOException {
        Boolean visible = visibilityMap.getIfPresent(txn.getTxnId());
        if(visible!=null) return visible;

        //if I haven't succeeded yet, don't do anything
        if(myTransaction.getState()!= Txn.State.COMMITTED) return false;
        //if my parent was rolled back, do nothing
        if(myTransaction.getParentTxnView().getEffectiveState()== Txn.State.ROLLEDBACK) return false;

//        if I have a parent, and he was rolled back, don't do anything
//        if(myParenTxn!=null && myParenTxn.getEffectiveState()== Txn.State.ROLLEDBACK) return false;
        try{
            return visibilityMap.get(txn.getTxnId(),new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return isVisible(txn);
                }
            });
        }catch(ExecutionException ee){
            throw new IOException(ee.getCause());
        }

		}

    private Boolean isVisible(TxnView txn) {
        /*
         * The Commit timestamp of myTransaction serves as the DDL
         * "demarcation point"--that is, the firm separator of
         * responsibilities. Any transaction which *begins before*
         * the demarcation point must be dealt with during a
         * second "populate" phase (e.g. Populate index, etc.), while
         * any transaction which *begins after* the demarcation point
         * is dealt with by our
         *
         */
        long otherTxnId = txn.getTxnId();
        return myTransaction.getEffectiveCommitTimestamp()<=otherTxnId;
    }

    public TxnView getTransaction() {
				return myTransaction;
		}

    @Override
    public boolean equals(Object o){
        return this==o || o instanceof DDLFilter && compareTo((DDLFilter)o)==0;
    }

    @Override
    public int hashCode(){
        return myTransaction.hashCode();
    }

    @Override
    public int compareTo(DDLFilter o) {
        if (o == null) {
            return 1;
        }
        if (myTransaction.getState()== Txn.State.COMMITTED) {
            if (o.getTransaction().getState() == Txn.State.COMMITTED) {
                return compare(myTransaction.getCommitTimestamp(), o.getTransaction().getCommitTimestamp());
            } else {
                return 1;
            }
        } else {
            if (o.getTransaction().getState()== Txn.State.COMMITTED) {
                return -1;
            } else {
                return compare(myTransaction.getEffectiveBeginTimestamp(), o.getTransaction().getEffectiveBeginTimestamp());
            }
        }
    }

    private static int compare(long my, long other) {
        if (my > other) {
            return 1;
        } else if (my < other) {
            return -1;
        } else {
            return 0;
        }
    }
}
