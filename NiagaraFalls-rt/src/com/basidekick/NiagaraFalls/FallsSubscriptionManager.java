package com.basidekick.niagarafalls;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class FallsSubscriptionManager
{
  private final BNiagaraFallsService service;
  private final Set<FallsClientSession> sessions =
      Collections.newSetFromMap(new ConcurrentHashMap<FallsClientSession, Boolean>());

  FallsSubscriptionManager(BNiagaraFallsService service)
  {
    this.service = service;
  }

  boolean register(FallsClientSession session)
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

  void unregister(FallsClientSession session)
  {
    sessions.remove(session);
    refreshMetrics();
  }

  void refreshMetrics()
  {
    int totalSubscriptions = 0;
    for (FallsClientSession session : sessions)
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
    for (FallsClientSession session : sessions.toArray(new FallsClientSession[0]))
    {
      session.close("service stopped");
    }
    sessions.clear();
    refreshMetrics();
  }
}
