const os = require("os");
const fs = require("fs");
const path = require("path");
const { execFile } = require("child_process");
const WebSocket = require("ws");

const RELAY_WSS_URL = (process.env.RELAY_WSS_URL || "").trim();
const FIREBASE_ID_TOKEN = (process.env.FIREBASE_ID_TOKEN || "").trim();
const PC_DEVICE_ID = (process.env.PC_DEVICE_ID || "win_workstation_01").trim();
const PC_DEVICE_NAME = (process.env.PC_DEVICE_NAME || os.hostname()).slice(0, 80);
const SAFE_WORKSPACE_DIR = path.resolve(process.env.SAFE_WORKSPACE_DIR || path.join(os.homedir(), "Documents", "DiyaWorkspace"));

if (!RELAY_WSS_URL.startsWith("wss://") || !FIREBASE_ID_TOKEN) {
  console.error("RELAY_WSS_URL (wss://...) and FIREBASE_ID_TOKEN are required. Agent exposes zero inbound ports.");
  process.exit(1);
}

// Strict allowlist — arbitrary shell execution and unrestricted file paths are forbidden.
function dispatchAllowlistedAction(msg) {
  const { commandId, actionType, targetScope, confirmed } = msg;

  switch (actionType) {
    case "WORKSPACE_STATUS_CHECK":
      return { commandId, status: "EXECUTED", detail: "Desktop agent healthy (outbound WSS active)." };

    case "OPEN_BROWSER_URL": {
      let parsed;
      try {
        parsed = new URL(String(targetScope || ""));
      } catch {
        return { commandId, status: "REJECTED", detail: "Invalid URL." };
      }
      if (parsed.protocol !== "https:") {
        return { commandId, status: "REJECTED", detail: "Only HTTPS URLs are permitted." };
      }
      // Uses safe non-shell launcher without shell: true
      execFile("rundll32.exe", ["url.dll,FileProtocolHandler", parsed.toString()], { shell: false });
      return { commandId, status: "EXECUTED", detail: "Opened allowlisted HTTPS URL." };
    }

    case "SEARCH_SCOPED_DOCUMENTS": {
      if (!fs.existsSync(SAFE_WORKSPACE_DIR)) {
        return { commandId, status: "EXECUTED", detail: "Scoped workspace folder does not exist yet." };
      }
      const files = fs.readdirSync(SAFE_WORKSPACE_DIR).slice(0, 25);
      return { commandId, status: "EXECUTED", detail: `Scoped files: ${files.join(", ")}` };
    }

    case "ARCHIVE_SCOPED_FILE": {
      if (confirmed !== true) {
        return { commandId, status: "REJECTED", detail: "Explicit confirmation required." };
      }
      const rawName = path.basename(String(targetScope || "").replace(/^scope:/, ""));
      const resolved = path.resolve(SAFE_WORKSPACE_DIR, rawName);
      if (!resolved.startsWith(SAFE_WORKSPACE_DIR + path.sep)) {
        return { commandId, status: "REJECTED", detail: "Path outside confined workspace denied." };
      }
      return { commandId, status: "EXECUTED", detail: `Verified safe workspace target: ${rawName}` };
    }

    default:
      return { commandId, status: "REJECTED", detail: "Action not in Windows agent allowlist." };
  }
}

// Outbound-only authenticated WSS connection (zero listening ports on Windows PC)
const ws = new WebSocket(`${RELAY_WSS_URL}/ws/agent?deviceId=${encodeURIComponent(PC_DEVICE_ID)}`, {
  headers: {
    Authorization: `Bearer ${FIREBASE_ID_TOKEN}`,
    "X-Device-Name": PC_DEVICE_NAME,
  },
});

ws.on("open", () => {
  console.log(`Authenticated outbound WSS connected as ${PC_DEVICE_NAME} (${PC_DEVICE_ID})`);
});

ws.on("message", (raw) => {
  try {
    const msg = JSON.parse(raw.toString());
    if (msg.type === "COMMAND_DISPATCH") {
      const ack = dispatchAllowlistedAction(msg);
      ws.send(JSON.stringify({ type: "COMMAND_ACK", ...ack }));
    }
  } catch {
    // Ignore malformed frames
  }
});

ws.on("close", (code, reason) => {
  console.log(`Outbound WSS closed (${code}): ${reason.toString()}`);
});
