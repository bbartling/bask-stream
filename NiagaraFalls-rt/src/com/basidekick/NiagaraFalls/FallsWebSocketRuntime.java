package com.basidekick.niagarafalls;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.baja.sys.BasicContext;
import javax.baja.user.BUser;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.websocket.common.WebSocketSession;
import org.eclipse.jetty.websocket.common.events.EventDriver;
import org.eclipse.jetty.websocket.common.extensions.ExtensionStack;
import org.eclipse.jetty.websocket.server.HandshakeRFC6455;
import org.eclipse.jetty.websocket.server.WebSocketServerConnection;
import org.eclipse.jetty.websocket.server.WebSocketServerFactory;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;

final class FallsWebSocketRuntime
{
  private static final String HTTP_CONNECTION_ATTRIBUTE = "org.eclipse.jetty.server.HttpConnection";
  private static final String HTTP_UPGRADE_ATTRIBUTE = "org.eclipse.jetty.server.HttpConnection.UPGRADE";
  private static final String SUPPORTED_WS_VERSION = "13";

  private final BNiagaraFallsService service;
  private final FallsCodec codec;
  private final FallsPointResolver resolver;
  private final FallsBrowseResolver browseResolver;
  private final FallsHistoryResolver historyResolver;
  private final FallsAlarmResolver alarmResolver;
  private final FallsScheduleResolver scheduleResolver;
  private final FallsWriteResolver writeResolver;
  private final FallsSubscriptionManager subscriptions;
  private final ScheduledExecutorService scheduler;
  private volatile WebSocketServerFactory socketFactory;

  FallsWebSocketRuntime(BNiagaraFallsService service)
  {
    this.service = service;
    this.codec = new FallsCodec();
    this.resolver = new FallsPointResolver(service);
    this.browseResolver = new FallsBrowseResolver(service);
    this.historyResolver = new FallsHistoryResolver(service);
    this.alarmResolver = new FallsAlarmResolver(service);
    this.scheduleResolver = new FallsScheduleResolver(service);
    this.writeResolver = new FallsWriteResolver(service, resolver);
    this.subscriptions = new FallsSubscriptionManager(service);
    this.scheduler = Executors.newSingleThreadScheduledExecutor(new FallsThreadFactory());
  }

  void handleUpgrade(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
  {
    try
    {
      WebSocketServerFactory factory = ensureFactory(request.getServletContext());
      if (!factory.isUpgradeRequest(request, response))
      {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "WebSocket upgrade required.");
        return;
      }

      ServletUpgradeRequest upgradeRequest = new ServletUpgradeRequest(request);
      ServletUpgradeResponse upgradeResponse = new ServletUpgradeResponse(response);
      FallsSocketCreator creator = new FallsSocketCreator(this);
      Object endpoint = creator.createWebSocket(upgradeRequest, upgradeResponse);

      if (upgradeResponse.isCommitted())
      {
        return;
      }
      if (endpoint == null)
      {
        upgradeResponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Endpoint Creation Failed");
        return;
      }

      endpoint = factory.getObjectFactory().decorate(endpoint);
      EventDriver eventDriver = factory.getEventDriverFactory().wrap(endpoint);
      HttpConnection httpConnection = (HttpConnection) request.getAttribute(HTTP_CONNECTION_ATTRIBUTE);
      if (httpConnection == null)
      {
        throw new ServletException("Jetty HttpConnection attribute not found.");
      }

      performUpgrade(factory, httpConnection, upgradeRequest, upgradeResponse, eventDriver);
    }
    catch (ServletException e)
    {
      throw e;
    }
    catch (IOException e)
    {
      throw e;
    }
    catch (URISyntaxException e)
    {
      throw new IOException("Unable to accept websocket due to mangled URI", e);
    }
    catch (Exception e)
    {
      throw new ServletException("Unable to upgrade NiagaraFalls websocket request.", e);
    }
  }

  private void performUpgrade(
      WebSocketServerFactory factory,
      HttpConnection httpConnection,
      ServletUpgradeRequest request,
      ServletUpgradeResponse response,
      EventDriver eventDriver) throws Exception
  {
    String upgrade = request.getHeader("Upgrade");
    if (upgrade == null || !"websocket".equalsIgnoreCase(upgrade))
    {
      throw new IllegalStateException("Not a 'WebSocket: Upgrade' request");
    }
    if (!"HTTP/1.1".equals(request.getHttpVersion()))
    {
      throw new IllegalStateException("Not a 'HTTP/1.1' request");
    }

    int version = request.getHeaderInt("Sec-WebSocket-Version");
    if (version < 0)
    {
      version = request.getHeaderInt("Sec-WebSocket-Draft");
    }
    if (version != 13)
    {
      response.setHeader("Sec-WebSocket-Version", SUPPORTED_WS_VERSION);
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported websocket version specification");
      return;
    }

    ExtensionStack extensions = new ExtensionStack(factory.getExtensionFactory());
    if (response.isExtensionsNegotiated())
    {
      extensions.negotiate(response.getExtensions());
    }
    else
    {
      extensions.negotiate(request.getExtensions());
    }

    Connector connector = httpConnection.getConnector();
    WebSocketServerConnection wsConnection = new WebSocketServerConnection(
        httpConnection.getEndPoint(),
        connector.getExecutor(),
        connector.getScheduler(),
        eventDriver.getPolicy(),
        connector.getByteBufferPool());

    Collection<Connection.Listener> listeners = connector.getBeans(Connection.Listener.class);
    for (Connection.Listener listener : listeners)
    {
      wsConnection.addListener(listener);
    }

    extensions.setPolicy(eventDriver.getPolicy());
    extensions.configure(wsConnection.getParser());
    extensions.configure(wsConnection.getGenerator());

    WebSocketSession session = new WebSocketSession(factory, request.getRequestURI(), eventDriver, wsConnection);
    session.setUpgradeRequest(request);
    response.setExtensions(extensions.getNegotiatedExtensions());
    session.setUpgradeResponse(response);

    wsConnection.addListener(session);
    wsConnection.setNextIncomingFrames(extensions);
    extensions.setNextIncoming(session);
    session.setOutgoingHandler(extensions);
    extensions.setNextOutgoing(wsConnection);
    session.addManaged(extensions);

    request.setServletAttribute(HTTP_UPGRADE_ATTRIBUTE, wsConnection);
    new HandshakeRFC6455().doHandshakeResponse(request, response);
    response.setSuccess(true);
  }

  private synchronized WebSocketServerFactory ensureFactory(ServletContext servletContext) throws Exception
  {
    WebSocketServerFactory existing = socketFactory;
    if (existing != null)
    {
      return existing;
    }

    WebSocketServerFactory created = new WebSocketServerFactory(servletContext);
    created.getPolicy().setIdleTimeout(service.getHeartbeatIntervalSecValue() * 2000L);
    created.start();
    socketFactory = created;
    return created;
  }

  FallsCodec getCodec()
  {
    return codec;
  }

  FallsPointResolver getResolver()
  {
    return resolver;
  }

  FallsBrowseResolver getBrowseResolver()
  {
    return browseResolver;
  }

  FallsHistoryResolver getHistoryResolver()
  {
    return historyResolver;
  }

  FallsAlarmResolver getAlarmResolver()
  {
    return alarmResolver;
  }

  FallsScheduleResolver getScheduleResolver()
  {
    return scheduleResolver;
  }

  FallsWriteResolver getWriteResolver()
  {
    return writeResolver;
  }

  BNiagaraFallsService getService()
  {
    return service;
  }

  boolean onOpen(FallsClientSession session)
  {
    return subscriptions.register(session);
  }

  void onClose(FallsClientSession session)
  {
    subscriptions.unregister(session);
  }

  void onSubscriptionCountChanged()
  {
    subscriptions.refreshMetrics();
  }

  ScheduledFuture<?> schedule(Runnable task, long delayMillis)
  {
    long safeDelay = Math.max(0L, delayMillis);
    return scheduler.schedule(new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          task.run();
        }
        catch (Throwable e)
        {
          service.LOG.log(Level.WARNING, "NiagaraFalls scheduled task failed", e);
        }
      }
    }, safeDelay, TimeUnit.MILLISECONDS);
  }

  FallsClientSession buildSession(FallsJettyWebSocketConnection connection, ServletUpgradeRequest request)
      throws FallsProtocolException
  {
    Principal principal = request.getUserPrincipal();
    if (!(principal instanceof BUser))
    {
      throw new FallsProtocolException("auth_required", "Authenticated Niagara user is required.");
    }

    BUser user = (BUser) principal;
    String language = user.getLanguage();
    if (language == null || language.trim().length() == 0)
    {
      Locale locale = request.getLocale();
      language = locale == null ? null : locale.toLanguageTag();
    }
    BasicContext context = language == null || language.trim().length() == 0
        ? new BasicContext(user)
        : new BasicContext(user, language);

    return new FallsClientSession(this, connection, user, context);
  }

  void stop()
  {
    subscriptions.shutdown();
    scheduler.shutdownNow();
    WebSocketServerFactory factory = socketFactory;
    socketFactory = null;
    if (factory != null)
    {
      try
      {
        factory.stop();
      }
      catch (Exception e)
      {
        service.LOG.log(Level.FINE, "Unable to stop NiagaraFalls websocket factory cleanly", e);
      }
    }
  }

  private static final class FallsThreadFactory implements ThreadFactory
  {
    private int next;

    @Override
    public Thread newThread(Runnable task)
    {
      Thread thread = new Thread(task, "NiagaraFalls-websocket-" + (++next));
      thread.setDaemon(true);
      return thread;
    }
  }

  private static final class FallsSocketCreator implements WebSocketCreator
  {
    private final FallsWebSocketRuntime runtime;

    private FallsSocketCreator(FallsWebSocketRuntime runtime)
    {
      this.runtime = runtime;
    }

    @Override
    public Object createWebSocket(ServletUpgradeRequest request, ServletUpgradeResponse response)
    {
      if (request.getUserPrincipal() == null)
      {
        try
        {
          response.sendForbidden("Authentication required.");
        }
        catch (IOException e)
        {
          runtime.getService().LOG.log(Level.WARNING, "Unable to reject unauthenticated websocket request", e);
        }
        return null;
      }

      if (runtime.subscriptions.getActiveConnectionCount() >= runtime.getService().getMaxConnectionsValue())
      {
        try
        {
          response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Maximum websocket connection count reached.");
        }
        catch (IOException e)
        {
          runtime.getService().LOG.log(Level.WARNING, "Unable to reject websocket upgrade after max connection check", e);
        }
        return null;
      }

      return new FallsJettyWebSocketConnection(runtime, request);
    }
  }
}
