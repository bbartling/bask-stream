package com.basidekick.niagarafalls;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.baja.nre.annotations.NiagaraProperty;
import javax.baja.nre.annotations.NiagaraType;
import javax.baja.sys.BRelTime;
import javax.baja.sys.Flags;
import javax.baja.sys.Property;
import javax.baja.sys.Sys;
import javax.baja.sys.Type;
import javax.baja.web.BWebServlet;
import javax.baja.web.WebOp;

@NiagaraType
@NiagaraProperty(
  name = "wsPath",
  type = "baja:String",
  defaultValue = "/falls",
  flags = Flags.SUMMARY
)
@NiagaraProperty(
  name = "maxConnections",
  type = "int",
  defaultValue = "10",
  flags = Flags.SUMMARY
)
@NiagaraProperty(
  name = "maxSubscriptionsPerClient",
  type = "int",
  defaultValue = "500",
  flags = Flags.SUMMARY
)
@NiagaraProperty(
  name = "heartbeatIntervalSec",
  type = "int",
  defaultValue = "30",
  flags = Flags.SUMMARY
)
@NiagaraProperty(
  name = "subscriptionLeaseSec",
  type = "int",
  defaultValue = "300",
  flags = Flags.SUMMARY
)
@NiagaraProperty(
  name = "covBatchWindowMillis",
  type = "int",
  defaultValue = "100",
  flags = Flags.SUMMARY
)
@NiagaraProperty(
  name = "allowedPathPatterns",
  type = "baja:String",
  defaultValue = "slot:/*",
  flags = Flags.SUMMARY
)
@NiagaraProperty(
  name = "activeConnections",
  type = "int",
  defaultValue = "0",
  flags = Flags.READONLY | Flags.TRANSIENT
)
@NiagaraProperty(
  name = "totalSubscriptions",
  type = "int",
  defaultValue = "0",
  flags = Flags.READONLY | Flags.TRANSIENT
)
public final class BNiagaraFallsService extends BWebServlet
{
  public static final Logger LOG = Logger.getLogger(BNiagaraFallsService.class.getName());
  private static final AtomicReference<BNiagaraFallsService> ACTIVE = new AtomicReference<BNiagaraFallsService>();
  private volatile FallsWebSocketRuntime runtime;

//region /*+ ------------ BEGIN BAJA AUTO GENERATED CODE ------------ +*/
//@formatter:off
/*@ $com.basidekick.niagarafalls.BNiagaraFallsService(2397860206)1.0$ @*/
/* Generated Mon May 25 21:23:30 MST 2026 by Slot-o-Matic (c) Tridium, Inc. 2012-2026 */

  //region Property "wsPath"

  /**
   * Slot for the {@code wsPath} property.
   * @see #getWsPath
   * @see #setWsPath
   */
  public static final Property wsPath = newProperty(Flags.SUMMARY, "/falls", null);

  /**
   * Get the {@code wsPath} property.
   * @see #wsPath
   */
  public String getWsPath() { return getString(wsPath); }

  /**
   * Set the {@code wsPath} property.
   * @see #wsPath
   */
  public void setWsPath(String v) { setString(wsPath, v, null); }

  //endregion Property "wsPath"

  //region Property "maxConnections"

  /**
   * Slot for the {@code maxConnections} property.
   * @see #getMaxConnections
   * @see #setMaxConnections
   */
  public static final Property maxConnections = newProperty(Flags.SUMMARY, 10, null);

  /**
   * Get the {@code maxConnections} property.
   * @see #maxConnections
   */
  public int getMaxConnections() { return getInt(maxConnections); }

  /**
   * Set the {@code maxConnections} property.
   * @see #maxConnections
   */
  public void setMaxConnections(int v) { setInt(maxConnections, v, null); }

  //endregion Property "maxConnections"

  //region Property "maxSubscriptionsPerClient"

  /**
   * Slot for the {@code maxSubscriptionsPerClient} property.
   * @see #getMaxSubscriptionsPerClient
   * @see #setMaxSubscriptionsPerClient
   */
  public static final Property maxSubscriptionsPerClient = newProperty(Flags.SUMMARY, 500, null);

  /**
   * Get the {@code maxSubscriptionsPerClient} property.
   * @see #maxSubscriptionsPerClient
   */
  public int getMaxSubscriptionsPerClient() { return getInt(maxSubscriptionsPerClient); }

  /**
   * Set the {@code maxSubscriptionsPerClient} property.
   * @see #maxSubscriptionsPerClient
   */
  public void setMaxSubscriptionsPerClient(int v) { setInt(maxSubscriptionsPerClient, v, null); }

  //endregion Property "maxSubscriptionsPerClient"

  //region Property "heartbeatIntervalSec"

  /**
   * Slot for the {@code heartbeatIntervalSec} property.
   * @see #getHeartbeatIntervalSec
   * @see #setHeartbeatIntervalSec
   */
  public static final Property heartbeatIntervalSec = newProperty(Flags.SUMMARY, 30, null);

  /**
   * Get the {@code heartbeatIntervalSec} property.
   * @see #heartbeatIntervalSec
   */
  public int getHeartbeatIntervalSec() { return getInt(heartbeatIntervalSec); }

  /**
   * Set the {@code heartbeatIntervalSec} property.
   * @see #heartbeatIntervalSec
   */
  public void setHeartbeatIntervalSec(int v) { setInt(heartbeatIntervalSec, v, null); }

  //endregion Property "heartbeatIntervalSec"

  //region Property "subscriptionLeaseSec"

  /**
   * Slot for the {@code subscriptionLeaseSec} property.
   * @see #getSubscriptionLeaseSec
   * @see #setSubscriptionLeaseSec
   */
  public static final Property subscriptionLeaseSec = newProperty(Flags.SUMMARY, 300, null);

  /**
   * Get the {@code subscriptionLeaseSec} property.
   * @see #subscriptionLeaseSec
   */
  public int getSubscriptionLeaseSec() { return getInt(subscriptionLeaseSec); }

  /**
   * Set the {@code subscriptionLeaseSec} property.
   * @see #subscriptionLeaseSec
   */
  public void setSubscriptionLeaseSec(int v) { setInt(subscriptionLeaseSec, v, null); }

  //endregion Property "subscriptionLeaseSec"

  //region Property "covBatchWindowMillis"

  /**
   * Slot for the {@code covBatchWindowMillis} property.
   * @see #getCovBatchWindowMillis
   * @see #setCovBatchWindowMillis
   */
  public static final Property covBatchWindowMillis = newProperty(Flags.SUMMARY, 100, null);

  /**
   * Get the {@code covBatchWindowMillis} property.
   * @see #covBatchWindowMillis
   */
  public int getCovBatchWindowMillis() { return getInt(covBatchWindowMillis); }

  /**
   * Set the {@code covBatchWindowMillis} property.
   * @see #covBatchWindowMillis
   */
  public void setCovBatchWindowMillis(int v) { setInt(covBatchWindowMillis, v, null); }

  //endregion Property "covBatchWindowMillis"

  //region Property "allowedPathPatterns"

  /**
   * Slot for the {@code allowedPathPatterns} property.
   * @see #getAllowedPathPatterns
   * @see #setAllowedPathPatterns
   */
  public static final Property allowedPathPatterns = newProperty(Flags.SUMMARY, "slot:/*", null);

  /**
   * Get the {@code allowedPathPatterns} property.
   * @see #allowedPathPatterns
   */
  public String getAllowedPathPatterns() { return getString(allowedPathPatterns); }

  /**
   * Set the {@code allowedPathPatterns} property.
   * @see #allowedPathPatterns
   */
  public void setAllowedPathPatterns(String v) { setString(allowedPathPatterns, v, null); }

  //endregion Property "allowedPathPatterns"

  //region Property "activeConnections"

  /**
   * Slot for the {@code activeConnections} property.
   * @see #getActiveConnections
   * @see #setActiveConnections
   */
  public static final Property activeConnections = newProperty(Flags.READONLY | Flags.TRANSIENT, 0, null);

  /**
   * Get the {@code activeConnections} property.
   * @see #activeConnections
   */
  public int getActiveConnections() { return getInt(activeConnections); }

  /**
   * Set the {@code activeConnections} property.
   * @see #activeConnections
   */
  public void setActiveConnections(int v) { setInt(activeConnections, v, null); }

  //endregion Property "activeConnections"

  //region Property "totalSubscriptions"

  /**
   * Slot for the {@code totalSubscriptions} property.
   * @see #getTotalSubscriptions
   * @see #setTotalSubscriptions
   */
  public static final Property totalSubscriptions = newProperty(Flags.READONLY | Flags.TRANSIENT, 0, null);

  /**
   * Get the {@code totalSubscriptions} property.
   * @see #totalSubscriptions
   */
  public int getTotalSubscriptions() { return getInt(totalSubscriptions); }

  /**
   * Set the {@code totalSubscriptions} property.
   * @see #totalSubscriptions
   */
  public void setTotalSubscriptions(int v) { setInt(totalSubscriptions, v, null); }

  //endregion Property "totalSubscriptions"

  //region Type

  @Override
  public Type getType() { return TYPE; }
  public static final Type TYPE = Sys.loadType(BNiagaraFallsService.class);

  //endregion Type

//@formatter:on
//endregion /*+ ------------ END BAJA AUTO GENERATED CODE -------------- +*/

  public static BNiagaraFallsService getActiveService()
  {
    return ACTIVE.get();
  }

  @Override
  public void serviceStarted() throws Exception
  {
    super.serviceStarted();
    runtime = new FallsWebSocketRuntime(this);
    ACTIVE.set(this);
    setRuntimeMetrics(0, 0);
    configOk();
  }

  @Override
  public void serviceStopped() throws Exception
  {
    if (ACTIVE.compareAndSet(this, null))
    {
      setRuntimeMetrics(0, 0);
    }
    FallsWebSocketRuntime current = runtime;
    runtime = null;
    if (current != null)
    {
      current.stop();
    }
    super.serviceStopped();
  }

  @Override
  public void doGet(WebOp op) throws Exception
  {
    if (!getEnabled())
    {
      op.getResponse().sendError(503, "NiagaraFallsService is disabled.");
      return;
    }

    FallsWebSocketRuntime current = runtime;
    if (current == null)
    {
      op.getResponse().sendError(503, "NiagaraFallsService is not running.");
      return;
    }

    String pathInfo = op.getPathInfo();
    if (pathInfo == null || pathInfo.length() == 0 || "/".equals(pathInfo))
    {
      if (isWebSocketUpgrade(op))
      {
        current.handleUpgrade(op.getRequest(), op.getResponse());
        return;
      }

      op.getResponse().setStatus(426);
      op.setContentType("text/plain;charset=UTF-8");
      op.getWriter().write("Use an authenticated WebSocket upgrade request.");
      return;
    }

    if ("/health".equals(pathInfo))
    {
      writeHealth(op);
      return;
    }


    op.getResponse().sendError(404);
  }

  synchronized void setRuntimeMetrics(int active, int subscriptions)
  {
    setInt(activeConnections, Math.max(0, active), null);
    setInt(totalSubscriptions, Math.max(0, subscriptions), null);
  }

  int getMaxConnectionsValue()
  {
    return getMaxConnections();
  }

  int getMaxSubscriptionsPerClientValue()
  {
    return getMaxSubscriptionsPerClient();
  }

  int getHeartbeatIntervalSecValue()
  {
    return Math.max(1, getHeartbeatIntervalSec());
  }

  int getSubscriptionLeaseSecValue()
  {
    return Math.max(0, getSubscriptionLeaseSec());
  }

  int getCovBatchWindowMillisValue()
  {
    return Math.max(0, getCovBatchWindowMillis());
  }

  int getActiveConnectionsValue()
  {
    return getActiveConnections();
  }

  int getTotalSubscriptionsValue()
  {
    return getTotalSubscriptions();
  }

  BRelTime getHeartbeatInterval()
  {
    return BRelTime.makeSeconds(getHeartbeatIntervalSecValue());
  }

  void logFine(String message)
  {
    if (LOG.isLoggable(Level.FINE))
    {
      LOG.fine(message);
    }
  }

  private boolean isWebSocketUpgrade(WebOp op)
  {
    String upgrade = op.getRequest().getHeader("Upgrade");
    return upgrade != null && "websocket".equalsIgnoreCase(upgrade.trim());
  }

  private void writeHealth(WebOp op) throws java.io.IOException
  {
    java.security.Principal principal = op.getRequest().getUserPrincipal();
    String user = principal == null ? null : principal.getName();

    op.getResponse().setStatus(200);
    op.setContentType("application/json;charset=UTF-8");
    op.getWriter().write("{"
      + "\"service\":\"NiagaraFallsService\","
      + "\"enabled\":" + getEnabled() + ","
      + "\"wsPath\":\"" + escapeJson(getWsPath()) + "\","
      + "\"apiVersion\":\"1.2\","
      + "\"servletName\":\"" + escapeJson(getServletName()) + "\","
      + "\"pathInfo\":\"" + escapeJson(op.getPathInfo()) + "\","
      + "\"maxConnections\":" + getMaxConnectionsValue() + ","
      + "\"maxSubscriptionsPerClient\":" + getMaxSubscriptionsPerClientValue() + ","
      + "\"heartbeatIntervalSec\":" + getHeartbeatIntervalSecValue() + ","
      + "\"subscriptionLeaseSec\":" + getSubscriptionLeaseSecValue() + ","
      + "\"covBatchWindowMillis\":" + getCovBatchWindowMillisValue() + ","
      + "\"activeConnections\":" + getActiveConnectionsValue() + ","
      + "\"totalSubscriptions\":" + getTotalSubscriptionsValue() + ","
      + "\"authenticatedUser\":" + toJsonString(user)
      + "}");
  }

  private static String toJsonString(String value)
  {
    return value == null ? "null" : "\"" + escapeJson(value) + "\"";
  }

  private static String escapeJson(String value)
  {
    if (value == null)
    {
      return "";
    }

    StringBuilder out = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++)
    {
      char ch = value.charAt(i);
      switch (ch)
      {
        case '\\':
          out.append("\\\\");
          break;
        case '"':
          out.append('\\').append('\"');
          break;
        case '\n':
          out.append("\\n");
          break;
        case '\r':
          out.append("\\r");
          break;
        case '\t':
          out.append("\\t");
          break;
        default:
          out.append(ch);
          break;
      }
    }
    return out.toString();
  }
}
