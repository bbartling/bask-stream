package com.basidekick.baskstream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.baja.naming.BOrd;
import javax.baja.naming.OrdTarget;
import javax.baja.schedule.BAbstractSchedule;
import javax.baja.schedule.BCompositeSchedule;
import javax.baja.schedule.BControlSchedule;
import javax.baja.schedule.BDailySchedule;
import javax.baja.schedule.BDateRangeSchedule;
import javax.baja.schedule.BDateSchedule;
import javax.baja.schedule.BDaySchedule;
import javax.baja.schedule.BTimeSchedule;
import javax.baja.schedule.BWeekSchedule;
import javax.baja.schedule.BWeeklySchedule;
import javax.baja.status.BIStatusValue;
import javax.baja.status.BStatus;
import javax.baja.status.BStatusValue;
import javax.baja.sys.BAbsTime;
import javax.baja.sys.BBoolean;
import javax.baja.sys.BComplex;
import javax.baja.sys.BNumber;
import javax.baja.sys.BObject;
import javax.baja.sys.BSimple;
import javax.baja.sys.BString;
import javax.baja.sys.BTime;
import javax.baja.sys.BValue;
import javax.baja.sys.BWeekday;
import javax.baja.sys.Clock;
import javax.baja.sys.Context;

final class BaskStreamScheduleResolver
{
  private final BBaskStreamService service;

  BaskStreamScheduleResolver(BBaskStreamService service)
  {
    this.service = service;
  }

  Map<String, Object> readSchedule(String ord, Object atValue, Context context) throws BaskStreamProtocolException
  {
    String candidate = normalizeOrd(ord);
    if (!BaskStreamAccessPolicy.isAllowed(service, candidate))
    {
      throw new BaskStreamProtocolException("forbidden_point", "Schedule is outside the allowedPathPatterns policy.");
    }
    long at = normalizeMillis(atValue, Clock.millis());

    try
    {
      OrdTarget target = BOrd.make(candidate).resolve(service, context);
      if (!target.canRead())
      {
        throw new BaskStreamProtocolException("forbidden_point", "Schedule is not readable for the authenticated user.");
      }

      BObject object = target.get();
      BAbstractSchedule schedule = object instanceof BAbstractSchedule
          ? (BAbstractSchedule) object
          : (target.getComponent() instanceof BAbstractSchedule ? (BAbstractSchedule) target.getComponent() : null);

      if (schedule == null)
      {
        throw new BaskStreamProtocolException("invalid_point", "Resolved target is not a Niagara schedule.");
      }

      return toWire(schedule, target, BAbsTime.make(at), context);
    }
    catch (BaskStreamProtocolException e)
    {
      throw e;
    }
    catch (Exception e)
    {
      throw new BaskStreamProtocolException("schedule_failed",
          e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
  }

  private Map<String, Object> toWire(BAbstractSchedule schedule, OrdTarget target, BAbsTime at, Context context)
  {
    Map<String, Object> wire = new LinkedHashMap<String, Object>();
    wire.put("ord", target.getOrd().toString());
    wire.put("slotPath", target.getComponent() == null || target.getComponent().getSlotPath() == null
        ? null
        : target.getComponent().getSlotPath().toString());
    wire.put("name", target.getComponent() == null ? schedule.getType().toString() : target.getComponent().getName());
    wire.put("display", target.getComponent() == null ? schedule.toString(context) : target.getComponent().getDisplayName(context));
    wire.put("description", schedule.toString(context));
    wire.put("typeSpec", schedule.getType().toString());
    wire.put("kind", "schedule");
    wire.put("at", Long.valueOf(at.getMillis()));
    wire.put("alwaysEffective", Boolean.valueOf(schedule.getAlwaysEffective()));
    wire.put("effectiveValue", valueToWire(schedule.getEffectiveValue(), context));
    wire.put("effectiveNow", Boolean.valueOf(schedule.isEffective(at)));
    wire.put("nextEvent", schedule.nextEvent(at) == null ? null : Long.valueOf(schedule.nextEvent(at).getMillis()));
    wire.put("tree", scheduleTree(schedule, at, context));

    if (schedule instanceof BControlSchedule)
    {
      BControlSchedule control = (BControlSchedule) schedule;
      wire.put("status", control.getStatus().toString(context));
      wire.put("defaultOutput", valueToWire(control.getDefaultOutput(), context));
      wire.put("currentOutput", valueToWire(control.getOutput(at), context));
      wire.put("lastModified", control.getLastModified() == null ? null : Long.valueOf(control.getLastModified().getMillis()));

      BAbstractSchedule outputSource = control.getOutputSource(at);
      if (outputSource != null)
      {
        Map<String, Object> outputSourceWire = new LinkedHashMap<String, Object>();
        outputSourceWire.put("typeSpec", outputSource.getType().toString());
        outputSourceWire.put("display", outputSource.toString(context));
        outputSourceWire.put("name", outputSource.getName());
        wire.put("outputSource", outputSourceWire);
      }
    }

    return wire;
  }

  private Map<String, Object> scheduleTree(BAbstractSchedule schedule, BAbsTime at, Context context)
  {
    Map<String, Object> tree = new LinkedHashMap<String, Object>();
    tree.put("typeSpec", schedule.getType().toString());
    tree.put("name", schedule.getName());
    tree.put("display", schedule.toString(context));
    tree.put("alwaysEffective", Boolean.valueOf(schedule.getAlwaysEffective()));
    tree.put("effectiveNow", Boolean.valueOf(schedule.isEffective(at)));
    tree.put("effectiveValue", valueToWire(schedule.getEffectiveValue(), context));

    if (schedule instanceof BWeeklySchedule)
    {
      BWeeklySchedule weekly = (BWeeklySchedule) schedule;
      tree.put("effectiveRange", dateRangeToWire(weekly.getEffective(), context));
      tree.put("week", weekToWire(weekly.getWeek(), at, context));
      tree.put("specialEvents", dailySchedulesToWire(weekly.getSpecialEventsChildren(), at, context));
      tree.put("summary", weekly.getSummary(weekly, context));
    }
    else if (schedule instanceof BWeekSchedule)
    {
      tree.put("week", weekToWire((BWeekSchedule) schedule, at, context));
    }
    else if (schedule instanceof BDailySchedule)
    {
      BDailySchedule daily = (BDailySchedule) schedule;
      tree.put("day", dayToWire(daily.getDay(), context));
      if (daily.getDays() != null)
      {
        tree.put("days", scheduleTree(daily.getDays(), at, context));
      }
    }
    else if (schedule instanceof BDaySchedule)
    {
      tree.put("entries", dayToWire((BDaySchedule) schedule, context));
    }
    else if (schedule instanceof BTimeSchedule)
    {
      tree.put("entry", timeToWire((BTimeSchedule) schedule, context));
    }
    else if (schedule instanceof BDateRangeSchedule)
    {
      tree.put("range", dateRangeToWire((BDateRangeSchedule) schedule, context));
    }
    else if (schedule instanceof BDateSchedule)
    {
      tree.put("date", schedule.toString(context));
    }

    if (schedule instanceof BCompositeSchedule)
    {
      BAbstractSchedule[] children = ((BCompositeSchedule) schedule).getSchedules();
      if (children != null && children.length > 0)
      {
        List<Object> childWires = new ArrayList<Object>(children.length);
        for (int i = 0; i < children.length; i++)
        {
          childWires.add(scheduleTree(children[i], at, context));
        }
        tree.put("children", childWires);
      }
    }

    return tree;
  }

  private Map<String, Object> weekToWire(BWeekSchedule week, BAbsTime at, Context context)
  {
    Map<String, Object> wire = new LinkedHashMap<String, Object>();
    if (week == null)
    {
      return wire;
    }

    BWeekday[] days = BWeekSchedule.daysInOrder(context);
    BDailySchedule[] schedules = week.schedulesInOrder(context);
    for (int i = 0; i < days.length && i < schedules.length; i++)
    {
      BWeekday day = days[i];
      BDailySchedule daily = schedules[i];
      wire.put(day.getTag(), dailyToWire(daily, at, context));
    }
    return wire;
  }

  private List<Object> dailySchedulesToWire(BDailySchedule[] schedules, BAbsTime at, Context context)
  {
    List<Object> wire = new ArrayList<Object>();
    if (schedules == null)
    {
      return wire;
    }

    for (int i = 0; i < schedules.length; i++)
    {
      wire.add(dailyToWire(schedules[i], at, context));
    }
    return wire;
  }

  private Map<String, Object> dailyToWire(BDailySchedule daily, BAbsTime at, Context context)
  {
    Map<String, Object> wire = new LinkedHashMap<String, Object>();
    if (daily == null)
    {
      return wire;
    }

    wire.put("name", daily.getName());
    wire.put("display", daily.toString(context));
    wire.put("effectiveNow", Boolean.valueOf(daily.isEffective(at)));
    wire.put("day", dayToWire(daily.getDay(), context));
    if (daily.getDays() != null)
    {
      wire.put("days", scheduleTree(daily.getDays(), at, context));
    }
    return wire;
  }

  private List<Object> dayToWire(BDaySchedule day, Context context)
  {
    List<Object> entries = new ArrayList<Object>();
    if (day == null)
    {
      return entries;
    }

    BTimeSchedule[] times = day.getTimesInOrder();
    for (int i = 0; i < times.length; i++)
    {
      entries.add(timeToWire(times[i], context));
    }
    return entries;
  }

  private Map<String, Object> timeToWire(BTimeSchedule time, Context context)
  {
    Map<String, Object> wire = new LinkedHashMap<String, Object>();
    wire.put("start", time.getStart() == null ? null : formatTime(time.getStart(), context));
    wire.put("finish", time.getFinish() == null ? null : formatTime(time.getFinish(), context));
    wire.put("effectiveValue", valueToWire(time.getEffectiveValue(), context));
    wire.put("display", time.toString(context));
    return wire;
  }

  private Map<String, Object> dateRangeToWire(BDateRangeSchedule range, Context context)
  {
    Map<String, Object> wire = new LinkedHashMap<String, Object>();
    if (range == null)
    {
      return wire;
    }

    wire.put("start", range.getStart() == null ? null : range.getStart().toString(context));
    wire.put("end", range.getEnd() == null ? null : range.getEnd().toString(context));
    wire.put("display", range.toString(context));
    return wire;
  }

  private Map<String, Object> valueToWire(BObject source, Context context)
  {
    if (source == null)
    {
      return null;
    }

    String status = BStatus.ok.toString(context);
    boolean ok = true;
    Object wireValue = null;
    String displayValue = null;
    String valueType = source.getType().toString();

    if (source instanceof BIStatusValue)
    {
      BStatusValue statusValue = ((BIStatusValue) source).getStatusValue();
      status = statusValue.getStatus().toString(context);
      ok = statusValue.getStatus().isOk();
      source = statusValue.getValueValue();
      valueType = source.getType().toString();
    }

    if (source instanceof BBoolean)
    {
      wireValue = Boolean.valueOf(((BBoolean) source).getBoolean());
    }
    else if (source instanceof BNumber)
    {
      wireValue = Double.valueOf(((BNumber) source).getDouble());
    }
    else if (source instanceof BString)
    {
      wireValue = ((BString) source).getString();
    }
    else if (source instanceof BSimple)
    {
      wireValue = source.toString(context);
    }
    else if (source instanceof BValue)
    {
      wireValue = source.toString(context);
    }

    displayValue = source.toString(context);

    Map<String, Object> wire = new LinkedHashMap<String, Object>();
    wire.put("ok", Boolean.valueOf(ok));
    wire.put("valueType", valueType);
    wire.put("value", wireValue);
    wire.put("displayValue", displayValue);
    wire.put("status", status);
    return wire;
  }

  private static String formatTime(BTime time, Context context)
  {
    return time == null ? null : time.toString(context);
  }

  private String normalizeOrd(String ord) throws BaskStreamProtocolException
  {
    if (ord == null || ord.trim().length() == 0)
    {
      throw new BaskStreamProtocolException("bad_request", "Field 'ord' is required for read_schedule.");
    }

    String candidate = ord.trim();
    if (!candidate.startsWith("slot:/"))
    {
      throw new BaskStreamProtocolException("invalid_point", "read_schedule supports slot:/ ords only.");
    }
    return candidate;
  }

  private long normalizeMillis(Object value, long defaultValue) throws BaskStreamProtocolException
  {
    if (value == null)
    {
      return defaultValue;
    }
    if (!(value instanceof Number))
    {
      throw new BaskStreamProtocolException("bad_request", "Schedule time fields must be epoch milliseconds.");
    }
    return ((Number) value).longValue();
  }
}
