# NiagaraFalls Third-Party API Guide

NiagaraFalls exposes station data over an authenticated WebSocket endpoint using MessagePack frames. It is intended to give external applications a faster live-data path than polling Niagara REST, while still preserving Niagara's native object model, permissions, point status, alarms, schedules, and histories.

## Connection Model

- Health check: `GET /falls/health`
- WebSocket endpoint: `/falls`
- Transport: `wss://<station>/falls`
- Encoding: MessagePack maps
- Authentication: the WebSocket runs inside the authenticated Niagara web session. Browser-based clients can reuse the logged-in station session; service clients should perform Niagara login first and then connect with the session cookies.

Every request frame should include:

```json
{
  "op": "ping",
  "id": "client-request-id"
}
```

Responses echo `id` when the operation is request/response based. Push frames such as `cov` and `alarm_cov` do not require a request id.

## Supported Operations

### `ping`

Request:

```json
{ "op": "ping", "id": "1" }
```

Response:

```json
{ "op": "pong", "id": "1" }
```

### `capabilities`

Returns API version, supported operations, limits, schema versions, and live subscription types. Call this after connecting so an external app can adapt to the deployed module version.

```json
{ "op": "capabilities", "id": "caps-1" }
```

Response:

```json
{
  "op": "capabilities_result",
  "id": "caps-1",
  "capabilities": {
    "apiVersion": "1.2",
    "operations": ["browse", "read", "subscribe", "replace_subscriptions", "write"],
    "limits": {
      "maxSubscriptionsPerClient": 500,
      "heartbeatIntervalSec": 30,
      "subscriptionLeaseSec": 300,
      "covBatchWindowMillis": 100,
      "maxBrowseDepth": 4
    },
    "subscriptions": {
      "pointCov": true,
      "pointCovBatching": true,
      "viewGroups": true,
      "leasedGroups": true,
      "alarmEvents": true,
      "modelEvents": true,
      "historyLive": false,
      "scheduleLive": false
    }
  }
}
```

### `browse`

Returns a station tree node and, when `depth` is greater than zero, child nodes.

By default, `browse` omits the `metadata` block so large station traversals stay light. Request metadata only during discovery or refresh passes.

```json
{
  "op": "browse",
  "id": "2",
  "base": "slot:/Drivers",
  "depth": 2,
  "metadata": "full"
}
```

Response:

```json
{
  "op": "browse_result",
  "id": "2",
  "depth": 2,
  "metadata": "full",
  "node": {
    "ord": "slot:/Drivers/LonNetwork/Floor1/AHU_01",
    "slotPath": "slot:/Drivers/LonNetwork/Floor1/AHU_01",
    "name": "AHU_01",
    "display": "AHU-01",
    "description": "lonworks:LonDevice",
    "typeSpec": "lonworks:LonDevice",
    "kind": "container",
    "hasChildren": true,
    "writable": false,
    "features": [],
    "operations": ["describe", "browse"],
    "metadata": {
      "classification": {
        "isDriverDevice": true,
        "equipmentCertainty": "device"
      }
    }
  }
}
```

Lean repeat browse:

```json
{
  "op": "browse",
  "id": "2b",
  "base": "slot:/Drivers/LonNetwork",
  "depth": 1,
  "metadata": "none"
}
```

`metadata` also accepts booleans: `true` is the same as `"full"`, and `false` is the same as `"none"`.

`depth` is clamped by the station service. Clients should use shallow browse calls and drill in as needed rather than requesting broad deep station traversals.

### `describe`

Returns the same node shape as `browse`, without children. Use this for precise metadata on a single ORD. `describe` includes metadata by default because it returns only one node; set `"metadata": "none"` or `false` if the client only needs the structural fields.

```json
{
  "op": "describe",
  "id": "3",
  "ord": "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/TestEnum"
}
```

### `search`

Searches within a bounded tree branch and returns matching shallow node objects. Use this for discovery shortcuts such as finding writable points, history-capable points, or schedules under a branch. It is still bounded by the service browse-depth limit.

```json
{
  "op": "search",
  "id": "3b",
  "base": "slot:/Drivers",
  "depth": 4,
  "query": "temp",
  "features": ["point"],
  "operations": ["read"],
  "metadata": "none",
  "limit": 250
}
```

Response:

```json
{
  "op": "search_result",
  "id": "3b",
  "result": {
    "base": "slot:/Drivers",
    "count": 12,
    "truncated": false,
    "nodes": []
  }
}
```

### `read`

Reads one or more point/value ORDs.

```json
{
  "op": "read",
  "id": "4",
  "points": [
    "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/SpaceTemp",
    "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/FanCmd"
  ]
}
```

Response:

```json
{
  "op": "read_result",
  "id": "4",
  "points": [
    {
      "point": "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/SpaceTemp",
      "displayName": "Space Temp",
      "valueType": "baja:Double",
      "value": 72.4,
      "displayValue": "72.4 °F",
      "status": "{ok}",
      "ok": true,
      "timestamp": 1779648232328
    }
  ]
}
```

Enum and Boolean values include enum metadata when available:

```json
{
  "valueType": "baja:DynamicEnum",
  "value": "Off",
  "displayValue": "Off",
  "enumOrdinal": 0,
  "enumTag": "Off",
  "enumDisplay": "Off",
  "enumOptions": [
    { "ordinal": 0, "tag": "Off", "display": "Off" },
    { "ordinal": 1, "tag": "On", "display": "On" },
    { "ordinal": 2, "tag": "Auto", "display": "Auto" }
  ]
}
```

### `subscribe` and `unsubscribe`

Subscribes to point COV updates. Point subscription frames are value/status oriented and do not include node metadata.

```json
{
  "op": "subscribe",
  "id": "5",
  "points": [
    "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/SpaceTemp"
  ]
}
```

Initial response:

```json
{
  "op": "subscribed",
  "id": "5",
  "points": [ { "point": "...", "value": 72.4, "status": "{ok}" } ]
}
```

Push frame:

```json
{
  "op": "cov",
  "sequence": 42,
  "timestamp": 1779648232328,
  "batched": true,
  "sourceEvents": 3,
  "points": [
    { "point": "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/SpaceTemp", "value": 72.5 }
  ]
}
```

Unsubscribe:

```json
{
  "op": "unsubscribe",
  "points": [
    "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/SpaceTemp"
  ]
}
```

### View-Scoped Subscriptions

For graphics and other UI views, prefer grouped subscriptions over manual subscribe/unsubscribe churn. A group is the client's current desired point list for one screen or widget. `replace_subscriptions` diffs the group server-side, subscribes newly needed points, releases points no longer referenced by any direct subscription or group, and returns current snapshots immediately.

```json
{
  "op": "replace_subscriptions",
  "id": "view-1",
  "group": "graphic:ahu-01",
  "points": [
    "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/SpaceTemp",
    "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/FanCmd"
  ],
  "leaseSec": 300
}
```

Response:

```json
{
  "op": "subscriptions_replaced",
  "id": "view-1",
  "group": "graphic:ahu-01",
  "points": [
    { "point": ".../SpaceTemp", "value": 72.4, "status": "{ok}" }
  ],
  "added": 2,
  "removed": 0,
  "leaseSec": 300,
  "leaseExpiresAt": 1779648532328,
  "pointSubscriptions": 2,
  "subscriptionGroups": 1
}
```

Use `renew_subscriptions` to extend a view lease if the view stays open:

```json
{
  "op": "renew_subscriptions",
  "id": "view-1-renew",
  "group": "graphic:ahu-01",
  "leaseSec": 300
}
```

Use `release_subscriptions` when the view closes:

```json
{
  "op": "release_subscriptions",
  "id": "view-1-close",
  "group": "graphic:ahu-01"
}
```

If the client crashes or navigates without releasing the group, the lease expires and the server releases points that are not referenced by another group or direct subscription. A lease of `0` disables expiration for that group.

For diagnostics:

```json
{
  "op": "subscription_status",
  "id": "subs-1",
  "includePoints": true
}
```

Response:

```json
{
  "op": "subscription_status_result",
  "id": "subs-1",
  "session": {
    "pointSubscriptions": 120,
    "directPointSubscriptions": 0,
    "subscriptionGroups": 3,
    "alarmSubscriptions": 1,
    "modelSubscriptions": 0,
    "pendingCovPoints": 4
  },
  "groups": [
    {
      "group": "graphic:ahu-01",
      "pointCount": 42,
      "leaseSec": 300,
      "ttlSec": 251
    }
  ]
}
```

The older `subscribe` and `unsubscribe` operations remain useful for simple clients and long-lived manual point watches. They are connection-scoped and are removed automatically when the WebSocket closes.

### `write`

Writes to Niagara writable points.

Supported actions:

- `set`: set fallback
- `override`: level 8 override, optional `durationSec`
- `auto`: release level 8
- `emergency_override`: level 1 override
- `emergency_auto`: release level 1

```json
{
  "op": "write",
  "id": "6",
  "point": "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/FanCmd",
  "action": "override",
  "value": true,
  "durationSec": 300
}
```

Response:

```json
{
  "op": "write_result",
  "id": "6",
  "points": [
    {
      "point": "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/FanCmd",
      "action": "override",
      "activeLevel": "8",
      "value": true,
      "status": "{overridden} @ 8"
    }
  ]
}
```

If a higher priority input is active, a lower priority write can succeed without changing the output. Clients should inspect `activeLevel`, `status`, and the returned value.

### `describe_write`

Returns write capabilities without writing. Use this before rendering set/override/auto controls.

```json
{
  "op": "describe_write",
  "id": "6b",
  "points": [
    "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/FanCmd"
  ]
}
```

Response:

```json
{
  "op": "write_description",
  "id": "6b",
  "points": [
    {
      "point": "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/FanCmd",
      "writable": true,
      "valueKind": "boolean",
      "actions": ["set", "override", "auto", "emergency_override", "emergency_auto"],
      "supportsDuration": true,
      "fallback": { "value": false, "status": "{ok}" },
      "levels": []
    }
  ]
}
```

### `read_history`

Reads records for a history-capable point/history ORD.

```json
{
  "op": "read_history",
  "id": "7",
  "ord": "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/SpaceTemp",
  "start": 1779043432000,
  "end": 1779648232000,
  "limit": 1000
}
```

### `describe_history`

Returns history descriptors and summary information without pulling records. Use this to decide whether to show chart/history UI and to choose sane default time windows.

```json
{
  "op": "describe_history",
  "id": "7b",
  "ord": "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/SpaceTemp"
}
```

Response:

```json
{
  "op": "history_description",
  "id": "7b",
  "history": {
    "requestOrd": "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/SpaceTemp",
    "count": 1,
    "histories": [
      {
        "historyOrd": "history:/Station/SpaceTemp",
        "historyId": "Station/SpaceTemp",
        "recordType": "history:NumericTrendRecord",
        "totalCount": 12000,
        "firstTimestamp": 1779043432000,
        "lastTimestamp": 1779648232000,
        "config": {}
      }
    ]
  }
}
```

### `read_schedule`

Reads a Niagara schedule.

```json
{
  "op": "read_schedule",
  "id": "8",
  "ord": "slot:/Schedules/BooleanSchedule",
  "at": 1779648232000
}
```

### `read_alarms`

Reads a bounded alarm snapshot.

```json
{
  "op": "read_alarms",
  "id": "9",
  "scope": "open",
  "limit": 500
}
```

Common scopes:

- `open`
- `ack_pending`
- `all`

### `subscribe_alarms`

Subscribes to alarm changes. The initial response always includes a bounded snapshot. Live pushes default to event mode so large stations do not resend all alarms on every transition.

```json
{
  "op": "subscribe_alarms",
  "id": "10",
  "source": "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/TestPoint",
  "scope": "all",
  "mode": "event",
  "limit": 500
}
```

Initial response:

```json
{
  "op": "alarms_subscribed",
  "id": "10",
  "mode": "event",
  "alarms": { "count": 12, "alarms": [] }
}
```

Event-mode push:

```json
{
  "op": "alarm_cov",
  "sequence": 18,
  "timestamp": 1779648232328,
  "source": "slot:/Drivers/LonNetwork/Floor1/AHU_01/points/TestPoint",
  "scope": "all",
  "limit": 500,
  "mode": "event",
  "event": { "uuid": "..." },
  "inScope": true
}
```

Alarm modes:

- `event`: push only the changed event record.
- `snapshot`: push the bounded alarm snapshot each time.
- `both`: push both the changed event and the refreshed snapshot.

For large stations, use `event`, keep a client-side alarm map keyed by `uuid`, and call `read_alarms` only for initial load or resync.

### `subscribe_model` and `unsubscribe_model`

Subscribes to bounded component model-change hints. This is for station-structure changes such as added, removed, renamed, reordered, recategorized, flags/facets changes, and tag/relation changes on subscribed components. It is not a point-value subscription.

```json
{
  "op": "subscribe_model",
  "id": "model-1",
  "base": "slot:/Drivers",
  "depth": 2
}
```

Push frame:

```json
{
  "op": "model_cov",
  "sequence": 3,
  "timestamp": 1779648232328,
  "event": "property_added",
  "slot": "NewPoint",
  "source": {
    "slotPath": "slot:/Drivers/LonNetwork/Floor1/AHU_01/points",
    "name": "points"
  },
  "refreshRecommended": true
}
```

Treat `model_cov` as a hint and refresh the affected branch with `browse` or `describe`. Keep model subscriptions scoped to branches the app has cached or is actively monitoring.

## Node Metadata

`metadata` is attached to each node when requested. It is evidence for your application; it is not a universal equipment classifier.

Recommended flow:

1. Initial discovery: call shallow `browse` requests with `"metadata": "full"` where you need equipment/point evidence.
2. Live operation: use `read` and `replace_subscriptions` for view-scoped values; these responses stay value-only.
3. Structure refresh: optionally use `subscribe_model` for cached branches, then call `browse` or `describe` again with `"metadata": "full"` for the affected branch or object when a `model_cov` hint arrives.
4. Routine tree navigation: call `browse` with `"metadata": "none"` or omit the field.

Model events are branch-scoped hints, not a full synchronized station database. Apps should still support startup discovery, manual refresh, scheduled rediscovery, and app-observed mismatch refreshes.

```json
{
  "metadata": {
    "classification": {
      "isComponent": true,
      "isControlPoint": true,
      "isWritablePoint": true,
      "isStatusValue": true,
      "isDriverNetwork": false,
      "isDriverDevice": false,
      "isPointDeviceExt": false,
      "isPointExtension": false,
      "isProxyExt": false,
      "isProxyPoint": true,
      "isSchedule": false,
      "hasHistory": true,
      "hasAlarm": true,
      "isPoint": true,
      "equipmentCertainty": "unknown"
    },
    "parent": {
      "ord": "slot:/Drivers/LonNetwork/Floor1/AHU_01/points",
      "slotPath": "slot:/Drivers/LonNetwork/Floor1/AHU_01/points",
      "name": "points",
      "display": "points",
      "typeSpec": "lonworks:LonPointDeviceExt"
    },
    "ancestors": [],
    "driver": {
      "isDriverBacked": true,
      "network": {},
      "device": {},
      "pointDeviceExt": {},
      "proxyExt": {},
      "proxyExtType": "lonworks:LonProxyExt",
      "deviceExtType": "lonworks:LonPointDeviceExt",
      "readWriteMode": "readWrite",
      "tuningPolicyName": "Default Policy",
      "deviceFacets": {}
    },
    "point": {
      "recognizedAsPoint": true,
      "writable": true,
      "facets": {},
      "hasProxyExt": true,
      "hasHistoryExt": true,
      "hasAlarmExt": true,
      "activeLevel": "def",
      "extensions": []
    },
    "write": {
      "writable": true,
      "valueKind": "boolean",
      "actions": ["set", "override", "auto"],
      "detailsOp": "describe_write"
    },
    "history": {
      "hasHistory": true,
      "count": 1,
      "detailsOp": "describe_history"
    },
    "alarm": {
      "hasAlarm": true,
      "snapshotOp": "read_alarms",
      "subscribeOp": "subscribe_alarms"
    },
    "subscriptions": {
      "pointCov": true,
      "alarmEvents": true,
      "historyReadOnDemand": true,
      "scheduleReadOnDemand": false
    },
    "facets": {},
    "tags": [],
    "relations": []
  }
}
```

### Classification Semantics

Use these flags as evidence:

- `classification.isDriverDevice`: the component is a Niagara driver `BDevice`. This is deterministic.
- `classification.isDriverNetwork`: the component is a Niagara driver network. This is deterministic.
- `classification.isControlPoint`: the component is a Niagara `BControlPoint`. This is deterministic.
- `classification.isProxyPoint`: the point has a driver proxy extension. This is strong evidence that it maps to an external protocol/device value.
- `classification.equipmentCertainty`: currently `"device"` only when the component is a `BDevice`; otherwise `"unknown"`.

Do not treat `"unknown"` as “not equipment.” It only means NiagaraFalls cannot prove that the component is equipment from type alone.

### Parent And Ancestors

- `metadata.parent` is the direct parent component.
- `metadata.ancestors` is the root-to-parent chain.
- Each summary contains `ord`, `slotPath`, `name`, `display`, and `typeSpec`.

This lets client applications preserve the station's structure and make their own grouping decisions.

### Driver Metadata

`metadata.driver` connects points back to Niagara driver structure when available:

- `network`: nearest or direct `BDeviceNetwork`.
- `device`: nearest or direct `BDevice`.
- `pointDeviceExt`: point container extension for driver proxy points.
- `proxyExt`: driver proxy extension on a control point.
- `proxyExtType`: protocol-specific proxy type.
- `deviceExtType`: protocol-specific point-device extension type.
- `readWriteMode`: proxy read/write mode when available.
- `tuningPolicyName`: driver tuning policy name when available.
- `deviceFacets`: facets reported by the proxy extension.

This is the best source for protocol-neutral device/point discovery across BACnet, Lon, Modbus, and other Niagara drivers.

### Point Metadata

`metadata.point` includes:

- whether NiagaraFalls recognized the node as a point
- whether it is writable
- point facets such as units, precision, range, or text labels when available
- proxy extension summary
- point extension summaries
- booleans for history and alarm extensions
- active priority level for writable points when available

Related blocks:

- `metadata.write`: compact write summary for graphics controls; use `describe_write` for full priority-array detail.
- `metadata.history`: attached history extensions and a pointer to `describe_history`.
- `metadata.alarm`: alarm-source summary and alarm snapshot/subscription operations.
- `metadata.subscriptions`: which live or on-demand flows make sense for the node.

### Tags And Relations

`metadata.tags` and `metadata.relations` expose Niagara tag/relation evidence when present. This is where Haystack-like modeling or project-specific semantic modeling can make equipment classification deterministic.

Tags and relations are supplemental. If a provider throws while reading tags or relations, browse/describe will still succeed and return an empty list for that portion.

## Equipment Discovery Guidance

NiagaraFalls intentionally does not claim 100% equipment detection.

Deterministic:

- A `BDevice` is a Niagara driver device.
- A `BControlPoint` is a Niagara control point.
- A point with `isProxyPoint` maps through a driver proxy extension.
- Tags/relations or a maintained mapping can confirm equipment if your station standard defines them.

Not deterministic:

- A folder named `AHU_01` may be equipment, a graphic grouping, a logic folder, or a convention.
- A driver device may represent one physical unit, a gateway, a controller serving multiple logical systems, or a virtual integration endpoint.
- A protocol network may organize points differently by vendor, station builder, or project.

Recommended client approach:

1. Treat driver devices and control/proxy points as type-guaranteed facts.
2. Treat tags/relations or a user-maintained mapping as confirmed equipment.
3. Use parent chain, driver ancestry, naming, facets, and point signatures for inferred equipment.
4. Present inferred equipment for manual review inside the app.
5. Store user review decisions so the next discovery pass becomes deterministic for that station.

## Suggested App-Side Confidence Levels

- `confirmed_device`: `metadata.classification.isDriverDevice === true`.
- `confirmed_point`: `metadata.classification.isControlPoint === true`.
- `confirmed_equipment`: station tags/relations or user mapping say it is equipment.
- `inferred_equipment_high`: folder or device has a strong point signature and consistent driver ancestry.
- `inferred_equipment_low`: name or location suggests equipment, but point signature is weak.
- `not_equipment`: user rejected it or it is a known organizational/support node.

## Compatibility Notes

The `metadata` block is additive and request-controlled. Clients can ignore it or omit it and continue using `ord`, `slotPath`, `name`, `typeSpec`, `features`, `operations`, and point read/write payloads.

Third-party clients should not require every metadata subfield to be populated. Different protocols and station models expose different evidence.
