(() => {
  const existing = document.getElementById("nf-harness-root");
  if (existing) {
    existing.remove();
  }

  const style = document.createElement("style");
  style.id = "nf-harness-style";
  style.textContent = `
    #nf-harness-root {
      position: fixed;
      top: 16px;
      right: 16px;
      width: 420px;
      max-height: calc(100vh - 32px);
      overflow: auto;
      z-index: 2147483647;
      font: 13px/1.4 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      color: #111;
      background: #fff;
      border: 1px solid #d0d0d0;
      border-radius: 14px;
      box-shadow: 0 16px 48px rgba(0, 0, 0, 0.2);
      padding: 16px;
    }
    #nf-harness-root h2 {
      margin: 0 0 6px;
      font-size: 20px;
    }
    #nf-harness-root p {
      margin: 0 0 12px;
      color: #555;
    }
    #nf-harness-root .nf-row {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 10px;
    }
    #nf-harness-root .nf-card {
      margin-bottom: 12px;
    }
    #nf-harness-root label {
      display: block;
      font-weight: 600;
      margin: 0 0 4px;
    }
    #nf-harness-root input,
    #nf-harness-root select,
    #nf-harness-root button,
    #nf-harness-root textarea {
      font: inherit;
    }
    #nf-harness-root input,
    #nf-harness-root select {
      width: 100%;
      box-sizing: border-box;
      padding: 8px 10px;
      border: 1px solid #ccc;
      border-radius: 8px;
      margin-bottom: 10px;
      background: #fff;
    }
    #nf-harness-root button {
      padding: 8px 10px;
      border: 1px solid #bbb;
      border-radius: 8px;
      background: #fff;
      cursor: pointer;
    }
    #nf-harness-root button.primary {
      background: #111;
      border-color: #111;
      color: #fff;
    }
    #nf-harness-root .nf-status {
      font-weight: 700;
      margin-bottom: 10px;
    }
    #nf-harness-root pre {
      background: #111;
      color: #f2f2f2;
      padding: 12px;
      border-radius: 10px;
      min-height: 220px;
      white-space: pre-wrap;
      word-break: break-word;
      overflow: auto;
    }
    #nf-harness-root .nf-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 12px;
    }
    #nf-harness-root .nf-close {
      flex: 0 0 auto;
    }
  `;

  const root = document.createElement("div");
  root.id = "nf-harness-root";
  root.innerHTML = `
    <div class="nf-header">
      <div>
        <h2>baskStream Tester</h2>
        <p>Running inside the current station page so the logged-in browser session is reused.</p>
      </div>
      <button class="nf-close">Close</button>
    </div>

    <div class="nf-row">
      <button id="nf-connect" class="primary">Connect</button>
      <button id="nf-disconnect">Disconnect</button>
      <button id="nf-keepalive">Keepalive Off</button>
      <button id="nf-clear">Clear Log</button>
    </div>

    <div class="nf-status">Status: <span id="nf-status">disconnected</span></div>

    <div class="nf-card">
      <label for="nf-wsUrl">WebSocket URL</label>
      <input id="nf-wsUrl" value="wss://localhost/stream">

      <label for="nf-base">Browse Base</label>
      <input id="nf-base" value="slot:/Drivers">

      <label for="nf-depth">Browse Depth</label>
      <input id="nf-depth" type="number" value="1" min="0" max="4">

      <label for="nf-point">Point / Source ORD</label>
      <input id="nf-point" value="slot:/Drivers/LonNetwork/Floor1/AHU_01/points/TestPoint">

      <label for="nf-write-value">Write Value</label>
      <input id="nf-write-value" value="">

      <label for="nf-write-action">Write Action</label>
      <select id="nf-write-action">
        <option value="set">set</option>
        <option value="override">override</option>
        <option value="auto">auto</option>
        <option value="emergency_override">emergency_override</option>
        <option value="emergency_auto">emergency_auto</option>
      </select>

      <label for="nf-write-duration">Override Duration Seconds</label>
      <input id="nf-write-duration" type="number" value="" min="0">

      <label for="nf-hours">History Hours</label>
      <input id="nf-hours" type="number" value="24" min="1">

      <label for="nf-limit">Limit</label>
      <input id="nf-limit" type="number" value="500" min="1" max="5000">

      <label for="nf-alarmScope">Alarm Scope</label>
      <select id="nf-alarmScope">
        <option value="open">open</option>
        <option value="ack_pending">ack_pending</option>
        <option value="all">all</option>
      </select>

      <label for="nf-alarmMode">Alarm Push Mode</label>
      <select id="nf-alarmMode">
        <option value="event">event</option>
        <option value="snapshot">snapshot</option>
        <option value="both">both</option>
      </select>
    </div>

    <div class="nf-row">
      <button id="nf-ping">Ping</button>
      <button id="nf-browse">Browse</button>
      <button id="nf-describe">Describe</button>
      <button id="nf-read">Read</button>
      <button id="nf-write">Write</button>
      <button id="nf-subscribe">Subscribe</button>
      <button id="nf-unsubscribe">Unsubscribe</button>
      <button id="nf-history">Read History</button>
      <button id="nf-alarms">Read Alarms</button>
      <button id="nf-subscribe-alarms">Subscribe Alarms</button>
      <button id="nf-unsubscribe-alarms">Unsub Alarms</button>
      <button id="nf-schedule">Read Schedule</button>
    </div>

    <pre id="nf-log"></pre>
  `;

  document.head.appendChild(style);
  document.body.appendChild(root);

  const qs = (id) => document.getElementById(id);
  const logEl = qs("nf-log");
  const statusEl = qs("nf-status");
  const state = { ws: null, seq: 0, keepalive: null };

  const log = (value) => {
    const line = typeof value === "string" ? value : JSON.stringify(value, null, 2);
    logEl.textContent += line + "\n";
    logEl.scrollTop = logEl.scrollHeight;
  };

  const te = new TextEncoder();
  const td = new TextDecoder();

  const mp = {
    enc(v) {
      const join = (parts) => {
        const len = parts.reduce((n, p) => n + p.length, 0);
        const out = new Uint8Array(len);
        let off = 0;
        for (const p of parts) {
          out.set(p, off);
          off += p.length;
        }
        return out;
      };
      const u8 = (...n) => Uint8Array.from(n);
      const encStr = (s) => {
        const b = te.encode(s);
        if (b.length <= 31) return join([u8(0xa0 | b.length), b]);
        if (b.length <= 255) return join([u8(0xd9, b.length), b]);
        return join([u8(0xda, (b.length >> 8) & 0xff, b.length & 0xff), b]);
      };
      const encInt = (n) => {
        if (n >= 0 && n <= 0x7f) return u8(n);
        if (n >= -32 && n < 0) return u8(0x100 + n);
        if (n >= -128 && n <= 127) return u8(0xd0, n & 0xff);
        if (n >= -32768 && n <= 32767) return u8(0xd1, (n >> 8) & 0xff, n & 0xff);
        if (n >= -2147483648 && n <= 2147483647) {
          return u8(0xd2, (n >> 24) & 0xff, (n >> 16) & 0xff, (n >> 8) & 0xff, n & 0xff);
        }

        const b = new ArrayBuffer(9);
        const dv = new DataView(b);
        if (n >= 0) {
          dv.setUint8(0, 0xcf);
          dv.setBigUint64(1, BigInt(n));
        } else {
          dv.setUint8(0, 0xd3);
          dv.setBigInt64(1, BigInt(n));
        }
        return new Uint8Array(b);
      };
      if (v == null) return u8(0xc0);
      if (typeof v === "string") return encStr(v);
      if (typeof v === "boolean") return u8(v ? 0xc3 : 0xc2);
      if (typeof v === "number") {
        if (Number.isInteger(v)) return encInt(v);
        const b = new ArrayBuffer(9);
        const dv = new DataView(b);
        dv.setUint8(0, 0xcb);
        dv.setFloat64(1, v);
        return new Uint8Array(b);
      }
      if (Array.isArray(v)) return join([u8(0x90 | v.length), ...v.map(mp.enc)]);
      if (typeof v === "object") {
        const entries = Object.entries(v);
        const body = [];
        for (const [k, val] of entries) body.push(encStr(k), mp.enc(val));
        return join([u8(0x80 | entries.length), ...body]);
      }
      throw new Error("unsupported");
    },
    dec(bytes) {
      const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
      let i = 0;
      const take = (n) => {
        const start = i;
        i += n;
        return new Uint8Array(bytes.buffer, bytes.byteOffset + start, n);
      };
      const read = () => {
        const t = dv.getUint8(i++);
        if (t <= 0x7f) return t;
        if (t >= 0xe0) return t - 256;
        if ((t & 0xe0) === 0xa0) return td.decode(take(t & 0x1f));
        if ((t & 0xf0) === 0x90) return Array.from({ length: t & 0x0f }, () => read());
        if ((t & 0xf0) === 0x80) {
          const out = {};
          for (let j = 0; j < (t & 0x0f); j++) out[read()] = read();
          return out;
        }
        switch (t) {
          case 0xc0: return null;
          case 0xc2: return false;
          case 0xc3: return true;
          case 0xcb: { const v = dv.getFloat64(i); i += 8; return v; }
          case 0xcc: return dv.getUint8(i++);
          case 0xcd: { const v = dv.getUint16(i); i += 2; return v; }
          case 0xce: { const v = dv.getUint32(i); i += 4; return v; }
          case 0xcf: { const v = Number(dv.getBigUint64(i)); i += 8; return v; }
          case 0xd0: return dv.getInt8(i++);
          case 0xd1: { const v = dv.getInt16(i); i += 2; return v; }
          case 0xd2: { const v = dv.getInt32(i); i += 4; return v; }
          case 0xd3: { const v = Number(dv.getBigInt64(i)); i += 8; return v; }
          case 0xd9: { const len = dv.getUint8(i++); return td.decode(take(len)); }
          case 0xda: { const len = dv.getUint16(i); i += 2; return td.decode(take(len)); }
          case 0xdc: { const len = dv.getUint16(i); i += 2; return Array.from({ length: len }, () => read()); }
          case 0xde: {
            const len = dv.getUint16(i); i += 2;
            const out = {};
            for (let j = 0; j < len; j++) out[read()] = read();
            return out;
          }
          default: throw new Error("unsupported msgpack byte 0x" + t.toString(16));
        }
      };
      return read();
    }
  };

  const nextId = () => String(++state.seq);

  const send = (obj) => {
    if (!state.ws || state.ws.readyState !== WebSocket.OPEN) {
      log("socket not open");
      return;
    }
    state.ws.send(mp.enc(obj));
  };

  const connect = () => {
    if (state.ws && state.ws.readyState === WebSocket.OPEN) return;
    state.ws = new WebSocket(qs("nf-wsUrl").value);
    state.ws.binaryType = "arraybuffer";
    statusEl.textContent = "connecting";
    state.ws.onopen = () => {
      statusEl.textContent = "open";
      log("open");
      send({ op: "ping", id: nextId() });
    };
    state.ws.onmessage = async (e) => {
      const bytes =
        e.data instanceof ArrayBuffer
          ? new Uint8Array(e.data)
          : new Uint8Array(await e.data.arrayBuffer());
      log({ message: mp.dec(bytes) });
    };
    state.ws.onerror = (e) => log({ wsError: String(e.type || "error") });
    state.ws.onclose = (e) => {
      statusEl.textContent = "closed";
      log({ closed: e.code, reason: e.reason });
    };
  };

  const disconnect = () => {
    if (state.ws) state.ws.close(1000, "client close");
  };

  const toggleKeepalive = () => {
    if (state.keepalive) {
      clearInterval(state.keepalive);
      state.keepalive = null;
      qs("nf-keepalive").textContent = "Keepalive Off";
      return;
    }
    state.keepalive = setInterval(() => {
      if (state.ws && state.ws.readyState === WebSocket.OPEN) {
        send({ op: "ping", id: nextId() });
      }
    }, 20000);
    qs("nf-keepalive").textContent = "Keepalive On";
  };

  root.querySelector(".nf-close").onclick = () => {
    if (state.keepalive) clearInterval(state.keepalive);
    if (state.ws && state.ws.readyState <= WebSocket.OPEN) {
      state.ws.close(1000, "panel close");
    }
    root.remove();
    style.remove();
  };

  qs("nf-connect").onclick = connect;
  qs("nf-disconnect").onclick = disconnect;
  qs("nf-keepalive").onclick = toggleKeepalive;
  qs("nf-clear").onclick = () => { logEl.textContent = ""; };
  qs("nf-ping").onclick = () => send({ op: "ping", id: nextId() });
  qs("nf-browse").onclick = () => send({ op: "browse", id: nextId(), base: qs("nf-base").value, depth: Number(qs("nf-depth").value || 1) });
  qs("nf-describe").onclick = () => send({ op: "describe", id: nextId(), ord: qs("nf-point").value });
  qs("nf-read").onclick = () => send({ op: "read", id: nextId(), points: [qs("nf-point").value] });
  qs("nf-write").onclick = () => {
    const message = { op: "write", id: nextId(), point: qs("nf-point").value, action: qs("nf-write-action").value };
    if (qs("nf-write-value").value !== "") message.value = qs("nf-write-value").value;
    if (qs("nf-write-duration").value !== "") message.durationSec = Number(qs("nf-write-duration").value);
    send(message);
  };
  qs("nf-subscribe").onclick = () => send({ op: "subscribe", id: nextId(), points: [qs("nf-point").value] });
  qs("nf-unsubscribe").onclick = () => send({ op: "unsubscribe", id: nextId(), points: [qs("nf-point").value] });
  qs("nf-history").onclick = () => {
    const end = Date.now();
    const hours = Number(qs("nf-hours").value || 24);
    send({
      op: "read_history",
      id: nextId(),
      ord: qs("nf-point").value,
      start: end - (hours * 60 * 60 * 1000),
      end,
      limit: Number(qs("nf-limit").value || 500)
    });
  };
  qs("nf-alarms").onclick = () => send({
    op: "read_alarms",
    id: nextId(),
    source: qs("nf-point").value || null,
    scope: qs("nf-alarmScope").value,
    limit: Number(qs("nf-limit").value || 500)
  });
  qs("nf-subscribe-alarms").onclick = () => send({
    op: "subscribe_alarms",
    id: nextId(),
    source: qs("nf-point").value || null,
    scope: qs("nf-alarmScope").value,
    mode: qs("nf-alarmMode").value,
    limit: Number(qs("nf-limit").value || 500)
  });
  qs("nf-unsubscribe-alarms").onclick = () => send({
    op: "unsubscribe_alarms",
    source: qs("nf-point").value || null,
    scope: qs("nf-alarmScope").value,
    mode: qs("nf-alarmMode").value,
    limit: Number(qs("nf-limit").value || 500)
  });
  qs("nf-schedule").onclick = () => send({
    op: "read_schedule",
    id: nextId(),
    ord: qs("nf-point").value || null,
    at: Date.now()
  });

  log("baskStream tester injected. Click Connect.");
})();
