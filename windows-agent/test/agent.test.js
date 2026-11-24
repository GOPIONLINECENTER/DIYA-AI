const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const os = require("os");
const path = require("path");
const WebSocket = require("ws");

const { createRelayServer, ALLOWED_ACTIONS } = require("../../cloud-relay/server");
const { createSignedAgentToken } = require("../../cloud-relay/tokenUtils");
const {
  redeemPairingCode,
  refreshAgentToken,
  createWindowsAgent,
  normalizeRelayEndpoints,
} = require("../agent");
const { createActionDispatcher } = require("../lib/actions");
const { computeBackoffDelayMs, loadSession, saveSession } = require("../lib/session");
const { redactSensitiveText } = require("../lib/env");

function createInMemoryRelayStore() {
  const pairingCodes = new Map();
  const devices = new Map();
  const agentSessions = new Map();
  const commands = new Map();
  const commandListeners = new Map();

  return {
    pairingCodes,
    devices,
    agentSessions,
    commands,
    serverTimestamp: () => new Date().toISOString(),

    async findActivePairingCode(uid, code) {
      for (const [key, item] of pairingCodes.entries()) {
        if (item.uid === uid && item.code === code && item.status === "ACTIVE") {
          return { id: key, data: item, ref: { key } };
        }
      }
      return null;
    },

    async updatePairingCodeStatus(ref, status) {
      const existing = pairingCodes.get(ref.key);
      if (existing) existing.status = status;
    },

    async upsertPairedPcDevice(uid, pcDeviceId, pcDeviceName) {
      devices.set(`${uid}_${pcDeviceId}`, {
        userId: uid,
        deviceType: "WINDOWS_DESKTOP",
        deviceName: pcDeviceName,
        connectionStatus: "STANDBY",
        pairingStatus: "PAIRED",
        allowedActions: Array.from(ALLOWED_ACTIONS),
        lastConnectedAt: new Date().toISOString(),
      });
    },

    async getDevice(uid, deviceId) {
      const data = devices.get(`${uid}_${deviceId}`);
      return data ? { id: deviceId, data } : null;
    },

    async updateDeviceStatus(uid, deviceId, fields) {
      const key = `${uid}_${deviceId}`;
      const existing = devices.get(key) || {};
      devices.set(key, { ...existing, ...fields });
    },

    async saveAgentSession(uid, deviceId, session) {
      agentSessions.set(`${uid}_${deviceId}`, {
        uid,
        deviceId,
        tokenHash: session.tokenHash,
        expiresAtMs: session.exp,
        revoked: false,
      });
    },

    async getAgentSession(uid, deviceId) {
      return agentSessions.get(`${uid}_${deviceId}`) || null;
    },

    subscribeApprovedCommands(uid, onCommand) {
      commandListeners.set(uid, onCommand);
      return () => commandListeners.delete(uid);
    },

    triggerCommand(uid, cmd) {
      commands.set(`${uid}_${cmd.id}`, cmd);
      const listener = commandListeners.get(uid);
      if (listener && cmd.status === "APPROVED_QUEUED") {
        listener(cmd);
      }
    },

    async updateCommandStatus(uid, commandId, status) {
      const key = `${uid}_${commandId}`;
      const existing = commands.get(key);
      if (existing) existing.status = status;
    },
  };
}

test("Approved Windows action dispatcher executes allowlisted actions safely without shell", () => {
  const spawned = [];
  const tmpRoot = fs.mkdtempSync(path.join(os.tmpdir(), "diya-agent-test-"));
  const workspaceDir = path.join(tmpRoot, "DiyaWorkspace");
  const docsDir = path.join(tmpRoot, "Documents");
  fs.mkdirSync(workspaceDir, { recursive: true });
  fs.mkdirSync(docsDir, { recursive: true });
  fs.writeFileSync(path.join(workspaceDir, "note.txt"), "hello diya");

  const dispatcher = createActionDispatcher({
    platform: "win32",
    homeDir: tmpRoot,
    safeWorkspaceDir: workspaceDir,
    existsSync: (p) => p === "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe" || fs.existsSync(p),
    spawnDetached: (cmd, args) => spawned.push({ cmd, args }),
  });

  // 1. Open YouTube
  const yt = dispatcher({ commandId: "c1", actionType: "OPEN_BROWSER_URL", targetScope: "https://www.youtube.com" });
  assert.equal(yt.status, "EXECUTED");
  assert.equal(spawned.at(-1).cmd, "rundll32.exe");
  assert.equal(spawned.at(-1).args[1], "https://www.youtube.com/");

  // 2. Open Chrome
  const chrome = dispatcher({ commandId: "c2", actionType: "OPEN_BROWSER_URL", targetScope: "app:chrome" });
  assert.equal(chrome.status, "EXECUTED");
  assert.match(spawned.at(-1).cmd, /chrome\.exe$/i);

  // 3. Open WhatsApp Web
  const wa = dispatcher({ commandId: "c3", actionType: "OPEN_BROWSER_URL", targetScope: "https://web.whatsapp.com" });
  assert.equal(wa.status, "EXECUTED");
  assert.equal(spawned.at(-1).args[1], "https://web.whatsapp.com/");

  // 4. Open Notepad & Calculator
  assert.equal(dispatcher({ commandId: "c4", actionType: "OPEN_BROWSER_URL", targetScope: "app:notepad" }).status, "EXECUTED");
  assert.match(spawned.at(-1).cmd, /notepad\.exe$/i);
  assert.equal(dispatcher({ commandId: "c5", actionType: "OPEN_BROWSER_URL", targetScope: "app:calculator" }).status, "EXECUTED");
  assert.match(spawned.at(-1).cmd, /calc\.exe$/i);

  // 5. Open approved folder (Documents) & block unapproved folder
  const docs = dispatcher({ commandId: "c6", actionType: "SEARCH_SCOPED_DOCUMENTS", targetScope: "folder:documents" });
  assert.equal(docs.status, "EXECUTED");
  assert.equal(spawned.at(-1).cmd, "explorer.exe");
  assert.equal(spawned.at(-1).args[0], docsDir);

  const badFolder = dispatcher({ commandId: "c7", actionType: "SEARCH_SCOPED_DOCUMENTS", targetScope: "folder:system32" });
  assert.equal(badFolder.status, "REJECTED");

  // 6. Block arbitrary shell / private URLs / unconfirmed sensitive actions
  assert.equal(dispatcher({ commandId: "c8", actionType: "RUN_SHELL", targetScope: "cmd /c dir" }).status, "REJECTED");
  assert.equal(dispatcher({ commandId: "c9", actionType: "OPEN_BROWSER_URL", targetScope: "http://127.0.0.1:8080" }).status, "REJECTED");
  assert.equal(dispatcher({ commandId: "c10", actionType: "DRAFT_MESSAGE_PREVIEW", targetScope: "channel:whatsapp_draft", confirmed: false }).status, "REJECTED");
  assert.equal(dispatcher({ commandId: "c11", actionType: "ARCHIVE_SCOPED_FILE", targetScope: "scope:note.txt", confirmed: false }).status, "REJECTED");

  // 7. Confirmed sensitive archive works inside workspace only
  const archived = dispatcher({ commandId: "c12", actionType: "ARCHIVE_SCOPED_FILE", targetScope: "scope:note.txt", confirmed: true });
  assert.equal(archived.status, "EXECUTED");
  assert.equal(fs.existsSync(path.join(workspaceDir, "_archive", "note.txt")), true);

  fs.rmSync(tmpRoot, { recursive: true, force: true });
});

test("Session backoff, URL enforcement, and token redaction behave securely", () => {
  assert.throws(() => normalizeRelayEndpoints("http://insecure.example.com", ""));
  const ok = normalizeRelayEndpoints("https://relay.example.com/", "");
  assert.equal(ok.httpsBase, "https://relay.example.com");
  assert.equal(ok.wssBase, "wss://relay.example.com");

  const d0 = computeBackoffDelayMs(0, 1000, 60000, 0);
  const d3 = computeBackoffDelayMs(3, 1000, 60000, 0);
  const d10 = computeBackoffDelayMs(10, 1000, 60000, 0);
  assert.equal(d0, 1000);
  assert.equal(d3, 8000);
  assert.equal(d10, 60000);

  const redacted = redactSensitiveText("Header Authorization: Bearer diya_agent_v1.abc.def");
  assert.equal(redacted.includes("diya_agent_v1.abc.def"), false);
  assert.match(redacted, /\[REDACTED\]/);
});

test("End-to-end Cloud Relay <-> Windows Agent pairing, heartbeat, token refresh, and command execution", async () => {
  const store = createInMemoryRelayStore();
  const uid = "user_diya_001";
  const pcDeviceId = "win_pc_test_01";
  const pcDeviceName = "Test Windows 11 PC";
  const agentTokenSecret = "test_hmac_secret_32_bytes_minimum_1234";

  store.pairingCodes.set("pair_1", {
    uid,
    code: "DIYA-8K4M9P",
    mobileDeviceId: "web_console",
    mobileDeviceName: "DIYA Web Console",
    status: "ACTIVE",
    expiresAt: Date.now() + 5 * 60 * 1000,
  });

  const { server } = createRelayServer({
    store,
    agentTokenSecret,
    agentTokenTtlSeconds: 120, // short TTL so refresh window triggers cleanly
    verifyFirebaseIdToken: async (token) => {
      if (token === "valid_firebase_token") return { uid, exp: Math.floor(Date.now() / 1000) + 3600 };
      throw new Error("Invalid token");
    },
    allowedOrigins: "https://diya.example.com",
  });

  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const port = server.address().port;
  const httpsBase = `http://127.0.0.1:${port}`;
  const wssBase = `ws://127.0.0.1:${port}`;

  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "diya-e2e-"));
  const sessionFilePath = path.join(tmpDir, ".agent-session.json");

  try {
    // 1. Health endpoint
    const healthRes = await fetch(`${httpsBase}/health`);
    assert.equal(healthRes.status, 200);

    // 2. Redeem pairing code -> receives expiring agentToken
    const pairResult = await redeemPairingCode({
      httpsBase,
      bearerToken: "valid_firebase_token",
      code: "DIYA-8K4M9P",
      pcDeviceId,
      pcDeviceName,
    });
    assert.equal(pairResult.paired, true);
    assert.ok(pairResult.agentToken.startsWith("diya_agent_v1."));
    assert.ok(Date.parse(pairResult.expiresAt) > Date.now());

    saveSession(sessionFilePath, {
      pcDeviceId,
      pcDeviceName,
      agentToken: pairResult.agentToken,
      expiresAt: pairResult.expiresAt,
    });
    assert.equal(loadSession(sessionFilePath).pcDeviceId, pcDeviceId);

    // 3. Expired token is rejected by relay
    const expiredToken = createSignedAgentToken({
      uid,
      deviceId: pcDeviceId,
      secret: agentTokenSecret,
      ttlSeconds: -60,
    }).token;
    await assert.rejects(
      () => refreshAgentToken({ httpsBase, agentToken: expiredToken, pcDeviceId }),
      /401|expired|Invalid/i
    );

    // 4. Connect mobile client to observe live PC_STATUS and COMMAND_ACK
    const mobileFrames = [];
    const mobileWs = new WebSocket(`${wssBase}/ws/mobile?deviceId=android_01`, {
      headers: { Authorization: "Bearer valid_firebase_token" },
    });
    await new Promise((resolve) => mobileWs.on("open", resolve));
    mobileWs.on("message", (raw) => mobileFrames.push(JSON.parse(raw.toString())));

    // 5. Connect real Windows Agent over WSS
    const executedLog = [];
    const agent = createWindowsAgent({
      relayHttpsUrl: httpsBase,
      relayWssUrl: wssBase,
      allowLoopbackHttpForTest: true,
      pcDeviceId,
      pcDeviceName,
      sessionFilePath,
      heartbeatIntervalMs: 150,
      dispatchAction: (msg) => {
        executedLog.push(msg);
        if (msg.actionType === "DRAFT_MESSAGE_PREVIEW" && msg.confirmed !== true) {
          return { commandId: msg.commandId, status: "REJECTED", detail: "Confirmation required." };
        }
        return { commandId: msg.commandId, status: "EXECUTED", detail: `Executed ${msg.actionType}` };
      },
    });

    const connResult = await agent.connect();
    assert.equal(connResult.connected, true);

    // Wait briefly for heartbeat + status updates
    await new Promise((r) => setTimeout(r, 250));

    assert.equal(store.devices.get(`${uid}_${pcDeviceId}`).connectionStatus, "ONLINE");
    assert.ok(mobileFrames.some((f) => f.type === "PC_STATUS" && f.pcOnline === true));
    assert.ok(mobileFrames.some((f) => f.type === "AGENT_HEARTBEAT" && f.pcOnline === true));

    // 6. Dispatch command via Firestore APPROVED_QUEUED bridge
    store.triggerCommand(uid, {
      id: "cmd_yt_01",
      actionType: "OPEN_BROWSER_URL",
      targetScope: "https://www.youtube.com",
      confirmationRequired: false,
      confirmed: false,
      status: "APPROVED_QUEUED",
    });

    await new Promise((r) => setTimeout(r, 150));
    assert.equal(executedLog.length, 1);
    assert.equal(executedLog[0].commandId, "cmd_yt_01");
    assert.equal(store.commands.get(`${uid}_cmd_yt_01`).status, "EXECUTED");
    assert.ok(mobileFrames.some((f) => f.type === "COMMAND_ACK" && f.commandId === "cmd_yt_01" && f.status === "EXECUTED"));

    // 7. Sensitive unconfirmed command via mobile WSS is rejected before reaching PC
    mobileWs.send(
      JSON.stringify({
        type: "COMMAND_DISPATCH",
        commandId: "cmd_sens_01",
        actionType: "DRAFT_MESSAGE_PREVIEW",
        targetScope: "channel:whatsapp_draft",
        confirmationRequired: true,
        confirmed: false,
      })
    );
    await new Promise((r) => setTimeout(r, 100));
    assert.equal(executedLog.length, 1); // Never dispatched to PC
    assert.ok(mobileFrames.some((f) => f.type === "COMMAND_ACK" && f.commandId === "cmd_sens_01" && f.status === "REJECTED"));

    // 8. Disconnect Windows Agent -> PC transitions back to OFFLINE
    agent.stop();
    await new Promise((r) => setTimeout(r, 150));
    assert.equal(store.devices.get(`${uid}_${pcDeviceId}`).connectionStatus, "OFFLINE");
    assert.ok(mobileFrames.some((f) => f.type === "PC_STATUS" && f.pcOnline === false));

    mobileWs.close();
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
    await new Promise((resolve) => server.close(resolve));
  }
});
