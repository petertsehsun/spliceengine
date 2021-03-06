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

package com.splicemachine.test;

import com.splicemachine.concurrent.Threads;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

/**
 * Waits for connections on a given host+port to be available.
 */
public class SpliceTestPlatformWait {

    private static final long MAX_WAIT_SECS = TimeUnit.SECONDS.toSeconds(180);

    /**
     * argument 0 - hostname
     * argument 1 - port
     */
    public static void main(String[] arguments) throws IOException {

        String hostname = arguments[0];
        int port = Integer.valueOf(arguments[1]);

        long startTime = System.currentTimeMillis();
        long elapsedSecs = 0;
        while (elapsedSecs < MAX_WAIT_SECS) {
            try {
                new Socket(hostname, port);
                System.out.println("\nStarted\n");
                break;
            } catch (Exception e) {
                System.out.println(format("SpliceTestPlatformWait: Not started, still waiting for '%s:%s'. %s of %s seconds elapsed.",
                        hostname, port, elapsedSecs, MAX_WAIT_SECS));
                Threads.sleep(1, TimeUnit.SECONDS);
            }
            elapsedSecs = (long) ((System.currentTimeMillis() - startTime) / 1000d);
        }
        if (elapsedSecs >= MAX_WAIT_SECS) {
            System.out.println(format("Waited %s seconds without success", MAX_WAIT_SECS));
            System.exit(-1);
        }

    }

}
