package com.basidekick.baskstream;

import java.util.LinkedHashMap;
import java.util.Map;

final class PointSnapshot
{
  private final String pointOrd;
  private final String displayName;
  private final String valueType;
  private final Object value;
  private final String displayValue;
  private final String status;
  private final boolean ok;
  private final long timestamp;
  private final Map<String, Object> extra;

  PointSnapshot(String pointOrd, String displayName, String valueType, Object value,
      String displayValue, String status, boolean ok, long timestamp)
  {
    this(pointOrd, displayName, valueType, value, displayValue, status, ok, timestamp, null);
  }

  PointSnapshot(String pointOrd, String displayName, String valueType, Object value,
      String displayValue, String status, boolean ok, long timestamp, Map<String, Object> extra)
  {
    this.pointOrd = pointOrd;
    this.displayName = displayName;
    this.valueType = valueType;
    this.value = value;
    this.displayValue = displayValue;
    this.status = status;
    this.ok = ok;
    this.timestamp = timestamp;
    this.extra = extra;
  }

  Map<String, Object> toWire()
  {
    Map<String, Object> wire = new LinkedHashMap<String, Object>();
    wire.put("point", pointOrd);
    wire.put("ok", Boolean.valueOf(ok));
    wire.put("display", displayName);
    wire.put("valueType", valueType);
    wire.put("value", value);
    wire.put("displayValue", displayValue);
    wire.put("status", status);
    wire.put("timestamp", Long.valueOf(timestamp));
    if (extra != null)
    {
      wire.putAll(extra);
    }
    return wire;
  }
}
