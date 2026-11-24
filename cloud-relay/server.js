const http = require("http");
const crypto = require("crypto");
const express = require("express");
const cors = require("cors");
const { WebSocketServer } = require("ws");
const admin = require("firebase-admin");
const { GoogleGenAI } = require("@google/genai");
const {
  TOKEN_PREFIX,
  createSignedAgentToken,
  verifySignedAgentToken,
  timingSafeHexEqual,
} = require("./tokenUtils");

const DEFAULT_DATABASE_ID =
  "ai-studio-android-diyaai-96dc77ad-8481-4285-b7fd-2dc01f2813b9";

const ALLOWED_ACTIONS = new Set([
  "OPEN_BROWSER_URL",
  "SEARCH_SCOPED_DOCUMENTS",
  "MEDIA_PLAYBACK_CONTROL",
  "WORKSPACE_STATUS_CHECK",
  "DRAFT_MESSAGE_PREVIEW",
  "ARCHIVE_SCOPED_FILE",
]);

const SENSITIVE_ACTIONS = new Set([
  "DRAFT_MESSAGE_PREVIEW",
  "ARCHIVE_SCOPED_FILE",
]);

const HEARTBEAT_SWEEP_MS = 30 * 1000;
const HEARTBEAT_TIMEOUT_MS = 70 * 1000;
const TOKEN_REFRESH_WINDOW_MS = 6 * 60 * 60 * 1000;

function createFirestoreStore(db) {
  return {
    serverTimestamp: () => admin.firestore.FieldValue.serverTimestamp(),

    async findActivePairingCode(uid, code) {
      const snap = await db
        .collection("users")
        .doc(uid)
        .collection("pairingCodes")
        .where("code", "==", code)
        .where("status", "==", "ACTIVE")
        .limit(1)
        .get();
      if (snap.empty) return null;
      const doc = snap.docs[0];
      return { id: doc.id, data: doc.data(), ref: doc.ref };
    },

    async updatePairingCodeStatus(ref, status) {
      await ref.update({
        status,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    },

    async upsertPairedPcDevice(uid, pcDeviceId, pcDeviceName) {
      await db
        .collection("users")
        .doc(uid)
        .collection("devices")
        .doc(pcDeviceId)
        .set(
          {
            userId: uid,
            deviceType: "WINDOWS_DESKTOP",
            deviceName: pcDeviceName,
            connectionStatus: "STANDBY",
            pairingStatus: "PAIRED",
            allowedActions: Array.from(ALLOWED_ACTIONS),
            lastConnectedAt: admin.firestore.FieldValue.serverTimestamp(),
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          },
          { merge: true }
        );
    },

    async getDevice(uid, deviceId) {
      const doc = await db
        .collection("users")
        .doc(uid)
        .collection("devices")
        .doc(deviceId)
        .get();
      if (!doc.exists) return null;
      return { id: doc.id, data: doc.data(), ref: doc.ref };
    },

    async updateDeviceStatus(uid, deviceId, fields) {
      await db
        .collection("users")
        .doc(uid)
        .collection("devices")
        .doc(deviceId)
        .update({
          ...fields,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
    },

    async saveAgentSession(uid, deviceId, session) {
      await db
        .collection("serverAgentSessions")
        .doc(`${uid}_${deviceId}`)
        .set({
          uid,
          deviceId,
          tokenHash: session.tokenHash,
          expiresAtMs: session.exp,
          revoked: false,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
    },

    async getAgentSession(uid, deviceId) {
      const doc = await db
        .collection("serverAgentSessions")
        .doc(`${uid}_${deviceId}`)
        .get();
      return doc.exists ? doc.data() : null;
    },

    subscribeApprovedCommands(uid, onCommand) {
      return db
        .collection("users")
        .doc(uid)
        .collection("commandRequests")
        .where("status", "==", "APPROVED_QUEUED")
        .onSnapshot(
          (snap) => {
            snap.docChanges().forEach((change) => {
              if (change.type === "added" || change.type === "modified") {
                onCommand({ id: change.doc.id, ...change.doc.data() });
              }
            });
          },
          (err) => {
            console.warn(`Command listener warning for user ${uid}:`, err?.message || err);
          }
        );
    },

    async updateCommandStatus(uid, commandId, status) {
      if (!/^[a-zA-Z0-9_\-]{1,128}$/.test(String(commandId || ""))) return;
      const docRef = db
        .collection("users")
        .doc(uid)
        .collection("commandRequests")
        .doc(commandId);
      const snap = await docRef.get();
      if (!snap.exists) return;
      await docRef.update({
        status,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    },
  };
}

function createRelayServer(options = {}) {
  const databaseId =
    options.databaseId ||
    process.env.FIREBASE_DATABASE_ID ||
    DEFAULT_DATABASE_ID;

  let store = options.store;
  let verifyFirebaseIdToken = options.verifyFirebaseIdToken;
  let generateAiReply = options.generateAiReply;

  if (!store || !verifyFirebaseIdToken) {
    if (!admin.apps.length) {
      admin.initializeApp();
    }
    if (!store) {
      const db = admin.firestore();
      db.settings({ databaseId });
      store = createFirestoreStore(db);
    }
    if (!verifyFirebaseIdToken) {
      verifyFirebaseIdToken = (token) => admin.auth().verifyIdToken(token);
    }
  }

  if (!generateAiReply) {
    generateAiReply = async (prompt, language) => {
      const apiKey = process.env.GEMINI_API_KEY;
      if (!apiKey) {
        const err = new Error("Server Gemini API key is not configured.");
        err.statusCode = 503;
        throw err;
      }
      const ai = new GoogleGenAI({ apiKey });
      const response = await ai.models.generateContent({
        model: "gemini-2.5-flash",
        contents: prompt,
        config: {
          systemInstruction:
            `You are DIYA AI, a polite, intelligent personal assistant. Primary language: Bengali (bn-IN), also fluent in English and Hindi. Respond concisely in the user's requested language (${language}). Never fabricate device actions.`,
        },
      });
      return response.text || "";
    };
  }

  const agentTokenSecret =
    options.agentTokenSecret ||
    process.env.AGENT_TOKEN_SECRET ||
    crypto.randomBytes(32).toString("hex");
  const agentTokenTtlSeconds = parseInt(
    options.agentTokenTtlSeconds || process.env.AGENT_TOKEN_TTL_SECONDS || String(24 * 60 * 60),
    10
  );

  const allowedOrigins = (
    options.allowedOrigins !== undefined
      ? options.allowedOrigins
      : process.env.ALLOWED_ORIGINS || ""
  )
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);

  // Simple sliding-window rate limiter for /api/pairing/redeem
  const redeemAttemptsByIp = new Map();
  function isRedeemRateLimited(ip) {
    const now = Date.now();
    const windowMs = 60 * 1000;
    const recent = (redeemAttemptsByIp.get(ip) || []).filter((ts) => now - ts < windowMs);
    if (recent.length >= 20) {
      redeemAttemptsByIp.set(ip, recent);
      return true;
    }
    recent.push(now);
    redeemAttemptsByIp.set(ip, recent);
    return false;
  }

  async function issueAndStoreAgentToken(uid, deviceId) {
    const issued = createSignedAgentToken({
      uid,
      deviceId,
      secret: agentTokenSecret,
      ttlSeconds: agentTokenTtlSeconds,
    });
    await store.saveAgentSession(uid, deviceId, issued);
    return issued;
  }

  async function verifyAgentBearerToken(token, expectedDeviceId) {
    if (typeof token === "string" && token.startsWith(`${TOKEN_PREFIX}.`)) {
      const verified = verifySignedAgentToken(token, agentTokenSecret);
      if (!verified || verified.expired) {
        return null;
      }
      const { uid, deviceId, exp } = verified.payload;
      if (expectedDeviceId && deviceId !== expectedDeviceId) {
        return null;
      }
      const storedSession = await store.getAgentSession(uid, deviceId);
      if (
        !storedSession ||
        storedSession.revoked === true ||
        typeof storedSession.expiresAtMs !== "number" ||
        Date.now() >= storedSession.expiresAtMs ||
        !timingSafeHexEqual(verified.tokenHash, storedSession.tokenHash)
      ) {
        return null;
      }
      return { uid, deviceId, authType: "agent_token", exp };
    }

    const decoded = await verifyFirebaseIdToken(token);
    return {
      uid: decoded.uid,
      deviceId: expectedDeviceId || "",
      authType: "firebase_id_token",
      exp: (decoded.exp || 0) * 1000,
    };
  }

  const app = express();
  app.set("trust proxy", 1);
  app.use(express.json({ limit: "256kb" }));
  app.use(
    cors({
      origin: (origin, callback) => {
        if (!origin || allowedOrigins.includes(origin)) {
          return callback(null, true);
        }
        return callback(new Error("Origin not permitted by CORS policy"));
      },
      credentials: true,
      allowedHeaders: ["Content-Type", "Authorization", "X-Device-Name"],
    })
  );

  // Enforce HTTPS when running behind Cloud Run TLS termination
  app.use((req, res, next) => {
    const proto = req.header("x-forwarded-proto");
    if (process.env.NODE_ENV === "production" && proto && proto !== "https") {
      return res.status(403).json({ error: "Encrypted HTTPS transport required." });
    }
    return next();
  });

  async function requireFirebaseAuth(req, res, next) {
    const authHeader = req.header("Authorization") || "";
    const token = authHeader.startsWith("Bearer ") ? authHeader.slice(7).trim() : "";
    if (!token) {
      return res.status(401).json({ error: "Missing Firebase ID bearer token." });
    }
    try {
      req.user = await verifyFirebaseIdToken(token);
      return next();
    } catch {
      return res.status(401).json({ error: "Invalid or expired Firebase ID token." });
    }
  }

  app.get("/health", (_req, res) => {
    res.json({ ok: true, service: "diya-ai-cloud-relay", databaseId });
  });

  // Server-side Gemini API endpoint (GEMINI_API_KEY never reaches mobile or React clients)
  app.post("/api/ai/respond", requireFirebaseAuth, async (req, res) => {
    const prompt = typeof req.body?.prompt === "string" ? req.body.prompt.trim().slice(0, 2000) : "";
    const language = typeof req.body?.language === "string" ? req.body.language.slice(0, 16) : "bn-IN";
    if (!prompt) {
      return res.status(400).json({ error: "Prompt is required." });
    }

    try {
      const reply = await generateAiReply(prompt, language);
      return res.json({ reply });
    } catch (err) {
      if (err?.statusCode === 503) {
        return res.status(503).json({ error: err.message });
      }
      return res.status(500).json({ error: "Server AI generation failed." });
    }
  });

  // Windows agent redeems an active temporary pairing code (or refreshes an active session) over HTTPS
  app.post("/api/pairing/redeem", async (req, res) => {
    const clientIp = req.ip || req.socket?.remoteAddress || "unknown";
    if (isRedeemRateLimited(clientIp)) {
      return res.status(429).json({ error: "Too many pairing attempts. Try again shortly." });
    }

    const authHeader = req.header("Authorization") || "";
    const bearerToken = authHeader.startsWith("Bearer ") ? authHeader.slice(7).trim() : "";
    if (!bearerToken) {
      return res.status(401).json({ error: "Missing authentication bearer token." });
    }

    const pcDeviceId = typeof req.body?.pcDeviceId === "string" ? req.body.pcDeviceId.trim() : "";
    if (!/^[a-zA-Z0-9_\-]{4,128}$/.test(pcDeviceId)) {
      return res.status(400).json({ error: "Invalid pcDeviceId format." });
    }

    // Support expiring agent token rotation via existing /api/pairing/redeem route
    if (req.body?.grantType === "refresh") {
      try {
        const authResult = await verifyAgentBearerToken(bearerToken, pcDeviceId);
        if (!authResult) {
          return res.status(401).json({ error: "Invalid or expired agent session token." });
        }
        const existingDevice = await store.getDevice(authResult.uid, pcDeviceId);
        if (!existingDevice || existingDevice.data?.pairingStatus !== "PAIRED") {
          return res.status(403).json({ error: "Desktop device is not paired." });
        }
        const issued = await issueAndStoreAgentToken(authResult.uid, pcDeviceId);
        return res.json({
          paired: true,
          pcDeviceId,
          agentToken: issued.token,
          expiresAt: issued.expiresAt,
        });
      } catch {
        return res.status(401).json({ error: "Token refresh failed." });
      }
    }

    let firebaseUser;
    try {
      firebaseUser = await verifyFirebaseIdToken(bearerToken);
    } catch {
      return res.status(401).json({ error: "Invalid or expired Firebase ID token." });
    }

    const uid = firebaseUser.uid;
    const code = typeof req.body?.code === "string" ? req.body.code.trim().toUpperCase() : "";
    const pcDeviceName = typeof req.body?.pcDeviceName === "string" ? req.body.pcDeviceName.trim().slice(0, 80) : "";

    if (!/^[A-Z0-9\-]{6,16}$/.test(code) || !pcDeviceName) {
      return res.status(400).json({ error: "Invalid pairing payload." });
    }

    const pairingMatch = await store.findActivePairingCode(uid, code);
    if (!pairingMatch) {
      return res.status(404).json({ error: "Active pairing code not found." });
    }

    const data = pairingMatch.data;
    const expiresMs = data.expiresAt?.toDate
      ? data.expiresAt.toDate().getTime()
      : typeof data.expiresAt === "number"
        ? data.expiresAt
        : 0;
    if (Date.now() > expiresMs) {
      await store.updatePairingCodeStatus(pairingMatch.ref, "EXPIRED");
      return res.status(410).json({ error: "Pairing code has expired." });
    }

    await store.updatePairingCodeStatus(pairingMatch.ref, "CONSUMED");
    await store.upsertPairedPcDevice(uid, pcDeviceId, pcDeviceName);
    const issued = await issueAndStoreAgentToken(uid, pcDeviceId);

    return res.json({
      paired: true,
      pcDeviceId,
      mobileDeviceId: data.mobileDeviceId,
      agentToken: issued.token,
      expiresAt: issued.expiresAt,
    });
  });

  // Active authenticated WebSocket registries scoped by uid
  const mobileSocketsByUid = new Map();
  const pcSocketsByUid = new Map();

  function broadcastToMobiles(uid, payloadObj) {
    const sockets = mobileSocketsByUid.get(uid);
    if (!sockets) return;
    const payload = JSON.stringify(payloadObj);
    for (const ws of sockets) {
      if (ws.readyState === ws.OPEN) ws.send(payload);
    }
  }

  function broadcastPcStatusToMobiles(uid, pcOnline, deviceName = "Windows Desktop", extra = {}) {
    broadcastToMobiles(uid, {
      type: "PC_STATUS",
      pcOnline,
      deviceName,
      ...extra,
    });
  }

  const server = http.createServer(app);
  const wss = new WebSocketServer({ noServer: true });

  server.on("upgrade", async (req, socket, head) => {
    try {
      const authHeader = req.headers.authorization || "";
      const token = authHeader.startsWith("Bearer ") ? authHeader.slice(7).trim() : "";
      if (!token) {
        socket.write("HTTP/1.1 401 Unauthorized\r\n\r\n");
        socket.destroy();
        return;
      }

      const url = new URL(req.url, "https://localhost");
      const role =
        url.pathname === "/ws/agent"
          ? "WINDOWS_DESKTOP"
          : url.pathname === "/ws/mobile"
            ? "ANDROID_MOBILE"
            : null;
      const deviceId = url.searchParams.get("deviceId") || "";

      if (!role || !/^[a-zA-Z0-9_\-]{4,128}$/.test(deviceId)) {
        socket.write("HTTP/1.1 400 Bad Request\r\n\r\n");
        socket.destroy();
        return;
      }

      let uid = "";
      let authMeta = null;

      if (role === "WINDOWS_DESKTOP") {
        authMeta = await verifyAgentBearerToken(token, deviceId);
        if (!authMeta) {
          socket.write("HTTP/1.1 401 Unauthorized\r\n\r\n");
          socket.destroy();
          return;
        }
        uid = authMeta.uid;
      } else {
        const decoded = await verifyFirebaseIdToken(token);
        uid = decoded.uid;
        authMeta = { uid, deviceId, authType: "firebase_id_token", exp: (decoded.exp || 0) * 1000 };
      }

      wss.handleUpgrade(req, socket, head, (ws) => {
        wss.emit("connection", ws, {
          uid,
          role,
          deviceId,
          deviceName: String(req.headers["x-device-name"] || role).slice(0, 80),
          authMeta,
        });
      });
    } catch {
      socket.write("HTTP/1.1 401 Unauthorized\r\n\r\n");
      socket.destroy();
    }
  });

  wss.on("connection", async (ws, meta) => {
    const { uid, role, deviceId, deviceName, authMeta } = meta;

    if (role === "ANDROID_MOBILE") {
      if (!mobileSocketsByUid.has(uid)) mobileSocketsByUid.set(uid, new Set());
      mobileSocketsByUid.get(uid).add(ws);

      const activePc = pcSocketsByUid.get(uid);
      ws.send(
        JSON.stringify({
          type: "PC_STATUS",
          pcOnline: Boolean(activePc && activePc.ws.readyState === activePc.ws.OPEN),
          deviceName: activePc?.deviceName || "Windows Desktop",
          deviceId: activePc?.deviceId || "",
          lastHeartbeatAt: activePc?.lastHeartbeatIso || null,
        })
      );

      ws.on("message", async (raw) => {
        try {
          const msg = JSON.parse(raw.toString());
          if (msg.type !== "COMMAND_DISPATCH") return;
          if (!ALLOWED_ACTIONS.has(msg.actionType)) return;
          if ((SENSITIVE_ACTIONS.has(msg.actionType) || msg.confirmationRequired === true) && msg.confirmed !== true) {
            ws.send(
              JSON.stringify({
                type: "COMMAND_ACK",
                commandId: msg.commandId,
                status: "REJECTED",
                detail: "Explicit confirmation required for sensitive action.",
              })
            );
            return;
          }

          const targetPc = pcSocketsByUid.get(uid);
          if (!targetPc || targetPc.ws.readyState !== targetPc.ws.OPEN) {
            ws.send(
              JSON.stringify({
                type: "COMMAND_ACK",
                commandId: msg.commandId,
                status: "PC_OFFLINE_NOT_SENT",
              })
            );
            return;
          }
          if (msg.commandId) targetPc.dispatchedCommandIds.add(msg.commandId);
          targetPc.ws.send(JSON.stringify(msg));
        } catch {
          // Ignore malformed frames
        }
      });

      ws.on("close", () => {
        mobileSocketsByUid.get(uid)?.delete(ws);
      });
    } else if (role === "WINDOWS_DESKTOP") {
      // Verify Windows device is already paired under this uid before marking ONLINE
      const deviceRecord = await store.getDevice(uid, deviceId);
      if (!deviceRecord || deviceRecord.data?.pairingStatus !== "PAIRED") {
        ws.close(4003, "Desktop device is not paired.");
        return;
      }

      const previousPc = pcSocketsByUid.get(uid);
      if (previousPc && previousPc.ws !== ws) {
        try {
          previousPc.unsubscribeCommands?.();
          previousPc.ws.close(4000, "Superseded by new agent connection.");
        } catch {
          // Ignore close error on stale socket
        }
      }

      const dispatchedCommandIds = new Set();
      let lastHeartbeatFirestoreWrite = Date.now();
      const nowIso = new Date().toISOString();

      const pcEntry = {
        ws,
        uid,
        deviceId,
        deviceName,
        authMeta,
        lastSeenAt: Date.now(),
        lastHeartbeatIso: nowIso,
        dispatchedCommandIds,
        unsubscribeCommands: null,
      };
      pcSocketsByUid.set(uid, pcEntry);

      await store.updateDeviceStatus(uid, deviceId, {
        deviceName,
        connectionStatus: "ONLINE",
        lastConnectedAt: store.serverTimestamp(),
      });

      broadcastPcStatusToMobiles(uid, true, deviceName, {
        deviceId,
        lastHeartbeatAt: nowIso,
      });

      // Bridge Firestore APPROVED_QUEUED commands (e.g. from web-console or mobile offline queue) to live PC socket
      pcEntry.unsubscribeCommands = store.subscribeApprovedCommands(uid, (cmd) => {
        if (ws.readyState !== ws.OPEN) return;
        if (!cmd?.id || dispatchedCommandIds.has(cmd.id)) return;
        if (cmd.targetDeviceId && cmd.targetDeviceId !== deviceId) return;
        if (!ALLOWED_ACTIONS.has(cmd.actionType)) return;
        if ((SENSITIVE_ACTIONS.has(cmd.actionType) || cmd.confirmationRequired === true) && cmd.confirmed !== true) {
          return;
        }
        dispatchedCommandIds.add(cmd.id);
        ws.send(
          JSON.stringify({
            type: "COMMAND_DISPATCH",
            commandId: cmd.id,
            actionType: cmd.actionType,
            targetScope: String(cmd.targetScope || ""),
            confirmed: Boolean(cmd.confirmed),
            confirmationRequired: Boolean(cmd.confirmationRequired),
          })
        );
      });

      ws.on("pong", () => {
        pcEntry.lastSeenAt = Date.now();
      });

      ws.on("message", async (raw) => {
        try {
          const msg = JSON.parse(raw.toString());
          pcEntry.lastSeenAt = Date.now();

          if (msg.type === "AGENT_HEARTBEAT") {
            const hbIso = new Date().toISOString();
            pcEntry.lastHeartbeatIso = hbIso;
            ws.send(JSON.stringify({ type: "HEARTBEAT_ACK", serverTime: hbIso }));
            broadcastToMobiles(uid, {
              type: "AGENT_HEARTBEAT",
              deviceId,
              deviceName,
              pcOnline: true,
              lastHeartbeatAt: hbIso,
            });

            if (Date.now() - lastHeartbeatFirestoreWrite >= 60 * 1000) {
              lastHeartbeatFirestoreWrite = Date.now();
              store
                .updateDeviceStatus(uid, deviceId, {
                  connectionStatus: "ONLINE",
                  lastConnectedAt: store.serverTimestamp(),
                })
                .catch(() => {});
            }

            const refreshWindowMs = Math.min(
              TOKEN_REFRESH_WINDOW_MS,
              Math.floor(agentTokenTtlSeconds * 1000 * 0.25)
            );
            if (
              pcEntry.authMeta?.authType === "agent_token" &&
              pcEntry.authMeta?.exp - Date.now() <= refreshWindowMs &&
              !pcEntry.refreshInFlight
            ) {
              pcEntry.refreshInFlight = true;
              issueAndStoreAgentToken(uid, deviceId)
                .then((issued) => {
                  pcEntry.authMeta.exp = issued.exp;
                  if (ws.readyState === ws.OPEN) {
                    ws.send(
                      JSON.stringify({
                        type: "AGENT_TOKEN_REFRESH",
                        agentToken: issued.token,
                        expiresAt: issued.expiresAt,
                      })
                    );
                  }
                })
                .catch(() => {})
                .finally(() => {
                  pcEntry.refreshInFlight = false;
                });
            }
            return;
          }

          if (msg.type === "COMMAND_ACK") {
            broadcastToMobiles(uid, msg);
            if (msg.commandId && (msg.status === "EXECUTED" || msg.status === "REJECTED")) {
              store.updateCommandStatus(uid, msg.commandId, msg.status).catch(() => {});
            }
          }
        } catch {
          // Ignore malformed frames
        }
      });

      ws.on("close", async () => {
        pcEntry.unsubscribeCommands?.();
        const current = pcSocketsByUid.get(uid);
        if (current && current.ws === ws) {
          pcSocketsByUid.delete(uid);
          await store
            .updateDeviceStatus(uid, deviceId, {
              connectionStatus: "OFFLINE",
            })
            .catch(() => {});
          broadcastPcStatusToMobiles(uid, false, deviceName, { deviceId });
        }
      });
    }
  });

  const heartbeatSweepMs = options.heartbeatSweepMs || HEARTBEAT_SWEEP_MS;
  const heartbeatTimeoutMs = options.heartbeatTimeoutMs || HEARTBEAT_TIMEOUT_MS;
  const sweepTimer = setInterval(() => {
    const now = Date.now();
    for (const [, pcEntry] of pcSocketsByUid.entries()) {
      if (now - pcEntry.lastSeenAt > heartbeatTimeoutMs) {
        try {
          pcEntry.ws.terminate();
        } catch {
          // Ignore terminate error
        }
      } else if (pcEntry.ws.readyState === pcEntry.ws.OPEN) {
        try {
          pcEntry.ws.ping();
        } catch {
          // Ignore ping error
        }
      }
    }
  }, heartbeatSweepMs);
  sweepTimer.unref?.();

  server.on("close", () => {
    clearInterval(sweepTimer);
  });

  return { app, server, wss, pcSocketsByUid, mobileSocketsByUid };
}

if (require.main === module) {
  const { server } = createRelayServer();
  const port = parseInt(process.env.PORT || "8080", 10);
  server.listen(port, () => {
    console.log(`DIYA AI secure Cloud Run relay listening on port ${port}`);
  });
}

module.exports = {
  createRelayServer,
  ALLOWED_ACTIONS,
  SENSITIVE_ACTIONS,
};
