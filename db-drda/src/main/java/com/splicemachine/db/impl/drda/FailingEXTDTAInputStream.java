/*
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified this file.
 *
 * All Splice Machine modifications are Copyright 2012 - 2016 Splice Machine, Inc.,
 * and are licensed to you under the License; you may not use this file except in
 * compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.splicemachine.db.impl.drda;

import java.io.IOException;
import java.io.InputStream;

/**
 * A stream class that throws an exception on the first read request.
 */
public final class FailingEXTDTAInputStream
        extends InputStream {

    /** The status byte used to determine which exception to throw. */
    private final byte extdtaStatus;

    public FailingEXTDTAInputStream(byte extdtaStatus) {
        this.extdtaStatus = extdtaStatus;
    }

    /**
     * Throws an exception.
     *
     * @return n/a
     * @throws IOException The exception to throw as dictated by the status
     *      byte sent by the client driver when reading user data and sending
     *      it as EXTDTA.
     */
    public int read()
            throws IOException {
        EXTDTAReaderInputStream.throwEXTDTATransferException(extdtaStatus);
        // Should never get this far, but just in case...
        throw new IllegalStateException("programming error - EXTDTA status");
    }
}
