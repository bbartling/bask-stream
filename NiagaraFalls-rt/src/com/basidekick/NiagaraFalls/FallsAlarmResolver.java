package com.basidekick.niagarafalls;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.baja.alarm.AlarmSpaceConnection;
import javax.baja.alarm.BAckState;
import javax.baja.alarm.BAlarmRecord;
import javax.baja.alarm.BAlarmService;
import javax.baja.alarm.BSourceState;
import javax.baja.naming.BOrd;
import javax.baja.naming.BOrdList;
import javax.baja.sys.BObject;
import javax.baja.sys.Context;
import javax.baja.sys.Cursor;

final class FallsAlarmResolver
{
  private static final int DEFAULT_LIMIT = 500;
  private static final int MAX_LIMIT = 5000;

  FallsAlarmResolver(BNiagaraFallsService service)
  {
  }

  AlarmSubscriptionSpec normalizeSubscription(String sourceOrd, String scope, Object limitValue)
      throws FallsProtocolException
  {
    return normalizeSubscription(sourceOrd, scope, limitValue, null);
  }

  AlarmSubscriptionSpec normalizeSubscription(String sourceOrd, String scope, Object limitValue, String mode)
      throws FallsProtocolException
  {
    return new AlarmSubscriptionSpec(
        normalizeSource(sourceOrd),
        normalizeScope(scope),
        normalizeLimit(limitValue),
        normalizeMode(mode));
  }

  Map<String, Object> readAlarms(String sourceOrd, String scope, Object limitValue, Context context)
      throws FallsProtocolException
  {
    AlarmSubscriptionSpec spec = normalizeSubscription(sourceOrd, scope, limitValue);

    BAlarmService alarmService = BAlarmService.getService();
    if (alarmService == null)
    {
      throw new FallsProtocolException("alarm_failed", "Niagara AlarmService is not available.");
    }

    List<Object> alarms = new ArrayList<Object>();

    try (AlarmSpaceConnection connection = alarmService.getAlarmDb().getConnection(context))
    {
      Cursor<BAlarmRecord> cursor = null;
      try
      {
        cursor = openCursor(connection, spec.scope);
        int count = 0;
        while (cursor.next() && count < spec.limit)
        {
          BAlarmRecord record = cursor.get();
          if (spec.source != null && !matchesSource(record, spec.source))
          {
            continue;
          }
          alarms.add(toWire(record, context));
          count++;
        }
      }
      finally
      {
        if (cursor != null)
        {
          cursor.close();
        }
      }
    }
    catch (FallsProtocolException e)
    {
      throw e;
    }
    catch (Exception e)
    {
      throw new FallsProtocolException(
          "alarm_failed",
          e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }

    return result(spec, alarms);
  }

  Map<String, Object> result(AlarmSubscriptionSpec spec, List<Object> alarms)
  {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("scope", spec.scope);
    result.put("source", spec.source);
    result.put("limit", Long.valueOf(spec.limit));
    result.put("count", Long.valueOf(alarms.size()));
    result.put("alarms", alarms);
    return result;
  }

  private Cursor<BAlarmRecord> openCursor(AlarmSpaceConnection connection, String scope)
      throws Exception
  {
    if ("ack_pending".equals(scope))
    {
      return connection.getAckPendingAlarms();
    }
    if ("all".equals(scope))
    {
      return connection.scan();
    }
    return connection.getOpenAlarms();
  }

  Map<String, Object> toWire(BAlarmRecord record, Context context)
  {
    Map<String, Object> wire = new LinkedHashMap<String, Object>();
    wire.put("timestamp", record.getTimestamp() == null ? null : Long.valueOf(record.getTimestamp().getMillis()));
    wire.put("uuid", record.getUuid() == null ? null : record.getUuid().toString());
    wire.put("sourceState", sourceStateName(record.getSourceState()));
    wire.put("ackState", ackStateName(record.getAckState()));
    wire.put("ackRequired", Boolean.valueOf(record.getAckRequired()));
    wire.put("alarmClass", record.getAlarmClass());
    wire.put("alarmClassDisplay", record.getAlarmClassDisplayName(context));
    wire.put("priority", Long.valueOf(record.getPriority()));
    wire.put("normalTime", record.getNormalTime() == null ? null : Long.valueOf(record.getNormalTime().getMillis()));
    wire.put("ackTime", record.getAckTime() == null ? null : Long.valueOf(record.getAckTime().getMillis()));
    wire.put("lastUpdate", record.getLastUpdate() == null ? null : Long.valueOf(record.getLastUpdate().getMillis()));
    wire.put("user", record.getUser());
    wire.put("transition", sourceStateName(record.getAlarmTransition()));
    wire.put("summary", record.toSummaryString());
    wire.put("display", record.toString(context));
    wire.put("isOpen", Boolean.valueOf(record.isOpen()));
    wire.put("isAlarm", Boolean.valueOf(record.isAlarm()));
    wire.put("isAcknowledged", Boolean.valueOf(record.isAcknowledged()));
    wire.put("isAckPending", Boolean.valueOf(record.isAckPending()));
    wire.put("isNormal", Boolean.valueOf(record.isNormal()));
    wire.put("sources", toSourceList(record.getSource()));
    wire.put("data", toAlarmData(record, context));
    return wire;
  }

  private List<Object> toSourceList(BOrdList ords)
  {
    List<Object> sources = new ArrayList<Object>();
    if (ords == null)
    {
      return sources;
    }
    for (int i = 0; i < ords.size(); i++)
    {
      BOrd ord = ords.get(i);
      sources.add(ord == null ? null : ord.toString());
    }
    return sources;
  }

  private Map<String, Object> toAlarmData(BAlarmRecord record, Context context)
  {
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    if (record.getAlarmData() == null)
    {
      return data;
    }

    String[] keys = record.getAlarmData().list();
    for (int i = 0; i < keys.length; i++)
    {
      BObject value = record.getAlarmData().get(keys[i]);
      data.put(keys[i], value == null ? null : value.toString(context));
    }
    return data;
  }

  boolean matchesSource(BAlarmRecord record, String sourceOrd)
  {
    String filter = normalizeSlotOrd(sourceOrd);
    BOrdList sources = record.getSource();
    if (sources == null)
    {
      return false;
    }

    for (int i = 0; i < sources.size(); i++)
    {
      BOrd ord = sources.get(i);
      if (ord == null)
      {
        continue;
      }

      String candidate = normalizeSlotOrd(ord.toString());
      if (candidate == null)
      {
        continue;
      }

      if (filter.equals(candidate)
          || candidate.startsWith(filter + "/")
          || filter.startsWith(candidate + "/"))
      {
        return true;
      }
    }
    return false;
  }

  boolean matchesScope(BAlarmRecord record, String scope)
  {
    if (record == null)
    {
      return false;
    }
    if ("all".equals(scope))
    {
      return true;
    }
    if ("ack_pending".equals(scope))
    {
      return record.isAckPending();
    }
    return record.isOpen();
  }

  private static String normalizeSlotOrd(String ord)
  {
    if (ord == null)
    {
      return null;
    }

    int index = ord.indexOf("slot:/");
    return index >= 0 ? ord.substring(index) : ord;
  }

  private String normalizeSource(String sourceOrd) throws FallsProtocolException
  {
    if (sourceOrd == null || sourceOrd.trim().length() == 0)
    {
      return null;
    }

    String candidate = sourceOrd.trim();
    if (!candidate.startsWith("slot:/"))
    {
      throw new FallsProtocolException("invalid_point", "Alarm source filter supports slot:/ ORDs only.");
    }
    return candidate;
  }

  private String normalizeScope(String scope) throws FallsProtocolException
  {
    if (scope == null || scope.trim().length() == 0)
    {
      return "open";
    }

    String candidate = scope.trim().toLowerCase();
    if ("open".equals(candidate) || "ack_pending".equals(candidate) || "all".equals(candidate))
    {
      return candidate;
    }

    throw new FallsProtocolException("bad_request", "Alarm scope must be one of: open, ack_pending, all.");
  }

  private String normalizeMode(String mode) throws FallsProtocolException
  {
    if (mode == null || mode.trim().length() == 0)
    {
      return "event";
    }

    String candidate = mode.trim().toLowerCase().replace('-', '_');
    if ("event".equals(candidate) || "snapshot".equals(candidate) || "both".equals(candidate))
    {
      return candidate;
    }

    throw new FallsProtocolException("bad_request", "Alarm subscription mode must be one of: event, snapshot, both.");
  }

  private int normalizeLimit(Object value) throws FallsProtocolException
  {
    if (value == null)
    {
      return DEFAULT_LIMIT;
    }
    if (!(value instanceof Number))
    {
      throw new FallsProtocolException("bad_request", "Field 'limit' must be a number.");
    }
    int limit = ((Number) value).intValue();
    if (limit <= 0)
    {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }

  private static String sourceStateName(BSourceState state)
  {
    return state == null ? null : state.toString();
  }

  private static String ackStateName(BAckState state)
  {
    return state == null ? null : state.toString();
  }

  static final class AlarmSubscriptionSpec
  {
    final String source;
    final String scope;
    final int limit;
    final String mode;

    AlarmSubscriptionSpec(String source, String scope, int limit, String mode)
    {
      this.source = source;
      this.scope = scope;
      this.limit = limit;
      this.mode = mode;
    }

    String key()
    {
      return (source == null ? "*" : source) + "|" + scope + "|" + limit + "|" + mode;
    }
  }
}
