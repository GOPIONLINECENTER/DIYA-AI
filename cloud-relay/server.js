const http = require("http");
const express = require("express");
const cors = require("cors");
const { WebSocketServer } = require("ws");
const admin = require("firebase-admin");
const { GoogleGenAI } = require("@google/genai");

admin.initializeApp();

const DATABASE_ID =
  process.env.FIREBASE_DATABASE_ID ||
  "ai-studio-android-diyaai-96dc77ad-8481-4285-b7fd-2dc01f2813b9";
const db = admin.firestore();
db.settings({ databaseId: DATABASE_ID });

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

const allowedOrigins = (process.env.ALLOWED_ORIGINS || "")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);

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
    req.user = await admin.auth().verifyIdToken(token);
    return next();
  } catch (err) {
    return res.status(401).json({ error: "Invalid or expired Firebase ID token." });
  }
}

app.get("/health", (_req, res) => {
  res.json({ ok: true, service: "diya-ai-cloud-relay", databaseId: DATABASE_ID });
});

// Server-side Gemini API endpoint (GEMINI_API_KEY never reaches mobile or React clients)
app.post("/api/ai/respond", requireFirebaseAuth, async (req, res) => {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return res.status(503).json({ error: "Server Gemini API key is not configured." });
  }
  const prompt = typeof req.body?.prompt === "string" ? req.body.prompt.trim().slice(0, 2000) : "";
  const language = typeof req.body?.language === "string" ? req.body.language.slice(0, 16) : "bn-IN";
  if (!prompt) {
    return res.status(400).json({ error: "Prompt is required." });
  }

  try {
    const ai = new GoogleGenAI({ apiKey });
    const response = await ai.models.generateContent({
      model: "gemini-2.5-flash",
      contents: prompt,
      config: {
        systemInstruction:
          `You are DIYA AI, a polite, intelligent personal assistant. Primary language: Bengali (bn-IN), also fluent in English and Hindi. Respond concisely in the user's requested language (${language}). Never fabricate device actions.`,
      },
    });
    return res.json({ reply: response.text || "" });
  } catch (err) {
    return res.status(500).json({ error: "Server AI generation failed." });
  }
});

// Windows agent redeems an active temporary pairing code over HTTPS
app.post("/api/pairing/redeem", requireFirebaseAuth, async (req, res) => {
  const uid = req.user.uid;
  const code = typeof req.body?.code === "string" ? req.body.code.trim().toUpperCase() : "";
  const pcDeviceId = typeof req.body?.pcDeviceId === "string" ? req.body.pcDeviceId.trim() : "";
  const pcDeviceName = typeof req.body?.pcDeviceName === "string" ? req.body.pcDeviceName.trim().slice(0, 80) : "";

  if (!/^[A-Z0-9\-]{6,16}$/.test(code) || !/^[a-zA-Z0-9_\-]{4,128}$/.test(pcDeviceId) || !pcDeviceName) {
    return res.status(400).json({ error: "Invalid pairing payload." });
  }

  const snap = await db
    .collection("users")
    .doc(uid)
    .collection("pairingCodes")
    .where("code", "==", code)
    .where("status", "==", "ACTIVE")
    .limit(1)
    .get();

  if (snap.empty) {
    return res.status(404).json({ error: "Active pairing code not found." });
  }

  const doc = snap.docs[0];
  const data = doc.data();
  const expiresMs = data.expiresAt?.toDate ? data.expiresAt.toDate().getTime() : 0;
  if (Date.now() > expiresMs) {
    await doc.ref.update({ status: "EXPIRED", updatedAt: admin.firestore.FieldValue.serverTimestamp() });
    return res.status(410).json({ error: "Pairing code has expired." });
  }

  await doc.ref.update({ status: "CONSUMED", updatedAt: admin.firestore.FieldValue.serverTimestamp() });
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

  return res.json({ paired: true, pcDeviceId, mobileDeviceId: data.mobileDeviceId });
});

// Active authenticated WebSocket registries scoped by uid
const mobileSocketsByUid = new Map();
const pcSocketsByUid = new Map();

function broadcastPcStatusToMobiles(uid, pcOnline, deviceName = "Windows Desktop") {
  const sockets = mobileSocketsByUid.get(uid);
  if (!sockets) return;
  const payload = JSON.stringify({ type: "PC_STATUS", pcOnline, deviceName });
  for (const ws of sockets) {
    if (ws.readyState === ws.OPEN) ws.send(payload);
  }
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
    const decoded = await admin.auth().verifyIdToken(token);
    const url = new URL(req.url, "https://localhost");
    const role = url.pathname === "/ws/agent" ? "WINDOWS_DESKTOP" : url.pathname === "/ws/mobile" ? "ANDROID_MOBILE" : null;
    const deviceId = url.searchParams.get("deviceId") || "";

    if (!role || !/^[a-zA-Z0-9_\-]{4,128}$/.test(deviceId)) {
      socket.write("HTTP/1.1 400 Bad Request\r\n\r\n");
      socket.destroy();
      return;
    }

    wss.handleUpgrade(req, socket, head, (ws) => {
      wss.emit("connection", ws, { uid: decoded.uid, role, deviceId, deviceName: req.headers["x-device-name"] || role });
    });
  } catch {
    socket.write("HTTP/1.1 401 Unauthorized\r\n\r\n");
    socket.destroy();
  }
});

wss.on("connection", async (ws, meta) => {
  const { uid, role, deviceId, deviceName } = meta;

  if (role === "ANDROID_MOBILE") {
    if (!mobileSocketsByUid.has(uid)) mobileSocketsByUid.set(uid, new Set());
    mobileSocketsByUid.get(uid).add(ws);

    const activePc = pcSocketsByUid.get(uid);
    ws.send(
      JSON.stringify({
        type: "PC_STATUS",
        pcOnline: Boolean(activePc && activePc.ws.readyState === activePc.ws.OPEN),
        deviceName: activePc?.deviceName || "Windows Desktop",
      })
    );

    ws.on("message", async (raw) => {
      try {
        const msg = JSON.parse(raw.toString());
        if (msg.type !== "COMMAND_DISPATCH") return;
        if (!ALLOWED_ACTIONS.has(msg.actionType)) return;
        if (SENSITIVE_ACTIONS.has(msg.actionType) && msg.confirmed !== true) return;

        const targetPc = pcSocketsByUid.get(uid);
        if (!targetPc || targetPc.ws.readyState !== targetPc.ws.OPEN) {
          ws.send(JSON.stringify({ type: "COMMAND_ACK", commandId: msg.commandId, status: "PC_OFFLINE_NOT_SENT" }));
          return;
        }
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
    const deviceDoc = await db.collection("users").doc(uid).collection("devices").doc(deviceId).get();
    if (!deviceDoc.exists || deviceDoc.data()?.pairingStatus !== "PAIRED") {
      ws.close(4003, "Desktop device is not paired.");
      return;
    }

    pcSocketsByUid.set(uid, { ws, deviceId, deviceName: String(deviceName).slice(0, 80) });
    await deviceDoc.ref.update({
      connectionStatus: "ONLINE",
      lastConnectedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    broadcastPcStatusToMobiles(uid, true, String(deviceName).slice(0, 80));

    ws.on("message", (raw) => {
      try {
        const msg = JSON.parse(raw.toString());
        if (msg.type === "COMMAND_ACK") {
          const mobiles = mobileSocketsByUid.get(uid);
          if (mobiles) {
            for (const m of mobiles) {
              if (m.readyState === m.OPEN) m.send(JSON.stringify(msg));
            }
          }
        }
      } catch {
        // Ignore malformed frames
      }
    });

    ws.on("close", async () => {
      pcSocketsByUid.delete(uid);
      await deviceDoc.ref.update({
        connectionStatus: "OFFLINE",
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      broadcastPcStatusToMobiles(uid, false, String(deviceName).slice(0, 80));
    });
  }
});

const port = parseInt(process.env.PORT || "8080", 10);
server.listen(port, () => {
  console.log(`DIYA AI secure Cloud Run relay listening on port ${port}`);
});
