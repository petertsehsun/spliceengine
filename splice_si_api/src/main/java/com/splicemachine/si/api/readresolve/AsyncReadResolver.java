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

package com.splicemachine.si.api.readresolve;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.splicemachine.annotations.ThreadSafe;
import com.splicemachine.si.api.txn.TxnSupplier;
import com.splicemachine.si.impl.rollforward.RollForwardStatus;
import com.splicemachine.storage.Partition;
import com.splicemachine.utils.ByteSlice;
import com.splicemachine.utils.TrafficControl;
import org.apache.log4j.Logger;
import org.spark_project.guava.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Read-Resolver which asynchronously submits regions for execution, discarding
 * any entries which exceed the size of the processing queue.
 * <p/>
 * This implementation uses an LMAX disruptor to asynchronously pass Read-resolve events
 * to a background thread, which in turn uses a SynchronousReadResolver to actually perform the resolution.
 *
 * @author Scott Fines
 *         Date: 7/1/14
 */
@ThreadSafe
public class AsyncReadResolver{
    private static final Logger LOG=Logger.getLogger(AsyncReadResolver.class);
    private final RingBuffer<ResolveEvent> ringBuffer;
    private final Disruptor<ResolveEvent> disruptor;

    private final ThreadPoolExecutor consumerThreads;
    private volatile boolean stopped;
    private final TxnSupplier txnSupplier;
    private final RollForwardStatus status;
    private final TrafficControl trafficControl;
    private final KeyedReadResolver synchronousResolver;

    public AsyncReadResolver(int maxThreads,int bufferSize,
                             TxnSupplier txnSupplier,
                             RollForwardStatus status,
                             TrafficControl trafficControl,
                             KeyedReadResolver synchronousResolver){
        this.txnSupplier=txnSupplier;
        this.trafficControl=trafficControl;
        this.status=status;
        this.synchronousResolver = synchronousResolver;
        consumerThreads=new ThreadPoolExecutor(maxThreads,maxThreads,
                60,TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(),
                new ThreadFactoryBuilder().setNameFormat("readResolver-%d").setDaemon(true).build());

        int bSize=1;
        while(bSize<bufferSize)
            bSize<<=1;
        disruptor=new Disruptor<>(new ResolveEventFactory(),bSize,consumerThreads,
                ProducerType.MULTI,
                new BlockingWaitStrategy()); //we want low latency here, but it might cost too much in CPU
        disruptor.handleEventsWith(new ResolveEventHandler());
        ringBuffer=disruptor.getRingBuffer();
    }

    public void start(){
        disruptor.start();
    }

    public void shutdown(){
        stopped=true;
        disruptor.shutdown();
        consumerThreads.shutdownNow();
    }

    @ThreadSafe
    public ReadResolver getResolver(Partition region,RollForward rollForward){
        return new PartitionReadResolver(region,rollForward);
    }

    private static class ResolveEvent{
        Partition region;
        long txnId;
        ByteSlice rowKey=new ByteSlice();
        RollForward rollForward;
    }

    private static class ResolveEventFactory implements EventFactory<ResolveEvent>{

        @Override
        public ResolveEvent newInstance(){
            return new ResolveEvent();
        }
    }

    private class ResolveEventHandler implements EventHandler<ResolveEvent>{

        @Override
        public void onEvent(ResolveEvent event,long sequence,boolean endOfBatch) throws Exception{
            try{
                if(synchronousResolver.resolve(event.region,
                        event.rowKey,
                        event.txnId,
                        txnSupplier,
                        status,
                        false,
                        trafficControl)){
                    event.rollForward.recordResolved(event.rowKey,event.txnId);
                }
            }catch(Exception e){
                LOG.info("Error during read resolution",e);
                throw e;
            }
        }
    }

    private class PartitionReadResolver implements ReadResolver{
        private final Partition region;
        private final RollForward rollForward;

        public PartitionReadResolver(Partition region,RollForward rollForward){
            this.region=region;
            this.rollForward=rollForward;
        }

        @Override
        public void resolve(ByteSlice rowKey,long txnId){
            if(stopped) return; //we aren't running, so do nothing
            long sequence;
            try{
                sequence=ringBuffer.tryNext();
            }catch(InsufficientCapacityException e){
                if(LOG.isTraceEnabled())
                    LOG.trace("Unable to submit for read resolution");
                return;
            }

            try{
                ResolveEvent event=ringBuffer.get(sequence);
                event.region=region;
                event.txnId=txnId;
                event.rowKey.set(rowKey.getByteCopy());
                event.rollForward=rollForward;
            }finally{
                ringBuffer.publish(sequence);
            }
        }
    }
}
