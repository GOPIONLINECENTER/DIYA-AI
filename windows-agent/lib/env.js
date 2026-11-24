const fs = require("fs");
const path = require("path");

function loadDotEnv(envFilePath) {
  if (!envFilePath || !fs.existsSync(envFilePath)) return;
  const raw = fs.readFileSync(envFilePath, "utf8");
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const eqIndex = trimmed.indexOf("=");
    if (eqIndex <= 0) continue;
    const key = trimmed.slice(0, eqIndex).trim();
    let value = trimmed.slice(eqIndex + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    if (!(key in process.env) || process.env[key] === "") {
      process.env[key] = value;
    }
  }
}

function redactSensitiveText(text) {
  return String(text || "")
    .replace(/Bearer\s+[A-Za-z0-9\-._~+/]+=*/gi, "Bearer [REDACTED]")
    .replace(/diya_agent_v1\.[A-Za-z0-9\-_]+\.[A-Za-z0-9\-_]+/g, "diya_agent_v1.[REDACTED]");
}

function createLogger(component = "DIYA-AGENT") {
  function write(level, message, meta) {
    const ts = new Date().toISOString();
    const safeMsg = redactSensitiveText(message);
    const suffix = meta ? ` ${redactSensitiveText(JSON.stringify(meta))}` : "";
    const line = `[${ts}] [${component}] [${level}] ${safeMsg}${suffix}`;
    if (level === "ERROR") {
      console.error(line);
    } else if (level === "WARN") {
      console.warn(line);
    } else {
      console.log(line);
    }
  }

  return {
    info: (msg, meta) => write("INFO", msg, meta),
    warn: (msg, meta) => write("WARN", msg, meta),
    error: (msg, meta) => write("ERROR", msg, meta),
  };
}

module.exports = {
  loadDotEnv,
  redactSensitiveText,
  createLogger,
};
