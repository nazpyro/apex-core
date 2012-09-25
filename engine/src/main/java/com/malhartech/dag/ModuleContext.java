/*
 *  Copyright (c) 2012 Malhar, Inc.
 *  All Rights Reserved.
 */
package com.malhartech.dag;

import com.malhartech.util.CircularBuffer;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The for context for all of the operators<p>
 * <br>
 *
 * @author Chetan Narsude <chetan@malhar-inc.com>
 */
public class ModuleContext implements Context
{
  /**
   * Command to be executed at subsequent end of window.
   * Current used for module state saving, but applicable more widely.
   */
  public interface EndWindowCommand {
    public void execute(Module module, String id, long windowId) throws IOException;
  }

  private static final Logger LOG = LoggerFactory.getLogger(ModuleContext.class);
  private EndWindowCommand backupRequest;
  private final Thread executingThread;

  public Thread getExecutingThread()
  {
    return executingThread;
  }

  @SuppressWarnings("PublicInnerClass")
  public static enum RequestType
  {
    UNDEFINED,
    REPORT,
    BACKUP,
    TERMINATE
  }
  private final String id;
  // the size of the circular queue should be configurable. hardcoded to 1024 for now.
  private final CircularBuffer<HeartbeatCounters> heartbeatCounters = new CircularBuffer<HeartbeatCounters>(1024);
  private volatile RequestType request = RequestType.UNDEFINED;
  /**
   * The AbstractNode to which this context is passed, will timeout after the following milliseconds if no new tuple has been received by it.
   */
  // we should make it configurable somehow.
  private long idleTimeout = 1000L;

  /**
   * @return the requestType
   */
  public final RequestType getRequestType()
  {
    return request;
  }

  /**
   * @return the idleTimeout
   */
  public long getIdleTimeout()
  {
    return idleTimeout;
  }

  /**
   * @param idleTimeout the idleTimeout to set
   */
  public void setIdleTimeout(long idleTimeout)
  {
    this.idleTimeout = idleTimeout;
  }

  public ModuleContext(String id, Thread t)
  {
    this.id = id;
    executingThread = t;
  }

  public String getId()
  {
    return id;
  }

  /**
   * Reset counts for next heartbeateinterval and return current counts. This is called as part of the heartbeat processing.
   *
   * @return int
   */
  public final synchronized int drainHeartbeatCounters(Collection<? super HeartbeatCounters> counters)
  {
    return heartbeatCounters.drainTo(counters);
  }

  long lastProcessedWindowId;
  public final synchronized long getLastProcessedWindowId()
  {
    return lastProcessedWindowId;
  }

  synchronized void report(int consumedTupleCount, long processedBytes, long windowId)
  {
    lastProcessedWindowId = windowId;

    HeartbeatCounters newWindow = new HeartbeatCounters();
    newWindow.windowId = windowId;
    newWindow.tuplesProcessed = consumedTupleCount;
    newWindow.bytesProcessed = processedBytes;
    try {
      heartbeatCounters.add(newWindow);
    }
    catch (BufferOverflowException boe) {
      heartbeatCounters.get();
      heartbeatCounters.add(newWindow);
    }
  }

  void backup(Module aThis, long windowId) throws IOException
  {
    LOG.debug("Backup node={}, window={}", id, windowId);
    this.backupRequest.execute(aThis, id, windowId);
    request = RequestType.UNDEFINED;
  }

  public void requestBackup(EndWindowCommand backupRequest)
  {
    this.backupRequest = backupRequest;
    request = RequestType.BACKUP;
    LOG.debug("Received backup request (node={})", id);
  }

}
