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

package com.splicemachine.concurrent.traffic;

import com.splicemachine.annotations.ThreadSafe;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread-safe, updateable version of TrafficStats. Useful
 * for keeping running statistics counts.
 *
 * @author Scott Fines
 *         Date: 11/14/14
 */
@ThreadSafe
public class MutableTrafficStats implements TrafficStats{
    AtomicLong totalRequests = new AtomicLong(0l);
    AtomicLong totalPermitsRequested = new AtomicLong(0l);
    AtomicLong totalPermitsGranted = new AtomicLong(0l);

    @Override public long totalPermitsRequested() { return totalPermitsRequested.get(); }
    @Override public long totalPermitsGranted() { return totalPermitsGranted.get(); }

    @Override
    public double permitThroughput() {
        return 0;
    }

    @Override
    public double permitThroughput1M() {
        return 0;
    }

    @Override
    public double permitThroughput5M() {
        return 0;
    }

    @Override
    public double permitThroughput15M() {
        return 0;
    }

    @Override
    public long totalRequests() {
        return 0;
    }

    @Override
    public long avgRequestLatency() {
        return 0;
    }
}
