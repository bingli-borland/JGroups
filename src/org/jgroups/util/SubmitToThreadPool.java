package org.jgroups.util;

import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.logging.Log;
import org.jgroups.protocols.MsgStats;
import org.jgroups.protocols.TP;
import org.jgroups.protocols.TpHeader;
import org.jgroups.stack.MessageProcessingPolicy;

/**
 * Default message processing policy. Submits all received messages and batches to the thread pool
 * @author Bela Ban
 * @since  4.0
 */
public class SubmitToThreadPool implements MessageProcessingPolicy {
    protected TP    tp;
    protected short tp_id;
    protected Log   log;

    public void init(TP transport) {
        this.tp=transport;
        this.tp_id=tp.getId();
        this.log=tp.getLog();
    }


    public void process(Message msg) {
        boolean internal=msg.isFlagSet(Message.Flag.INTERNAL);
        tp.submitToThreadPool(new SingleMessageHandler(msg), internal);
    }

    public void process(MessageBatch batch, boolean oob, boolean internal) {
        if(oob)
            removeAndDispatchNonBundledMessages(batch);
        tp.submitToThreadPool(new BatchHandler(batch), internal);
    }



    protected void removeAndDispatchNonBundledMessages(MessageBatch oob_batch) {
        if(oob_batch == null)
            return;
        AsciiString tmp=oob_batch.clusterName();
        byte[] cname=tmp != null? tmp.chars() : null;
        for(Message msg: oob_batch) {
            if(msg.isFlagSet(Message.Flag.DONT_BUNDLE) && msg.isFlagSet(Message.Flag.OOB)) {
                boolean internal=msg.isFlagSet(Message.Flag.INTERNAL);
                oob_batch.remove(msg);
                if(tp.statsEnabled())
                    tp.getMessageStats().incrNumOOBMsgsReceived(1);
                tp.submitToThreadPool(new SingleMessageHandlerWithClusterName(msg, cname), internal);
            }
        }
    }


    protected class SingleMessageHandler implements Runnable {
        protected final Message msg;

        protected SingleMessageHandler(final Message msg) {
            this.msg=msg;
        }

        public void run() {
            Address dest=msg.getDest();
            boolean multicast=dest == null;
            try {
                if(tp.statsEnabled()) {
                    if(msg.isFlagSet(Message.Flag.OOB))
                        tp.getMessageStats().incrNumOOBMsgsReceived(1);
                    else if(msg.isFlagSet(Message.Flag.INTERNAL))
                        tp.getMessageStats().incrNumInternalMsgsReceived(1);
                    else
                        tp.getMessageStats().incrNumMsgsReceived(1);
                    tp.getMessageStats().incrNumBytesReceived(msg.getLength());
                }
                byte[] cname=getClusterName();
                tp.passMessageUp(msg, cname, true, multicast, true);
            }
            catch(Throwable t) {
                log.error(Util.getMessage("PassUpFailure"), t);
            }
        }

        protected byte[] getClusterName() {
            TpHeader hdr=msg.getHeader(tp_id);
            return hdr.getClusterName();
        }
    }

    protected class SingleMessageHandlerWithClusterName extends SingleMessageHandler {
        protected final byte[] cluster;

        @Override protected byte[] getClusterName() {
            return cluster;
        }

        protected SingleMessageHandlerWithClusterName(Message msg, byte[] cluster_name) {
            super(msg);
            this.cluster=cluster_name;
        }
    }

    protected class BatchHandler implements Runnable {
        protected MessageBatch batch;

        public BatchHandler(final MessageBatch batch) {
            this.batch=batch;
        }

        public void run() {
            if(tp.statsEnabled()) {
                int batch_size=batch.size();
                MsgStats msg_stats=tp.getMessageStats();
                if(batch.getMode() == MessageBatch.Mode.OOB)
                    msg_stats.incrNumOOBMsgsReceived(batch_size);
                else if(batch.getMode() == MessageBatch.Mode.INTERNAL)
                    msg_stats.incrNumInternalMsgsReceived(batch_size);
                else
                    msg_stats.incrNumMsgsReceived(batch_size);
                msg_stats.incrNumBatchesReceived(1);
                msg_stats.incrNumBytesReceived(batch.length());
                tp.avgBatchSize().add(batch_size);
            }

            if(!batch.multicast() && tp.unicastDestMismatch(batch.dest()))
                return;
            tp.passBatchUp(batch, true, true);
        }
    }

}
