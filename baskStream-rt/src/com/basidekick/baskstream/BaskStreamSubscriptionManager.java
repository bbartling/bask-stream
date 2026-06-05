package com.basidekick.baskstream;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class BaskStreamSubscriptionManager
{
  private final BBaskStreamService service;
  private final Set<BaskStreamClientSession> sessions =
      Collections.newSetFromMap(new ConcurrentHashMap<BaskStreamClientSession, Boolean>());

  BaskStreamSubscriptionManager(BBaskStreamService service)
  {
    this.service = service;
  }

  boolean register(BaskStreamClientSession session)
  {
    if (getActiveConnectionCount() >= service.getMaxConnectionsValue())
    {
      refreshMetrics();
      return false;
    }

    boolean added = sessions.add(session);
    refreshMetrics();
    return added;
  }

  void unregister(BaskStreamClientSession session)
  {
    sessions.remove(session);
    refreshMetrics();
  }

  void refreshMetrics()
  {
    int totalSubscriptions = 0;
    for (BaskStreamClientSession session : sessions)
    {
      totalSubscriptions += session.getSubscriptionCount();
    }
    service.setRuntimeMetrics(sessions.size(), totalSubscriptions);
  }

  int getActiveConnectionCount()
  {
    return sessions.size();
  }

  void shutdown()
  {
    for (BaskStreamClientSession session : sessions.toArray(new BaskStreamClientSession[0]))
    {
      session.close("service stopped");
    }
    sessions.clear();
    refreshMetrics();
  }
}
