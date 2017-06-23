package net.spy.memcached.protocol.binary;

import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.evcache.pool.EVCacheClient;
import com.netflix.evcache.metrics.EVCacheMetricsFactory;
import com.netflix.evcache.pool.ServerGroup;
import com.netflix.evcache.util.EVCacheConfig;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Tag;

import net.spy.memcached.ConnectionFactory;
import net.spy.memcached.ops.Operation;
import sun.misc.Cleaner;
import sun.nio.ch.DirectBuffer;

@edu.umd.cs.findbugs.annotations.SuppressFBWarnings({ "FCBL_FIELD_COULD_BE_LOCAL", "EXS_EXCEPTION_SOFTENING_NO_CHECKED",
        "REC_CATCH_EXCEPTION", "SCII_SPOILED_CHILD_INTERFACE_IMPLEMENTATOR" })
public class EVCacheNodeImpl extends BinaryMemcachedNodeImpl implements EVCacheNodeImplMBean {
    private static final Logger log = LoggerFactory.getLogger(EVCacheNodeImpl.class);

    protected long stTime;
    protected final AtomicLong opCount = new AtomicLong(0);
    protected final AtomicInteger reconnectCount = new AtomicInteger(0);

    protected final String hostName;
    protected final BlockingQueue<Operation> readQ;
    protected final BlockingQueue<Operation> inputQueue;
    protected final DynamicBooleanProperty sendMetrics;
    protected final EVCacheClient client;
    protected final Counter counter;
    private long timeoutStartTime;

    public EVCacheNodeImpl(SocketAddress sa, SocketChannel c, int bufSize, BlockingQueue<Operation> rq, BlockingQueue<Operation> wq, BlockingQueue<Operation> iq,
            long opQueueMaxBlockTimeMillis, boolean waitForAuth, long dt, long at, ConnectionFactory fa, EVCacheClient client, long stTime) {
        super(sa, c, bufSize, rq, wq, iq, Long.valueOf(opQueueMaxBlockTimeMillis), waitForAuth, dt, at, fa);

        setConnectTime(stTime);
        this.client = client;
        final String appName = client.getAppName();
        this.readQ = rq;
        this.inputQueue = iq;
        this.sendMetrics = EVCacheConfig.getInstance().getDynamicBooleanProperty("EVCacheNodeImpl." + appName + ".sendMetrics", false);
        this.hostName = ((InetSocketAddress) getSocketAddress()).getHostName();
        counter = EVCacheMetricsFactory.getInstance().getCounter(EVCacheMetricsFactory.INTERNAL_NODE_OPERATION, client.getTagList());
        setupMonitoring(appName);
    }

    private String getMonitorName(String appName) {
        return "com.netflix.evcache:Group=" + appName + ",SubGroup=pool" + ",SubSubGroup=" + client.getServerGroupName()
                + ",SubSubSubGroup=" + client.getId() + ",SubSubSubSubGroup=" + hostName
                + "_" + stTime;
    }

    private void setupMonitoring(String appName) {
        try {
            final ObjectName mBeanName = ObjectName.getInstance(getMonitorName(appName));
            final MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            if (mbeanServer.isRegistered(mBeanName)) {
                if (log.isDebugEnabled()) log.debug("MBEAN with name " + mBeanName + " has been registered. Will unregister the previous instance and register a new one.");
                mbeanServer.unregisterMBean(mBeanName);
            }
            mbeanServer.registerMBean(this, mBeanName);
        } catch (Exception e) {
            if (log.isDebugEnabled()) log.debug("Exception while setting up the monitoring.", e);
        }
    }

    public boolean isAvailable() {
        return isActive();
    }

    public int getWriteQueueSize() {
        return writeQ.size();
    }

    public int getReadQueueSize() {
        return readQ.size();
    }

    public int getInputQueueSize() {
        return inputQueue.size();
    }

    public long incrOps() {
        counter.increment();
        return opCount.incrementAndGet();
    }

    public long getNumOfOps() {
        return opCount.get();
    }

    public void flushInputQueue() {
        inputQueue.clear();
    }

    public long getStartTime() {
        return stTime;
    }

    public long getTimeoutStartTime() {
        return timeoutStartTime;
    }

    public void removeMonitoring() {
        try {
            final ObjectName mBeanName = ObjectName.getInstance(getMonitorName(client.getAppName()));
            final MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            if (mbeanServer.isRegistered(mBeanName)) {
                if (log.isDebugEnabled()) log.debug("MBEAN with name " + mBeanName + " has been registered. Will unregister the previous instance and register a new one.");
                mbeanServer.unregisterMBean(mBeanName);
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) log.debug("Exception while setting up the monitoring.", e);
        }
    }

    public void shutdown() {
        removeMonitoring();
        writeQ.clear();
        readQ.clear();
        inputQueue.clear();
        try {
            // Cleanup the ByteBuffers only if they are sun.nio.ch.DirectBuffer
            // If we don't cleanup then we will leak 16K of memory
            if (getRbuf() instanceof DirectBuffer) {
                Cleaner cleaner = ((DirectBuffer) getRbuf()).cleaner();
                if (cleaner != null) cleaner.clean();
                cleaner = ((DirectBuffer) getWbuf()).cleaner();
                if (cleaner != null) cleaner.clean();
            }
        } catch (Throwable t) {
            getLogger().error("Exception cleaning ByteBuffer.", t);
        }
    }

    public long getCreateTime() {
        return stTime;
    }

    public void setConnectTime(long cTime) {
        this.stTime = cTime;
        reconnectCount.incrementAndGet();
    }

    public String getAppName() {
        return client.getAppName();
    }

    public String getHostName() {
        return hostName;
    }

    public ServerGroup getServerGroup() {
        return client.getServerGroup();
    }

    public int getId() {
        return client.getId();
    }

    public List<Tag> getTags() {
        return client.getTagList();
    }
    
    public int getTotalReconnectCount() {
        return reconnectCount.get();
    }
}
