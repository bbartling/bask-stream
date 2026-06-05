#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs/promises";
import http from "node:http";
import https from "node:https";
import net from "node:net";
import path from "node:path";
import tls from "node:tls";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const htmlPath = path.join(__dirname, "baskstream-nav-tree.html");
const port = Number(process.env.PORT || process.argv.find((arg) => arg.startsWith("--port="))?.split("=")[1] || 8787);
const host = process.env.HOST || "127.0.0.1";
const decoder = new TextDecoder();

const server = http.createServer(async (req, res) => {
  try {
    if (req.url === "/" || req.url === "/baskstream-nav-tree.html") {
      const html = await fs.readFile(htmlPath, "utf8");
      res.writeHead(200, { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" });
      res.end(html);
      return;
    }
    res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
    res.end("Not found");
  }
  catch (error) {
    res.writeHead(500, { "Content-Type": "text/plain; charset=utf-8" });
    res.end(error.message || String(error));
  }
});

server.on("upgrade", (req, socket) => {
  if (req.url !== "/app-ws") {
    socket.destroy();
    return;
  }
  const key = req.headers["sec-websocket-key"];
  if (!key) {
    socket.destroy();
    return;
  }
  const accept = crypto.createHash("sha1")
    .update(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
    .digest("base64");
  socket.write([
    "HTTP/1.1 101 Switching Protocols",
    "Upgrade: websocket",
    "Connection: Upgrade",
    `Sec-WebSocket-Accept: ${accept}`,
    "",
    ""
  ].join("\r\n"));
  new AppSession(socket);
});

server.listen(port, host, () => {
  console.log(`baskStream nav tree app: http://${host}:${port}/`);
});

class AppSession {
  constructor(clientSocket) {
    this.clientSocket = clientSocket;
    this.clientState = { socket: clientSocket, buffer: Buffer.alloc(0), maskPong: false };
    this.stationSocket = null;
    this.stationState = null;
    this.cookies = new Map();
    this.stationUrl = null;

    clientSocket.on("data", (chunk) => {
      this.clientState.buffer = Buffer.concat([this.clientState.buffer, chunk]);
      parseFrames(this.clientState, (payload, opcode) => this.onClientFrame(payload, opcode));
    });
    clientSocket.on("close", () => this.closeStation());
    clientSocket.on("error", () => this.closeStation());
  }

  async onClientFrame(payload, opcode) {
    if (opcode === 8) {
      if (!this.clientSocket.destroyed) {
        this.clientSocket.write(webSocketFrame(payload, 8, false));
      }
      this.clientSocket.end();
      return;
    }
    if (opcode !== 1) {
      this.sendClient({ op: "error", code: "bad_request", message: "Expected JSON text frames." });
      return;
    }

    let message;
    try {
      message = JSON.parse(payload.toString("utf8"));
    }
    catch {
      this.sendClient({ op: "error", code: "bad_request", message: "Invalid JSON frame." });
      return;
    }

    if (message.op === "connect_station") {
      await this.connectStation(message);
      return;
    }

    if (!this.stationSocket || this.stationSocket.destroyed) {
      this.sendClient({ op: "error", id: message.id, code: "not_connected", message: "Station WebSocket is not connected." });
      return;
    }

    try {
      this.stationSocket.write(webSocketFrame(encodeMsgpack(message), 2, true));
    }
    catch (error) {
      this.sendClient({ op: "error", id: message.id, code: "station_send_failed", message: error.message || String(error) });
    }
  }

  async connectStation(message) {
    const id = message.id;
    try {
      this.closeStation();
      this.stationUrl = new URL(message.stationUrl || "https://192.168.0.125");
      this.cookies.clear();
      const health = await this.login(message.username, message.password);
      const ws = await this.connectStationWebSocket();
      this.stationSocket = ws.socket;
      this.stationState = { socket: ws.socket, buffer: ws.initial, maskPong: true };
      ws.socket.on("data", (chunk) => {
        this.stationState.buffer = Buffer.concat([this.stationState.buffer, chunk]);
        parseFrames(this.stationState, (payload, opcode) => this.onStationFrame(payload, opcode));
      });
      ws.socket.on("close", () => this.sendClient({ op: "station_closed" }));
      ws.socket.on("error", (error) => this.sendClient({
        op: "error",
        code: "station_ws_error",
        message: error.message || String(error)
      }));
      parseFrames(this.stationState, (payload, opcode) => this.onStationFrame(payload, opcode));
      this.sendClient({ op: "station_connected", id, health });
    }
    catch (error) {
      this.sendClient({
        op: "error",
        id,
        code: "connect_failed",
        message: error.message || String(error)
      });
    }
  }

  async login(username, password) {
    if (!username || !password) {
      throw new Error("Station user and password are required.");
    }

    await this.request("GET", "/prelogin");
    const userStep = await this.request(
      "POST",
      "/login",
      `j_username=${encodeURIComponent(username)}`,
      { "Content-Type": "application/x-www-form-urlencoded" }
    );
    if (userStep.status !== 200 || !userStep.body.includes("j_security_check")) {
      throw new Error(`Username step failed with HTTP ${userStep.status}.`);
    }

    const nonce = crypto.randomBytes(16).toString("base64");
    const clientFirstBare = `n=${prepUsername(username)},r=${nonce}`;
    const serverFirstResponse = await this.request(
      "POST",
      "/j_security_check/",
      `action=sendClientFirstMessage&clientFirstMessage=n,,${clientFirstBare}`,
      { "Content-Type": "application/x-niagara-login-support" }
    );
    if (serverFirstResponse.status !== 200) {
      throw new Error(`SCRAM first message failed with HTTP ${serverFirstResponse.status}.`);
    }

    const serverFirst = serverFirstResponse.body.trim();
    const parsed = parseScram(serverFirst);
    if (!parsed.r?.startsWith(nonce) || !parsed.s || !parsed.i) {
      throw new Error("Invalid SCRAM server first message.");
    }

    const salted = crypto.pbkdf2Sync(
      Buffer.from(String(password).normalize("NFKC"), "utf8"),
      Buffer.from(parsed.s, "base64"),
      Number(parsed.i),
      32,
      "sha256"
    );
    const clientFinalNoProof = `c=biws,r=${parsed.r}`;
    const authMessage = `${clientFirstBare},${serverFirst},${clientFinalNoProof}`;
    const clientKey = hmac(salted, "Client Key");
    const proof = xor(clientKey, hmac(sha256(clientKey), authMessage)).toString("base64");

    const finalResponse = await this.request(
      "POST",
      "/j_security_check/",
      `action=sendClientFinalMessage&clientFinalMessage=${clientFinalNoProof},p=${proof}`,
      { "Content-Type": "application/x-niagara-login-support" }
    );
    if (finalResponse.status !== 200) {
      throw new Error(`SCRAM final message failed with HTTP ${finalResponse.status}.`);
    }

    await this.request("GET", "/j_security_check/");
    const health = await this.request("GET", "/stream/health");
    if (health.status !== 200) {
      throw new Error(`Health check failed after login with HTTP ${health.status}.`);
    }
    return JSON.parse(health.body);
  }

  request(method, requestPath, body = "", headers = {}) {
    const url = new URL(requestPath, this.stationUrl);
    const transport = url.protocol === "https:" ? https : http;
    return new Promise((resolve, reject) => {
      const req = transport.request({
        hostname: url.hostname,
        port: Number(url.port || (url.protocol === "https:" ? 443 : 80)),
        method,
        path: `${url.pathname}${url.search}`,
        rejectUnauthorized: false,
        headers: {
          Host: url.host,
          Cookie: this.cookieHeader(),
          ...headers,
          ...(body ? { "Content-Length": Buffer.byteLength(body) } : {})
        }
      }, (res) => {
        this.storeCookies(res.headers);
        const chunks = [];
        res.on("data", (chunk) => chunks.push(chunk));
        res.on("end", () => resolve({
          status: res.statusCode || 0,
          headers: res.headers,
          body: Buffer.concat(chunks).toString("utf8")
        }));
      });
      req.on("error", reject);
      if (body) req.write(body);
      req.end();
    });
  }

  connectStationWebSocket() {
    const useTls = this.stationUrl.protocol === "https:";
    const port = Number(this.stationUrl.port || (useTls ? 443 : 80));
    return new Promise((resolve, reject) => {
      const socket = useTls
        ? tls.connect({ host: this.stationUrl.hostname, port, rejectUnauthorized: false })
        : net.connect({ host: this.stationUrl.hostname, port });
      const key = crypto.randomBytes(16).toString("base64");
      let buffer = Buffer.alloc(0);

      const fail = (error) => {
        socket.destroy();
        reject(error);
      };
      const onData = (chunk) => {
        buffer = Buffer.concat([buffer, chunk]);
        const index = buffer.indexOf("\r\n\r\n");
        if (index < 0) return;
        const head = buffer.subarray(0, index).toString("utf8");
        if (!head.includes(" 101 ")) {
          fail(new Error(`WebSocket upgrade failed:\n${head}`));
          return;
        }
        socket.removeListener("data", onData);
        socket.removeListener("error", fail);
        resolve({ socket, initial: buffer.subarray(index + 4) });
      };
      socket.once("connect", () => {
        socket.write(
          "GET /stream HTTP/1.1\r\n" +
          `Host: ${this.stationUrl.host}\r\n` +
          "Upgrade: websocket\r\n" +
          "Connection: Upgrade\r\n" +
          `Sec-WebSocket-Key: ${key}\r\n` +
          "Sec-WebSocket-Version: 13\r\n" +
          `Cookie: ${this.cookieHeader()}\r\n` +
          `Origin: ${this.stationUrl.origin}\r\n` +
          "\r\n"
        );
      });
      socket.on("data", onData);
      socket.on("error", fail);
    });
  }

  onStationFrame(payload, opcode) {
    if (opcode === 8) {
      this.sendClient({ op: "station_closed" });
      return;
    }
    if (opcode !== 2) {
      return;
    }
    try {
      this.sendClient(decodeMsgpack(payload));
    }
    catch (error) {
      this.sendClient({
        op: "error",
        code: "station_decode_failed",
        message: error.message || String(error)
      });
    }
  }

  sendClient(message) {
    if (this.clientSocket.destroyed) return;
    this.clientSocket.write(webSocketFrame(Buffer.from(JSON.stringify(message), "utf8"), 1, false));
  }

  cookieHeader() {
    return [...this.cookies.entries()].map(([key, value]) => `${key}=${value}`).join("; ");
  }

  storeCookies(headers) {
    for (const raw of headers["set-cookie"] || []) {
      const pair = raw.split(";")[0];
      const index = pair.indexOf("=");
      if (index > 0) this.cookies.set(pair.slice(0, index), pair.slice(index + 1));
    }
  }

  closeStation() {
    if (this.stationSocket && !this.stationSocket.destroyed) {
      this.stationSocket.end();
    }
    this.stationSocket = null;
    this.stationState = null;
  }
}

function parseScram(value) {
  const out = {};
  for (const part of value.split(",")) {
    const index = part.indexOf("=");
    if (index > 0) out[part.slice(0, index)] = part.slice(index + 1);
  }
  return out;
}

function prepUsername(value) {
  return String(value).normalize("NFKC").replace(/=/g, "=3D").replace(/,/g, "=2C");
}

function hmac(key, text) {
  return crypto.createHmac("sha256", key).update(text, "utf8").digest();
}

function sha256(buffer) {
  return crypto.createHash("sha256").update(buffer).digest();
}

function xor(a, b) {
  const out = Buffer.alloc(a.length);
  for (let i = 0; i < a.length; i++) out[i] = a[i] ^ b[i];
  return out;
}

function webSocketFrame(payload, opcode = 2, maskPayload = false) {
  const length = payload.length;
  const headLength = length < 126 ? 2 : (length <= 65535 ? 4 : 10);
  const maskLength = maskPayload ? 4 : 0;
  const head = Buffer.alloc(headLength + maskLength);
  head[0] = 0x80 | opcode;
  if (length < 126) {
    head[1] = (maskPayload ? 0x80 : 0) | length;
  }
  else if (length <= 65535) {
    head[1] = (maskPayload ? 0x80 : 0) | 126;
    head.writeUInt16BE(length, 2);
  }
  else {
    head[1] = (maskPayload ? 0x80 : 0) | 127;
    head.writeBigUInt64BE(BigInt(length), 2);
  }
  if (!maskPayload) return Buffer.concat([head, payload]);

  const mask = crypto.randomBytes(4);
  mask.copy(head, headLength);
  const body = Buffer.alloc(length);
  for (let i = 0; i < length; i++) body[i] = payload[i] ^ mask[i % 4];
  return Buffer.concat([head, body]);
}

function parseFrames(state, onMessage) {
  while (state.buffer.length >= 2) {
    const opcode = state.buffer[0] & 15;
    let length = state.buffer[1] & 127;
    let offset = 2;
    if (length === 126) {
      if (state.buffer.length < 4) return;
      length = state.buffer.readUInt16BE(2);
      offset = 4;
    }
    else if (length === 127) {
      if (state.buffer.length < 10) return;
      length = Number(state.buffer.readBigUInt64BE(2));
      offset = 10;
    }

    const masked = Boolean(state.buffer[1] & 128);
    let mask = null;
    if (masked) {
      if (state.buffer.length < offset + 4) return;
      mask = state.buffer.subarray(offset, offset + 4);
      offset += 4;
    }
    if (state.buffer.length < offset + length) return;

    const payload = Buffer.from(state.buffer.subarray(offset, offset + length));
    state.buffer = state.buffer.subarray(offset + length);
    if (masked) {
      for (let i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
    }
    if (opcode === 9) {
      state.socket.write(webSocketFrame(payload, 10, Boolean(state.maskPong)));
      continue;
    }
    onMessage(payload, opcode);
  }
}

const bytes = (...values) => Buffer.from(values);
const join = (parts) => Buffer.concat(parts.map((part) => Buffer.from(part)));

function encodeMsgpack(value) {
  if (value == null) return bytes(0xc0);
  if (typeof value === "boolean") return bytes(value ? 0xc3 : 0xc2);
  if (typeof value === "string") {
    const encoded = Buffer.from(value, "utf8");
    if (encoded.length <= 31) return join([bytes(0xa0 | encoded.length), encoded]);
    if (encoded.length <= 255) return join([bytes(0xd9, encoded.length), encoded]);
    return join([bytes(0xda, encoded.length >> 8, encoded.length & 255), encoded]);
  }
  if (typeof value === "number") {
    if (Number.isInteger(value)) {
      if (value >= 0 && value <= 127) return bytes(value);
      if (value >= -32 && value < 0) return bytes(256 + value);
      if (value >= -128 && value <= 127) return bytes(0xd0, value & 255);
      if (value >= -32768 && value <= 32767) return bytes(0xd1, (value >> 8) & 255, value & 255);
      if (value >= -2147483648 && value <= 2147483647) return bytes(0xd2, (value >> 24) & 255, (value >> 16) & 255, (value >> 8) & 255, value & 255);
      const out = Buffer.alloc(9);
      out[0] = 0xd3;
      out.writeBigInt64BE(BigInt(value), 1);
      return out;
    }
    const out = Buffer.alloc(9);
    out[0] = 0xcb;
    out.writeDoubleBE(value, 1);
    return out;
  }
  if (Array.isArray(value)) {
    if (value.length <= 15) return join([bytes(0x90 | value.length), ...value.map(encodeMsgpack)]);
    return join([bytes(0xdc, value.length >> 8, value.length & 255), ...value.map(encodeMsgpack)]);
  }
  const entries = Object.entries(value);
  const body = [];
  for (const [key, item] of entries) body.push(encodeMsgpack(key), encodeMsgpack(item));
  if (entries.length <= 15) return join([bytes(0x80 | entries.length), ...body]);
  return join([bytes(0xde, entries.length >> 8, entries.length & 255), ...body]);
}

function decodeMsgpack(buffer) {
  let offset = 0;
  const take = (length) => {
    const out = buffer.subarray(offset, offset + length);
    offset += length;
    return out;
  };
  const read = () => {
    const tag = buffer[offset++];
    if (tag <= 0x7f) return tag;
    if (tag >= 0xe0) return tag - 256;
    if ((tag & 0xe0) === 0xa0) return decoder.decode(take(tag & 31));
    if ((tag & 0xf0) === 0x90) return Array.from({ length: tag & 15 }, read);
    if ((tag & 0xf0) === 0x80) {
      const out = {};
      for (let i = 0; i < (tag & 15); i++) out[read()] = read();
      return out;
    }
    switch (tag) {
      case 0xc0: return null;
      case 0xc2: return false;
      case 0xc3: return true;
      case 0xca: { const out = buffer.readFloatBE(offset); offset += 4; return out; }
      case 0xcb: { const out = buffer.readDoubleBE(offset); offset += 8; return out; }
      case 0xcc: return buffer[offset++];
      case 0xcd: { const out = buffer.readUInt16BE(offset); offset += 2; return out; }
      case 0xce: { const out = buffer.readUInt32BE(offset); offset += 4; return out; }
      case 0xcf: { const out = Number(buffer.readBigUInt64BE(offset)); offset += 8; return out; }
      case 0xd0: { const out = buffer.readInt8(offset); offset += 1; return out; }
      case 0xd1: { const out = buffer.readInt16BE(offset); offset += 2; return out; }
      case 0xd2: { const out = buffer.readInt32BE(offset); offset += 4; return out; }
      case 0xd3: { const out = Number(buffer.readBigInt64BE(offset)); offset += 8; return out; }
      case 0xd9: { const length = buffer[offset++]; return decoder.decode(take(length)); }
      case 0xda: { const length = buffer.readUInt16BE(offset); offset += 2; return decoder.decode(take(length)); }
      case 0xdb: { const length = buffer.readUInt32BE(offset); offset += 4; return decoder.decode(take(length)); }
      case 0xdc: { const length = buffer.readUInt16BE(offset); offset += 2; return Array.from({ length }, read); }
      case 0xdd: { const length = buffer.readUInt32BE(offset); offset += 4; return Array.from({ length }, read); }
      case 0xde: {
        const length = buffer.readUInt16BE(offset);
        offset += 2;
        const out = {};
        for (let i = 0; i < length; i++) out[read()] = read();
        return out;
      }
      case 0xdf: {
        const length = buffer.readUInt32BE(offset);
        offset += 4;
        const out = {};
        for (let i = 0; i < length; i++) out[read()] = read();
        return out;
      }
      default:
        throw new Error(`Unsupported MessagePack byte 0x${tag.toString(16)}.`);
    }
  };
  return read();
}
