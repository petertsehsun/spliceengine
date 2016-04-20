package com.splicemachine.pipeline.writeconfiguration;

import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.splicemachine.derby.hbase.DerbyFactoryDriver;
import com.splicemachine.derby.hbase.ExceptionTranslator;
import com.splicemachine.pipeline.api.Code;
import com.splicemachine.pipeline.api.WriteConfiguration;
import com.splicemachine.pipeline.api.WriteResponse;
import com.splicemachine.pipeline.impl.ActionStatusReporter;
import com.splicemachine.pipeline.impl.BulkWrite;
import com.splicemachine.pipeline.impl.BulkWriteResult;
import com.splicemachine.pipeline.impl.WriteResult;

import java.util.concurrent.ExecutionException;

/**
 * @author Scott Fines
 *         Created on: 9/6/13
 */
public class CountingWriteConfiguration extends ForwardingWriteConfiguration {
    private final ActionStatusReporter statusReporter;

    public CountingWriteConfiguration(WriteConfiguration writeConfiguration, ActionStatusReporter statusMonitor) {
				super(writeConfiguration);
        this.statusReporter = statusMonitor;
    }

    @Override
    public WriteResponse globalError(Throwable t) throws ExecutionException {
        statusReporter.globalFailures.incrementAndGet();
        ExceptionTranslator handler = DerbyFactoryDriver.derbyFactory.getExceptionHandler();
        if(handler.isCallTimeoutException(t))
            statusReporter.timedOutFlushes.incrementAndGet();
        else if(handler.isNotServingRegionException(t))
            statusReporter.notServingRegionFlushes.incrementAndGet();
        else if(handler.isWrongRegionException(t))
            statusReporter.wrongRegionFlushes.incrementAndGet();
        return super.globalError(t);
    }

    @Override
    public WriteResponse processGlobalResult(BulkWriteResult bulkWriteResult)
            throws Throwable {
        WriteResult result = bulkWriteResult.getGlobalResult();
        Code code = result.getCode();
        switch (code) {
            case UNIQUE_VIOLATION:
            case CHECK_VIOLATION:
            case FAILED:
            case FOREIGN_KEY_VIOLATION:
            case PRIMARY_KEY_VIOLATION:
                statusReporter.failedBufferFlushes.incrementAndGet();
                break;
            case NOT_SERVING_REGION:
                statusReporter.notServingRegionFlushes.incrementAndGet();
                break;
            case PARTIAL:
                statusReporter.partialFailures.incrementAndGet();
                break;
            case PIPELINE_TOO_BUSY:
            case REGION_TOO_BUSY:
                statusReporter.rejectedCount.incrementAndGet();
                break;
            case WRITE_CONFLICT:
                statusReporter.writeConflictBufferFlushes.incrementAndGet();
                break;
            case WRONG_REGION:
                statusReporter.wrongRegionFlushes.incrementAndGet();
                break;
            default:
                break;

        }
        return super.processGlobalResult(bulkWriteResult);
    }

	@Override
    public WriteResponse partialFailure(BulkWriteResult result, BulkWrite request) throws ExecutionException {
        statusReporter.partialFailures.incrementAndGet();
        //look for timeouts, not serving regions, wrong regions, and so forth
        boolean notServingRegion= false;
        boolean wrongRegion = false;
        boolean failed = false;
        boolean writeConflict = false;
        for(IntObjectCursor<WriteResult> cursor:result.getFailedRows()){
            Code code = cursor.value.getCode();
            switch (code) {
                case FAILED:
                    failed=true;
                    break;
                case WRITE_CONFLICT:
                    writeConflict=true;
                    break;
                case NOT_SERVING_REGION:
                    notServingRegion = true;
                    break;
                case WRONG_REGION:
                    wrongRegion = true;
                    break;
            }
        }
        if(notServingRegion)
            statusReporter.notServingRegionFlushes.incrementAndGet();
        if(wrongRegion)
            statusReporter.wrongRegionFlushes.incrementAndGet();
        if(writeConflict)
            statusReporter.writeConflictBufferFlushes.incrementAndGet();
        if(failed)
            statusReporter.failedBufferFlushes.incrementAndGet();
        return super.partialFailure(result,request);
    }
	
	@Override
	public String toString() {
		return String.format("CountingWriteConfiguration{delegate=%s, statusReporter=%s}",this.delegate, statusReporter);
	}
	
}