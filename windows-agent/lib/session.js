const fs = require("fs");
const path = require("path");

function computeBackoffDelayMs(attempt, baseMs = 1000, maxMs = 60000, jitterRatio = 0.2) {
  const safeAttempt = Math.max(0, Number(attempt) || 0);
  const exponential = Math.min(maxMs, baseMs * Math.pow(2, safeAttempt));
  const jitter = exponential * jitterRatio * Math.random();
  return Math.round(exponential + jitter);
}

function isTokenExpiringSoon(expiresAtIso, thresholdMs = 6 * 60 * 60 * 1000) {
  if (!expiresAtIso) return true;
  const expMs = Date.parse(expiresAtIso);
  if (Number.isNaN(expMs)) return true;
  return expMs - Date.now() <= thresholdMs;
}

function isTokenExpired(expiresAtIso) {
  if (!expiresAtIso) return true;
  const expMs = Date.parse(expiresAtIso);
  if (Number.isNaN(expMs)) return true;
  return Date.now() >= expMs;
}

function loadSession(sessionFilePath) {
  try {
    if (!fs.existsSync(sessionFilePath)) return null;
    const parsed = JSON.parse(fs.readFileSync(sessionFilePath, "utf8"));
    if (!parsed || typeof parsed.agentToken !== "string" || !parsed.agentToken) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

function saveSession(sessionFilePath, sessionData) {
  fs.mkdirSync(path.dirname(sessionFilePath), { recursive: true });
  const payload = {
    pcDeviceId: sessionData.pcDeviceId,
    pcDeviceName: sessionData.pcDeviceName,
    agentToken: sessionData.agentToken,
    expiresAt: sessionData.expiresAt,
    updatedAt: new Date().toISOString(),
  };
  fs.writeFileSync(sessionFilePath, JSON.stringify(payload, null, 2), { mode: 0o600 });
}

function clearSession(sessionFilePath) {
  try {
    if (fs.existsSync(sessionFilePath)) {
      fs.unlinkSync(sessionFilePath);
    }
  } catch {
    // Ignore cleanup error
  }
}

module.exports = {
  computeBackoffDelayMs,
  isTokenExpiringSoon,
  isTokenExpired,
  loadSession,
  saveSession,
  clearSession,
};
