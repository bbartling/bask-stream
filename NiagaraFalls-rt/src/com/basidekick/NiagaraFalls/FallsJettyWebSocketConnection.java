package com.basidekick.niagarafalls;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.logging.Level;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;

final class FallsJettyWebSocketConnection extends WebSocketAdapter
{
  private final FallsWebSocketRuntime runtime;
  private final ServletUpgradeRequest upgradeRequest;
  private volatile FallsClientSession clientSession;

  FallsJettyWebSocketConnection(FallsWebSocketRuntime runtime, ServletUpgradeRequest upgradeRequest)
  {
    this.runtime = runtime;
    this.upgradeRequest = upgradeRequest;
  }

  @Override
  public void onWebSocketConnect(Session session)
  {
    super.onWebSocketConnect(session);
    session.setIdleTimeout(runtime.getService().getHeartbeatIntervalSecValue() * 2000L);

    try
    {
      FallsClientSession next = runtime.buildSession(this, upgradeRequest);
      if (!runtime.onOpen(next))
      {
        session.close(1013, "Connection limit reached.");
        return;
      }
      clientSession = next;
    }
    catch (FallsProtocolException e)
    {
      runtime.getService().LOG.log(Level.WARNING, "Failed to initialize NiagaraFalls websocket session", e);
      session.close(1008, e.getMessage());
    }
  }

  @Override
  public void onWebSocketBinary(byte[] payload, int offset, int len)
  {
    FallsClientSession current = clientSession;
    if (current == null)
    {
      return;
    }

    byte[] frame = Arrays.copyOfRange(payload, offset, offset + len);
    current.onBinary(frame);
  }

  @Override
  public void onWebSocketText(String message)
  {
    Session session = getSession();
    if (session != null && session.isOpen())
    {
      session.close(1003, "Text frames are not supported.");
    }
  }

  @Override
  public void onWebSocketClose(int statusCode, String reason)
  {
    FallsClientSession current = clientSession;
    clientSession = null;
    if (current != null)
    {
      current.close(reason == null ? "closed" : reason);
    }
    super.onWebSocketClose(statusCode, reason);
  }

  @Override
  public void onWebSocketError(Throwable cause)
  {
    runtime.getService().LOG.log(Level.WARNING, "NiagaraFalls websocket transport error", cause);
    FallsClientSession current = clientSession;
    if (current != null)
    {
      current.close(cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
    }
    super.onWebSocketError(cause);
  }

  void send(byte[] payload) throws IOException
  {
    if (getRemote() != null)
    {
      getRemote().sendBytes(ByteBuffer.wrap(payload));
    }
  }
}
