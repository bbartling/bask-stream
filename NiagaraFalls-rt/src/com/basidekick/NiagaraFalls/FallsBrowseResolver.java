package com.basidekick.niagarafalls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.baja.control.BControlPoint;
import javax.baja.control.BIWritablePoint;
import javax.baja.control.BPointExtension;
import javax.baja.driver.BDevice;
import javax.baja.driver.BDeviceExt;
import javax.baja.driver.BDeviceNetwork;
import javax.baja.driver.point.BPointDeviceExt;
import javax.baja.driver.point.BProxyExt;
import javax.baja.history.BHistoryConfig;
import javax.baja.history.BIHistory;
import javax.baja.history.ext.BHistoryExt;
import javax.baja.naming.BOrd;
import javax.baja.naming.OrdTarget;
import javax.baja.nav.BINavNode;
import javax.baja.status.BIStatusValue;
import javax.baja.status.BStatusValue;
import javax.baja.sys.BAbsTime;
import javax.baja.sys.BComponent;
import javax.baja.sys.BComplex;
import javax.baja.sys.BEnumRange;
import javax.baja.sys.BFacets;
import javax.baja.sys.BIEnum;
import javax.baja.sys.BObject;
import javax.baja.sys.Context;
import javax.baja.sys.BValue;
import javax.baja.sys.Property;
import javax.baja.tag.Relation;
import javax.baja.tag.Tag;

final class FallsBrowseResolver
{
  static final String METADATA_NONE = "none";
  static final String METADATA_FULL = "full";

  static final int DEFAULT_DEPTH = 1;
  static final int MAX_DEPTH = 4;
  private static final int DEFAULT_SEARCH_LIMIT = 500;
  private static final int MAX_SEARCH_LIMIT = 5000;

  private final BNiagaraFallsService service;

  FallsBrowseResolver(BNiagaraFallsService service)
  {
    this.service = service;
  }

  Map<String, Object> browse(String baseOrd, int depth, String metadataMode, Context context) throws FallsProtocolException
  {
    String ord = normalizeOrd(baseOrd);
    int clampedDepth = clampDepth(depth);
    return resolveNode(ord, context, clampedDepth, metadataMode);
  }

  Map<String, Object> search(
      String baseOrd,
      int depth,
      String metadataMode,
      String query,
      String kind,
      List<String> requiredFeatures,
      List<String> requiredOperations,
      Boolean writable,
      Object limitValue,
      Context context) throws FallsProtocolException
  {
    String ord = normalizeOrd(baseOrd);
    int clampedDepth = clampDepth(depth);
    int limit = normalizeLimit(limitValue);
    Map<String, Object> root = resolveNode(ord, context, clampedDepth, metadataMode);
    List<Object> matches = new ArrayList<Object>();
    collectMatches(root, normalizeSearchText(query), normalizeSearchText(kind),
        requiredFeatures, requiredOperations, writable, limit, matches);

    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("base", ord);
    result.put("depth", Long.valueOf(clampedDepth));
    result.put("metadata", metadataMode);
    result.put("limit", Long.valueOf(limit));
    result.put("count", Long.valueOf(matches.size()));
    result.put("truncated", Boolean.valueOf(matches.size() >= limit));
    result.put("nodes", matches);
    return result;
  }

  Map<String, Object> describe(String ord, String metadataMode, Context context) throws FallsProtocolException
  {
    return resolveNode(normalizeOrd(ord), context, 0, metadataMode);
  }

  boolean isAllowedOrd(String ord)
  {
    return ord != null && isAllowed(ord);
  }

  int normalizeDepth(Object value) throws FallsProtocolException
  {
    if (value == null)
    {
      return DEFAULT_DEPTH;
    }
    if (!(value instanceof Number))
    {
      throw new FallsProtocolException("bad_request", "Field 'depth' must be a number.");
    }
    return clampDepth(((Number) value).intValue());
  }

  String normalizeMetadataMode(Object value, String defaultMode) throws FallsProtocolException
  {
    if (value == null)
    {
      return defaultMode;
    }
    if (value instanceof Boolean)
    {
      return Boolean.TRUE.equals(value) ? METADATA_FULL : METADATA_NONE;
    }
    if (!(value instanceof String))
    {
      throw new FallsProtocolException("bad_request", "Field 'metadata' must be 'full', 'none', or a boolean.");
    }

    String mode = ((String) value).trim().toLowerCase();
    if (mode.length() == 0)
    {
      return defaultMode;
    }
    if ("full".equals(mode) || "true".equals(mode) || "include".equals(mode))
    {
      return METADATA_FULL;
    }
    if ("none".equals(mode) || "false".equals(mode) || "omit".equals(mode))
    {
      return METADATA_NONE;
    }
    throw new FallsProtocolException("bad_request", "Field 'metadata' must be 'full', 'none', or a boolean.");
  }

  private Map<String, Object> resolveNode(String ord, Context context, int depth, String metadataMode) throws FallsProtocolException
  {
    if (!isAllowed(ord))
    {
      throw new FallsProtocolException("forbidden_point", "Node is outside the allowedPathPatterns policy.");
    }

    try
    {
      OrdTarget target = BOrd.make(ord).resolve(service, context);
      if (!target.canRead())
      {
        throw new FallsProtocolException("forbidden_point", "Node is not readable for the authenticated user.");
      }

      BObject object = target.get();
      if (object == null)
      {
        throw new FallsProtocolException("invalid_point", "Resolved browse target was null.");
      }

      BINavNode navNode = object instanceof BINavNode
          ? (BINavNode) object
          : (target.getComponent() instanceof BINavNode ? (BINavNode) target.getComponent() : null);

      if (navNode == null)
      {
        throw new FallsProtocolException("invalid_point", "Resolved target is not browsable.");
      }

      return toWire(navNode, target, context, depth, metadataMode);
    }
    catch (FallsProtocolException e)
    {
      throw e;
    }
    catch (Exception e)
    {
      throw new FallsProtocolException("browse_failed",
          e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
  }

  private Map<String, Object> toWire(BINavNode navNode, OrdTarget target, Context context, int depth, String metadataMode)
  {
    BObject object = target.get();
    BComponent component = object instanceof BComponent ? (BComponent) object : target.getComponent();

    String ord = navNode.getNavOrd() == null ? target.getOrd().toString() : navNode.getNavOrd().toString();
    String typeSpec = object.getType().toString();
    boolean hasChildren = navNode.hasNavChildren();
    boolean point = isPoint(object, component, typeSpec);
    boolean writable = point && isWritable(component, typeSpec);
    boolean schedule = isSchedule(typeSpec);
    boolean history = isHistory(typeSpec) || hasChildTypePrefix(component, "history:");
    boolean alarm = isAlarm(typeSpec) || hasChildTypePrefix(component, "alarm:");

    Map<String, Object> wire = new LinkedHashMap<String, Object>();
    wire.put("ord", ord);
    wire.put("slotPath", component == null || component.getSlotPath() == null ? null : component.getSlotPath().toString());
    wire.put("name", component == null ? navNode.getNavName() : component.getName());
    wire.put("display", safe(navNode.getNavDisplayName(context)));
    wire.put("description", safe(navNode.getNavDescription(context)));
    wire.put("typeSpec", typeSpec);
    wire.put("kind", classifyKind(point, schedule, hasChildren));
    wire.put("hasChildren", Boolean.valueOf(hasChildren));
    wire.put("writable", Boolean.valueOf(writable));
    wire.put("features", buildFeatures(point, history, alarm, schedule));
    wire.put("operations", buildOperations(hasChildren, point, writable, history, alarm, schedule));
    if (includeMetadata(metadataMode))
    {
      wire.put("metadata", metadata(object, component, point, writable, history, alarm, schedule, context));
    }
    Map<String, Object> enumInfo = enumInfo(object, component, context);
    if (!enumInfo.isEmpty())
    {
      wire.put("enum", enumInfo);
    }

    if (depth > 0 && hasChildren)
    {
      BINavNode[] children = navNode.getNavChildren();
      List<Object> childWires = new ArrayList<Object>(children.length);
      for (int i = 0; i < children.length; i++)
      {
        Map<String, Object> childWire = toChildWire(children[i], context, depth - 1, metadataMode);
        if (childWire != null)
        {
          childWires.add(childWire);
        }
      }
      wire.put("children", childWires);
    }

    return wire;
  }

  private boolean includeMetadata(String metadataMode)
  {
    return METADATA_FULL.equals(metadataMode);
  }

  private Map<String, Object> metadata(
      BObject object,
      BComponent component,
      boolean point,
      boolean writable,
      boolean history,
      boolean alarm,
      boolean schedule,
      Context context)
  {
    Map<String, Object> metadata = new LinkedHashMap<String, Object>();
    metadata.put("classification", classification(object, component, point, writable, history, alarm, schedule));
    metadata.put("parent", componentSummary(parentComponent(component), context));
    metadata.put("ancestors", ancestors(component, context));
    metadata.put("driver", driverMetadata(component, context));
    metadata.put("point", pointMetadata(component, point, writable, context));
    metadata.put("write", writeMetadata(component, writable, context));
    metadata.put("history", historyMetadata(component, context));
    metadata.put("alarm", alarmMetadata(component, context));
    metadata.put("subscriptions", subscriptionMetadata(point, history, alarm, schedule));
    metadata.put("facets", componentFacets(component, context));
    metadata.put("tags", tags(component));
    metadata.put("relations", relations(component));
    return metadata;
  }

  private Map<String, Object> classification(
      BObject object,
      BComponent component,
      boolean point,
      boolean writable,
      boolean history,
      boolean alarm,
      boolean schedule)
  {
    Map<String, Object> classification = new LinkedHashMap<String, Object>();
    classification.put("isComponent", Boolean.valueOf(component != null));
    classification.put("isControlPoint", Boolean.valueOf(component instanceof BControlPoint));
    classification.put("isWritablePoint", Boolean.valueOf(writable || component instanceof BIWritablePoint));
    classification.put("isStatusValue", Boolean.valueOf(object instanceof BIStatusValue || component instanceof BIStatusValue));
    classification.put("isDriverNetwork", Boolean.valueOf(component instanceof BDeviceNetwork));
    classification.put("isDriverDevice", Boolean.valueOf(component instanceof BDevice));
    classification.put("isPointDeviceExt", Boolean.valueOf(component instanceof BPointDeviceExt));
    classification.put("isPointExtension", Boolean.valueOf(component instanceof BPointExtension));
    classification.put("isProxyExt", Boolean.valueOf(component instanceof BProxyExt));
    classification.put("isProxyPoint", Boolean.valueOf(proxyExt(component) != null));
    classification.put("isSchedule", Boolean.valueOf(schedule));
    classification.put("hasHistory", Boolean.valueOf(history));
    classification.put("hasAlarm", Boolean.valueOf(alarm));
    classification.put("isPoint", Boolean.valueOf(point));
    classification.put("equipmentCertainty", component instanceof BDevice ? "device" : "unknown");
    return classification;
  }

  private Map<String, Object> driverMetadata(BComponent component, Context context)
  {
    Map<String, Object> driver = new LinkedHashMap<String, Object>();
    if (component == null)
    {
      return driver;
    }

    BProxyExt proxyExt = proxyExt(component);
    BPointDeviceExt pointDeviceExt = pointDeviceExt(component, proxyExt);
    BDevice device = device(component, proxyExt, pointDeviceExt);
    BDeviceNetwork network = network(component, proxyExt, pointDeviceExt, device);

    driver.put("isDriverBacked", Boolean.valueOf(proxyExt != null || pointDeviceExt != null || device != null || network != null));
    driver.put("network", componentSummary(network, context));
    driver.put("device", componentSummary(device, context));
    driver.put("pointDeviceExt", componentSummary(pointDeviceExt, context));
    driver.put("proxyExt", componentSummary(proxyExt, context));
    if (proxyExt != null)
    {
      driver.put("proxyExtType", proxyExt.getType().toString());
      driver.put("deviceExtType", proxyExt.getDeviceExtType().toString());
      driver.put("readWriteMode", proxyExt.getMode() == null ? null : proxyExt.getMode().toString(context));
      driver.put("tuningPolicyName", proxyExt.getTuningPolicyName());
      driver.put("deviceFacets", facetsToWire(proxyExt.getDeviceFacets(), context));
    }
    return driver;
  }

  private Map<String, Object> pointMetadata(BComponent component, boolean point, boolean writable, Context context)
  {
    Map<String, Object> pointInfo = new LinkedHashMap<String, Object>();
    pointInfo.put("recognizedAsPoint", Boolean.valueOf(point));
    pointInfo.put("writable", Boolean.valueOf(writable || component instanceof BIWritablePoint));
    if (!(component instanceof BControlPoint))
    {
      return pointInfo;
    }

    BControlPoint controlPoint = (BControlPoint) component;
    BProxyExt proxyExt = proxyExt(component);
    BPointExtension[] extensions = controlPoint.getExtensions();

    pointInfo.put("facets", facetsToWire(controlPoint.getFacets(), context));
    pointInfo.put("proxyExt", componentSummary(proxyExt, context));
    pointInfo.put("hasProxyExt", Boolean.valueOf(proxyExt != null));
    pointInfo.put("extensions", extensions(extensions, context));
    pointInfo.put("hasHistoryExt", Boolean.valueOf(hasExtensionType(extensions, "history")));
    pointInfo.put("hasAlarmExt", Boolean.valueOf(hasExtensionType(extensions, "alarm")));
    if (component instanceof BIWritablePoint)
    {
      BIWritablePoint writablePoint = (BIWritablePoint) component;
      pointInfo.put("activeLevel", writablePoint.getActiveLevel() == null ? null : writablePoint.getActiveLevel().toString(context));
      pointInfo.put("activeLevelOrdinal", writablePoint.getActiveLevel() == null ? null :
          Long.valueOf(writablePoint.getActiveLevel().getOrdinal()));
    }
    return pointInfo;
  }

  private Map<String, Object> writeMetadata(BComponent component, boolean writable, Context context)
  {
    Map<String, Object> write = new LinkedHashMap<String, Object>();
    write.put("writable", Boolean.valueOf(writable || component instanceof BIWritablePoint));
    write.put("valueKind", valueKind(component));
    write.put("actions", writeActions(component));
    write.put("supportsDuration", Boolean.valueOf(supportsAction(component, "override")
        || supportsAction(component, "active") || supportsAction(component, "inactive")));
    write.put("detailsOp", "describe_write");

    if (component instanceof BIWritablePoint)
    {
      BIWritablePoint writablePoint = (BIWritablePoint) component;
      write.put("activeLevel", writablePoint.getActiveLevel() == null ? null : writablePoint.getActiveLevel().toString(context));
      write.put("activeLevelOrdinal", writablePoint.getActiveLevel() == null ? null :
          Long.valueOf(writablePoint.getActiveLevel().getOrdinal()));
      write.put("overrideExpiration", absTime(componentValue(component, "overrideExpiration"), context));
      write.put("fallback", statusValueSummary(componentValue(component, "fallback"), context));
    }
    return write;
  }

  private Map<String, Object> historyMetadata(BComponent component, Context context)
  {
    Map<String, Object> history = new LinkedHashMap<String, Object>();
    List<Object> histories = new ArrayList<Object>();
    if (component != null)
    {
      BHistoryExt[] extensions = component.getChildren(BHistoryExt.class);
      for (int i = 0; i < extensions.length; i++)
      {
        histories.add(historyExtensionSummary(extensions[i], context));
      }
    }
    history.put("hasHistory", Boolean.valueOf(!histories.isEmpty()));
    history.put("count", Long.valueOf(histories.size()));
    history.put("histories", histories);
    history.put("detailsOp", "describe_history");
    return history;
  }

  private Map<String, Object> historyExtensionSummary(BHistoryExt extension, Context context)
  {
    Map<String, Object> summary = componentSummary(extension, context);
    summary.put("enabled", Boolean.valueOf(extension.getEnabled()));
    summary.put("active", Boolean.valueOf(extension.getActive()));
    summary.put("status", extension.getStatus() == null ? null : extension.getStatus().toString(context));
    summary.put("faultCause", extension.getFaultCause());
    summary.put("sourceOrd", extension.getSourceOrd() == null ? null : extension.getSourceOrd().toString());
    summary.put("historyName", extension.resolveHistoryName());
    summary.put("recordType", extension.getRecordType() == null ? null : extension.getRecordType().toString());
    summary.put("config", historyConfigSummary(extension.getHistoryConfig(), context));
    try
    {
      BIHistory history = extension.getHistory();
      summary.put("historyOrd", history == null || history.getOrdInSpace() == null ? null : history.getOrdInSpace().toString());
      summary.put("historyId", history == null || history.getId() == null ? null : history.getId().toString());
    }
    catch (Exception e)
    {
      summary.put("historyError", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
    return summary;
  }

  private Map<String, Object> historyConfigSummary(BHistoryConfig config, Context context)
  {
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    if (config == null)
    {
      return summary;
    }
    summary.put("id", config.getId() == null ? null : config.getId().toString());
    summary.put("historyName", config.getHistoryName());
    summary.put("recordType", config.getRecordType() == null ? null : config.getRecordType().toString());
    summary.put("capacity", config.getCapacity() == null ? null : config.getCapacity().toString(context));
    summary.put("fullPolicy", config.getFullPolicy() == null ? null : config.getFullPolicy().toString(context));
    summary.put("storageType", config.getStorageType() == null ? null : config.getStorageType().toString(context));
    summary.put("interval", config.getInterval() == null ? null : config.getInterval().toString(context));
    summary.put("timeZone", config.getTimeZone() == null ? null : config.getTimeZone().toString(context));
    return summary;
  }

  private Map<String, Object> alarmMetadata(BComponent component, Context context)
  {
    Map<String, Object> alarm = new LinkedHashMap<String, Object>();
    List<Object> sources = new ArrayList<Object>();
    if (component instanceof BControlPoint)
    {
      BPointExtension[] extensions = ((BControlPoint) component).getExtensions();
      for (int i = 0; i < extensions.length; i++)
      {
        String typeSpec = extensions[i].getType().toString().toLowerCase();
        if (typeSpec.indexOf("alarm") >= 0)
        {
          sources.add(componentSummary(extensions[i], context));
        }
      }
    }
    alarm.put("hasAlarm", Boolean.valueOf(!sources.isEmpty()));
    alarm.put("count", Long.valueOf(sources.size()));
    alarm.put("sources", sources);
    alarm.put("snapshotOp", "read_alarms");
    alarm.put("subscribeOp", "subscribe_alarms");
    return alarm;
  }

  private Map<String, Object> subscriptionMetadata(boolean point, boolean history, boolean alarm, boolean schedule)
  {
    Map<String, Object> subscriptions = new LinkedHashMap<String, Object>();
    subscriptions.put("pointCov", Boolean.valueOf(point));
    subscriptions.put("alarmEvents", Boolean.valueOf(alarm));
    subscriptions.put("historyLive", Boolean.FALSE);
    subscriptions.put("scheduleLive", Boolean.FALSE);
    subscriptions.put("historyReadOnDemand", Boolean.valueOf(history));
    subscriptions.put("scheduleReadOnDemand", Boolean.valueOf(schedule));
    return subscriptions;
  }

  private String valueKind(BComponent component)
  {
    if (component == null)
    {
      return null;
    }
    String typeSpec = component.getType().toString().toLowerCase();
    if (typeSpec.indexOf("boolean") >= 0)
    {
      return "boolean";
    }
    if (typeSpec.indexOf("numeric") >= 0)
    {
      return "numeric";
    }
    if (typeSpec.indexOf("enum") >= 0)
    {
      return "enum";
    }
    if (typeSpec.indexOf("string") >= 0)
    {
      return "string";
    }
    return component.getType().toString();
  }

  private List<Object> writeActions(BComponent component)
  {
    List<Object> actions = new ArrayList<Object>();
    if (component == null)
    {
      return actions;
    }
    if (supportsAction(component, "set"))
    {
      actions.add("set");
    }
    if (supportsAction(component, "override") || supportsAction(component, "active") || supportsAction(component, "inactive"))
    {
      actions.add("override");
    }
    if (supportsAction(component, "auto"))
    {
      actions.add("auto");
    }
    if (supportsAction(component, "emergencyOverride") || supportsAction(component, "emergencyActive") || supportsAction(component, "emergencyInactive"))
    {
      actions.add("emergency_override");
    }
    if (supportsAction(component, "emergencyAuto"))
    {
      actions.add("emergency_auto");
    }
    return actions;
  }

  private boolean supportsAction(BComponent component, String actionName)
  {
    return component != null && component.getAction(actionName) != null;
  }

  private BValue componentValue(BComponent component, String propertyName)
  {
    if (component == null)
    {
      return null;
    }
    Property property = component.getProperty(propertyName);
    if (property == null)
    {
      return null;
    }
    try
    {
      return component.get(property);
    }
    catch (Exception e)
    {
      return null;
    }
  }

  private Object absTime(BValue value, Context context)
  {
    if (value instanceof BAbsTime)
    {
      BAbsTime time = (BAbsTime) value;
      if (time.isNull())
      {
        return null;
      }
      Map<String, Object> wire = new LinkedHashMap<String, Object>();
      wire.put("timestamp", Long.valueOf(time.getMillis()));
      wire.put("display", time.toString(context));
      return wire;
    }
    return value == null ? null : value.toString(context);
  }

  private Object statusValueSummary(BValue value, Context context)
  {
    if (value == null)
    {
      return null;
    }
    Map<String, Object> wire = new LinkedHashMap<String, Object>();
    if (value instanceof BIStatusValue)
    {
      BStatusValue statusValue = ((BIStatusValue) value).getStatusValue();
      wire.put("status", statusValue.getStatus() == null ? null : statusValue.getStatus().toString(context));
      wire.put("ok", Boolean.valueOf(statusValue.getStatus() != null && statusValue.getStatus().isOk()));
      wire.put("value", valueToWire(statusValue.getValueValue(), context));
      wire.put("display", statusValue.toString(context));
      return wire;
    }
    wire.put("value", valueToWire(value, context));
    wire.put("display", value.toString(context));
    return wire;
  }

  private Map<String, Object> componentSummary(BComponent component, Context context)
  {
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    if (component == null)
    {
      return summary;
    }
    summary.put("ord", component.getSlotPath() == null ? null : component.getSlotPath().toString());
    summary.put("slotPath", component.getSlotPath() == null ? null : component.getSlotPath().toString());
    summary.put("name", component.getName());
    summary.put("display", safe(component.getDisplayName(context)));
    summary.put("typeSpec", component.getType().toString());
    return summary;
  }

  private List<Object> ancestors(BComponent component, Context context)
  {
    List<Object> ancestors = new ArrayList<Object>();
    if (component == null)
    {
      return ancestors;
    }

    BComponent parent = parentComponent(component);
    int guard = 0;
    while (parent != null && guard++ < 32)
    {
      ancestors.add(0, componentSummary(parent, context));
      parent = parentComponent(parent);
    }
    return ancestors;
  }

  private Map<String, Object> componentFacets(BComponent component, Context context)
  {
    if (component instanceof BControlPoint)
    {
      return facetsToWire(((BControlPoint) component).getFacets(), context);
    }
    return new LinkedHashMap<String, Object>();
  }

  private List<Object> extensions(BPointExtension[] extensions, Context context)
  {
    List<Object> out = new ArrayList<Object>();
    for (int i = 0; i < extensions.length; i++)
    {
      out.add(componentSummary(extensions[i], context));
    }
    return out;
  }

  private boolean hasExtensionType(BPointExtension[] extensions, String token)
  {
    for (int i = 0; i < extensions.length; i++)
    {
      String typeSpec = extensions[i].getType().toString().toLowerCase();
      if (typeSpec.indexOf(token) >= 0)
      {
        return true;
      }
    }
    return false;
  }

  private Map<String, Object> facetsToWire(BFacets facets, Context context)
  {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    if (facets == null || facets.isNull() || facets.isEmpty())
    {
      return out;
    }

    String[] keys = facets.list();
    for (int i = 0; i < keys.length; i++)
    {
      BObject value = facets.get(keys[i]);
      out.put(keys[i], valueToWire(value, context));
    }
    return out;
  }

  private Object valueToWire(BObject value, Context context)
  {
    if (value == null)
    {
      return null;
    }
    if (value instanceof BEnumRange)
    {
      return enumOptions((BEnumRange) value, context);
    }
    if (value instanceof BValue)
    {
      return ((BValue) value).toString(context);
    }
    return value.toString();
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

  private List<Object> tags(BComponent component)
  {
    List<Object> tags = new ArrayList<Object>();
    if (component == null)
    {
      return tags;
    }

    try
    {
      int count = 0;
      for (Tag tag : component.tags())
      {
        if (count++ >= 100)
        {
          break;
        }
        Map<String, Object> wire = new LinkedHashMap<String, Object>();
        wire.put("id", tag.getId().toString());
        wire.put("dictionary", tag.getId().hasDictionary() ? tag.getId().getDictionary() : null);
        wire.put("name", tag.getId().getName());
        wire.put("value", tag.getValue() == null ? null : tag.getValue().toString());
        tags.add(wire);
      }
    }
    catch (Exception ignored)
    {
      // Tags are supplemental metadata; do not fail browse/describe if a tag provider errors.
    }
    return tags;
  }

  private List<Object> relations(BComponent component)
  {
    List<Object> relations = new ArrayList<Object>();
    if (component == null)
    {
      return relations;
    }

    try
    {
      int count = 0;
      Collection<Relation> all = component.relations().getAll();
      for (Relation relation : all)
      {
        if (count++ >= 100)
        {
          break;
        }
        Map<String, Object> wire = new LinkedHashMap<String, Object>();
        wire.put("id", relation.getId().toString());
        wire.put("direction", relation.isInbound() ? "in" : "out");
        wire.put("endpointOrd", relation.getEndpointOrd() == null ? null : relation.getEndpointOrd().toString());
        wire.put("endpoint", relation.getEndpoint() == null ? null : relation.getEndpoint().toString());
        relations.add(wire);
      }
    }
    catch (Exception ignored)
    {
      // Relations are supplemental metadata; do not fail browse/describe if a relation provider errors.
    }
    return relations;
  }

  private BProxyExt proxyExt(BComponent component)
  {
    if (component instanceof BProxyExt)
    {
      return (BProxyExt) component;
    }
    if (component instanceof BControlPoint)
    {
      BPointExtension proxyExt = ((BControlPoint) component).getProxyExt();
      if (proxyExt instanceof BProxyExt)
      {
        return (BProxyExt) proxyExt;
      }
    }
    return null;
  }

  private BPointDeviceExt pointDeviceExt(BComponent component, BProxyExt proxyExt)
  {
    if (component instanceof BPointDeviceExt)
    {
      return (BPointDeviceExt) component;
    }
    if (proxyExt != null)
    {
      return proxyExt.getDeviceExt();
    }
    BComponent ancestor = nearestAncestor(component, BPointDeviceExt.class, true);
    return ancestor instanceof BPointDeviceExt ? (BPointDeviceExt) ancestor : null;
  }

  private BDevice device(BComponent component, BProxyExt proxyExt, BPointDeviceExt pointDeviceExt)
  {
    if (component instanceof BDevice)
    {
      return (BDevice) component;
    }
    if (proxyExt != null)
    {
      return proxyExt.getDevice();
    }
    if (pointDeviceExt != null)
    {
      return pointDeviceExt.getDevice();
    }
    if (component instanceof BDeviceExt)
    {
      return ((BDeviceExt) component).getDevice();
    }
    BComponent ancestor = nearestAncestor(component, BDevice.class, true);
    return ancestor instanceof BDevice ? (BDevice) ancestor : null;
  }

  private BDeviceNetwork network(BComponent component, BProxyExt proxyExt, BPointDeviceExt pointDeviceExt, BDevice device)
  {
    if (component instanceof BDeviceNetwork)
    {
      return (BDeviceNetwork) component;
    }
    if (proxyExt != null)
    {
      return proxyExt.getNetwork();
    }
    if (pointDeviceExt != null)
    {
      return pointDeviceExt.getNetwork();
    }
    if (device != null)
    {
      return device.getNetwork();
    }
    if (component instanceof BDeviceExt)
    {
      return ((BDeviceExt) component).getNetwork();
    }
    BComponent ancestor = nearestAncestor(component, BDeviceNetwork.class, true);
    return ancestor instanceof BDeviceNetwork ? (BDeviceNetwork) ancestor : null;
  }

  private BComponent nearestAncestor(BComponent component, Class<?> type, boolean includeSelf)
  {
    BComponent current = includeSelf ? component : parentComponent(component);
    int guard = 0;
    while (current != null && guard++ < 32)
    {
      if (type.isInstance(current))
      {
        return current;
      }
      current = parentComponent(current);
    }
    return null;
  }

  private BComponent parentComponent(BComponent component)
  {
    if (component == null)
    {
      return null;
    }
    BComplex parent = component.getParent();
    return parent instanceof BComponent ? (BComponent) parent : null;
  }

  private Map<String, Object> enumInfo(BObject object, BComponent component, Context context)
  {
    BIEnum enumSource = null;
    if (object instanceof BIEnum)
    {
      enumSource = (BIEnum) object;
    }
    else if (component instanceof BIEnum)
    {
      enumSource = (BIEnum) component;
    }

    Map<String, Object> info = new LinkedHashMap<String, Object>();
    if (enumSource == null)
    {
      return info;
    }

    BObject rawRange = enumSource.getEnumFacets().get(BFacets.RANGE);
    if (!(rawRange instanceof BEnumRange))
    {
      return info;
    }

    BEnumRange range = (BEnumRange) rawRange;
    List<Object> options = new ArrayList<Object>();
    int[] ordinals = range.getOrdinals();
    for (int i = 0; i < ordinals.length; i++)
    {
      Map<String, Object> option = new LinkedHashMap<String, Object>();
      option.put("ordinal", Long.valueOf(ordinals[i]));
      option.put("tag", range.getTag(ordinals[i]));
      option.put("display", range.getDisplayTag(ordinals[i], context));
      options.add(option);
    }

    info.put("options", options);
    return info;
  }

  private Map<String, Object> toChildWire(BINavNode child, Context context, int depth, String metadataMode)
  {
    BOrd ord = child.getNavOrd();
    if (ord == null)
    {
      return fallbackNode(child, null, null, metadataMode);
    }

    String childOrd = ord.toString();
    try
    {
      OrdTarget childTarget = ord.resolve(service, context);
      String slotOrd = slotOrd(childTarget, childOrd);
      if (slotOrd == null || !isAllowed(slotOrd))
      {
        return null;
      }

      if (!childTarget.canRead())
      {
        return fallbackNode(child, slotOrd, "not_readable", metadataMode);
      }
      return toWire(child, childTarget, context, depth, metadataMode);
    }
    catch (Exception e)
    {
      return fallbackNode(child, extractSlotOrd(childOrd), e.getClass().getSimpleName(), metadataMode);
    }
  }

  private static String slotOrd(OrdTarget target, String fallbackOrd)
  {
    BComponent component = null;
    if (target != null)
    {
      BObject object = target.get();
      component = object instanceof BComponent ? (BComponent) object : target.getComponent();
    }
    if (component != null && component.getSlotPath() != null)
    {
      return component.getSlotPath().toString();
    }
    return extractSlotOrd(fallbackOrd);
  }

  private static String extractSlotOrd(String ord)
  {
    if (ord == null)
    {
      return null;
    }

    int index = ord.indexOf("slot:/");
    return index >= 0 ? ord.substring(index) : ord;
  }

  private Map<String, Object> fallbackNode(BINavNode navNode, String ord, String error, String metadataMode)
  {
    Map<String, Object> wire = new LinkedHashMap<String, Object>();
    wire.put("ord", ord);
    wire.put("slotPath", null);
    wire.put("name", navNode.getNavName());
    wire.put("display", navNode.getNavName());
    wire.put("description", null);
    wire.put("typeSpec", null);
    wire.put("kind", navNode.hasNavChildren() ? "container" : "component");
    wire.put("hasChildren", Boolean.valueOf(navNode.hasNavChildren()));
    wire.put("writable", Boolean.FALSE);
    wire.put("features", new ArrayList<Object>(0));
    wire.put("operations", navNode.hasNavChildren() ? listOf("browse") : new ArrayList<Object>(0));
    if (includeMetadata(metadataMode))
    {
      wire.put("metadata", new LinkedHashMap<String, Object>());
    }
    if (error != null)
    {
      wire.put("error", error);
    }
    return wire;
  }

  private void collectMatches(
      Map<String, Object> node,
      String query,
      String kind,
      List<String> requiredFeatures,
      List<String> requiredOperations,
      Boolean writable,
      int limit,
      List<Object> matches)
  {
    if (node == null || matches.size() >= limit)
    {
      return;
    }
    if (matches(node, query, kind, requiredFeatures, requiredOperations, writable))
    {
      Map<String, Object> shallow = new LinkedHashMap<String, Object>(node);
      shallow.remove("children");
      matches.add(shallow);
      if (matches.size() >= limit)
      {
        return;
      }
    }

    Object rawChildren = node.get("children");
    if (!(rawChildren instanceof List))
    {
      return;
    }
    for (Object child : (List<?>) rawChildren)
    {
      if (child instanceof Map)
      {
        @SuppressWarnings("unchecked")
        Map<String, Object> childNode = (Map<String, Object>) child;
        collectMatches(childNode, query, kind, requiredFeatures, requiredOperations, writable, limit, matches);
        if (matches.size() >= limit)
        {
          return;
        }
      }
    }
  }

  private boolean matches(
      Map<String, Object> node,
      String query,
      String kind,
      List<String> requiredFeatures,
      List<String> requiredOperations,
      Boolean writable)
  {
    if (query != null && !containsQuery(node, query))
    {
      return false;
    }
    if (kind != null)
    {
      Object nodeKind = node.get("kind");
      if (nodeKind == null || !kind.equalsIgnoreCase(String.valueOf(nodeKind)))
      {
        return false;
      }
    }
    if (writable != null && !writable.equals(node.get("writable")))
    {
      return false;
    }
    if (!containsAll(node.get("features"), requiredFeatures))
    {
      return false;
    }
    return containsAll(node.get("operations"), requiredOperations);
  }

  private boolean containsQuery(Map<String, Object> node, String query)
  {
    return containsText(node.get("ord"), query)
        || containsText(node.get("slotPath"), query)
        || containsText(node.get("name"), query)
        || containsText(node.get("display"), query)
        || containsText(node.get("description"), query)
        || containsText(node.get("typeSpec"), query);
  }

  private boolean containsText(Object value, String query)
  {
    return value != null && String.valueOf(value).toLowerCase().indexOf(query) >= 0;
  }

  private boolean containsAll(Object rawValues, List<String> required)
  {
    if (required == null || required.isEmpty())
    {
      return true;
    }
    if (!(rawValues instanceof List))
    {
      return false;
    }
    List<?> values = (List<?>) rawValues;
    for (String requiredValue : required)
    {
      boolean found = false;
      for (Object value : values)
      {
        if (value != null && requiredValue.equalsIgnoreCase(String.valueOf(value)))
        {
          found = true;
          break;
        }
      }
      if (!found)
      {
        return false;
      }
    }
    return true;
  }

  private String normalizeSearchText(String value)
  {
    if (value == null || value.trim().length() == 0)
    {
      return null;
    }
    return value.trim().toLowerCase();
  }

  private int normalizeLimit(Object value) throws FallsProtocolException
  {
    if (value == null)
    {
      return DEFAULT_SEARCH_LIMIT;
    }
    if (!(value instanceof Number))
    {
      throw new FallsProtocolException("bad_request", "Field 'limit' must be a number.");
    }
    int limit = ((Number) value).intValue();
    if (limit <= 0)
    {
      return DEFAULT_SEARCH_LIMIT;
    }
    return Math.min(limit, MAX_SEARCH_LIMIT);
  }

  private List<Object> buildFeatures(boolean point, boolean history, boolean alarm, boolean schedule)
  {
    Set<Object> features = new LinkedHashSet<Object>();
    if (point)
    {
      features.add("point");
    }
    if (history)
    {
      features.add("history");
    }
    if (alarm)
    {
      features.add("alarm");
    }
    if (schedule)
    {
      features.add("schedule");
    }
    return new ArrayList<Object>(features);
  }

  private List<Object> buildOperations(
      boolean hasChildren,
      boolean point,
      boolean writable,
      boolean history,
      boolean alarm,
      boolean schedule)
  {
    List<Object> operations = new ArrayList<Object>();
    operations.add("describe");
    if (hasChildren)
    {
      operations.add("browse");
    }
    if (point)
    {
      operations.add("read");
      operations.add("subscribe");
      if (history)
      {
        operations.add("read_history");
      }
      if (alarm)
      {
        operations.add("read_alarms");
      }
      if (writable)
      {
        operations.add("write");
      }
    }
    if (schedule)
    {
      operations.add("read_schedule");
    }
    return operations;
  }

  private static String classifyKind(boolean point, boolean schedule, boolean hasChildren)
  {
    if (point)
    {
      return "point";
    }
    if (schedule)
    {
      return "schedule";
    }
    if (hasChildren)
    {
      return "container";
    }
    return "component";
  }

  private static boolean isPoint(BObject object, BComponent component, String typeSpec)
  {
    return object instanceof BIStatusValue
        || component instanceof BIStatusValue
        || typeSpec.startsWith("control:");
  }

  private static boolean isWritable(BComponent component, String typeSpec)
  {
    return typeSpec.indexOf("Writable") >= 0
        || (component != null && component.getProperty("fallback") != null);
  }

  private static boolean isSchedule(String typeSpec)
  {
    return typeSpec.startsWith("schedule:");
  }

  private static boolean isHistory(String typeSpec)
  {
    return typeSpec.startsWith("history:");
  }

  private static boolean isAlarm(String typeSpec)
  {
    return typeSpec.startsWith("alarm:");
  }

  private static boolean hasChildTypePrefix(BComponent component, String prefix)
  {
    if (component == null)
    {
      return false;
    }

    BComponent[] children = component.getChildComponents();
    for (int i = 0; i < children.length; i++)
    {
      String typeSpec = children[i].getType().toString();
      if (typeSpec != null && typeSpec.startsWith(prefix))
      {
        return true;
      }
    }
    return false;
  }

  private String normalizeOrd(String ord) throws FallsProtocolException
  {
    String candidate = ord == null || ord.trim().length() == 0 ? "slot:/" : ord.trim();
    if (!candidate.startsWith("slot:/"))
    {
      throw new FallsProtocolException("invalid_point", "Only slot:/ ORDs are supported.");
    }
    return candidate;
  }

  private int clampDepth(int depth)
  {
    if (depth < 0)
    {
      return 0;
    }
    if (depth > MAX_DEPTH)
    {
      return MAX_DEPTH;
    }
    return depth;
  }

  private boolean isAllowed(String ord)
  {
    for (Pattern pattern : parsePatterns(service.getAllowedPathPatterns()))
    {
      if (pattern.matcher(ord).matches())
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
    for (int i = 0; i < pieces.length; i++)
    {
      String trimmed = pieces[i].trim();
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

  private static String safe(String value)
  {
    return value == null ? "" : value;
  }

  private static List<Object> listOf(String value)
  {
    List<Object> list = new ArrayList<Object>(1);
    list.add(value);
    return list;
  }
}
