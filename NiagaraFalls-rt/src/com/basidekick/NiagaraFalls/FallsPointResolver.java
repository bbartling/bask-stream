package com.basidekick.niagarafalls;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.baja.naming.BOrd;
import javax.baja.naming.OrdTarget;
import javax.baja.status.BIStatusValue;
import javax.baja.status.BStatus;
import javax.baja.status.BStatusValue;
import javax.baja.sys.BComponent;
import javax.baja.sys.BBoolean;
import javax.baja.sys.BComplex;
import javax.baja.sys.BEnum;
import javax.baja.sys.BEnumRange;
import javax.baja.sys.BNumber;
import javax.baja.sys.BObject;
import javax.baja.sys.BSimple;
import javax.baja.sys.BString;
import javax.baja.sys.BValue;
import javax.baja.sys.Clock;
import javax.baja.sys.Context;
import javax.baja.sys.Property;

final class FallsPointResolver
{
  private final BNiagaraFallsService service;

  FallsPointResolver(BNiagaraFallsService service)
  {
    this.service = service;
  }

  ResolvedPoint resolve(String pointOrd, Context context) throws FallsProtocolException
  {
    if (pointOrd == null || pointOrd.trim().length() == 0)
    {
      throw new FallsProtocolException("invalid_point", "Point ORD cannot be blank.");
    }

    if (!pointOrd.startsWith("slot:/"))
    {
      throw new FallsProtocolException("invalid_point", "Only slot:/ ORDs are supported.");
    }

    if (!isAllowed(pointOrd))
    {
      throw new FallsProtocolException("forbidden_point", "Point is outside the allowedPathPatterns policy.");
    }

    try
    {
      OrdTarget target = BOrd.make(pointOrd).resolve(service, context);
      if (!target.canRead())
      {
        throw new FallsProtocolException("forbidden_point", "Point is not readable for the authenticated user.");
      }

      BObject targetObject = target.get();
      if (targetObject == null)
      {
        throw new FallsProtocolException("invalid_point", "Resolved point target was null.");
      }

      if (!(targetObject instanceof BValue) && !(targetObject instanceof BComplex))
      {
        throw new FallsProtocolException("invalid_point", "Resolved target is not a readable Baja value.");
      }

      BComplex complex = targetObject instanceof BComplex ? (BComplex) targetObject : null;
      BComponent component = target.getComponent();
      Property targetProperty = target.getSlotInComponent() == null ? null : target.getSlotInComponent().asProperty();
      String observedSlot = targetProperty == null ? null : targetProperty.getName();

      if (complex != null && observedSlot == null)
      {
        if (targetObject instanceof BIStatusValue || complex.getProperty("out") != null)
        {
          observedSlot = "out";
        }
        else if (complex.getProperty("value") != null)
        {
          observedSlot = "value";
        }
      }

      String displayName = component != null ? component.getDisplayName(context) : targetObject.toString(context);
      return new ResolvedPoint(pointOrd, target, component, observedSlot, displayName);
    }
    catch (FallsProtocolException e)
    {
      throw e;
    }
    catch (Exception e)
    {
      throw new FallsProtocolException("invalid_point", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
  }

  PointSnapshot snapshot(ResolvedPoint point, Context context) throws FallsProtocolException
  {
    try
    {
      BObject source = point.getSnapshotSource();
      String status = BStatus.ok.toString(context);
      boolean ok = true;
      Object wireValue = null;
      String displayValue = null;
      String valueType = source.getType().toString();
      Map<String, Object> extra = null;

      if (source instanceof BIStatusValue)
      {
        BStatusValue statusValue = ((BIStatusValue) source).getStatusValue();
        status = statusValue.getStatus().toString(context);
        ok = statusValue.getStatus().isOk();
        source = statusValue.getValueValue();
        valueType = source.getType().toString();
      }

      if (source instanceof BEnum)
      {
        BEnum enumValue = (BEnum) source;
        wireValue = enumValue.getTag();
        displayValue = enumValue.getDisplayTag(context);
        extra = enumDetails(enumValue, context);
      }
      else if (source instanceof BBoolean)
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

      if (displayValue == null)
      {
        displayValue = source.toString(context);
      }
      return new PointSnapshot(point.getPointOrd(), point.getDisplayName(), valueType, wireValue,
          displayValue, status, ok, Clock.millis(), extra);
    }
    catch (FallsProtocolException e)
    {
      throw e;
    }
    catch (Exception e)
    {
      throw new FallsProtocolException("read_failed", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
  }

  private Map<String, Object> enumDetails(BEnum value, Context context)
  {
    Map<String, Object> details = new LinkedHashMap<String, Object>();
    details.put("enumOrdinal", Long.valueOf(value.getOrdinal()));
    details.put("enumTag", value.getTag());
    details.put("enumDisplay", value.getDisplayTag(context));
    details.put("enumOptions", enumOptions(value.getRange(), context));
    return details;
  }

  private List<Object> enumOptions(BEnumRange range, Context context)
  {
    List<Object> options = new ArrayList<Object>();
    if (range == null || range.isNull())
    {
      return options;
    }

    int[] ordinals = range.getOrdinals();
    for (int i = 0; i < ordinals.length; i++)
    {
      Map<String, Object> option = new LinkedHashMap<String, Object>();
      option.put("ordinal", Long.valueOf(ordinals[i]));
      option.put("tag", range.getTag(ordinals[i]));
      option.put("display", range.getDisplayTag(ordinals[i], context));
      options.add(option);
    }
    return options;
  }

  private boolean isAllowed(String pointOrd)
  {
    for (Pattern pattern : parsePatterns(service.getAllowedPathPatterns()))
    {
      if (pattern.matcher(pointOrd).matches())
      {
        return true;
      }
    }
    return false;
  }

  private static List<Pattern> parsePatterns(String configured)
  {
    String raw = configured == null ? "" : configured;
    String[] pieces = raw.split("[\\r\\n,;]+");
    List<Pattern> patterns = new ArrayList<Pattern>();
    for (String piece : pieces)
    {
      String trimmed = piece.trim();
      if (trimmed.length() == 0)
      {
        continue;
      }
      patterns.add(Pattern.compile(toRegex(trimmed)));
    }
    if (patterns.isEmpty())
    {
      patterns.add(Pattern.compile(toRegex("slot:/*")));
    }
    return patterns;
  }

  private static String toRegex(String wildcard)
  {
    StringBuilder regex = new StringBuilder("^");
    for (int i = 0; i < wildcard.length(); i++)
    {
      char ch = wildcard.charAt(i);
      if (ch == '*')
      {
        regex.append(".*");
      }
      else if ("\\.[]{}()+-^$?|".indexOf(ch) >= 0)
      {
        regex.append('\\').append(ch);
      }
      else
      {
        regex.append(ch);
      }
    }
    regex.append('$');
    return regex.toString();
  }

  static final class ResolvedPoint
  {
    private final String pointOrd;
    private final OrdTarget target;
    private final BComponent component;
    private final String observedSlot;
    private final String displayName;

    ResolvedPoint(String pointOrd, OrdTarget target, BComponent component, String observedSlot, String displayName)
    {
      this.pointOrd = pointOrd;
      this.target = target;
      this.component = component;
      this.observedSlot = observedSlot;
      this.displayName = displayName;
    }

    String getPointOrd()
    {
      return pointOrd;
    }

    BComponent getComponent()
    {
      return component;
    }

    OrdTarget getTarget()
    {
      return target;
    }

    String getObservedSlot()
    {
      return observedSlot;
    }

    String getDisplayName()
    {
      return displayName;
    }

    boolean isTriggeredBy(String slotName)
    {
      return observedSlot == null || slotName == null || observedSlot.equals(slotName);
    }

    BObject getSnapshotSource() throws FallsProtocolException
    {
      BObject targetObject = target.get();
      if (targetObject == null)
      {
        throw new FallsProtocolException("read_failed", "Resolved point target is no longer available.");
      }

      if (targetObject instanceof BIStatusValue)
      {
        return ((BIStatusValue) targetObject).getStatusValue();
      }

      if (targetObject instanceof BComplex)
      {
        BComplex complex = (BComplex) targetObject;
        if (complex.getProperty("out") != null)
        {
          return complex.get("out");
        }
        if (complex.getProperty("value") != null)
        {
          return complex.get("value");
        }
      }

      return targetObject;
    }
  }
}
