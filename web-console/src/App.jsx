import React, { useEffect, useMemo, useState } from "react";
import { initializeApp } from "firebase/app";
import {
  getAuth,
  GoogleAuthProvider,
  onAuthStateChanged,
  signInWithPopup,
  signOut,
} from "firebase/auth";
import {
  addDoc,
  collection,
  doc,
  getFirestore,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  Timestamp,
  updateDoc,
} from "firebase/firestore";
import { parseSafeVoiceCommand } from "./voiceCommands.js";

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "gen-lang-client-0530773790",
};

const RELAY_HTTPS_URL = (import.meta.env.VITE_CLOUD_RELAY_HTTPS_URL || "")
  .trim()
  .replace(/\/+$/, "");

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(
  app,
  import.meta.env.VITE_FIRESTORE_DATABASE_ID ||
    "ai-studio-android-diyaai-96dc77ad-8481-4285-b7fd-2dc01f2813b9"
);

function generateCryptoPairingCode() {
  const alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
  const bytes = new Uint8Array(8);
  crypto.getRandomValues(bytes);
  let raw = "";
  for (let i = 0; i < bytes.length; i += 1) {
    raw += alphabet[bytes[i] % alphabet.length];
  }
  return `DIYA-${raw.slice(0, 4)}${raw.slice(4, 8)}`;
}

function isValidLocalPairCallback(rawUrl) {
  try {
    const parsed = new URL(rawUrl);
    return (
      parsed.protocol === "http:" &&
      (parsed.hostname === "127.0.0.1" || parsed.hostname === "localhost") &&
      parsed.pathname === "/pair" &&
      Boolean(parsed.port)
    );
  } catch {
    return false;
  }
}

function formatTimestamp(ts) {
  if (!ts) return "Never";
  if (typeof ts.toDate === "function") return ts.toDate().toLocaleString();
  const parsed = new Date(ts);
  return Number.isNaN(parsed.getTime()) ? "Unknown" : parsed.toLocaleString();
}

const cardStyle = {
  background: "rgba(10, 18, 36, 0.88)",
  border: "1px solid rgba(0, 229, 255, 0.25)",
  borderRadius: 16,
  padding: 20,
  marginBottom: 18,
  boxShadow: "0 0 24px rgba(0, 229, 255, 0.08)",
};

const buttonStyle = {
  background: "rgba(0, 229, 255, 0.14)",
  color: "#00E5FF",
  border: "1px solid rgba(0, 229, 255, 0.45)",
  borderRadius: 10,
  padding: "9px 14px",
  cursor: "pointer",
  fontWeight: 600,
  fontSize: 13,
};

const primaryButtonStyle = {
  ...buttonStyle,
  background: "linear-gradient(135deg, #00E5FF, #2979FF)",
  color: "#030712",
  border: "none",
};

export default function App() {
  const [user, setUser] = useState(null);
  const [devices, setDevices] = useState([]);
  const [commands, setCommands] = useState([]);

  const [pairingCodeInfo, setPairingCodeInfo] = useState(null);
  const [pairingBusy, setPairingBusy] = useState(false);
  const [pairingStatusMsg, setPairingStatusMsg] = useState("");

  const [commandInput, setCommandInput] = useState("");
  const [selectedFolder, setSelectedFolder] = useState("folder:documents");
  const [listeningVoice, setListeningVoice] = useState(false);
  const [feedbackMsg, setFeedbackMsg] = useState("");
  const [errorMsg, setErrorMsg] = useState("");

  const pairRequest = useMemo(() => {
    const params = new URLSearchParams(window.location.search);
    const callback = params.get("pairCallback") || "";
    const state = params.get("state") || "";
    const deviceId = params.get("deviceId") || "win_workstation_01";
    const deviceName = params.get("deviceName") || "Windows Workstation";
    if (isValidLocalPairCallback(callback) && /^[a-f0-9]{16,128}$/i.test(state)) {
      return { callback, state, deviceId, deviceName };
    }
    return null;
  }, []);

  useEffect(() => onAuthStateChanged(auth, setUser), []);

  useEffect(() => {
    if (!user) {
      setDevices([]);
      setCommands([]);
      return undefined;
    }
    const unsubDevices = onSnapshot(
      collection(db, "users", user.uid, "devices"),
      (snap) => setDevices(snap.docs.map((d) => ({ id: d.id, ...d.data() })))
    );
    const unsubCommands = onSnapshot(
      query(
        collection(db, "users", user.uid, "commandRequests"),
        orderBy("createdAt", "desc")
      ),
      (snap) => setCommands(snap.docs.map((d) => ({ id: d.id, ...d.data() })))
    );
    return () => {
      unsubDevices();
      unsubCommands();
    };
  }, [user]);

  const pcDevice = devices.find((d) => d.deviceType === "WINDOWS_DESKTOP");
  const pcOnline = pcDevice?.connectionStatus === "ONLINE";

  const handleGeneratePairingCode = async () => {
    if (!user) return;
    setErrorMsg("");
    setPairingBusy(true);
    try {
      const code = generateCryptoPairingCode();
      const expiresAt = Timestamp.fromMillis(Date.now() + 10 * 60 * 1000);
      await addDoc(collection(db, "users", user.uid, "pairingCodes"), {
        userId: user.uid,
        code,
        mobileDeviceId: "web_console",
        mobileDeviceName: "DIYA Web Console",
        status: "ACTIVE",
        expiresAt,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      setPairingCodeInfo({ code, expiresAt: expiresAt.toDate() });
      setPairingStatusMsg("Temporary pairing code generated (valid for 10 minutes).");
    } catch (err) {
      setErrorMsg(err?.message || "Failed to generate pairing code.");
    } finally {
      setPairingBusy(false);
    }
  };

  const handleApproveLocalAgentPair = async () => {
    if (!user || !pairRequest) return;
    setErrorMsg("");
    setPairingStatusMsg("");
    if (!RELAY_HTTPS_URL || !RELAY_HTTPS_URL.startsWith("https://")) {
      setErrorMsg("VITE_CLOUD_RELAY_HTTPS_URL is not configured in web-console environment.");
      return;
    }
    setPairingBusy(true);
    try {
      const code = generateCryptoPairingCode();
      const expiresAt = Timestamp.fromMillis(Date.now() + 10 * 60 * 1000);
      await addDoc(collection(db, "users", user.uid, "pairingCodes"), {
        userId: user.uid,
        code,
        mobileDeviceId: "web_console",
        mobileDeviceName: "DIYA Web Console",
        status: "ACTIVE",
        expiresAt,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });

      const idToken = await user.getIdToken(true);
      const redeemRes = await fetch(`${RELAY_HTTPS_URL}/api/pairing/redeem`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${idToken}`,
        },
        body: JSON.stringify({
          code,
          pcDeviceId: pairRequest.deviceId,
          pcDeviceName: pairRequest.deviceName,
        }),
      });
      const redeemData = await redeemRes.json().catch(() => ({}));
      if (!redeemRes.ok) {
        throw new Error(redeemData.error || `Relay pairing error (HTTP ${redeemRes.status})`);
      }

      const callbackRes = await fetch(pairRequest.callback, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          state: pairRequest.state,
          pcDeviceId: redeemData.pcDeviceId || pairRequest.deviceId,
          agentToken: redeemData.agentToken,
          expiresAt: redeemData.expiresAt,
        }),
      });
      if (!callbackRes.ok) {
        throw new Error("Could not deliver pairing confirmation to local Windows agent listener.");
      }

      setPairingStatusMsg(
        `Windows PC "${pairRequest.deviceName}" paired successfully. You may return to your agent terminal.`
      );
      window.history.replaceState({}, document.title, window.location.pathname);
    } catch (err) {
      setErrorMsg(err?.message || "Windows agent pairing failed.");
    } finally {
      setPairingBusy(false);
    }
  };

  const submitCommand = async ({
    actionType,
    targetScope,
    summaryText,
    confirmationRequired = false,
  }) => {
    if (!user) return;
    setErrorMsg("");
    setFeedbackMsg("");

    const initialStatus = confirmationRequired
      ? "PENDING_CONFIRMATION"
      : pcOnline
        ? "APPROVED_QUEUED"
        : "PC_OFFLINE_NOT_SENT";

    try {
      await addDoc(collection(db, "users", user.uid, "commandRequests"), {
        userId: user.uid,
        sourceDeviceId: "web_console",
        targetDeviceId: pcDevice?.id || "win_workstation_01",
        actionType,
        summaryText: summaryText.slice(0, 300),
        targetScope: targetScope.slice(0, 300),
        confirmationRequired,
        confirmed: false,
        status: initialStatus,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });

      if (confirmationRequired) {
        setFeedbackMsg(`Confirmation required: approve "${summaryText}" in Command Requests below.`);
      } else if (pcOnline) {
        setFeedbackMsg(`Dispatched to connected Windows PC: ${summaryText}`);
      } else {
        setFeedbackMsg(`Windows PC is currently OFFLINE — action marked PC_OFFLINE_NOT_SENT.`);
      }
    } catch (err) {
      setErrorMsg(err?.message || "Failed to create command request.");
    }
  };

  const handleCommandFormSubmit = async (e) => {
    e.preventDefault();
    const parsed = parseSafeVoiceCommand(commandInput);
    if (!parsed.allowed) {
      setErrorMsg(parsed.reason);
      return;
    }
    setCommandInput("");
    await submitCommand(parsed);
  };

  const handleVoiceMic = () => {
    setErrorMsg("");
    const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRec) {
      setErrorMsg("Voice recognition is not supported in this browser. Type a command instead.");
      return;
    }
    const recognition = new SpeechRec();
    recognition.lang = "en-US";
    recognition.interimResults = false;
    recognition.maxAlternatives = 1;

    setListeningVoice(true);
    recognition.onresult = async (event) => {
      const transcript = event.results?.[0]?.[0]?.transcript || "";
      setCommandInput(transcript);
      const parsed = parseSafeVoiceCommand(transcript);
      if (!parsed.allowed) {
        setErrorMsg(`Voice input "${transcript}" rejected: ${parsed.reason}`);
      } else {
        await submitCommand(parsed);
      }
    };
    recognition.onerror = () => {
      setErrorMsg("Voice capture failed or microphone permission denied.");
      setListeningVoice(false);
    };
    recognition.onend = () => {
      setListeningVoice(false);
    };
    recognition.start();
  };

  const confirmCommand = async (cmdId) => {
    if (!user) return;
    setErrorMsg("");
    await updateDoc(doc(db, "users", user.uid, "commandRequests", cmdId), {
      confirmed: true,
      status: pcOnline ? "APPROVED_QUEUED" : "PC_OFFLINE_NOT_SENT",
      updatedAt: serverTimestamp(),
    });
  };

  const cancelCommand = async (cmdId) => {
    if (!user) return;
    setErrorMsg("");
    await updateDoc(doc(db, "users", user.uid, "commandRequests", cmdId), {
      status: "CANCELLED",
      updatedAt: serverTimestamp(),
    });
  };

  return (
    <main
      style={{
        background: "radial-gradient(circle at top, #0B1633 0%, #030712 65%)",
        color: "#F0F6FF",
        minHeight: "100vh",
        padding: 24,
        fontFamily: "system-ui, -apple-system, sans-serif",
      }}
    >
      <div style={{ maxWidth: 960, margin: "0 auto" }}>
        <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 12, marginBottom: 24 }}>
          <div>
            <h1 style={{ color: "#00E5FF", letterSpacing: 3, margin: 0, fontSize: 24 }}>
              DIYA AI — REMOTE CONNECTION CONSOLE
            </h1>
            <p style={{ color: "#8FA8C8", margin: "4px 0 0", fontSize: 12, letterSpacing: 1.5 }}>
              YOUR PERSONAL INTELLIGENT ASSISTANT · ENCRYPTED HTTPS/WSS BRIDGE
            </p>
          </div>
          {user && (
            <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
              <span style={{ fontSize: 13, color: "#B8D0EC" }}>{user.email || user.uid}</span>
              <button style={buttonStyle} onClick={() => signOut(auth)}>
                Sign Out
              </button>
            </div>
          )}
        </header>

        {!user ? (
          <section style={cardStyle}>
            <h2 style={{ marginTop: 0, color: "#00E5FF" }}>Authenticate to Access DIYA AI</h2>
            <p style={{ color: "#B8D0EC", fontSize: 14 }}>
              Sign in with Google to pair your Windows 10/11 agent, view live PC status, and authorize safe desktop actions.
            </p>
            <button
              style={primaryButtonStyle}
              onClick={() => signInWithPopup(auth, new GoogleAuthProvider())}
            >
              Sign in with Google
            </button>
          </section>
        ) : (
          <>
            {pairRequest && (
              <section style={{ ...cardStyle, borderColor: "#00E5FF" }}>
                <h2 style={{ marginTop: 0, color: "#00E5FF" }}>
                  Windows Agent Pairing Request Detected
                </h2>
                <p style={{ margin: "6px 0 14px", color: "#D6E6FA", fontSize: 14 }}>
                  Local Windows PC <strong>{pairRequest.deviceName}</strong> (<code>{pairRequest.deviceId}</code>) is requesting an expiring authenticated session.
                </p>
                <button
                  style={primaryButtonStyle}
                  disabled={pairingBusy}
                  onClick={handleApproveLocalAgentPair}
                >
                  {pairingBusy ? "Pairing..." : "Approve & Pair Windows PC"}
                </button>
              </section>
            )}

            {errorMsg && (
              <div style={{ ...cardStyle, borderColor: "#FF5252", color: "#FFB4B4" }}>
                {errorMsg}
              </div>
            )}
            {feedbackMsg && (
              <div style={{ ...cardStyle, borderColor: "#00E676", color: "#B9F6CA" }}>
                {feedbackMsg}
              </div>
            )}
            {pairingStatusMsg && (
              <div style={{ ...cardStyle, borderColor: "#00E5FF", color: "#B2F5FF" }}>
                {pairingStatusMsg}
              </div>
            )}

            {/* Real PC Status & Pairing Card */}
            <section style={cardStyle}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 12 }}>
                <div>
                  <h2 style={{ margin: 0, fontSize: 18 }}>
                    PC Status:{" "}
                    <span style={{ color: pcOnline ? "#00E676" : "#FF8A65" }}>
                      {pcOnline ? "ONLINE (Authenticated Agent Active)" : "OFFLINE (Waiting for Outbound Agent)"}
                    </span>
                  </h2>
                  <p style={{ margin: "6px 0 0", fontSize: 13, color: "#8FA8C8" }}>
                    Device: {pcDevice?.deviceName || "Not paired yet"} · Pairing:{" "}
                    {pcDevice?.pairingStatus || "UNPAIRED"} · Last Connected:{" "}
                    {formatTimestamp(pcDevice?.lastConnectedAt)}
                  </p>
                </div>
                <button
                  style={buttonStyle}
                  disabled={pairingBusy}
                  onClick={handleGeneratePairingCode}
                >
                  {pairingBusy ? "Generating..." : "Generate Pairing Code"}
                </button>
              </div>

              {pairingCodeInfo && (
                <div style={{ marginTop: 14, padding: 12, borderRadius: 10, background: "rgba(0, 229, 255, 0.08)", border: "1px dashed rgba(0, 229, 255, 0.4)" }}>
                  <div style={{ fontSize: 13, color: "#B8D0EC" }}>Temporary Pairing Code (expires {pairingCodeInfo.expiresAt.toLocaleTimeString()}):</div>
                  <div style={{ fontSize: 22, fontWeight: 700, letterSpacing: 3, color: "#00E5FF", marginTop: 4 }}>
                    {pairingCodeInfo.code}
                  </div>
                </div>
              )}

              {devices.length > 0 && (
                <ul style={{ marginTop: 14, paddingLeft: 18, color: "#D6E6FA", fontSize: 13 }}>
                  {devices.map((d) => (
                    <li key={d.id} style={{ marginBottom: 4 }}>
                      <strong>{d.deviceName}</strong> ({d.deviceType}) — {d.connectionStatus} / {d.pairingStatus}
                      {d.lastConnectedAt ? ` · Last seen ${formatTimestamp(d.lastConnectedAt)}` : ""}
                    </li>
                  ))}
                </ul>
              )}
            </section>

            {/* Safe Voice & Approved Quick Actions */}
            <section style={cardStyle}>
              <h2 style={{ marginTop: 0, fontSize: 18, color: "#00E5FF" }}>
                Approved Windows Actions & Safe Voice Commands
              </h2>
              <form onSubmit={handleCommandFormSubmit} style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 14 }}>
                <input
                  type="text"
                  value={commandInput}
                  onChange={(e) => setCommandInput(e.target.value)}
                  placeholder='Speak or type (e.g., "Open YouTube", "Open Notepad", "Open Downloads")'
                  style={{
                    flex: "1 1 280px",
                    padding: "10px 12px",
                    borderRadius: 10,
                    border: "1px solid rgba(0, 229, 255, 0.35)",
                    background: "#050B1A",
                    color: "#F0F6FF",
                    fontSize: 14,
                  }}
                />
                <button type="button" style={buttonStyle} onClick={handleVoiceMic}>
                  {listeningVoice ? "Listening..." : "Voice Mic"}
                </button>
                <button type="submit" style={primaryButtonStyle}>
                  Run Safe Command
                </button>
              </form>

              <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginBottom: 12 }}>
                <button
                  style={buttonStyle}
                  onClick={() =>
                    submitCommand({
                      actionType: "OPEN_BROWSER_URL",
                      targetScope: "https://www.youtube.com",
                      summaryText: "Open YouTube in desktop browser",
                    })
                  }
                >
                  Open YouTube
                </button>
                <button
                  style={buttonStyle}
                  onClick={() =>
                    submitCommand({
                      actionType: "OPEN_BROWSER_URL",
                      targetScope: "app:chrome",
                      summaryText: "Open Google Chrome on Windows PC",
                    })
                  }
                >
                  Open Chrome
                </button>
                <button
                  style={buttonStyle}
                  onClick={() =>
                    submitCommand({
                      actionType: "OPEN_BROWSER_URL",
                      targetScope: "https://web.whatsapp.com",
                      summaryText: "Open WhatsApp Web in desktop browser",
                    })
                  }
                >
                  Open WhatsApp Web
                </button>
                <button
                  style={buttonStyle}
                  onClick={() =>
                    submitCommand({
                      actionType: "OPEN_BROWSER_URL",
                      targetScope: "app:notepad",
                      summaryText: "Open Notepad on Windows PC",
                    })
                  }
                >
                  Open Notepad
                </button>
                <button
                  style={buttonStyle}
                  onClick={() =>
                    submitCommand({
                      actionType: "OPEN_BROWSER_URL",
                      targetScope: "app:calculator",
                      summaryText: "Open Calculator on Windows PC",
                    })
                  }
                >
                  Open Calculator
                </button>
                <button
                  style={buttonStyle}
                  onClick={() =>
                    submitCommand({
                      actionType: "WORKSPACE_STATUS_CHECK",
                      targetScope: "telemetry:status",
                      summaryText: "Check Windows Agent Heartbeat",
                    })
                  }
                >
                  Check PC Health
                </button>
              </div>

              <div style={{ display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center" }}>
                <select
                  aria-label="Approved Windows folder"
                  value={selectedFolder}
                  onChange={(e) => setSelectedFolder(e.target.value)}
                  style={{
                    padding: "9px 12px",
                    borderRadius: 10,
                    border: "1px solid rgba(0, 229, 255, 0.35)",
                    background: "#050B1A",
                    color: "#F0F6FF",
                    fontSize: 13,
                  }}
                >
                  <option value="folder:documents">Documents Folder</option>
                  <option value="folder:downloads">Downloads Folder</option>
                  <option value="folder:desktop">Desktop Folder</option>
                  <option value="folder:pictures">Pictures Folder</option>
                  <option value="folder:workspace">DiyaWorkspace Folder</option>
                </select>
                <button
                  style={buttonStyle}
                  onClick={() =>
                    submitCommand({
                      actionType: "SEARCH_SCOPED_DOCUMENTS",
                      targetScope: selectedFolder,
                      summaryText: `Open approved ${selectedFolder.replace("folder:", "")} folder`,
                    })
                  }
                >
                  Open Approved Folder
                </button>
                <button
                  style={{ ...buttonStyle, borderColor: "rgba(255, 193, 7, 0.55)", color: "#FFD54F" }}
                  onClick={() =>
                    submitCommand({
                      actionType: "DRAFT_MESSAGE_PREVIEW",
                      targetScope: "channel:whatsapp_draft",
                      summaryText: "Sensitive: Draft / Review WhatsApp Message",
                      confirmationRequired: true,
                    })
                  }
                >
                  Draft WhatsApp Message (Requires Confirm)
                </button>
              </div>
            </section>

            {/* Command History & Confirmation Queue */}
            <section style={cardStyle}>
              <h2 style={{ marginTop: 0, fontSize: 18 }}>Command Requests & Confirmation Queue</h2>
              {commands.length === 0 ? (
                <p style={{ color: "#8FA8C8", fontSize: 13 }}>No desktop commands queued yet.</p>
              ) : (
                <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
                  {commands.map((c) => (
                    <li
                      key={c.id}
                      style={{
                        padding: "10px 12px",
                        marginBottom: 8,
                        borderRadius: 10,
                        background: "rgba(3, 7, 18, 0.65)",
                        border: "1px solid rgba(255, 255, 255, 0.08)",
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                        gap: 10,
                        flexWrap: "wrap",
                      }}
                    >
                      <div>
                        <div style={{ fontSize: 14, fontWeight: 600 }}>
                          {c.actionType}: {c.summaryText}
                        </div>
                        <div style={{ fontSize: 12, color: "#8FA8C8", marginTop: 2 }}>
                          Scope: <code>{c.targetScope}</code> · Status:{" "}
                          <strong style={{ color: c.status === "EXECUTED" ? "#00E676" : "#00E5FF" }}>
                            {c.status}
                          </strong>
                        </div>
                      </div>
                      {c.status === "PENDING_CONFIRMATION" && (
                        <div style={{ display: "flex", gap: 8 }}>
                          <button style={primaryButtonStyle} onClick={() => confirmCommand(c.id)}>
                            Confirm
                          </button>
                          <button
                            style={{ ...buttonStyle, borderColor: "rgba(255, 82, 82, 0.5)", color: "#FF8A80" }}
                            onClick={() => cancelCommand(c.id)}
                          >
                            Cancel
                          </button>
                        </div>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </>
        )}
      </div>
    </main>
  );
}
