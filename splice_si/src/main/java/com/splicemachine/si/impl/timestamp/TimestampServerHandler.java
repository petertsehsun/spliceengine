package com.splicemachine.si.impl.timestamp;

import com.splicemachine.utils.SpliceLogUtils;
import org.apache.hadoop.hbase.zookeeper.RecoverableZooKeeper;
import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;

import com.splicemachine.constants.SpliceConstants;

public class TimestampServerHandler extends TimestampBaseHandler {

    private static final Logger LOG = Logger.getLogger(TimestampServerHandler.class);

    private RecoverableZooKeeper rzk;
    private TimestampOracle oracle;

    public TimestampServerHandler(RecoverableZooKeeper rzk) {
        super();
        this.rzk = rzk;
    }

    public void initializeIfNeeded() throws TimestampIOException {
        SpliceLogUtils.trace(LOG, "Checking whether initialization is needed");
        synchronized (this) {
            if (oracle == null) {
                oracle = TimestampOracle.getInstance(rzk, SpliceConstants.zkSpliceMaxReservedTimestampPath);
            }
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        assert oracle != null;
        assert rzk != null;

        ChannelBuffer buf = (ChannelBuffer) e.getMessage();
        assert buf != null;
        ensureReadableBytes(buf, TimestampServer.FIXED_MSG_RECEIVED_LENGTH);

        final short callerId = buf.readShort();
        ensureReadableBytes(buf, 0);

        SpliceLogUtils.trace(LOG, "Received timestamp request from client. Caller id = %s", callerId);
        long nextTimestamp = oracle.getNextTimestamp();
        assert nextTimestamp > 0;


        //
        // Respond to the client
        //

        ChannelBuffer writeBuf = ChannelBuffers.buffer(TimestampServer.FIXED_MSG_SENT_LENGTH);
        writeBuf.writeShort(callerId);
        writeBuf.writeLong(nextTimestamp);
        SpliceLogUtils.debug(LOG, "Responding to caller %s with timestamp %s", callerId, nextTimestamp);
        ChannelFuture futureResponse = e.getChannel().write(writeBuf); // Could also use Channels.write
        futureResponse.addListener(new ChannelFutureListener() {
                                       @Override
                                       public void operationComplete(ChannelFuture cf) throws Exception {
                                           if (!cf.isSuccess()) {
                                               throw new TimestampIOException(
                                                       "Failed to respond successfully to caller id " + callerId, cf.getCause());
                                           }
                                       }
                                   }
        );

        super.messageReceived(ctx, e);
    }

    protected void doTrace(String message, Object... args) {
        SpliceLogUtils.trace(LOG, message, args);
    }

    protected void doDebug(String message, Object... args) {
        SpliceLogUtils.debug(LOG, message, args);
    }

    protected void doError(String message, Throwable t, Object... args) {
        SpliceLogUtils.error(LOG, message, t, args);
    }

}
