const os = require("os");
const path = require("path");
const http = require("http");
const crypto = require("crypto");
const { execFile } = require("child_process");
const WebSocket = require("ws");
const { loadDotEnv, createLogger } = require("./lib/env");
const {
  computeBackoffDelayMs,
  isTokenExpiringSoon,
  isTokenExpired,
  loadSession,
  saveSession,
  clearSession,
} = require("./lib/session");
const { createActionDispatcher } = require("./lib/actions");

function normalizeRelayEndpoints(rawHttpsUrl, rawWssUrl, allowLoopbackHttp = false) {
  const httpsInput = String(rawHttpsUrl || "").trim().replace(/\/+$/, "");
  const wssInput = String(rawWssUrl || "").trim().replace(/\/+$/, "");

  let httpsBase = httpsInput;
  let wssBase = wssInput;

  if (!httpsBase && wssBase) {
    httpsBase = wssBase.replace(/^wss:\/\//i, "https://").replace(/^ws:\/\//i, "http://");
  }
  if (!wssBase && httpsBase) {
    wssBase = httpsBase.replace(/^https:\/\//i, "wss://").replace(/^http:\/\//i, "ws://");
  }

  if (!httpsBase || !wssBase) {
    throw new Error("RELAY_HTTPS_URL (https://...) or RELAY_WSS_URL (wss://...) is required.");
  }

  const parsedHttps = new URL(httpsBase);
  const parsedWss = new URL(wssBase);
  const isLoopback =
    allowLoopbackHttp &&
    (parsedHttps.hostname === "127.0.0.1" || parsedHttps.hostname === "localhost");

  if (!isLoopback && (parsedHttps.protocol !== "https:" || parsedWss.protocol !== "wss:")) {
    throw new Error("Encrypted HTTPS/WSS relay URLs are required (https://... and wss://...).");
  }

  return { httpsBase, wssBase };
}

function parseCliArgs(argv) {
  const args = { pair: false, code: "" };
  for (const item of argv) {
    if (item === "--pair") args.pair = true;
    if (item.startsWith("--code=")) args.code = item.slice("--code=".length).trim().toUpperCase();
  }
  return args;
}

async function redeemPairingCode({ httpsBase, bearerToken, code, pcDeviceId, pcDeviceName }) {
  const res = await fetch(`${httpsBase}/api/pairing/redeem`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${bearerToken}`,
    },
    body: JSON.stringify({ code, pcDeviceId, pcDeviceName }),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.error || `Pairing failed with HTTP ${res.status}`);
  }
  return body;
}

async function refreshAgentToken({ httpsBase, agentToken, pcDeviceId }) {
  const res = await fetch(`${httpsBase}/api/pairing/redeem`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${agentToken}`,
    },
    body: JSON.stringify({ grantType: "refresh", pcDeviceId }),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.error || `Token refresh failed with HTTP ${res.status}`);
  }
  return body;
}

function openUrlInLocalBrowser(urlString) {
  if (process.platform === "win32") {
    const child = execFile("rundll32.exe", ["url.dll,FileProtocolHandler", urlString], {
      shell: false,
      detached: true,
      stdio: "ignore",
    });
    child.unref?.();
    return true;
  }
  return false;
}

function waitForBrowserPairCallback({ webConsoleUrl, pcDeviceId, pcDeviceName, logger, timeoutMs = 5 * 60 * 1000 }) {
  const parsedConsole = new URL(String(webConsoleUrl || "").trim());
  if (parsedConsole.protocol !== "https:" && parsedConsole.hostname !== "localhost" && parsedConsole.hostname !== "127.0.0.1") {
    return Promise.reject(new Error("WEB_CONSOLE_URL must use HTTPS."));
  }
  const expectedOrigin = parsedConsole.origin;
  const csrfState = crypto.randomBytes(24).toString("hex");

  return new Promise((resolve, reject) => {
    let settled = false;
    const server = http.createServer((req, res) => {
      const origin = req.headers.origin;
      const corsOrigin = origin && origin === expectedOrigin ? origin : expectedOrigin;

      res.setHeader("Access-Control-Allow-Origin", corsOrigin);
      res.setHeader("Vary", "Origin");
      res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
      res.setHeader("Access-Control-Allow-Headers", "Content-Type");
      res.setHeader("Access-Control-Allow-Private-Network", "true");

      if (req.method === "OPTIONS") {
        res.writeHead(204);
        res.end();
        return;
      }

      if (req.method !== "POST" || req.url !== "/pair") {
        res.writeHead(404, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ error: "Not found" }));
        return;
      }

      let rawBody = "";
      req.on("data", (chunk) => {
        rawBody += chunk;
        if (rawBody.length > 32 * 1024) {
          req.destroy();
        }
      });

      req.on("end", () => {
        try {
          const parsed = JSON.parse(rawBody || "{}");
          if (
            parsed.state !== csrfState ||
            typeof parsed.agentToken !== "string" ||
            !parsed.agentToken.startsWith("diya_agent_v1.") ||
            typeof parsed.expiresAt !== "string"
          ) {
            res.writeHead(400, { "Content-Type": "application/json" });
            res.end(JSON.stringify({ error: "Invalid pairing payload or state mismatch." }));
            return;
          }
          res.writeHead(200, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ ok: true }));
          if (!settled) {
            settled = true;
            clearTimeout(timer);
            server.close();
            resolve({
              agentToken: parsed.agentToken,
              expiresAt: parsed.expiresAt,
              pcDeviceId: parsed.pcDeviceId || pcDeviceId,
            });
          }
        } catch {
          res.writeHead(400, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ error: "Malformed JSON." }));
        }
      });
    });

    const timer = setTimeout(() => {
      if (!settled) {
        settled = true;
        server.close();
        reject(new Error("Browser pairing timed out after 5 minutes."));
      }
    }, timeoutMs);
    timer.unref?.();

    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      const callbackUrl = `http://127.0.0.1:${address.port}/pair`;
      const pairUrl = new URL(parsedConsole.toString());
      pairUrl.searchParams.set("pairCallback", callbackUrl);
      pairUrl.searchParams.set("state", csrfState);
      pairUrl.searchParams.set("deviceId", pcDeviceId);
      pairUrl.searchParams.set("deviceName", pcDeviceName);

      logger.info("Local pairing listener active on 127.0.0.1 (closes automatically after pairing).");
      logger.info(`Open this link in your browser and sign in with Google to pair:\n${pairUrl.toString()}`);
      openUrlInLocalBrowser(pairUrl.toString());
    });

    server.on("error", (err) => {
      if (!settled) {
        settled = true;
        clearTimeout(timer);
        reject(err);
      }
    });
  });
}

function createWindowsAgent(config = {}) {
  const logger = config.logger || createLogger("DIYA-WIN-AGENT");
  const { httpsBase, wssBase } = normalizeRelayEndpoints(
    config.relayHttpsUrl,
    config.relayWssUrl,
    Boolean(config.allowLoopbackHttpForTest)
  );
  const pcDeviceId = String(config.pcDeviceId || "win_workstation_01").trim();
  const pcDeviceName = String(config.pcDeviceName || os.hostname()).trim().slice(0, 80);
  const sessionFilePath = config.sessionFilePath || path.join(__dirname, ".agent-session.json");
  const heartbeatIntervalMs = Number(config.heartbeatIntervalMs || 20000);
  const dispatchAction =
    config.dispatchAction ||
    createActionDispatcher({
      safeWorkspaceDir: config.safeWorkspaceDir,
      platform: config.platform,
    });

  let ws = null;
  let heartbeatTimer = null;
  let reconnectTimer = null;
  let reconnectAttempt = 0;
  let stopped = false;

  async function resolveActiveCredentials() {
    const saved = loadSession(sessionFilePath);
    if (saved && saved.agentToken) {
      if (isTokenExpired(saved.expiresAt)) {
        logger.warn("Stored agent session token has expired. Clearing expired session.");
        clearSession(sessionFilePath);
      } else if (isTokenExpiringSoon(saved.expiresAt)) {
        logger.info("Agent session token is nearing expiry; refreshing over HTTPS...");
        try {
          const refreshed = await refreshAgentToken({
            httpsBase,
            agentToken: saved.agentToken,
            pcDeviceId,
          });
          saveSession(sessionFilePath, {
            pcDeviceId,
            pcDeviceName,
            agentToken: refreshed.agentToken,
            expiresAt: refreshed.expiresAt,
          });
          return { token: refreshed.agentToken, authMode: "agent_token" };
        } catch (err) {
          logger.warn(`Token refresh warning: ${err.message}. Falling back to remaining TTL.`);
          return { token: saved.agentToken, authMode: "agent_token" };
        }
      } else {
        return { token: saved.agentToken, authMode: "agent_token" };
      }
    }

    if (config.agentToken) {
      return { token: String(config.agentToken).trim(), authMode: "agent_token" };
    }
    if (config.firebaseIdToken) {
      return { token: String(config.firebaseIdToken).trim(), authMode: "firebase_id_token" };
    }
    return null;
  }

  function stopTimers() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
  }

  function scheduleReconnect(reason) {
    if (stopped) return;
    const delayMs = computeBackoffDelayMs(reconnectAttempt, 1000, 60000);
    reconnectAttempt += 1;
    logger.warn(`Scheduling reconnect attempt #${reconnectAttempt} in ${delayMs}ms (${reason}).`);
    reconnectTimer = setTimeout(() => {
      connect().catch((err) => {
        logger.error(`Reconnect failed: ${err.message}`);
        scheduleReconnect(err.message);
      });
    }, delayMs);
    reconnectTimer.unref?.();
  }

  async function connect() {
    if (stopped) return;
    stopTimers();

    const creds = await resolveActiveCredentials();
    if (!creds) {
      throw new Error(
        "No valid agent session found. Run 'npm run pair' (or set PAIRING_CODE + FIREBASE_ID_TOKEN) first."
      );
    }

    const wsUrl = `${wssBase}/ws/agent?deviceId=${encodeURIComponent(pcDeviceId)}`;
    logger.info(`Connecting outbound WSS to ${wssBase}/ws/agent as ${pcDeviceName} (${pcDeviceId})...`);

    return new Promise((resolve) => {
      let opened = false;
      ws = new WebSocket(wsUrl, {
        headers: {
          Authorization: `Bearer ${creds.token}`,
          "X-Device-Name": pcDeviceName,
        },
      });

      ws.on("open", () => {
        opened = true;
        reconnectAttempt = 0;
        logger.info(`Authenticated outbound WSS connected as ${pcDeviceName} (${pcDeviceId}).`);

        const sendHeartbeat = () => {
          if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(
              JSON.stringify({
                type: "AGENT_HEARTBEAT",
                deviceId: pcDeviceId,
                deviceName: pcDeviceName,
                platform: process.platform,
                uptimeSeconds: Math.floor(process.uptime()),
                timestamp: new Date().toISOString(),
              })
            );
          }
        };

        sendHeartbeat();
        heartbeatTimer = setInterval(sendHeartbeat, heartbeatIntervalMs);
        heartbeatTimer.unref?.();
        resolve({ connected: true });
      });

      ws.on("message", (raw) => {
        try {
          const msg = JSON.parse(raw.toString());
          if (msg.type === "HEARTBEAT_ACK") {
            return;
          }
          if (msg.type === "AGENT_TOKEN_REFRESH" && msg.agentToken && msg.expiresAt) {
            saveSession(sessionFilePath, {
              pcDeviceId,
              pcDeviceName,
              agentToken: msg.agentToken,
              expiresAt: msg.expiresAt,
            });
            logger.info(`Expiring agent session token rotated (next expiry: ${msg.expiresAt}).`);
            return;
          }
          if (msg.type === "COMMAND_DISPATCH") {
            logger.info(`Received command ${msg.commandId} [${msg.actionType}]`);
            const ack = dispatchAction(msg);
            logger.info(`Command ${msg.commandId} -> ${ack.status}: ${ack.detail}`);
            if (ws && ws.readyState === WebSocket.OPEN) {
              ws.send(JSON.stringify({ type: "COMMAND_ACK", ...ack }));
            }
          }
        } catch {
          logger.warn("Ignored malformed relay message frame.");
        }
      });

      ws.on("unexpected-response", (_req, res) => {
        logger.error(`Relay rejected WSS upgrade with HTTP ${res.statusCode}.`);
        if (res.statusCode === 401) {
          clearSession(sessionFilePath);
          logger.error("Session rejected as unauthorized/expired. Please re-pair with 'npm run pair'.");
          stopped = true;
        }
        if (!opened) resolve({ connected: false, statusCode: res.statusCode });
      });

      ws.on("error", (err) => {
        logger.error(`Outbound WSS error: ${err.message}`);
        if (!opened) {
          resolve({ connected: false, error: err.message });
        }
      });

      ws.on("close", (code, reasonBuf) => {
        if (heartbeatTimer) {
          clearInterval(heartbeatTimer);
          heartbeatTimer = null;
        }
        const reason = reasonBuf ? reasonBuf.toString() : "";
        logger.warn(`Outbound WSS closed (${code}${reason ? `: ${reason}` : ""}).`);
        if (code === 4003) {
          logger.error("Device is not paired under this account. Run 'npm run pair' to pair this PC.");
          stopped = true;
          return;
        }
        if (code === 4000) {
          // Superseded by newer connection
          stopped = true;
          return;
        }
        if (!stopped) {
          scheduleReconnect(`close code ${code}`);
        }
      });
    });
  }

  function stop() {
    stopped = true;
    stopTimers();
    if (ws) {
      try {
        ws.close(1000, "Agent shutting down");
      } catch {
        // Ignore close error
      }
      ws = null;
    }
  }

  return {
    connect,
    stop,
    getSocket: () => ws,
  };
}

async function runCli() {
  loadDotEnv(path.join(__dirname, ".env"));
  const logger = createLogger("DIYA-WIN-AGENT");
  const args = parseCliArgs(process.argv.slice(2));

  const pcDeviceId = (process.env.PC_DEVICE_ID || "win_workstation_01").trim();
  const pcDeviceName = (process.env.PC_DEVICE_NAME || os.hostname()).trim().slice(0, 80);
  const sessionFilePath = path.join(__dirname, ".agent-session.json");
  const { httpsBase, wssBase } = normalizeRelayEndpoints(
    process.env.RELAY_HTTPS_URL,
    process.env.RELAY_WSS_URL
  );

  // Pairing flow via CLI code+token or Browser Callback
  const pairingCode = args.code || (process.env.PAIRING_CODE || "").trim().toUpperCase();
  const firebaseToken = (process.env.FIREBASE_ID_TOKEN || "").trim();

  if (pairingCode && firebaseToken) {
    logger.info(`Redeeming pairing code ${pairingCode} over HTTPS for ${pcDeviceName}...`);
    const paired = await redeemPairingCode({
      httpsBase,
      bearerToken: firebaseToken,
      code: pairingCode,
      pcDeviceId,
      pcDeviceName,
    });
    saveSession(sessionFilePath, {
      pcDeviceId: paired.pcDeviceId || pcDeviceId,
      pcDeviceName,
      agentToken: paired.agentToken,
      expiresAt: paired.expiresAt,
    });
    logger.info(`Device paired successfully! Expiring session saved (expires: ${paired.expiresAt}).`);
  } else if (args.pair) {
    const webConsoleUrl = (process.env.WEB_CONSOLE_URL || "").trim();
    if (!webConsoleUrl) {
      throw new Error("WEB_CONSOLE_URL is required in .env for browser pairing ('npm run pair').");
    }
    const paired = await waitForBrowserPairCallback({
      webConsoleUrl,
      pcDeviceId,
      pcDeviceName,
      logger,
    });
    saveSession(sessionFilePath, {
      pcDeviceId: paired.pcDeviceId || pcDeviceId,
      pcDeviceName,
      agentToken: paired.agentToken,
      expiresAt: paired.expiresAt,
    });
    logger.info(`Browser pairing completed! Expiring session saved (expires: ${paired.expiresAt}).`);
  }

  const agent = createWindowsAgent({
    relayHttpsUrl: httpsBase,
    relayWssUrl: wssBase,
    pcDeviceId,
    pcDeviceName,
    safeWorkspaceDir: process.env.SAFE_WORKSPACE_DIR,
    agentToken: process.env.AGENT_TOKEN,
    firebaseIdToken: process.env.FIREBASE_ID_TOKEN,
    sessionFilePath,
    logger,
  });

  process.on("SIGINT", () => {
    logger.info("Received SIGINT, closing DIYA Windows Agent...");
    agent.stop();
    process.exit(0);
  });

  process.on("SIGTERM", () => {
    logger.info("Received SIGTERM, closing DIYA Windows Agent...");
    agent.stop();
    process.exit(0);
  });

  await agent.connect();
}

if (require.main === module) {
  runCli().catch((err) => {
    const logger = createLogger("DIYA-WIN-AGENT");
    logger.error(err.message || String(err));
    process.exit(1);
  });
}

module.exports = {
  normalizeRelayEndpoints,
  redeemPairingCode,
  refreshAgentToken,
  createWindowsAgent,
};
